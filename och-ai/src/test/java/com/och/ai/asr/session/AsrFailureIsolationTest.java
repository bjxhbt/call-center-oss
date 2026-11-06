package com.och.ai.asr.session;

import com.och.ai.asr.AsrErrorCode;
import com.och.ai.asr.audio.AudioNormalizer;
import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.entity.CallTranscript;
import com.och.ai.asr.model.AsrProviderType;
import com.och.ai.asr.model.AsrSessionState;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.provider.AsrProviderRegistry;
import com.och.ai.asr.provider.IAsrProvider;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ASR 故障隔离测试（方案 7.15 / 文档 10.2 P0）：覆盖鉴权失败、限流、发送失败、
 * 最终结果超时等场景。断言：ASR 失败绝不抛出/不挂电话，有限重连后进入明确终态。
 */
class AsrFailureIsolationTest {

    private AsrProperties props() {
        AsrProperties p = new AsrProperties();
        p.setEnabled(true);
        p.setSampleRate(16000);
        p.setInputCodec(AudioCodec.PCM);
        p.getMock().setIntervalMs(10);
        p.setFinalWaitTimeoutMs(300L); // 缩短超时，加快超时用例
        p.setMaxReconnect(2);
        return p;
    }

    private CallBridgeEvent bridge(long callId) {
        return CallBridgeEvent.builder()
                .callId(callId).uniqueId("u-" + callId).caller("1000").callee("2000")
                .agentId(10L).direction(1).bridgeTime(System.currentTimeMillis()).build();
    }

    private CallHangupEvent hangup(long callId) {
        return CallHangupEvent.builder().callId(callId).uniqueId("u-" + callId)
                .endTime(System.currentTimeMillis()).build();
    }

    private AsrSessionManager buildWithMockProvider(AsrProperties props, AsrWsPushService push,
                                                    IAsrTranscriptService ts, MockAsrProvider provider) {
        AsrProviderRegistry registry = new AsrProviderRegistry(List.of(provider), props);
        registry.init();
        AudioNormalizer normalizer = new AudioNormalizer(props);
        AsrResultHandler handler = new AsrResultHandler(push, ts);
        return new AsrSessionManager(props, registry, normalizer, handler, push, ts);
    }

    private IAsrTranscriptService stubTs(long id) {
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        CallTranscript t = new CallTranscript();
        t.setId(id);
        when(ts.getOrCreateSummary(any())).thenReturn(t);
        when(ts.appendFinalSegment(anyString(), any(), any(), ArgumentMatchers.any())).thenReturn(true);
        return ts;
    }

    /** 鉴权失败属配置类错误：不重连，立即进入 ERROR 终态。 */
    @Test
    void authFailureTerminatesImmediately() {
        AsrProperties props = props();
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        MockAsrProvider provider = new MockAsrProvider(props);
        AsrSessionManager manager = buildWithMockProvider(props, push, stubTs(11L), provider);

        assertDoesNotThrow(() -> manager.onBridge(bridge(11L)));
        var ctx = manager.getSession(11L);
        assertDoesNotThrow(() -> provider.injectError(ctx, AsrErrorCode.AUTH_FAILED));

        assertEquals(0, manager.getSession(11L).getReconnectCount(), "鉴权失败不应重连");
        assertEquals(AsrSessionState.ERROR, manager.getSession(11L).getState(), "应立即进入 ERROR");
        // 基础通话不受影响：挂断仍可正常处理
        assertDoesNotThrow(() -> manager.onHangup(hangup(11L)));
    }

    /** 限流属瞬时错误：有限重连，超限后进入 ERROR。 */
    @Test
    void rateLimitReconnectsThenTerminates() {
        AsrProperties props = props();
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        MockAsrProvider provider = new MockAsrProvider(props);
        AsrSessionManager manager = buildWithMockProvider(props, push, stubTs(12L), provider);

        manager.onBridge(bridge(12L));
        var ctx = manager.getSession(12L);

        provider.injectError(ctx, AsrErrorCode.RATE_LIMITED);
        assertEquals(1, manager.getSession(12L).getReconnectCount());
        assertEquals(AsrSessionState.ACTIVE, manager.getSession(12L).getState());

        provider.injectError(ctx, AsrErrorCode.RATE_LIMITED);
        provider.injectError(ctx, AsrErrorCode.RATE_LIMITED);
        assertEquals(AsrSessionState.ERROR, manager.getSession(12L).getState(), "超限应终止");
        assertTrue(manager.getSession(12L).getReconnectCount() <= props.getMaxReconnect(), "不超过上限");
    }

    /** 发送失败属瞬时错误：触发一次重连后恢复 ACTIVE。 */
    @Test
    void sendFailureReconnects() {
        AsrProperties props = props();
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        MockAsrProvider provider = new MockAsrProvider(props);
        AsrSessionManager manager = buildWithMockProvider(props, push, stubTs(13L), provider);

        manager.onBridge(bridge(13L));
        var ctx = manager.getSession(13L);
        provider.injectError(ctx, AsrErrorCode.SEND_FAILED);
        assertEquals(1, manager.getSession(13L).getReconnectCount());
        assertEquals(AsrSessionState.ACTIVE, manager.getSession(13L).getState());
    }

    /**
     * 最终结果超时：Provider 在 finishSession 后未回调 onClosed（云端卡死/丢包），
     * 会话管理器等待 finalWaitTimeoutMs 后落库为 PARTIAL，不抛出、不挂死。
     */
    @Test
    void finalResultTimeoutYieldsPartial() {
        AsrProperties props = props();
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        // 用 Mockito 造一个“不回调 onClosed”的 Provider
        IAsrProvider deadProvider = Mockito.mock(IAsrProvider.class);
        when(deadProvider.type()).thenReturn(AsrProviderType.MOCK);
        when(deadProvider.ready()).thenReturn(true);
        AsrProviderRegistry registry = new AsrProviderRegistry(List.of(deadProvider), props);
        registry.init();
        AudioNormalizer normalizer = new AudioNormalizer(props);
        AsrResultHandler handler = new AsrResultHandler(push, ts);
        AsrSessionManager manager = new AsrSessionManager(props, registry, normalizer, handler, push, ts);

        manager.onBridge(bridge(14L));
        // finishSession 为 mock 默认空实现 → 不触发 onClosed → latch 超时
        assertDoesNotThrow(() -> manager.onHangup(hangup(14L)));
        // 落库为 PARTIAL（最终结果超时），非 SUCCESS
        Mockito.verify(ts).finalizeSummary(anyString(),
                Mockito.argThat((IAsrTranscriptService.FinalizeRequest r) -> "PARTIAL".equals(r.status)));
    }
}
