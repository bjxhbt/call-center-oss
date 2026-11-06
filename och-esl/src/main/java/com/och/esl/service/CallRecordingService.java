package com.och.esl.service;

import cn.hutool.core.date.DateUtil;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.SysSettingConfig;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.esl.client.FsClient;
import com.och.system.domain.entity.CallRecord;
import com.och.system.service.ICallRecordService;
import com.och.system.util.RecordingFileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallRecordingService {

    private static final String AFTER_PLAYBACK_KEY_PREFIX = "och:call:record:after-playback:";
    private static final String NOTICE_LOCK_KEY_PREFIX = "och:call:record:notice:";
    private static final String START_LOCK_KEY_PREFIX = "och:call:record:start:";
    private static final long MARKER_TTL_MINUTES = 10;
    private static final long START_LOCK_TTL_HOURS = 24;
    private static final Set<PosixFilePermission> RECORDING_DIRECTORY_PERMISSIONS =
            PosixFilePermissions.fromString("rwxrwxr-x");

    private final RedisService redisService;
    private final IFsCallCacheService fsCallCacheService;
    private final ObjectProvider<FsClient> fsClientProvider;
    private final SysSettingConfig sysSettingConfig;
    private final ICallRecordService callRecordService;

    public boolean armAfterPlayback(Long callId, String uniqueId) {
        if (callId == null || StringUtils.isBlank(uniqueId)) {
            return false;
        }
        if (!claimRecordingNotice(callId, uniqueId)) {
            log.info("通话录音提示已触发，忽略重复请求 callId:{}, uniqueId:{}", callId, uniqueId);
            return false;
        }
        redisService.setCacheObject(afterPlaybackKey(callId, uniqueId), Boolean.TRUE,
                MARKER_TTL_MINUTES, TimeUnit.MINUTES);
        log.info("通话录音已等待提示音播放完成 callId:{}, uniqueId:{}", callId, uniqueId);
        return true;
    }

    public boolean claimRecordingNotice(Long callId, String uniqueId) {
        return callId != null && StringUtils.isNotBlank(uniqueId)
                && Boolean.TRUE.equals(redisService.tryLock(NOTICE_LOCK_KEY_PREFIX + callId, uniqueId,
                MARKER_TTL_MINUTES, TimeUnit.MINUTES));
    }

    public void cancelAfterPlayback(Long callId, String uniqueId) {
        if (callId == null || StringUtils.isBlank(uniqueId)) {
            return;
        }
        redisService.deleteObject(afterPlaybackKey(callId, uniqueId));
    }

    public boolean startAfterPlayback(String address, Long callId, String uniqueId) {
        if (callId == null || StringUtils.isBlank(uniqueId)) {
            return false;
        }
        String markerKey = afterPlaybackKey(callId, uniqueId);
        if (!redisService.keyIsExists(markerKey)) {
            return false;
        }
        CallInfo callInfo = fsCallCacheService.getCallInfo(callId);
        redisService.deleteObject(markerKey);
        if (callInfo != null && (StringUtils.isNotBlank(callInfo.getRecord())
                || callInfo.getRecordStartTime() != null)) {
            log.info("通话录音已在坐席接听时启动，提示音播放完成 callId:{} uniqueId:{} record:{}",
                    callId, uniqueId, callInfo.getRecord());
            return true;
        }
        return startRecording(address, callId, uniqueId);
    }

    public boolean startRecordingOnAgentAnswer(String address, CallInfo callInfo, String agentUniqueId) {
        if (callInfo == null || callInfo.getCallId() == null || StringUtils.isBlank(agentUniqueId)) {
            return false;
        }
        String callerUniqueId = findCallerUniqueId(callInfo, agentUniqueId);
        if (StringUtils.isBlank(callerUniqueId)) {
            log.warn("坐席接听时未找到主叫通道，无法启动录音 callId:{} agentUuid:{}",
                    callInfo.getCallId(), agentUniqueId);
            return false;
        }
        return startRecording(address, callInfo.getCallId(), callerUniqueId);
    }

    public boolean startRecording(String address, Long callId, String uniqueId) {
        if (StringUtils.isBlank(address) || callId == null || StringUtils.isBlank(uniqueId)) {
            log.warn("录音启动参数不完整 address:{}, callId:{}, uniqueId:{}", address, callId, uniqueId);
            return false;
        }

        CallInfo callInfo = fsCallCacheService.getCallInfo(callId);
        if (callInfo == null) {
            log.warn("录音启动失败，未找到呼叫信息 callId:{}", callId);
            return false;
        }
        if (StringUtils.isNotBlank(callInfo.getRecord()) || callInfo.getRecordStartTime() != null) {
            log.info("通话录音已启动，忽略重复请求 callId:{}, record:{}", callId, callInfo.getRecord());
            return false;
        }

        String lockKey = START_LOCK_KEY_PREFIX + callId;
        String lockValue = uniqueId + ":" + System.currentTimeMillis();
        if (!Boolean.TRUE.equals(redisService.tryLock(lockKey, lockValue,
                START_LOCK_TTL_HOURS, TimeUnit.HOURS))) {
            log.info("通话录音启动请求已被处理，忽略重复请求 callId:{}", callId);
            return false;
        }

        String recordPath = buildRecordPath(callId);
        long recordStartTime = System.currentTimeMillis();
        try {
            createParentDirectory(recordPath);
            callInfo = fsCallCacheService.getCallInfo(callId);
            if (callInfo == null) {
                redisService.unLock(lockKey, lockValue);
                return false;
            }
            if (StringUtils.isNotBlank(callInfo.getRecord()) || callInfo.getRecordStartTime() != null) {
                return false;
            }

            callInfo.setRecord(recordPath);
            callInfo.setRecordStartTime(recordStartTime);
            fsCallCacheService.saveCallInfo(callInfo);

            // RECORD_STOP 可能早于最后一腿挂机事件到达，先把录音话单幂等落库，
            // 上传线程不再依赖“最后一腿挂机”这个容易发生竞态的时点。
            try {
                CallRecord recordingRecord = callRecordService.ensureRecordingRecord(callInfo);
                if (recordingRecord == null) {
                    log.warn("录音话单预创建未返回记录 callId:{} record:{}", callId, recordPath);
                } else {
                    log.info("录音话单已预创建 callId:{} record:{} recordId:{}",
                            callId, recordPath, recordingRecord.getId());
                }
            } catch (Exception exception) {
                // 数据库短暂异常不阻断通话及 FreeSWITCH 录音，RECORD_STOP 会再次补偿建单。
                log.error("录音话单预创建异常 callId:{} record:{}", callId, recordPath, exception);
            }

            if (!fsClientProvider.getObject().record(address, callId, uniqueId, recordPath)) {
                callInfo.setRecord(null);
                callInfo.setRecordStartTime(null);
                fsCallCacheService.saveCallInfo(callInfo);
                redisService.unLock(lockKey, lockValue);
                log.error("FreeSWITCH拒绝启动录音 callId:{}, uniqueId:{}, record:{}",
                        callId, uniqueId, recordPath);
                return false;
            }
            log.info("通话提示播放完成，录音已启动 callId:{}, uniqueId:{}, record:{}",
                    callId, uniqueId, recordPath);
            return true;
        } catch (Exception exception) {
            CallInfo cachedCallInfo = fsCallCacheService.getCallInfo(callId);
            if (cachedCallInfo != null && StringUtils.equals(recordPath, cachedCallInfo.getRecord())) {
                cachedCallInfo.setRecord(null);
                cachedCallInfo.setRecordStartTime(null);
                fsCallCacheService.saveCallInfo(cachedCallInfo);
            }
            redisService.unLock(lockKey, lockValue);
            log.error("启动通话录音异常 callId:{}, uniqueId:{}, record:{}",
                    callId, uniqueId, recordPath, exception);
            return false;
        }
    }

    private String buildRecordPath(Long callId) {
        String basePath = StringUtils.removeEnd(resolveRecordingRoot(), "/");
        String suffix = StringUtils.defaultIfBlank(sysSettingConfig.getFsFileSuffix(), ".wav");
        return basePath + "/" + DateUtil.today() + "/" + callId + "_" + DateUtil.current() + suffix;
    }

    private void createParentDirectory(String recordPath) throws Exception {
        Path parent = Paths.get(recordPath).getParent();
        if (parent != null) {
            Files.createDirectories(parent);
            try {
                Files.setPosixFilePermissions(parent, RECORDING_DIRECTORY_PERMISSIONS);
            } catch (UnsupportedOperationException | java.io.IOException ignored) {
                log.debug("无法设置录音目录 POSIX 权限，继续使用现有权限: {}", parent);
            }
        }
    }

    private String resolveRecordingRoot() {
        String configured = RecordingFileResolver.resolveConfiguredRoot(sysSettingConfig.getFsProfile());

        try {
            return Paths.get(configured.replace('\\', '/')).toAbsolutePath().normalize().toString();
        } catch (Exception exception) {
            log.warn("录音目录配置无效: {}", configured);
            return Paths.get(RecordingFileResolver.resolveConfiguredRoot(null)).toAbsolutePath().normalize().toString();
        }
    }

    private String afterPlaybackKey(Long callId, String uniqueId) {
        return AFTER_PLAYBACK_KEY_PREFIX + callId + ":" + uniqueId;
    }

    private String findCallerUniqueId(CallInfo callInfo, String agentUniqueId) {
        if (callInfo.getChannelMap() == null || callInfo.getChannelMap().isEmpty()) {
            return null;
        }
        ChannelInfo agentChannel = callInfo.getChannelMap().get(agentUniqueId);
        if (agentChannel != null && StringUtils.isNotBlank(agentChannel.getOtherUniqueId())) {
            ChannelInfo peerChannel = callInfo.getChannelMap().get(agentChannel.getOtherUniqueId());
            if (peerChannel != null && Integer.valueOf(1).equals(peerChannel.getDirectionType())) {
                return peerChannel.getUniqueId();
            }
        }
        return callInfo.getChannelMap().values().stream()
                .filter(channel -> channel != null && Integer.valueOf(1).equals(channel.getDirectionType()))
                .map(ChannelInfo::getUniqueId)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }
}
