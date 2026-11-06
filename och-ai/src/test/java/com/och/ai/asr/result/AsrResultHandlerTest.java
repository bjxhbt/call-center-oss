package com.och.ai.asr.result;

import com.och.ai.asr.model.AsrProviderType;
import com.och.ai.asr.model.AsrRecognitionResult;
import com.och.ai.asr.model.AsrSegment;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.model.Speaker;
import com.och.ai.asr.push.AsrWsPushService;
import com.och.ai.asr.service.IAsrTranscriptService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ASR 结果处理器测试（方案 7.10 增量覆盖 / 7.11 最终替换 / 7.15 去重）。
 */
class AsrResultHandlerTest {

    private AsrSessionContext ctx() {
        AsrSessionContext ctx = new AsrSessionContext();
        ctx.setSessionId("asr-1");
        ctx.setCallId(1L);
        ctx.setAgentId(10L);
        ctx.setTranscriptId(99L);
        return ctx;
    }

    private AsrRecognitionResult temp(String segmentId, int seq, String text) {
        return AsrRecognitionResult.builder()
                .provider(AsrProviderType.MOCK)
                .sessionId("asr-1")
                .callId(1L)
                .segment(AsrSegment.builder()
                        .segmentId(segmentId)
                        .sequence(seq)
                        .text(text)
                        .finalResult(false)
                        .speaker(Speaker.CUSTOMER)
                        .build())
                .success(true)
                .receivedAt(System.currentTimeMillis())
                .build();
    }

    private AsrRecognitionResult fin(String segmentId, int seq, String text) {
        AsrRecognitionResult r = temp(segmentId, seq, text);
        r.getSegment().setFinalResult(true);
        return r;
    }

    @Test
    void temporaryResultsOverwriteBySequence() {
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        AsrResultHandler handler = new AsrResultHandler(push, ts);
        AsrSessionContext ctx = ctx();

        handler.handle(ctx, temp("s1", 1, "你"));
        handler.handle(ctx, temp("s1", 2, "你好"));
        handler.handle(ctx, temp("s1", 2, "你好（重复旧序号）")); // seq<=last(2) → 跳过
        handler.handle(ctx, temp("s1", 3, "你好吗"));

        // 仅递增序号被推送：seq1、seq2、seq3 三次（重复 seq2 被去重）
        verify(push, times(3)).pushPartial(eq(ctx), any());
    }

    @Test
    void finalReplacesAndPersistsOnce() {
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        when(ts.appendFinalSegment(anyString(), any(), any(), any())).thenReturn(true);
        AsrResultHandler handler = new AsrResultHandler(push, ts);
        AsrSessionContext ctx = ctx();

        handler.handle(ctx, fin("s1", 1, "你好吗"));
        handler.handle(ctx, fin("s1", 2, "你好吗（重复最终）")); // 同段已 final → 去重

        verify(ts, times(1)).appendFinalSegment(eq("1"), any(), any(), eq(99L));
        verify(push, times(1)).pushFinal(eq(ctx), any());
    }

    @Test
    void segmentLockedAfterFinal() {
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        when(ts.appendFinalSegment(anyString(), any(), any(), any())).thenReturn(true);
        AsrResultHandler handler = new AsrResultHandler(push, ts);
        AsrSessionContext ctx = ctx();

        handler.handle(ctx, fin("s1", 1, "你好吗"));
        handler.handle(ctx, temp("s1", 9, "不应再推送")); // 锁定 → 忽略

        verify(push, never()).pushPartial(any(), any());
    }

    @Test
    void latencyUpdatedWhenPresent() {
        AsrWsPushService push = Mockito.mock(AsrWsPushService.class);
        IAsrTranscriptService ts = Mockito.mock(IAsrTranscriptService.class);
        AsrResultHandler handler = new AsrResultHandler(push, ts);
        AsrSessionContext ctx = ctx();

        AsrRecognitionResult r = AsrRecognitionResult.builder()
                .provider(AsrProviderType.MOCK)
                .callId(1L)
                .segment(AsrSegment.builder().segmentId("s1").sequence(1).text("a").finalResult(false).speaker(Speaker.AGENT).build())
                .firstTextLatencyMs(120L)
                .success(true)
                .receivedAt(1L)
                .build();
        handler.handle(ctx, r);

        verify(ts, times(1)).updateLatencies(eq("1"), eq(120L), any());
    }
}
