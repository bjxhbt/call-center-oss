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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FunASR 本地 runtime Provider（方案 7.9，验证用，不进 MVP 生产主链路）。
 *
 * <p>协议依据 FunASR runtime（modelscope）公开 WebSocket 接口：</p>
 * <ul>
 *   <li>连接 {@code ws://127.0.0.1:10095}（无鉴权，本地服务）；</li>
 *   <li>首帧配置 JSON：mode=2pass（同时输出在线增量与离线最终）、chunk_size、audio_fs、hotwords、itn；</li>
 *   <li>音频：二进制 16bit PCM；</li>
 *   <li>结果：{@code mode=2pass-online} 为增量、{@code mode=2pass-offline} 为句最终；</li>
 *   <li>结束：发送 {@code {"is_speaking":false}}。</li>
 * </ul>
 *
 * <p>本地模型不下载到仓库、不进主链路；ready 仅要求 enabled 且 wsUrl 非空。
 * 另提供独立 Python 验证脚本（见 docs）对同一批测试音频离线/流式评估。</p>
 */
@Slf4j
@Component
public class FunAsrProvider extends AbstractWebSocketAsrProvider {

    public FunAsrProvider(AsrProperties properties) {
        super(properties);
    }

    @Override
    public AsrProviderType type() {
        return AsrProviderType.FUNASR;
    }

    @Override
    public boolean ready() {
        AsrProperties.FunAsr c = properties.getFunasr();
        return c != null && c.isEnabled() && c.getWsUrl() != null && !c.getWsUrl().isBlank();
    }

    @Override
    protected URI buildUri(AsrSessionContext ctx) throws Exception {
        return URI.create(properties.getFunasr().getWsUrl());
    }

    @Override
    protected String buildStartFrame(AsrSessionContext ctx) {
        AsrProperties.FunAsr c = properties.getFunasr();
        JSONObject frame = new JSONObject();
        frame.put("mode", c.getMode() == null ? "2pass" : c.getMode());
        frame.put("chunk_size", new int[]{5, 10, 5});
        frame.put("chunk_interval", 10);
        frame.put("wav_name", String.valueOf(ctx.getCallId()));
        frame.put("wav_format", "pcm");
        frame.put("is_speaking", true);
        frame.put("audio_fs", c.getSampleRate() > 0 ? c.getSampleRate() : properties.getSampleRate());
        frame.put("itn", properties.isConvertDigit());
        if (c.getHotWordsJson() != null && !c.getHotWordsJson().isBlank()) {
            frame.put("hotwords", c.getHotWordsJson());
        }
        return frame.toJSONString();
    }

    @Override
    protected String buildEndFrame(AsrSessionContext ctx) {
        JSONObject frame = new JSONObject();
        frame.put("is_speaking", false);
        return frame.toJSONString();
    }

    @Override
    protected void parseTextMessage(AsrSessionContext ctx, AsrResultCallback callback, String text) {
        JSONObject json = parse(text);
        if (json == null) {
            return;
        }
        String mode = json.getString("mode");
        String t = json.getString("text");
        if (t == null || t.isEmpty()) {
            return;
        }
        boolean offline = mode != null && mode.contains("offline");
        // 2pass-offline / offline = 最终；online/2pass-online = 增量
        boolean isFinal = offline || json.getBooleanValue("is_final", false);
        String segmentId = offline
                ? ctx.getSessionId() + "-f-" + FINAL_SEQ.computeIfAbsent(ctx.getSessionId(), k -> new AtomicInteger(0)).incrementAndGet()
                : ctx.getSessionId() + "-online";
        AsrSegment segment = AsrSegment.builder()
                .segmentId(segmentId)
                .sequence(SEQ.computeIfAbsent(ctx.getSessionId(), k -> new AtomicInteger(0)).incrementAndGet())
                .text(t)
                .finalResult(isFinal)
                .speaker(Speaker.UNKNOWN)
                .confidence(json.getDouble("confidence"))
                .build();
        boolean firstText = SEQ.get(ctx.getSessionId()).get() == 1;
        emit(ctx, callback, segment, firstText, null);
    }

    private static final ConcurrentHashMap<String, AtomicInteger> SEQ = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> FINAL_SEQ = new ConcurrentHashMap<>();
}
