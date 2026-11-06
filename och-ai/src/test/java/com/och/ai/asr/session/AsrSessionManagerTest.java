package com.och.ai.asr.session;

import com.och.ai.asr.AsrErrorCode;
import com.och.ai.asr.audio.AudioNormalizer;
import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.entity.CallTranscript;
import com.och.ai.asr.model.AsrSessionState;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.CallAudioFrame;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ASR 会话管理器测试（方案 7.5 / 7.15 / 9.5）：幂等创建、全链路 Mock 流转、有限重连、异常不影响通话。
 */
class AsrSessionManagerTest {

    private AsrProperties props() {
        AsrProperties p = new AsrProperties();
        p.setEnabled(true);
        // provider 默认即为 MOCK
        p.setSampleRate(16000);
        p.setInputCodec(AudioCodec.PCM);
        p.getMock().setIntervalMs(10);
        p.setFinalWaitTimeoutMs(2000L);
        p.setMaxReconnect(2);
        return p;
    }

    private CallBridgeEvent bridge(long callId) {
        return CallBridgeEvent.builder()
                .callId(callId)
                .uniqueId("u-" + callId)
                .caller("1000")
                .callee("2000")
                .agentId(10L)
                .agentNumber("1000")
                .direction(1)
                .bridgeTime(System.currentTimeMillis())
                .build();
    }

    private CallHangupEvent hangup(long callId) {
        return CallHangupEvent.builder()
                .callId(callId)
                .uniqueId("u-" + callId)
                .endTime(System.currentTimeMillis())
                .build();
    }

