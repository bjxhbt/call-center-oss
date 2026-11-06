package com.och.ai.tts.provider;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.och.ai.tts.TtsErrorCode;
import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;
import com.och.ai.tts.util.TtsAudioUtils;
import com.och.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * CosyVoice 本地 TTS Provider（本地验证，不进 MVP 生产主链路）。
 *
 * <p>调用本地 CosyVoice HTTP 服务（如社区 CosyVoice-api / 自建 FastAPI 包装）：
 * <pre>
 *   POST {baseUrl}/api/tts
 *   Content-Type: application/json
 *   {"text":"...", "voice":"longxiaochun", "speed":1.0, "sample_rate":8000}
 * </pre>
 * 兼容两种返回：二进制音频（audio/wav、audio/pcm）或 JSON {audio:"&lt;base64&gt;", format:"wav|pcm"}。
 *
 * <p>{@code och.tts.cosyvoice.enabled=false}（默认）时不装配；验证时打开并配置 baseUrl。
 * 服务不可用、超时、模型未加载均返回 {@link TtsErrorCode#SERVICE_UNAVAILABLE}，不影响主链路。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "och.tts.cosyvoice", name = "enabled", havingValue = "true")
public class CosyVoiceTtsProvider implements TtsServiceProvider {

    private final TtsProperties properties;
    private final HttpClient http;

    public CosyVoiceTtsProvider(TtsProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000L, properties.getTimeoutMs())))
                .build();
    }

    @Override
    public TtsProviderType type() {
        return TtsProviderType.COSYVOICE;
    }

    @Override
    public boolean ready() {
        return properties.getCosyvoice() != null
                && StringUtils.isNotBlank(properties.getCosyvoice().getBaseUrl());
    }

    @Override
    public TtsResult synthesize(TtsRequest request) {
        long start = System.currentTimeMillis();
        if (StringUtils.isBlank(properties.getCosyvoice().getBaseUrl())) {
            return failed(TtsErrorCode.SERVICE_UNAVAILABLE, "CosyVoice baseUrl 未配置", start);
        }
        int sampleRate = request.getSampleRate() == null ? properties.getDefaultSampleRate() : request.getSampleRate();
        TtsAudioFormat targetFormat = request.getFormat() == null ? properties.getDefaultFormat() : request.getFormat();
        String voice = StringUtils.isBlank(request.getVoice())
                ? properties.getCosyvoice().getVoice() : request.getVoice();
        try {
            JSONObject body = new JSONObject();
            body.put("text", request.getText());
            body.put("voice", voice);
            body.put("speed", request.getSpeed() == null ? 1.0 : request.getSpeed());
            body.put("sample_rate", sampleRate);
            body.put("format", "pcm");

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(rtrim(properties.getCosyvoice().getBaseUrl()) + "/api/tts"))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .build();

            HttpResponse<byte[]> resp = http.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                return failed(classify(resp.statusCode()), "CosyVoice HTTP " + resp.statusCode(), start);
            }
            byte[] payload = resp.body();
            if (payload == null || payload.length == 0) {
                return failed(TtsErrorCode.EMPTY_AUDIO, "CosyVoice 返回空音频", start);
            }
            byte[] pcm;
            String contentType = resp.headers().firstValue("Content-Type").orElse("").toLowerCase();
            if (contentType.contains("json") || (payload.length > 0 && payload[0] == '{')) {
                JSONObject json = JSON.parseObject(new String(payload, java.nio.charset.StandardCharsets.UTF_8));
                String audioField = json.getString("audio");
                if (StringUtils.isBlank(audioField)) {
                    return failed(TtsErrorCode.EMPTY_AUDIO, "CosyVoice JSON 无 audio 字段", start);
                }
                pcm = Base64.getDecoder().decode(audioField);
                String fmt = json.getString("format");
                if ("wav".equalsIgnoreCase(fmt)) {
                    // 已经是 wav，直接用
                    return success(payload, sampleRate, voice, start, targetFormat, true);
                }
            } else if (contentType.contains("wav") || (payload.length > 11 && payload[0] == 'R')) {
                return success(payload, sampleRate, voice, start, targetFormat, true);
            } else {
                pcm = payload;
            }
            byte[] audio = TtsAudioUtils.normalize(pcm, targetFormat, sampleRate);
            log.info("CosyVoice 合成成功 文本长度={} 字节数={} 耗时={}ms",
                    request.getText().length(), audio.length, System.currentTimeMillis() - start);
            return TtsResult.builder()
                    .success(true)
                    .provider(TtsProviderType.COSYVOICE)
                    .voice(voice)
                    .format(targetFormat)
                    .sampleRate(sampleRate)
                    .durationMs(TtsAudioUtils.estimateDurationMs(pcm.length, sampleRate))
                    .sizeBytes((long) audio.length)
                    .audioBytes(audio)
                    .providerStatus("success")
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } catch (java.net.http.HttpTimeoutException e) {
            return failed(TtsErrorCode.TIMEOUT, "CosyVoice 调用超时", start);
        } catch (Exception e) {
            log.error("CosyVoice 合成异常: {}", e.getMessage());
            return failed(classify(0), e.getMessage(), start);
        }
    }

    private TtsResult success(byte[] wavBytes, int sampleRate, String voice, long start,
                              TtsAudioFormat targetFormat, boolean alreadyWav) {
        return TtsResult.builder()
                .success(true)
                .provider(TtsProviderType.COSYVOICE)
                .voice(voice)
                .format(alreadyWav ? TtsAudioFormat.WAV : targetFormat)
                .sampleRate(sampleRate)
                .durationMs(TtsAudioUtils.estimateDurationMs(Math.max(0, wavBytes.length - 44), sampleRate))
                .sizeBytes((long) wavBytes.length)
                .audioBytes(wavBytes)
                .providerStatus("success")
                .elapsedMs(System.currentTimeMillis() - start)
                .build();
    }

    private static String rtrim(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String classify(int status) {
        if (status == 401 || status == 403) {
            return TtsErrorCode.AUTH_FAILED;
        }
        if (status == 429) {
            return TtsErrorCode.RATE_LIMITED;
        }
        if (status == 0) {
            return TtsErrorCode.SERVICE_UNAVAILABLE;
        }
        return TtsErrorCode.SERVICE_UNAVAILABLE;
    }

    private static TtsResult failed(String code, String msg, long start) {
        return TtsResult.builder()
                .success(false)
                .provider(TtsProviderType.COSYVOICE)
                .errorCode(code)
                .errorMsg(msg)
                .elapsedMs(System.currentTimeMillis() - start)
                .build();
    }
}
