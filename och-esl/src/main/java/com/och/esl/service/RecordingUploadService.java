package com.och.esl.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.och.common.constant.SysSettingConfig;
import com.och.common.domain.CallInfo;
import com.och.common.domain.file.FileUploadVo;
import com.och.file.config.S3StorageConfig;
import com.och.file.handler.S3FileUploadHandler;
import com.och.system.domain.entity.CallRecord;
import com.och.system.mapper.CallRecordMapper;
import com.och.system.service.ICallRecordService;
import com.och.system.util.RecordingFileResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 录音文件上传服务
 * <p>
 * 负责将本地录音文件异步上传至MinIO对象存储，并更新call_record表中的录音状态。
 * 录音状态码：0-未上传  1-上传中  2-已上传  3-上传失败
 * </p>
 *
 * @author och
 */
@Slf4j
@Service
public class RecordingUploadService {

    /**
     * Maximum number of retries when waiting for call_record row to be created.
     * The row is created during CHANNEL_HANGUP_COMPLETE which may fire after RECORD_STOP.
     */
    private static final int MAX_WAIT_RETRIES = 30;

    /**
     * Sleep interval between retries (seconds).
     */
    private static final long WAIT_INTERVAL_SECONDS = 2;
    private static final int FILE_WAIT_RETRIES = 30;
    private static final long FILE_WAIT_INTERVAL_SECONDS = 1;
    private static final int MAX_UPLOAD_ATTEMPTS = 3;
    private static final long UPLOAD_RETRY_INTERVAL_SECONDS = 2;
    private static final int MAX_DB_UPDATE_ATTEMPTS = 5;
    private static final long DB_UPDATE_RETRY_INTERVAL_SECONDS = 1;

    @Autowired
    private CallRecordMapper callRecordMapper;

    @Autowired
    private IFsCallCacheService fsCallCacheService;

    @Autowired
    private ICallRecordService callRecordService;

    @Autowired
    private SysSettingConfig sysSettingConfig;

    @Autowired
    private S3StorageConfig storageConfig;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void retryPendingRecordings() {
        List<CallRecord> records = callRecordMapper.selectList(
                new LambdaQueryWrapper<CallRecord>()
                        .isNotNull(CallRecord::getFilePath)
                        .and(wrapper -> wrapper.isNull(CallRecord::getRecordStatus)
                                .or().in(CallRecord::getRecordStatus, 0, 1, 3))
                        .orderByDesc(CallRecord::getId)
                        .last("LIMIT 200")
        );
        for (CallRecord record : records) {
            if (record == null || StrUtil.isBlank(record.getCallId()) || StrUtil.isBlank(record.getFilePath())) {
                continue;
            }
            uploadRecordingInternal(record.getCallId(), record.getFilePath(), record.getRecordDuration(), false);
        }
    }

    /**
     * Asynchronously upload a recording file to MinIO and update the call_record row.
     * <p>
     * This method is designed to be called from the RECORD_STOP event handler.
     * Since the call_record row may not exist yet at that point (it is created
     * during CHANNEL_HANGUP_COMPLETE), the method polls the database with a
     * short sleep until the row appears or the retry limit is exhausted.
     * </p>
     *
     * @param callId         the call ID used to locate the call_record row
     * @param localFilePath  the absolute path to the local recording file
     * @param recordDuration recording duration in seconds
     */
    @Async
    public void uploadRecording(String callId, String localFilePath, Integer recordDuration) {
        if (StrUtil.isBlank(callId)) {
            log.warn("RecordingUploadService abort: callId is blank");
            return;
        }
        if (StrUtil.isBlank(localFilePath)) {
            try {
                CallRecord existing = callRecordService.getByCallId(callId);
                if (existing != null) {
                    localFilePath = existing.getFilePath();
                    if (recordDuration == null) {
                        recordDuration = existing.getRecordDuration();
                    }
                    log.info("录音事件未携带路径，使用话单路径继续上传 callId:{} path:{}",
                            callId, localFilePath);
                }
            } catch (Exception exception) {
                log.warn("查询话单录音路径失败 callId:{} error:{}", callId, exception.getMessage());
            }
        }
        uploadRecordingInternal(callId, localFilePath, recordDuration, true);
    }

