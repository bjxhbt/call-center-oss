package com.och.esl.listener;

import com.och.esl.client.FsClient;
import com.och.esl.event.CallBridgeEvent;
import com.och.esl.service.CallRecordingService;
import com.och.common.enums.DirectionEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallRecordingBridgeListener {

    private final FsClient fsClient;
    private final CallRecordingService callRecordingService;

    @Value(value = "${och.recording.notice-path:}")
    private String recordingNoticePath;

    @EventListener
    public void onBridge(CallBridgeEvent event) {
        if (event == null || event.getCallId() == null) {
            return;
        }
        if (Objects.equals(event.getDirection(), DirectionEnum.OUTBOUND.getType())) {
            return;
        }
        try {
            String address = fsClient.anyAddress();
            if (!StringUtils.hasText(address)) {
                log.warn("Recording on bridge skipped: no FS connection callId:{}", event.getCallId());
                return;
            }
            String playUuid = StringUtils.hasText(event.getRecordingUniqueId())
                    ? event.getRecordingUniqueId()
                    : StringUtils.hasText(event.getOtherUniqueId())
                    ? event.getOtherUniqueId() : event.getUniqueId();
            boolean recordingStarted = callRecordingService.startRecording(
                    address, event.getCallId(), playUuid);
            String noticePath = resolveNoticePath();
            if (StringUtils.hasText(noticePath)
                    && callRecordingService.claimRecordingNotice(event.getCallId(), playUuid)) {
                boolean noticeStarted = fsClient.broadcastFile(address, playUuid, noticePath, "aleg");
                log.info("真实桥接录音链路 callId:{} uniqueId:{} recordingStarted:{} noticeStarted:{} file:{}",
                        event.getCallId(), playUuid, recordingStarted, noticeStarted, noticePath);
            } else if (!StringUtils.hasText(noticePath)) {
                log.info("录音提示未配置，录音链路继续 callId:{} uniqueId:{} recordingStarted:{}",
                        event.getCallId(), playUuid, recordingStarted);
            } else {
                log.info("录音提示已播放，跳过重复桥接事件 callId:{} uniqueId:{}",
                        event.getCallId(), playUuid);
            }
        } catch (Exception e) {
            log.error("Start recording on bridge error (ignored) callId:{} err:{}", event.getCallId(), e.getMessage(), e);
        }
    }

    private String resolveNoticePath() {
        if (StringUtils.hasText(recordingNoticePath)) {
            return recordingNoticePath.trim();
        }
        return null;
    }
}
