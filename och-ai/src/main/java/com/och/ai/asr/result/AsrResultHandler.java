package com.och.ai.asr.result;

import com.och.ai.asr.model.AsrRecognitionResult;
import com.och.ai.asr.model.AsrSegment;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.push.AsrWsPushService;
import com.och.ai.asr.service.IAsrTranscriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ASR 结果处理器（方案 7.10 增量 / 7.11 最终 / 7.15 去重）。
 *
 * <p>核心规则：</p>
 * <ul>
 *   <li>临时结果按 {@code segmentId + sequence} 覆盖更新，<b>禁止重复追加</b>：
 *       同 segmentId 仅在 sequence 递增时推送一次（前端 reducer 再做幂等覆盖）；</li>
 *   <li>最终结果替换临时结果：锁定 segmentId，后续 partial/final 一律忽略；</li>
 *   <li>最终结果持久化到 call_transcript_segment，并更新摘要全文/计数；</li>
 *   <li>重连后防止文本重复：sequence 游标与会话级 finalized 集合跨重连保留；</li>
 *   <li>任何落库/推送异常仅记日志，绝不向上冒泡到通话链路。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrResultHandler {

    private final AsrWsPushService pushService;
    private final IAsrTranscriptService transcriptService;

    /** 会话级去重状态：sessionId → 状态。 */
    private final Map<String, SessionDedup> states = new ConcurrentHashMap<>();

    /**
     * 处理一条 Provider 结果（临时或最终）。
     */
    public void handle(AsrSessionContext ctx, AsrRecognitionResult result) {
        if (ctx == null || result == null || result.getSegment() == null) {
            return;
        }
        try {
            AsrSegment seg = result.getSegment();
            SessionDedup dedup = states.computeIfAbsent(ctx.getSessionId(), k -> new SessionDedup());

            if (seg.isFinalResult()) {
                handleFinal(ctx, result, seg, dedup);
            } else {
                handleTemp(ctx, result, seg, dedup);
            }
            // 延迟指标（首文本/最终）
            if (result.getFirstTextLatencyMs() != null || result.getFinalLatencyMs() != null) {
                transcriptService.updateLatencies(String.valueOf(ctx.getCallId()),
                        result.getFirstTextLatencyMs(), result.getFinalLatencyMs());
            }
        } catch (Exception e) {
            log.warn("处理 ASR 结果失败(忽略) callId:{} err:{}", ctx.getCallId(), e.getMessage(), e);
        }
    }

    /**
     * 处理错误（连接断开/鉴权失败等）：标记摘要错误并推送状态。
     */
    public void handleError(AsrSessionContext ctx, String code, String message) {
        if (ctx == null) {
            return;
        }
        try {
            transcriptService.markError(String.valueOf(ctx.getCallId()), code, message);
            pushService.pushStatus(ctx, "error", code, message);
        } catch (Exception e) {
            log.warn("处理 ASR 错误失败(忽略) callId:{} err:{}", ctx.getCallId(), e.getMessage());
        }
    }

    /** 会话结束时清理去重状态。 */
    public void clear(String sessionId) {
        if (sessionId != null) {
            states.remove(sessionId);
        }
    }

    private void handleTemp(AsrSessionContext ctx, AsrRecognitionResult result, AsrSegment seg, SessionDedup dedup) {
        if (dedup.finalized.contains(seg.getSegmentId())) {
            // 该段已最终，忽略后续临时结果（最终优先且不可变）
            return;
        }
        Integer lastSeq = dedup.lastPartialSeq.get(seg.getSegmentId());
        if (seg.getSequence() != null && lastSeq != null && seg.getSequence() <= lastSeq) {
            // 旧序号/重复，跳过（重连去重）
            return;
        }
        if (seg.getSequence() != null) {
            dedup.lastPartialSeq.put(seg.getSegmentId(), seg.getSequence());
        }
        pushService.pushPartial(ctx, seg);
    }

    private void handleFinal(AsrSessionContext ctx, AsrRecognitionResult result, AsrSegment seg, SessionDedup dedup) {
        if (!dedup.finalized.add(seg.getSegmentId())) {
            // 已最终过的段，去重（重连重复最终结果不二次落库/推送）
            return;
        }
        ensureTranscriptId(ctx);
        boolean inserted = transcriptService.appendFinalSegment(
                String.valueOf(ctx.getCallId()), seg, result.getProvider(), ctx.getTranscriptId());
        if (inserted) {
            pushService.pushFinal(ctx, seg);
        }
    }

    private void ensureTranscriptId(AsrSessionContext ctx) {
        if (ctx.getTranscriptId() != null) {
            return;
        }
        // 摘要可能在桥接时因 DB 故障未能创建（会话仍建立以保障实时转写）。
        // 此处用幂等的 getOrCreateSummary 重试：DB 恢复后即可补建，避免最终段落成为孤儿数据。
        try {
            var summary = transcriptService.getOrCreateSummary(ctx);
            if (summary != null) {
                ctx.setTranscriptId(summary.getId());
            }
        } catch (Exception e) {
            // 持久化仍不可用：本条最终结果以 callId 关联尽量落库（transcriptId 可空），不阻断实时推送
            log.warn("懒加载转写摘要失败(忽略，最终结果按 callId 落库) callId:{} err:{}",
                    ctx.getCallId(), e.getMessage());
        }
    }

    /** 会话级去重游标。 */
    private static class SessionDedup {
        /** 已最终段落 segmentId 集合（锁定，不再接受更新）。 */
        final Set<String> finalized = ConcurrentHashMap.newKeySet();
        /** 各 segmentId 最近一次推送的 partial sequence（去重/重连防重）。 */
        final Map<String, Integer> lastPartialSeq = new ConcurrentHashMap<>();
    }
}