    private void uploadRecordingInternal(String callId, String localFilePath, Integer recordDuration,
                                         boolean waitForFile) {
        log.info("RecordingUploadService started: callId={}, localFilePath={}, duration={}s",
                callId, localFilePath, recordDuration);

        if (StrUtil.isBlank(callId) || StrUtil.isBlank(localFilePath)) {
            log.warn("RecordingUploadService abort: callId or localFilePath is blank. callId={}", callId);
            return;
        }

        // 录音事件可能先于挂机事件，先从 Redis 快照或最小信息补建话单，再开始上传。
        CallRecord callRecord = ensureCallRecord(callId, localFilePath, recordDuration);
        if (callRecord == null) {
            callRecord = waitForCallRecord(callId);
        }
        if (callRecord == null) {
            log.error("call_record not found after fallback and {} retries for callId={}. Aborting upload.",
                    MAX_WAIT_RETRIES, callId);
            return;
        }

        if (Integer.valueOf(2).equals(callRecord.getRecordStatus())
                && StrUtil.isNotBlank(callRecord.getRecordUrl())) {
            log.debug("Recording already uploaded, skipping duplicate event: callId={}", callId);
            return;
        }
        if (reconcileExistingRemoteRecording(callRecord, localFilePath, recordDuration)) {
            return;
        }

        Path recordingPath = waitForRecordingFile(localFilePath, waitForFile);
        if (recordingPath == null) {
            log.error("Recording file not found or empty after waiting: {}. Marking record_status=3 (failed) for callId={}",
                    localFilePath, callId);
            markUploadFailed(callRecord, recordDuration);
            return;
        }

        File recordingFile = recordingPath.toFile();
        if (!recordingPath.toString().equals(localFilePath)) {
            log.warn("Recording path was resolved from configured root: callId={}, requested={}, actual={}",
                    callId, localFilePath, recordingPath);
            callRecord.setFilePath(recordingPath.toString());
        }

        callRecordService.updateRecordingFields(callId, recordingPath.toString(), null, recordDuration, 1);

        Exception lastException = null;
        FileUploadVo uploadResult = null;
        for (int attempt = 1; attempt <= MAX_UPLOAD_ATTEMPTS; attempt++) {
            try {
                S3FileUploadHandler s3Uploader = new S3FileUploadHandler(sysSettingConfig, storageConfig);
                FileUploadVo result = s3Uploader.upload(recordingFile);
                if (result == null || StrUtil.isBlank(result.getFilePath())) {
                    throw new IllegalStateException("S3上传返回空文件地址");
                }
                uploadResult = result;
                break;
            } catch (Exception e) {
                lastException = e;
                log.warn("Recording upload attempt failed: callId={}, attempt={}/{}, error={}",
                        callId, attempt, MAX_UPLOAD_ATTEMPTS, e.getMessage());
                if (attempt < MAX_UPLOAD_ATTEMPTS) {
                    sleepBeforeRetry(callId, UPLOAD_RETRY_INTERVAL_SECONDS);
                }
            }
        }
        if (uploadResult == null) {
            log.error("Recording upload failed after retries: callId={}, error={}", callId,
                    lastException == null ? "unknown" : lastException.getMessage(), lastException);
            markUploadFailed(callRecord, recordDuration);
            return;
        }
        if (!persistUploadedRecording(callId, recordingPath.toString(), uploadResult.getFilePath(), recordDuration)) {
            log.error("Recording object uploaded but database status update failed: callId={}, objectKey={}, url={}",
                    callId, uploadResult.getCosId(), uploadResult.getFilePath());
            return;
        }
        log.info("Recording uploaded successfully and database verified: callId={}, objectKey={}, url={}",
                callId, uploadResult.getCosId(), uploadResult.getFilePath());
    }

    private boolean reconcileExistingRemoteRecording(CallRecord callRecord, String localFilePath,
                                                     Integer recordDuration) {
        if (callRecord == null || StrUtil.isBlank(callRecord.getRecordUrl())) {
            return false;
        }
        try {
            String objectKey = extractObjectKey(callRecord.getRecordUrl());
            S3FileUploadHandler s3Uploader = new S3FileUploadHandler(sysSettingConfig, storageConfig);
            if (s3Uploader.contentLength(objectKey) <= 0) {
                return false;
            }
            boolean persisted = persistUploadedRecording(
                    callRecord.getCallId(), localFilePath, callRecord.getRecordUrl(), recordDuration);
            if (persisted) {
                log.info("Reconciled existing RustFS recording status: callId={}, objectKey={}",
                        callRecord.getCallId(), objectKey);
            }
            return persisted;
        } catch (Exception e) {
            log.warn("Existing recording URL verification failed, falling back to local upload: callId={}, url={}, error={}",
                    callRecord.getCallId(), callRecord.getRecordUrl(), e.getMessage());
            return false;
        }
    }

    String extractObjectKey(String recordUrl) {
        if (StrUtil.isBlank(recordUrl)) {
            return recordUrl;
        }
        String marker = "/" + storageConfig.getBucket() + "/";
        int markerIndex = recordUrl.indexOf(marker);
        if (markerIndex >= 0) {
            return recordUrl.substring(markerIndex + marker.length());
        }
        String normalized = recordUrl.replaceFirst("^https?://", "");
        int firstSlash = normalized.indexOf('/');
        return firstSlash >= 0 ? normalized.substring(firstSlash + 1) : normalized;
    }

