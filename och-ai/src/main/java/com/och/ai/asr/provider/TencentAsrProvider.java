package com.och.ai.asr.provider;

import com.alibaba.fastjson2.JSONObject;
import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AsrProviderType;
import com.och.ai.asr.model.AsrSegment;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.model.Speaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 腾讯云实时语音识别 Provider（方案 7.7 主用云，WebSocket 一句话/实时 ASR v2）。
 *
 * <p>协议依据腾讯云《实时语音识别 WebSocket API》公开文档：</p>
 * <ul>
 *   <li>鉴权：HMAC-SHA256(secretKey, host+path+sortedQuery) → base64 → URL 编码为 signature；</li>
 *   <li>配置：全部经 URL query（engine_model_type/voice_format/filter_punc/convert_num_mode/hotword_id…），无 JSON 首帧；</li>
 *   <li>音频：连接建立后直接二进制发送 16bit PCM 分片；</li>
 *   <li>结果：JSON 中 {@code result.slice_type} 0/1=增量、2=句最终；{@code final}=1 表示整流结束；</li>
 *   <li>结束：关闭 WebSocket 触发服务端 flush 最终结果。</li>
 * </ul>
 *
 * <p><b>凭证守卫</b>：{@link #ready()} 要求 enabled 且 appId/secretId/secretKey 齐全，缺一即未就绪，
 * 注册表自动降级 Mock。凭证不写入代码/测试，由 och.asr.tencent.* 配置或环境注入。</p>
 *
 * <p>真实联调受凭证/网络限制，列为可选集成测试。</p>
 */
@Slf4j
@Component
public class TencentAsrProvider extends AbstractWebSocketAsrProvider {

    private static final String HOST_PATH = "asr.cloud.tencent.com/asr/v2/";

    public TencentAsrProvider(AsrProperties properties) {
        super(properties);
    }

    @Override
    public AsrProviderType type() {
        return AsrProviderType.TENCENT;
    }

    @Override
    public boolean ready() {
        AsrProperties.Tencent c = properties.getTencent();
        return c != null && c.isEnabled()
                && isNotBlank(c.getAppId())
                && isNotBlank(c.getSecretId())
                && isNotBlank(c.getSecretKey());
    }

    @Override
    protected URI buildUri(AsrSessionContext ctx) throws Exception {
        AsrProperties.Tencent c = properties.getTencent();
        TreeMap<String, String> params = new TreeMap<>();
        params.put("projectid", "0");
        params.put("engine_model_type", c.getEngineModelType());
        params.put("voice_format", String.valueOf(c.getVoiceFormat()));
        // voice_id 每会话唯一
        params.put("voice_id", UUID.randomUUID().toString().replace("-", ""));
        params.put("needvad", "1");
        params.put("filter_punc", properties.isPunctuation() ? "1" : "0");
        params.put("convert_num_mode", properties.isConvertDigit() ? "1" : "0");
        if (isNotBlank(c.getHotWordId())) {
            params.put("hotword_id", c.getHotWordId());
        }
        if (!properties.getHotWords().isEmpty()) {
            params.put("hotword_list", String.join(",", properties.getHotWords()));
        }
        // sorted query（不含 signature）
        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (qs.length() > 0) {
                qs.append('&');
            }
            qs.append(e.getKey()).append('=').append(urlEncode(e.getValue()));
        }
        String signStr = HOST_PATH + c.getAppId() + "?" + qs;
        String signature = base64HmacSha256(c.getSecretKey(), signStr);
        String full = qs + "&signature=" + urlEncode(signature);
        return URI.create("wss://" + HOST_PATH + c.getAppId() + "?" + full);
    }

    @Override
    protected String buildStartFrame(AsrSessionContext ctx) {
        // 腾讯实时 ASR 配置在 URL query，无需 JSON 首帧
        return null;
    }

    @Override
    protected String buildEndFrame(AsrSessionContext ctx) {
        // 关闭连接即结束，无需结束帧
        return null;
    }

    @Override
    protected void parseTextMessage(AsrSessionContext ctx, AsrResultCallback callback, String text) {
        JSONObject json = parse(text);
        if (json == null) {
            return;
        }
        int code = json.getIntValue("code", -1);
        if (code != 0) {
            String message = json.getString("message");
            log.warn("[TENCENT] 识别错误码 callId:{} code:{} msg:{}", ctx.getCallId(), code, message);
            callback.onError("ASR_999", "tencent code=" + code + " " + message);
            return;
        }
        JSONObject result = json.getJSONObject("result");
        if (result == null) {
            return;
        }
        int sliceType = result.getIntValue("slice_type", 0);
        String voiceText = result.getString("voice_text_str");
        if (voiceText == null || voiceText.isEmpty()) {
            return;
        }
        boolean isFinal = sliceType == 2;
        String messageId = json.getString("message_id");
        Long beginMs = result.getLong("start_time");
        Long endMs = result.getLong("end_time");
        // segmentId 以 message_id 为准（同句增量共享）；回退用 voice_id+start_time
        String segmentId = isNotBlank(messageId) ? messageId
                : (json.getString("voice_id") + "-" + beginMs);
        AsrSegment segment = AsrSegment.builder()
                .segmentId(segmentId)
                .sequence(beginMs == null ? null : beginMs.intValue())
                .text(voiceText)
                .finalResult(isFinal)
                .speaker(Speaker.UNKNOWN)
                .beginMs(beginMs)
                .endMs(endMs)
                .confidence(result.getDouble("confidence"))
                .build();
        boolean firstText = seqCounter(ctx).get() == 0;
        seqCounter(ctx).incrementAndGet();
        emit(ctx, callback, segment, firstText, null);
    }

    /** 每会话序号计数（用于判定首文本），存于上下文备用字段。 */
    private AtomicInteger seqCounter(AsrSessionContext ctx) {
        // 复用 reconnectCount 字段不合适；用一个会话级 map
        return SEQ.computeIfAbsent(ctx.getSessionId(), k -> new AtomicInteger(0));
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> SEQ = new java.util.concurrent.ConcurrentHashMap<>();

    private static String base64HmacSha256(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(raw);
    }

    private static String urlEncode(String v) {
        return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
