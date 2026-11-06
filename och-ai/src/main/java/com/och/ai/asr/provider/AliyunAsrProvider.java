package com.och.ai.asr.provider;

import com.alibaba.fastjson2.JSONObject;
import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AsrProviderType;
import com.och.ai.asr.model.AsrSegment;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.model.Speaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;

/**
 * 阿里云 NLS 实时语音识别 Provider（方案 7.8 第二云，验证用，不进 MVP 主链路）。
 *
 * <p>协议依据阿里云《实时语音识别 WebSocket API》公开文档：</p>
 * <ul>
 *   <li>鉴权：连接 {@code wss://nls-gateway.aliyuncs.com/ws/v1?token={accessToken}}，
 *       accessToken 由 AccessKeyId/AccessKeySecret 调 CreateToken 获取（运维缓存注入，不在仓库内置）；</li>
 *   <li>首帧：StartTranscriber JSON（appkey/format/sample_rate/标点/数字转换/中间结果）；</li>
 *   <li>音频：二进制 PCM；</li>
 *   <li>结果：SentenceBegin 新句、TranscriptionResultChanged 增量、SentenceEnd 最终、TranscriptionCompleted 结束；</li>
 *   <li>结束：发送 StopTranscriber JSON。</li>
 * </ul>
 *
 * <p><b>凭证守卫</b>：ready 要求 enabled 且 appKey 与 accessToken 齐全；缺则降级 Mock。
 * accessKeyId/accessKeySecret 仅用于本地换 token 的可选路径，默认走注入的 token。</p>
 */
@Slf4j
@Component
public class AliyunAsrProvider extends AbstractWebSocketAsrProvider {

    public AliyunAsrProvider(AsrProperties properties) {
        super(properties);
    }

    @Override
    public AsrProviderType type() {
        return AsrProviderType.ALIYUN;
    }

    @Override
    public boolean ready() {
        AsrProperties.Aliyun c = properties.getAliyun();
        return c != null && c.isEnabled()
                && isNotBlank(c.getAppKey())
                && isNotBlank(c.getAccessToken());
    }

    @Override
    protected URI buildUri(AsrSessionContext ctx) throws Exception {
        AsrProperties.Aliyun c = properties.getAliyun();
        String base = isBlank(c.getEndpoint()) ? "wss://nls-gateway.aliyuncs.com/ws/v1" : c.getEndpoint();
        return URI.create(base + "?token=" + java.net.URLEncoder.encode(c.getAccessToken(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    protected String buildStartFrame(AsrSessionContext ctx) {
        AsrProperties.Aliyun c = properties.getAliyun();
        String taskId = UUID.randomUUID().toString().replace("-", "");
        JSONObject frame = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("namespace", "SpeechTranscriber");
        header.put("name", "StartTranscriber");
        header.put("appkey", c.getAppKey());
        header.put("message_id", UUID.randomUUID().toString().replace("-", ""));
        header.put("task_id", taskId);
        header.put("attributes", true);
        frame.put("header", header);
        JSONObject payload = new JSONObject();
        payload.put("format", "pcm");
        payload.put("sample_rate", properties.getSampleRate());
        payload.put("enable_intermediate_result", true);
        payload.put("enable_punctuation", properties.isPunctuation());
        payload.put("enable_inverse_text_normalization", properties.isConvertDigit());
        frame.put("payload", payload);
        // 记录 taskId 供结果分段
        TASK_ID.put(ctx.getSessionId(), taskId);
        return frame.toJSONString();
    }

    @Override
    protected String buildEndFrame(AsrSessionContext ctx) {
        AsrProperties.Aliyun c = properties.getAliyun();
        JSONObject frame = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("namespace", "SpeechTranscriber");
        header.put("name", "StopTranscriber");
        header.put("appkey", c.getAppKey());
        header.put("message_id", UUID.randomUUID().toString().replace("-", ""));
        header.put("task_id", TASK_ID.getOrDefault(ctx.getSessionId(), ""));
        header.put("attributes", true);
        frame.put("header", header);
        return frame.toJSONString();
    }

    @Override
    protected void parseTextMessage(AsrSessionContext ctx, AsrResultCallback callback, String text) {
        JSONObject json = parse(text);
        if (json == null) {
            return;
        }
        JSONObject header = json.getJSONObject("header");
        if (header == null) {
            return;
        }
        String name = header.getString("name");
        int status = header.getIntValue("status", 0);
        if (status != 0 && status != 20000000) {
            String msg = header.getString("status_text");
            log.warn("[ALIYUN] 识别错误 callId:{} status:{} msg:{}", ctx.getCallId(), status, msg);
            callback.onError("ASR_999", "aliyun status=" + status + " " + msg);
            return;
        }
        JSONObject payload = json.getJSONObject("payload");
        String resultText = payload == null ? null : payload.getString("result");
        switch (name == null ? "" : name) {
            case "TranscriptionResultChanged" -> emitPartial(ctx, callback, payload, resultText);
            case "SentenceEnd" -> emitFinal(ctx, callback, payload, resultText);
            case "TranscriptionCompleted" -> {
                // 整流结束，等 onClose 释放闩
            }
            case "SentenceBegin", "TaskFailed" -> {
                if ("TaskFailed".equals(name)) {
                    callback.onError("ASR_999", "aliyun TaskFailed");
                }
            }
            default -> log.debug("[ALIYUN] 忽略事件 callId:{} name:{}", ctx.getCallId(), name);
        }
    }

    private void emitPartial(AsrSessionContext ctx, AsrResultCallback callback, JSONObject payload, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String segmentId = payload == null ? null : payload.getString("sentence_id");
        if (segmentId == null) {
            segmentId = ctx.getSessionId() + "-cur";
        }
        AsrSegment segment = AsrSegment.builder()
                .segmentId(segmentId)
                .sequence(payload == null ? null : payload.getInteger("sentence_id"))
                .text(text)
                .finalResult(false)
                .speaker(Speaker.UNKNOWN)
                .confidence(payload == null ? null : payload.getDouble("confidence"))
                .build();
        boolean firstText = SEQ.computeIfAbsent(ctx.getSessionId(), k -> new java.util.concurrent.atomic.AtomicInteger(0)).getAndIncrement() == 0;
        emit(ctx, callback, segment, firstText, null);
    }

    private void emitFinal(AsrSessionContext ctx, AsrResultCallback callback, JSONObject payload, String text) {
        if (text == null) {
            text = "";
        }
        String segmentId = payload == null ? null : payload.getString("sentence_id");
        if (segmentId == null) {
            segmentId = ctx.getSessionId() + "-" + FINAL_SEQ.computeIfAbsent(ctx.getSessionId(), k -> new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();
        }
        AsrSegment segment = AsrSegment.builder()
                .segmentId(segmentId)
                .sequence(payload == null ? null : payload.getInteger("sentence_id"))
                .text(text)
                .finalResult(true)
                .speaker(Speaker.UNKNOWN)
                .beginMs(payload == null ? null : payload.getLong("begin_time"))
                .endMs(payload == null ? null : payload.getLong("visited_time"))
                .confidence(payload == null ? null : payload.getDouble("confidence"))
                .build();
        emit(ctx, callback, segment, false, null);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> TASK_ID = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> SEQ = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> FINAL_SEQ = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
