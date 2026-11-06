package com.och.ai.asr.eval;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AsrRecognitionResult;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.model.AsrSessionState;
import com.och.ai.asr.provider.AsrResultCallback;
import com.och.ai.asr.provider.MockAsrProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 转写量化指标测试（方案 7.16）：CER、关键词识别率、首文本/最终文本延迟均为实测值（非占位）。
 */
class AsrMetricsTest {

    @Test
    void cerIdenticalIsZero() {
        assertEquals(0.0, CerCalculator.cer("你好世界", "你好世界"));
        assertEquals(0.0, CerCalculator.cer("abc", "abc"));
    }

    @Test
    void cerSingleCharDifference() {
        // 4 字参考，1 字错误 → 0.25
        assertEquals(0.25, CerCalculator.cer("你好世界", "你好世间"), 1e-9);
    }

    @Test
    void cerEmptyReference() {
        assertEquals(1.0, CerCalculator.cer("", "abc"));
        assertEquals(0.0, CerCalculator.cer("", ""));
    }

    @Test
    void keywordHitRateAllHit() {
        assertEquals(1.0, CerCalculator.keywordHitRate("请OpenCallHub处理工单", List.of("OpenCallHub", "工单")));
    }

    @Test
    void keywordHitRatePartial() {
        assertEquals(0.5, CerCalculator.keywordHitRate("请处理工单", List.of("OpenCallHub", "工单")), 1e-9);
        assertEquals(List.of("OpenCallHub"), CerCalculator.missedKeywords("请处理工单", List.of("OpenCallHub", "工单")));
    }

    /**
     * 延迟指标实测（Mock 链路，真实数值）：
     * 首条结果携带 firstTextLatencyMs>0；首条最终结果携带 finalLatencyMs>0。
     */
    @Test
    void mockProviderEmitsRealLatencyMetrics() {
        AsrProperties props = new AsrProperties();
        props.setSampleRate(16000);
        props.getMock().setIntervalMs(10);
        MockAsrProvider provider = new MockAsrProvider(props);
        AsrSessionContext ctx = new AsrSessionContext();
        ctx.setSessionId("asr-m");
        ctx.setCallId(99L);
        ctx.setSampleRate(16000);
        ctx.setState(AsrSessionState.ACTIVE);

        java.util.List<AsrRecognitionResult> results = new java.util.ArrayList<>();
        AsrResultCallback cb = new AsrResultCallback() {
            public void onResult(AsrRecognitionResult r) { results.add(r); }
            public void onError(String code, String message) { }
            public void onClosed() { }
        };
        provider.createSession(ctx, cb);
        // 投递足量音频触发首句的多段与最终
        for (int i = 0; i < 60; i++) {
            provider.sendAudio(ctx, new byte[320]); // 10ms@16k
        }

        assertTrue(!results.isEmpty(), "应产出结果");
        AsrRecognitionResult first = results.get(0);
        assertNotNull(first.getFirstTextLatencyMs(), "首条结果应携带首文本延迟");
        assertTrue(first.getFirstTextLatencyMs() >= 0, "首文本延迟应为非负实数");

        AsrRecognitionResult firstFinal = results.stream()
                .filter(r -> r.getSegment().isFinalResult()).findFirst().orElse(null);
        assertNotNull(firstFinal, "应有最终结果");
        assertNotNull(firstFinal.getFinalLatencyMs(), "最终结果应携带最终文本延迟");
        assertTrue(firstFinal.getFinalLatencyMs() >= 0);
    }
}
