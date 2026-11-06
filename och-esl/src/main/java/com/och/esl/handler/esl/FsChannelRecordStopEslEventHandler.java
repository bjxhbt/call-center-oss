
package com.och.esl.handler.esl;

import com.alibaba.fastjson.JSONObject;
import com.och.common.annotation.EslEventName;
import com.och.common.constant.EslEventNames;
import com.och.common.domain.CallInfo;
import com.och.common.utils.StringUtils;
import com.och.esl.factory.AbstractFsEslEventHandler;
import com.och.esl.service.RecordingUploadService;
import com.och.esl.utils.EslEventUtil;
import lombok.extern.slf4j.Slf4j;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 结束录音
 * @author danmo
 * @date 2023年09月18日 19:03
 */
@Slf4j
@EslEventName(EslEventNames.RECORD_STOP)
@Component
public class FsChannelRecordStopEslEventHandler extends AbstractFsEslEventHandler {

    @Autowired
    private RecordingUploadService recordingUploadService;

    @Override
    public void handleEslEvent(String address, EslEvent event) {
        log.info("LfsChannelRecordStopEslEventHandler EslEvent:{}.", JSONObject.toJSONString(event));
        String uniqueId = EslEventUtil.getUniqueId(event);
        CallInfo callInfo = resolveCallInfo(event);
        String eventRecordPath = EslEventUtil.getRecordFilePath(event);
        if (StringUtils.isBlank(eventRecordPath)) {
            String applicationData = EslEventUtil.getApplicationData(event);
            if (isRecordingPath(applicationData)) {
                eventRecordPath = applicationData;
            }
        }
        Long eventCallId = parseCallId(EslEventUtil.getOchCallId(event));
        if (eventCallId == null) {
            eventCallId = parseCallIdFromRecordPath(eventRecordPath);
        }
        if (callInfo == null && eventCallId == null) {
            log.warn("录音停止事件无法关联呼叫，跳过上传 uniqueId:{}", uniqueId);
            return;
        }
        long recordEndTime = event.getEventDateTimestamp() > 0
                ? event.getEventDateTimestamp() / 1000
                : System.currentTimeMillis();
        if (StringUtils.isNotBlank(eventRecordPath)) {
            eventRecordPath = eventRecordPath.trim();
            log.info("从 FreeSWITCH RECORD_STOP 事件取得录音路径 callId:{} path:{}",
                    callInfo == null ? eventCallId : callInfo.getCallId(), eventRecordPath);
        }
        Long recordLengthMillis = parseRecordLengthMillis(event);
        Integer durationSeconds;
        Long callId;
        if (callInfo != null) {
            if (StringUtils.isNotBlank(eventRecordPath)) {
                callInfo.setRecord(eventRecordPath);
            }
            callInfo.setRecordEndTime(recordEndTime);
            if (callInfo.getRecordStartTime() == null && recordLengthMillis != null && recordLengthMillis > 0) {
                callInfo.setRecordStartTime(Math.max(0L, recordEndTime - recordLengthMillis));
                log.warn("录音停止事件缺少开始事件，使用 FreeSWITCH Record-Length 反推开始时间 callId:{} lengthMs:{}",
                        callInfo.getCallId(), recordLengthMillis);
            }
            if (callInfo.getRecordStartTime() != null) {
                callInfo.setRecordTime(Math.max(0, recordEndTime - callInfo.getRecordStartTime()));
            } else {
                log.warn("录音停止事件缺少开始时间 callId:{}, uniqueId:{}", callInfo.getCallId(), uniqueId);
                callInfo.setRecordTime(recordLengthMillis == null ? 0L : recordLengthMillis);
            }
            try {
                iCallRecordService.ensureRecordingRecord(callInfo);
            } catch (Exception exception) {
                log.error("录音停止事件补偿建单失败 callId:{} uniqueId:{}",
                        callInfo.getCallId(), uniqueId, exception);
            }
            ifsCallCacheService.saveCallInfo(callInfo);
            callId = callInfo.getCallId();
            durationSeconds = durationSeconds(callInfo.getRecordTime());
            if (StringUtils.isBlank(eventRecordPath)) {
                eventRecordPath = callInfo.getRecord();
            }
        } else {
            callId = eventCallId;
            durationSeconds = durationSeconds(recordLengthMillis);
            log.warn("录音停止事件关联缓存已清理，使用事件参数继续上传 callId:{} uniqueId:{} path:{}",
                    callId, uniqueId, eventRecordPath);
        }

        triggerRecordingUpload(callId, eventRecordPath, durationSeconds);
    }

    private void triggerRecordingUpload(Long callId, String recordPath, Integer durationSeconds) {
        if (callId == null) {
            log.warn("录音上传缺少 callId，跳过");
            return;
        }
        log.info("Triggering async recording upload: callId={}, path={}, duration={}s",
                callId, recordPath, durationSeconds);

        recordingUploadService.uploadRecording(String.valueOf(callId), recordPath, durationSeconds);
    }

    private Integer durationSeconds(Long recordTimeMillis) {
        if (recordTimeMillis == null || recordTimeMillis <= 0) {
            return 0;
        }
        return (int) (recordTimeMillis / 1000L);
    }

    private Long parseCallId(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            log.warn("无法解析录音事件中的 och_call_id:{}", value);
            return null;
        }
    }

    private Long parseCallIdFromRecordPath(String recordPath) {
        if (StringUtils.isBlank(recordPath)) {
            return null;
        }
        String fileName = recordPath.trim().replace('\\', '/');
        int slashIndex = fileName.lastIndexOf('/');
        if (slashIndex >= 0) {
            fileName = fileName.substring(slashIndex + 1);
        }
        int separatorIndex = fileName.indexOf('_');
        if (separatorIndex <= 0) {
            return null;
        }
        return parseCallId(fileName.substring(0, separatorIndex));
    }

    private Long parseRecordLengthMillis(EslEvent event) {
        String value = EslEventUtil.getRecordLength(event);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            log.warn("无法解析 FreeSWITCH Record-Length value:{}", value);
            return null;
        }
    }

    private boolean isRecordingPath(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.endsWith(".wav")
                || normalized.endsWith(".mp3")
                || normalized.endsWith(".amr")
                || normalized.endsWith(".gsm")
                || normalized.endsWith(".ogg");
    }
}
