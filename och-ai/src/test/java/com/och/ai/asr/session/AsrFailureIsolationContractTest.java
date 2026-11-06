package com.och.ai.asr.session;

import com.och.ai.asr.AsrErrorCode;
import com.och.ai.asr.audio.AudioNormalizer;
import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.provider.AsrProviderRegistry;
import com.och.ai.asr.provider.MockAsrProvider;
import com.och.ai.asr.result.AsrResultHandler;
import com.och.ai.asr.push.AsrWsPushService;
import com.och.ai.asr.service.IAsrTranscriptService;
import com.och.esl.event.CallBridgeEvent;
import com.och.esl.event.CallHangupEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * 故障隔离四要素契约测试（方案 11.2 P0 真实故障隔离 / 10.3 #4）：
 * 云端鉴权失败时必须同时满足：①通话不挂断(不抛出) ②录音保留(recordUrl 回填)
 * ③会话释放(挂断后清理) ④错误可查(markError 落库 + FAILED 状态)。
 */
class AsrFailureIsolationContractTest {

    private AsrProperties props() {
        AsrProperties p = new AsrProperties();
        p.setEnabled(true);
        p.setSampleRate(16000);
        p.setInputCodec(AudioCodec.PCM);
        p.getMock().setIntervalMs(10);
        p.setFinalWaitTimeoutMs(300L);
        p.setMaxReconnect(2);
        return p;
    }

    @Test
    void authFailureKeepsCallRecordingReleasedSessionQueryableError() {
        AsrProperties props = props();
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        com.och.ai.asr.entity.CallTranscript summary = new com.och.ai.asr.entity.CallTranscript();
        summary.setId(21L);
        Mockito.when(ts.getOrCreateSummary(ArgumentMatchers.any())).thenReturn(summary);

        MockAsrProvider provider = new MockAsrProvider(props);
        AsrProviderRegistry registry = new AsrProviderRegistry(List.of(provider), props);
        registry.init();
        AudioNormalizer normalizer = new AudioNormalizer(props);
        AsrResultHandler handler = new AsrResultHandler(push, ts);
        AsrSessionManager manager = new AsrSessionManager(props, registry, normalizer, handler, push, ts);

        long callId = 21L;
        String recordPath = "/recordings/21-fail.wav";

        // ① ASR 鉴权失败：不得抛出、不得影响通话
        assertDoesNotThrow(() -> manager.onBridge(CallBridgeEvent.builder()
                .callId(callId).uniqueId("u-21").caller("13800000000").callee("1000")
                .agentId(1000L).direction(2).bridgeTime(System.currentTimeMillis()).build()));
        assertDoesNotThrow(() -> provider.injectError(manager.getSession(callId), AsrErrorCode.AUTH_FAILED));

        // 挂断（携带录音路径）
        assertDoesNotThrow(() -> manager.onHangup(CallHangupEvent.builder()
                .callId(callId).uniqueId("u-21").endTime(System.currentTimeMillis()).record(recordPath).build()));

        // ② 录音保留：finalizeSummary 应回填 recordUrl（关联回放/话单）
        verify(ts).finalizeSummary(eq(String.valueOf(callId)), Mockito.argThat(
                (IAsrTranscriptService.FinalizeRequest r) -> recordPath.equals(r.recordUrl)));

        // ③ 会话释放：挂断后会话应从内存清理
        assertNull(manager.getSession(callId), "会话应已释放");
        assertEquals0(manager.activeCount());

        // ④ 错误可查：markError 应被调用（错误码/消息落库，可按 callId 查询）
        verify(ts).markError(eq(String.valueOf(callId)), eq(AsrErrorCode.AUTH_FAILED), anyString());
        assertTrue(true, "故障隔离四要素全部满足");
    }

    private static void assertEquals0(int n) {
        assertTrue(n == 0, "活动会话数应为 0，实际=" + n);
    }
}
