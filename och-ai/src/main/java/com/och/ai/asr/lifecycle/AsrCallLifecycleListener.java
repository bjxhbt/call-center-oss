package com.och.ai.asr.lifecycle;

import com.och.ai.asr.session.AsrSessionManager;
import com.och.esl.event.CallBridgeEvent;
import com.och.esl.event.CallHangupEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Call lifecycle listener for ASR (plan 9.5).
 *
 * <p>On bridge: create ASR session then start FreeSWITCH audio stream.
 * On hangup: stop stream then finalize session. Never impacts call continuity.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrCallLifecycleListener {

    private final AsrSessionManager sessionManager;
    private final AsrFsAudioStreamService audioStreamService;

    @Async
    @EventListener
    public void onBridge(CallBridgeEvent event) {
        try {
            sessionManager.onBridge(event);
        } catch (Exception e) {
            log.error("handle bridge event error (ignored) callId:{} err:{}",
                    event != null ? event.getCallId() : null, e.getMessage(), e);
        }
        try {
            audioStreamService.startOnBridge(event);
        } catch (Exception e) {
            log.error("start ASR audio stream error (ignored) callId:{} err:{}",
                    event != null ? event.getCallId() : null, e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    public void onHangup(CallHangupEvent event) {
        try {
            audioStreamService.stopOnHangup(event);
        } catch (Exception e) {
            log.error("stop ASR audio stream error (ignored) callId:{} err:{}",
                    event != null ? event.getCallId() : null, e.getMessage());
        }
        try {
            sessionManager.onHangup(event);
        } catch (Exception e) {
            log.error("handle hangup event error (ignored) callId:{} err:{}",
                    event != null ? event.getCallId() : null, e.getMessage(), e);
        }
    }
}
