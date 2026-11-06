package com.och.ai.asr.session;

import com.och.ai.asr.AsrErrorCode;
import com.och.ai.asr.audio.AudioNormalizer;
import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.entity.CallTranscript;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.CallAudioFrame;
import com.och.ai.asr.provider.AsrProviderRegistry;
import com.och.ai.asr.provider.MockAsrProvider;
import com.och.ai.asr.result.AsrResultHandler;
import com.och.ai.asr.push.AsrWsPushService;
import com.och.ai.asr.service.IAsrTranscriptService;
import com.och.esl.event.CallBridgeEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 音频连续性/丢帧检测测试（方案 11.2 P0 FreeSWITCH 真实音频）：
 * 模拟序号跳变/重复/乱序，断言检测计数正确且不阻断发送。
 */
class AudioContinuityTest {

    private AsrSessionManager build() {
        AsrProperties props = new AsrProperties();
        props.setEnabled(true);
        props.setSampleRate(16000);
        props.setInputCodec(AudioCodec.PCM);
        props.getMock().setIntervalMs(10);
        props.setFinalWaitTimeoutMs(300L);
        props.setMaxReconnect(2);
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        CallTranscript s = new CallTranscript();
        s.setId(31L);
        Mockito.when(ts.getOrCreateSummary(Mockito.any())).thenReturn(s);
        MockAsrProvider provider = new MockAsrProvider(props);
        AsrProviderRegistry registry = new AsrProviderRegistry(List.of(provider), props);
        registry.init();
        return new AsrSessionManager(props, registry, new AudioNormalizer(props),
                new AsrResultHandler(push, ts), push, ts);
    }

    private CallAudioFrame frame(long callId, long seq) {
        return CallAudioFrame.builder().callId(callId).uniqueId("u").codec(AudioCodec.PCM)
                .sampleRate(16000).channels(1).data(new byte[320]).sequence(seq).build();
    }

    @Test
    void detectsGapsDuplicatesAndOutOfOrderWithoutBlocking() {
        AsrSessionManager manager = build();
        long callId = 31L;
        manager.onBridge(CallBridgeEvent.builder().callId(callId).uniqueId("u-31")
                .agentId(1000L).direction(2).bridgeTime(System.currentTimeMillis()).build());

        // 序号：1,2 正常 → 5(丢3,4=2帧) → 5(重复) → 3(乱序回退)
        manager.onAudioFrame(frame(callId, 1));
        manager.onAudioFrame(frame(callId, 2));
        manager.onAudioFrame(frame(callId, 5)); // gap=2
        manager.onAudioFrame(frame(callId, 5)); // duplicate
        manager.onAudioFrame(frame(callId, 3)); // out-of-order

        var ctx = manager.getSession(callId);
        assertEquals(5, ctx.getFramesReceived(), "5 帧全部接收");
        assertEquals(2, ctx.getGapsDetected(), "应检测到 2 个丢帧");
        assertTrue(ctx.getDuplicateOrOutOfOrder() >= 2, "重复+乱序至少 2");
        assertTrue(ctx.getAudioFramesSent() >= 5, "连续性问题不应阻断发送");
    }

    @Test
    void noFalseGapOnContiguousStream() {
        AsrSessionManager manager = build();
        long callId = 32L;
        manager.onBridge(CallBridgeEvent.builder().callId(callId).uniqueId("u-32")
                .agentId(1000L).direction(2).bridgeTime(System.currentTimeMillis()).build());
        for (long s = 0; s < 20; s++) {
            manager.onAudioFrame(frame(callId, s));
        }
        var ctx = manager.getSession(callId);
        assertEquals(0, ctx.getGapsDetected(), "连续流不应误报丢帧");
        assertEquals(0, ctx.getDuplicateOrOutOfOrder());
    }
}