    boolean persistUploadedRecording(String callId, String localFilePath,
                                     String recordUrl, Integer recordDuration) {
        for (int attempt = 1; attempt <= MAX_DB_UPDATE_ATTEMPTS; attempt++) {
            boolean updated = callRecordService.updateRecordingFields(
                    callId, localFilePath, recordUrl, recordDuration, 2);
            CallRecord persisted = callRecordService.getByCallId(callId);
            if (persisted != null
                    && Integer.valueOf(2).equals(persisted.getRecordStatus())
                    && Objects.equals(recordUrl, persisted.getRecordUrl())) {
                return true;
            }
            log.warn("Recording database verification failed: callId={}, attempt={}/{}, updated={}, status={}, url={}",
                    callId, attempt, MAX_DB_UPDATE_ATTEMPTS, updated,
                    persisted == null ? null : persisted.getRecordStatus(),
                    persisted == null ? null : persisted.getRecordUrl());
            if (attempt < MAX_DB_UPDATE_ATTEMPTS) {
                sleepBeforeRetry(callId, DB_UPDATE_RETRY_INTERVAL_SECONDS);
            }
        }
        return false;
    }

    private Path waitForRecordingFile(String requestedPath, boolean waitForFile) {
        int attempts = waitForFile ? FILE_WAIT_RETRIES : 1;
        String configuredRoot = RecordingFileResolver.resolveConfiguredRoot(sysSettingConfig.getFsProfile());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            Path resolved = RecordingFileResolver.resolveExisting(requestedPath, configuredRoot);
            if (resolved != null) {
                return resolved;
            }
            if (attempt < attempts) {
                log.debug("Waiting for recording file: requested={}, attempt={}/{}",
                        requestedPath, attempt, attempts);
                sleepBeforeRetry(requestedPath, FILE_WAIT_INTERVAL_SECONDS);
            }
        }
        return null;
    }

    private void markUploadFailed(CallRecord record, Integer recordDuration) {
        if (record == null || Integer.valueOf(2).equals(record.getRecordStatus())) {
            return;
        }
        callRecordService.updateRecordingFields(
                record.getCallId(), record.getFilePath(), null, recordDuration, 3);
    }

    private CallRecord ensureCallRecord(String callId, String localFilePath, Integer recordDuration) {
        try {
            CallInfo callInfo = fsCallCacheService.getCallInfo(Long.valueOf(callId));
            if (callInfo != null) {
                callInfo.setRecord(localFilePath);
                if (recordDuration != null && recordDuration > 0 && callInfo.getRecordTime() == null) {
                    callInfo.setRecordTime(recordDuration.longValue() * 1000L);
                }
                CallRecord record = callRecordService.ensureRecordingRecord(callInfo);
                if (record != null) {
                    return record;
                }
            }
        } catch (NumberFormatException exception) {
            log.warn("录音上传 callId 不是数字，无法读取 Redis 呼叫快照 callId:{}", callId);
        } catch (Exception exception) {
            log.warn("从 Redis 快照补建录音话单失败 callId:{} error:{}", callId, exception.getMessage());
        }

        // Redis 关系在挂机清理时可能已经删除，仍用录音事件参数保留一条最小话单。
        CallRecord minimal = new CallRecord();
        minimal.setCallId(callId);
        minimal.setFilePath(localFilePath);
        minimal.setCallState(2);
        minimal.setAnswerFlag(0);
        minimal.setRecordStatus(0);
        minimal.setRecordDuration(recordDuration);
        minimal.setCallStartTime(new java.util.Date());
        try {
            if (!callRecordService.saveOrUpdateByCallId(minimal)) {
                return callRecordService.getByCallId(callId);
            }
            return callRecordService.getByCallId(callId);
        } catch (Exception exception) {
            log.error("使用录音事件参数补建最小话单失败 callId:{}", callId, exception);
            return null;
        }
    }

    private void sleepBeforeRetry(String value, long seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for recording retry: {}", value);
        }
    }

    /**
     * Poll the database until a call_record row with the given callId appears,
     * or the maximum number of retries is exhausted.
     *
     * @param callId the call ID to query
     * @return the CallRecord entity, or null if not found within the retry limit
     */
    private CallRecord waitForCallRecord(String callId) {
        for (int i = 0; i < MAX_WAIT_RETRIES; i++) {
            CallRecord record = callRecordMapper.selectOne(
                    new LambdaQueryWrapper<CallRecord>()
                            .eq(CallRecord::getCallId, callId)
                            .last("LIMIT 1")
            );
            if (record != null) {
                return record;
            }
            try {
                log.debug("Waiting for call_record row: callId={}, attempt={}/{}",
                        callId, i + 1, MAX_WAIT_RETRIES);
                TimeUnit.SECONDS.sleep(WAIT_INTERVAL_SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for call_record: callId={}", callId);
                return null;
            }
        }
        return null;
    }
}
