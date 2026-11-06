package com.och.ai.tts.provider;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import com.och.ai.tts.TtsErrorCode;
import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;
import com.och.ai.tts.util.TtsAudioUtils;
import com.och.common.config.redis.RedisService;
import com.och.common.config.oss.AliCloudConfig;
import com.och.common.constant.SysSettingConfig;
import com.och.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 阿里云 NLS TTS Provider（MVP 主用云）。
 *
 * <p>鉴权：通过 {@link AccessToken} 用 AccessKeyId/Secret 换取 appToken，缓存到 Redis
 * （键 {@code CacheConstants.ALI_TTS_TOKEN_KEY}），过期前自动刷新。
 * 凭据来源 {@link SysSettingConfig#getAliConfig()}，本类不持有任何硬编码密钥。
 *
 * <p>仅在 {@code och.tts.aliyun.enabled=true} 时装配；缺凭证时返回
 * {@link TtsErrorCode#AUTH_FAILED}，不抛异常，便于上层降级到 Mock/默认语音。
 *
 * <p>输出统一封装为 WAV（8k/16bit/mono），可直接给浏览器试听与 FreeSWITCH 播放。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "och.tts.aliyun", name = "enabled", havingValue = "true")
public class AliyunTtsProvider implements TtsServiceProvider {

    private static final String TOKEN_CACHE_KEY = "ali_tts_engine_token";
    private static final long TOKEN_REFRESH_LEAD_SECONDS = 120L;

    private final TtsProperties properties;
    private final SysSettingConfig sysSettingConfig;
    private final RedisService redisService;

    private volatile NlsClient client;
    private volatile long tokenExpireAt = 0L;

    public AliyunTtsProvider(TtsProperties properties,
                             SysSettingConfig sysSettingConfig,
                             RedisService redisService) {
        this.properties = properties;
        this.sysSettingConfig = sysSettingConfig;
        this.redisService = redisService;
    }

    @Override
    public TtsProviderType type() {
        return TtsProviderType.ALIYUN;
    }

    @Override
    public boolean ready() {
        return hasCredentials(credentials());
    }

    @Override
    public TtsResult synthesize(TtsRequest request) {
        long start = System.currentTimeMillis();
        AliCloudConfig.AliTtsConfig tts = credentials();
        if (!hasCredentials(tts)) {
            return failed(TtsErrorCode.AUTH_FAILED, "阿里云 TTS 凭证缺失，请在系统设置中配置", start);
        }
        int sampleRate = request.getSampleRate() == null ? properties.getDefaultSampleRate() : request.getSampleRate();
        TtsAudioFormat format = request.getFormat() == null ? properties.getDefaultFormat() : request.getFormat();

        try {
            String token = getOrRefreshToken(tts.getAccessKeyId(), tts.getAccessKeySecret());
            if (StringUtils.isBlank(token)) {
                return failed(TtsErrorCode.AUTH_FAILED, "阿里云 TTS token 获取失败", start);
            }
            ensureClient(tts.getUrl(), token);

            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            AtomicReference<JSONObject> completeRef = new AtomicReference<>();

            SpeechSynthesizerListener listener = new SpeechSynthesizerListener() {
                @Override
                public void onComplete(SpeechSynthesizerResponse response) {
                    JSONObject json = new JSONObject();
                    json.put("status", response.getStatus());
                    json.put("statusText", response.getStatusText());
                    completeRef.set(json);
                }

                @Override
                public void onMessage(ByteBuffer message) {
                    byte[] chunk = new byte[message.remaining()];
                    message.get(chunk);
                    buffer.write(chunk, 0, chunk.length);
                }

                @Override
                public void onFail(SpeechSynthesizerResponse response) {
                    errorRef.set(new IllegalStateException("status=" + response.getStatus()
                            + ", text=" + response.getStatusText()));
                }
            };

            SpeechSynthesizer synthesizer = new SpeechSynthesizer(client, listener);
            try {
                synthesizer.setAppKey(tts.getAppKey());
                // 阿里返回 PCM，再由 TtsAudioUtils 封装为目标格式（默认 WAV）
                synthesizer.setFormat(OutputFormatEnum.PCM);
                synthesizer.setSampleRate(sampleRateEnum(sampleRate));
                String voice = StringUtils.isBlank(request.getVoice()) ? tts.getVoice() : request.getVoice();
                if (!StringUtils.isBlank(voice)) {
                    synthesizer.setVoice(voice);
                }
                if (request.getPitch() != null) {
                    synthesizer.setPitchRate(mapPitch(request.getPitch()));
                }
                if (request.getSpeed() != null) {
                    synthesizer.setSpeechRate(mapRate(request.getSpeed()));
                }
                synthesizer.setText(request.getText());
                synthesizer.addCustomedParam("enable_subtitle", false);
                synthesizer.start();
                synthesizer.waitForComplete();
            } finally {
                synthesizer.close();
            }

            Throwable err = errorRef.get();
            if (err != null) {
                return failed(classify(err), err.getMessage(), start);
            }
            byte[] pcm = buffer.toByteArray();
            if (pcm.length == 0) {
                return failed(TtsErrorCode.EMPTY_AUDIO, "阿里云 TTS 返回空音频", start);
            }
            byte[] audio = TtsAudioUtils.normalize(pcm, format, sampleRate);
            String statusText = completeRef.get() == null ? "ok" : completeRef.get().getString("statusText");
            log.info("阿里云 TTS 合成成功 文本长度={} 字节数={} 耗时={}ms",
                    request.getText().length(), audio.length, System.currentTimeMillis() - start);
            return TtsResult.builder()
                    .success(true)
                    .provider(TtsProviderType.ALIYUN)
                    .voice(StringUtils.isBlank(request.getVoice()) ? tts.getVoice() : request.getVoice())
                    .format(format)
                    .sampleRate(sampleRate)
                    .durationMs(TtsAudioUtils.estimateDurationMs(pcm.length, sampleRate))
                    .sizeBytes((long) audio.length)
                    .audioBytes(audio)
                    .providerStatus(statusText)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("阿里云 TTS 合成异常: {}", e.getMessage(), e);
            return failed(classify(e), e.getMessage(), start);
        }
    }

    private void ensureClient(String url, String token) {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = StringUtils.isBlank(url) ? new NlsClient(token) : new NlsClient(url, token);
                    return;
                }
            }
        }
        client.setToken(token);
    }

    private AliCloudConfig.AliTtsConfig credentials() {
        return sysSettingConfig.getAliConfig() == null ? null : sysSettingConfig.getAliConfig().getTts();
    }

    private static boolean hasCredentials(AliCloudConfig.AliTtsConfig tts) {
        return tts != null && StringUtils.isNotBlank(tts.getAccessKeyId())
                && StringUtils.isNotBlank(tts.getAccessKeySecret()) && StringUtils.isNotBlank(tts.getAppKey());
    }

    private String getOrRefreshToken(String ak, String sk) {
        long now = System.currentTimeMillis();
        if (tokenExpireAt - now > TOKEN_REFRESH_LEAD_SECONDS * 1000L && client != null) {
            // 客户端已持有有效 token
        }
        try {
            if (redisService != null && redisService.keyIsExists(TOKEN_CACHE_KEY)) {
                String cached = redisService.getCacheObject(TOKEN_CACHE_KEY);
                if (StringUtils.isNotBlank(cached)) {
                    tokenExpireAt = now + TOKEN_REFRESH_LEAD_SECONDS * 1000L;
                    return cached;
                }
            }
        } catch (Exception ignored) {
            // Redis 不可用不影响主流程
        }
        try {
            AccessToken accessToken = new AccessToken(ak, sk);
            accessToken.apply();
            long expireSec = Math.max(60L, accessToken.getExpireTime() - (now / 1000L));
            tokenExpireAt = now + expireSec * 1000L;
            try {
                if (redisService != null) {
                    redisService.setCacheObject(TOKEN_CACHE_KEY, accessToken.getToken(), expireSec, TimeUnit.SECONDS);
                }
            } catch (Exception ignored) {
            }
            return accessToken.getToken();
        } catch (Exception e) {
            log.error("阿里云 TTS token 获取失败: {}", e.getMessage());
            return null;
        }
    }

    private static SampleRateEnum sampleRateEnum(int sampleRate) {
        return sampleRate >= 16000 ? SampleRateEnum.SAMPLE_RATE_16K : SampleRateEnum.SAMPLE_RATE_8K;
    }

    /** 归一化 [-1,1] → 阿里 [-500,500]。 */
    private static int mapRate(double v) {
        return (int) Math.round(Math.max(-1.0, Math.min(1.0, v)) * 500);
    }

    private static int mapPitch(double v) {
        return (int) Math.round(Math.max(-1.0, Math.min(1.0, v)) * 500);
    }

    private static String classify(Throwable e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("timeout") || msg.contains("Timeout") || e instanceof java.net.SocketTimeoutException) {
            return TtsErrorCode.TIMEOUT;
        }
        if (msg.contains("429") || msg.contains("Throttl") || msg.contains("limit")) {
            return TtsErrorCode.RATE_LIMITED;
        }
        if (msg.contains("401") || msg.contains("403") || msg.contains("Auth") || msg.contains("token")) {
            return TtsErrorCode.AUTH_FAILED;
        }
        return TtsErrorCode.SERVICE_UNAVAILABLE;
    }

    private static TtsResult failed(String code, String msg, long start) {
        return TtsResult.builder()
                .success(false)
                .provider(TtsProviderType.ALIYUN)
                .errorCode(code)
                .errorMsg(msg)
                .elapsedMs(System.currentTimeMillis() - start)
                .build();
    }
}
