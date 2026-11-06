package com.och.ai.asr.provider;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AsrRecognitionResult;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.model.AsrSessionState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mock ASR Provider 测试：验证 sendAudio 驱动确定性产出增量/最终结果。
 */
class MockAsrProviderTest {

    private AsrProperties props() {
        AsrProperties p = new AsrProperties();
        p.setSampleRate(16000);
        p.getMock().setIntervalMs(10);
        return p;
    }

    private AsrSessionContext ctx() {
        AsrSessionContext ctx = new AsrSessionContext();
        ctx.setSessionId("asr-1");
        ctx.setCallId(1L);
        ctx.setSampleRate(16000);
        ctx.setState(AsrSessionState.ACTIVE);
        return ctx;
    }

    private byte[] frame10ms() {
        // 160 samples @16k = 10ms = 320 bytes（内容无关，Mock 按时长推进）
        return new byte[320];
    }

    @Test
    void producesPartialsAndFinals() {
        MockAsrProvider provider = new MockAsrProvider(props());
        AsrSessionContext ctx = ctx();
        List<AsrRecognitionResult> results = new ArrayList<>();
        AsrResultCallback callback = new AsrResultCallback() {
            @Override
            public void onResult(AsrRecognitionResult result) {
                results.add(result);
            }

            @Override
            public void onError(String code, String message) {
            }

            @Override
            public void onClosed() {
            }
        };
        provider.createSession(ctx, callback);
        // 投递足够多的 10ms 帧，使全部 5 句 ×3 段产出
        for (int i = 0; i < 200; i++) {
            provider.sendAudio(ctx, frame10ms());
        }

        long finals = results.stream().filter(r -> r.getSegment().isFinalResult()).count();
        long partials = results.stream().filter(r -> !r.getSegment().isFinalResult()).count();
        assertEquals(5, finals, "应产出 5 个最终结果");
        assertTrue(partials >= 5, "应产出若干增量结果，实际=" + partials);

        // 每句 segmentId 稳定
        List<String> finalTexts = results.stream()
                .filter(r -> r.getSegment().isFinalResult())
                .map(r -> r.getSegment().getText())
                .toList();
        assertEquals(MockAsrProvider.allFinalText(), finalTexts);
    }

    @Test
    void finishFlushesPendingSentence() {
        MockAsrProvider provider = new MockAsrProvider(props());
        AsrSessionContext ctx = ctx();
        List<AsrRecognitionResult> results = new ArrayList<>();
        AsrResultCallback callback = new AsrResultCallback() {
            @Override
            public void onResult(AsrRecognitionResult result) {
                results.add(result);
            }

            @Override
            public void onError(String code, String message) {
            }

            @Override
            public void onClosed() {
                results.add(null); // 标记 onClosed 已触发
            }
        };
        provider.createSession(ctx, callback);
        // 仅投递一帧，使第一句尚未完结
        provider.sendAudio(ctx, frame10ms());
        provider.finishSession(ctx);

        // finishSession 至少把第一句刷成最终，且触发 onClosed
        assertTrue(results.contains(null), "finishSession 应触发 onClosed");
        long finals = results.stream()
                .filter(java.util.Objects::nonNull)
                .filter(r -> r.getSegment().isFinalResult())
                .count();
        assertTrue(finals >= 1, "finishSession 应至少刷出 1 个最终结果");
    }
}
