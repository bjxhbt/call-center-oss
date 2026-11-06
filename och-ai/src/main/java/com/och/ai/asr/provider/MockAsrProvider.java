package com.och.ai.asr.provider;

import com.och.ai.asr.AsrErrorCode;
import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AsrProviderType;
import com.och.ai.asr.model.AsrRecognitionResult;
import com.och.ai.asr.model.AsrSegment;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.model.Speaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock ASR Provider（方案 7.6 / 并行启动）。
 *
 * <p>无需任何云凭证即可产生结构正确的增量/最终结果，用于：本方案前置联调、前端 Mock 联调、
 * 单测与无凭证环境回归。脚本与前端 {@code MockTranscriptSource} 一致，保证前后端字段对齐。</p>
 *
 * <p>结果驱动方式：<b>确定性</b>——按累计音频时长推进脚本，每 {@code intervalMs} 产出一个 partial，
 * 句末产出 final。无定时器、无墙钟依赖，便于单测断言。{@code finishSession} 刷新未完结句的 final。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockAsrProvider implements IAsrProvider {

    private final AsrProperties properties;

    /** 会话级脚本状态。 */
    private final ConcurrentHashMap<String, MockState> states = new ConcurrentHashMap<>();

    /** 会话级结果回调（Provider 自持，异步产出结果时回调用）。 */
    private final ConcurrentHashMap<String, AsrResultCallback> callbacks = new ConcurrentHashMap<>();

    /** 内置脚本（与前端 MockTranscriptSource 对齐，可被配置 scriptFile 覆盖）。 */
    private static final String[][] SCRIPT = new String[][]{
            new String[]{"您好", "您好，我想", "您好，我想咨询一下"},
            new String[]{"好的", "好的，请问您", "好的，请问您的账号是多少"},
            new String[]{"我的账号", "我的账号是 10086", "我的账号是 10086，姓名张三"},
            new String[]{"好的张先生", "好的张先生，我帮您查一下", "好的张先生，我帮您查一下，请稍等"},
            new String[]{"好的", "好的，谢谢你"}
    };
    private static final Speaker[] SCRIPT_SPEAKERS = new Speaker[]{
            Speaker.CUSTOMER, Speaker.AGENT, Speaker.CUSTOMER, Speaker.AGENT, Speaker.CUSTOMER
    };

    @Override
    public AsrProviderType type() {
        return AsrProviderType.MOCK;
    }

    @Override
    public boolean ready() {
        return true;
    }

    @Override
    public String createSession(AsrSessionContext ctx, AsrResultCallback callback) {
        MockState st = new MockState();
        st.sessionId = ctx.getSessionId();
        st.firstAudioSentAt = System.currentTimeMillis();
        states.put(ctx.getSessionId(), st);
        callbacks.put(ctx.getSessionId(), callback);
        ctx.setCloudSessionId("mock-" + ctx.getSessionId());
        log.info("[Mock-ASR] 创建会话 callId:{} providerSession:{}", ctx.getCallId(), ctx.getCloudSessionId());
        return ctx.getCloudSessionId();
    }

    @Override
    public void sendAudio(AsrSessionContext ctx, byte[] pcm) {
        MockState st = states.get(ctx.getSessionId());
        AsrResultCallback callback = callbacks.get(ctx.getSessionId());
        if (st == null || callback == null || ctx.getState() == null || ctx.getState().isTerminal()) {
            return;
        }
        int rate = ctx.getSampleRate() <= 0 ? properties.getSampleRate() : ctx.getSampleRate();
        int samples = pcm == null ? 0 : pcm.length / 2;
        long frameMs = rate > 0 ? (samples * 1000L / rate) : 0;
        st.accumulatedMs += frameMs;
        long interval = Math.max(1, properties.getMock().getIntervalMs());
        while (st.accumulatedMs >= interval && st.scriptIndex < SCRIPT.length) {
            st.accumulatedMs -= interval;
            emitPart(ctx, st, callback);
        }
    }

    @Override
    public void finishSession(AsrSessionContext ctx) {
        MockState st = states.get(ctx.getSessionId());
        AsrResultCallback callback = callbacks.get(ctx.getSessionId());
        if (st == null) {
            if (callback != null) {
                callback.onClosed();
            }
            return;
        }
        // 刷新当前未完结句：直接产出该句的最终文本
        if (callback != null && st.scriptIndex < SCRIPT.length) {
            st.partIndex = SCRIPT[st.scriptIndex].length - 1;
            emitPart(ctx, st, callback);
            st.scriptIndex++;
            st.partIndex = 0;
        }
        states.remove(ctx.getSessionId());
        callbacks.remove(ctx.getSessionId());
        // 通知会话管理器：最终结果已全部产出（触发 finalLatch 释放）
        if (callback != null) {
            callback.onClosed();
        }
    }

    @Override
    public void cancelSession(AsrSessionContext ctx) {
        states.remove(ctx.getSessionId());
        callbacks.remove(ctx.getSessionId());
    }

    private void emitPart(AsrSessionContext ctx, MockState st, AsrResultCallback callback) {
        if (callback == null || st.scriptIndex >= SCRIPT.length) {
            return;
        }
        String[] sentence = SCRIPT[st.scriptIndex];
        if (st.partIndex >= sentence.length) {
            st.scriptIndex++;
            st.partIndex = 0;
            if (st.scriptIndex >= SCRIPT.length) {
                return;
            }
            sentence = SCRIPT[st.scriptIndex];
        }
        boolean isFinal = st.partIndex == sentence.length - 1;
        String segmentId = ctx.getCallId() + "-mock-" + st.scriptIndex;
        AsrSegment segment = AsrSegment.builder()
                .segmentId(segmentId)
                .sequence(st.resultSeq++)
                .text(sentence[st.partIndex])
                .finalResult(isFinal)
                .speaker(SCRIPT_SPEAKERS[Math.min(st.scriptIndex, SCRIPT_SPEAKERS.length - 1)])
                .confidence(isFinal ? 0.95 : 0.6)
                .build();

        long now = System.currentTimeMillis();
        AsrRecognitionResult.AsrRecognitionResultBuilder b = AsrRecognitionResult.builder()
                .provider(AsrProviderType.MOCK)
                .sessionId(ctx.getSessionId())
                .callId(ctx.getCallId())
                .uniqueId(ctx.getUniqueId())
                .segment(segment)
                .success(true)
                .receivedAt(now);
        if (!st.firstTextEmitted) {
            b.firstTextLatencyMs(now - st.firstAudioSentAt);
            st.firstTextEmitted = true;
        }
        if (isFinal) {
            b.finalLatencyMs(now - st.firstAudioSentAt);
        }
        callback.onResult(b.build());

        if (isFinal) {
            st.scriptIndex++;
            st.partIndex = 0;
        } else {
            st.partIndex++;
        }
    }

    /** 错误注入（测试异常重连用）：直接回调 onError。 */
    public void injectError(AsrSessionContext ctx, String code) {
        AsrResultCallback callback = callbacks.get(ctx.getSessionId());
        if (callback != null) {
            callback.onError(code == null ? AsrErrorCode.UNKNOWN : code, "mock injected error");
        }
    }

    /** Mock 会话脚本状态。 */
    private static class MockState {
        String sessionId;
        int scriptIndex;
        int partIndex;
        int resultSeq;
        long accumulatedMs;
        long firstAudioSentAt;
        boolean firstTextEmitted;
    }

    /** 测试可见的脚本最终文本副本。 */
    static List<String> allFinalText() {
        List<String> all = new ArrayList<>();
        for (String[] s : SCRIPT) {
            all.add(s[s.length - 1]);
        }
        return all;
    }
}