    private CallAudioFrame frame(long callId) {
        return CallAudioFrame.builder()
                .callId(callId)
                .uniqueId("u-" + callId)
                .codec(AudioCodec.PCM)
                .sampleRate(16000)
                .channels(1)
                .data(new byte[320]) // 10ms@16k
                .sequence(1L)
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    /** 构造一个用真实 Mock Provider + 真实归一化/结果处理器，但持久化与推送为 Mock 的会话管理器。 */
    private AsrSessionManager buildManager(AsrProperties props, AsrWsPushService push, IAsrTranscriptService ts) {
        MockAsrProvider mockProvider = new MockAsrProvider(props);
        AsrProviderRegistry registry = new AsrProviderRegistry(List.of(mockProvider), props);
        registry.init();
        AudioNormalizer normalizer = new AudioNormalizer(props);
        AsrResultHandler handler = new AsrResultHandler(push, ts);
        return new AsrSessionManager(props, registry, normalizer, handler, push, ts);
    }

    private CallTranscript summary(long id) {
        CallTranscript t = new CallTranscript();
        t.setId(id);
        return t;
    }

    @Test
    void bridgeIdempotentCreatesOneSession() {
        AsrProperties props = props();
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        when(ts.getOrCreateSummary(any())).thenReturn(summary(1L));
        AsrSessionManager manager = buildManager(props, push, ts);

        manager.onBridge(bridge(1L));
        manager.onBridge(bridge(1L)); // 重复 → 幂等

        assertEquals(1, manager.activeCount());
        assertNotNull(manager.getSession(1L));
        assertEquals(AsrSessionState.ACTIVE, manager.getSession(1L).getState());
    }

    @Test
    void fullMockFlowPersistsFiveFinals() {
        AsrProperties props = props();
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        when(ts.getOrCreateSummary(any())).thenReturn(summary(2L));
        when(ts.appendFinalSegment(anyString(), any(), any(), ArgumentMatchers.any())).thenReturn(true);
        AsrSessionManager manager = buildManager(props, push, ts);

        manager.onBridge(bridge(2L));
        for (int i = 0; i < 200; i++) {
            manager.onAudioFrame(frame(2L));
        }
        manager.onHangup(hangup(2L));

        // 5 个最终段落落库
        Mockito.verify(ts, Mockito.times(5)).appendFinalSegment(anyString(), any(), any(), ArgumentMatchers.any());
        // 摘要结束落库（SUCCESS）
        Mockito.verify(ts, Mockito.atLeastOnce()).finalizeSummary(anyString(), any());
        // 会话已清理
        assertNull(manager.getSession(2L));
    }

    @Test
    void limitedReconnectThenError() {
        AsrProperties props = props();
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        when(ts.getOrCreateSummary(any())).thenReturn(summary(3L));
        // 取得 Mock Provider 引用以便注入错误
        MockAsrProvider mockProvider = new MockAsrProvider(props);
        AsrProviderRegistry registry = new AsrProviderRegistry(List.of(mockProvider), props);
        registry.init();
        AudioNormalizer normalizer = new AudioNormalizer(props);
        AsrResultHandler handler = new AsrResultHandler(push, ts);
        AsrSessionManager manager = new AsrSessionManager(props, registry, normalizer, handler, push, ts);

        manager.onBridge(bridge(3L));
        var ctx = manager.getSession(3L);
        assertNotNull(ctx);

        // 注入瞬时错误：第1、2次重连，第3次超限终止
        mockProvider.injectError(ctx, AsrErrorCode.CONNECTION_BROKEN);
        assertEquals(1, manager.getSession(3L).getReconnectCount(), "第1次应重连");
        assertEquals(AsrSessionState.ACTIVE, manager.getSession(3L).getState());

        mockProvider.injectError(ctx, AsrErrorCode.CONNECTION_BROKEN);
        assertEquals(2, manager.getSession(3L).getReconnectCount(), "第2次应重连");

        mockProvider.injectError(ctx, AsrErrorCode.CONNECTION_BROKEN);
        assertEquals(AsrSessionState.ERROR, manager.getSession(3L).getState(), "第3次超限应终止为 ERROR");
        assertEquals(2, manager.getSession(3L).getReconnectCount(), "不应超过 maxReconnect");
    }

    @Test
    void asrFailureNeverThrowsAndDoesNotCrash() {
        AsrProperties props = props();
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        // 摘要创建抛异常（模拟 DB 故障）
        when(ts.getOrCreateSummary(any())).thenThrow(new RuntimeException("db down"));
        AsrSessionManager manager = buildManager(props, push, ts);

        // 期望（根因修复后）：持久化(DB)失败不应抛出、不应崩溃，也不应阻断实时转写——
        // 会话仍创建（前端可持续收到增量/最终文本，验收标准 #3），仅摘要未落库(transcriptId=null)，
        // 后续 DB 恢复时由 AsrResultHandler.ensureTranscriptId 幂等补建（见 persistenceFailureRecoversLazily）。
        assertDoesNotThrow(() -> manager.onBridge(bridge(4L)));
        assertEquals(1, manager.activeCount(), "DB 故障时实时转写会话仍应建立，不应中断");
        assertNotNull(manager.getSession(4L), "会话上下文应存在");
        assertNull(manager.getSession(4L).getTranscriptId(), "摘要未落库时 transcriptId 应为空");
        assertEquals(AsrSessionState.ACTIVE, manager.getSession(4L).getState(), "会话应处于 ACTIVE");

        // 清理：挂断不应抛出
        assertDoesNotThrow(() -> manager.onHangup(hangup(4L)));
    }

    @Test
    void persistenceFailureRecoversLazily() {
        AsrProperties props = props();
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        // 桥接时 DB 故障
        CallTranscript recovered = summary(5L);
        Mockito.when(ts.getOrCreateSummary(any()))
                .thenThrow(new RuntimeException("db down at bridge"))
                .thenReturn(recovered);
        when(ts.appendFinalSegment(anyString(), any(), any(), ArgumentMatchers.any())).thenReturn(true);
        MockAsrProvider mockProvider = new MockAsrProvider(props);
        AsrProviderRegistry registry = new AsrProviderRegistry(List.of(mockProvider), props);
        registry.init();
        AudioNormalizer normalizer = new AudioNormalizer(props);
        AsrResultHandler handler = new AsrResultHandler(push, ts);
        AsrSessionManager manager = new AsrSessionManager(props, registry, normalizer, handler, push, ts);

        manager.onBridge(bridge(5L));
        assertNull(manager.getSession(5L).getTranscriptId(), "桥接时摘要未落库");

        // DB 恢复后投递音频：首条最终结果应幂等补建摘要并落库段落（transcriptId 非空）
        for (int i = 0; i < 60; i++) {
            manager.onAudioFrame(frame(5L));
        }
        Mockito.verify(ts, Mockito.atLeastOnce())
                .appendFinalSegment(anyString(), any(), any(), ArgumentMatchers.eq(5L));
        assertEquals(5L, manager.getSession(5L).getTranscriptId(), "DB 恢复后摘要应被幂等补建");
        manager.onHangup(hangup(5L));
    }
}
