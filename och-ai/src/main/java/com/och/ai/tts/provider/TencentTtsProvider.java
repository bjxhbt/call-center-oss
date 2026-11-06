package com.och.ai.tts.provider;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.tts.v20190823.TtsClient;
import com.tencentcloudapi.tts.v20190823.models.TextToVoiceRequest;
import com.tencentcloudapi.tts.v20190823.models.TextToVoiceResponse;
import com.och.ai.tts.TtsErrorCode;
import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;
import com.och.ai.tts.util.TtsAudioUtils;
import com.och.common.config.oss.TxCloudConfig;
import com.och.common.constant.SysSettingConfig;
import com.och.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 腾讯云 TTS Provider（第二云，验证与预留切换）。
 *
 * <p>采用同步 {@code TextToVoice}，返回 Base64 PCM；本类解码后封装为 WAV。
 * 凭据复用同账号 COS 的 SecretId/SecretKey（{@link TxCloudConfig.TxCosConfig}），
 * 不在代码中硬编码。{@code och.tts.tencent.enabled=false}（默认）时不装配，
 * 验证时显式打开。
 *
 * <p>本 Provider 仅用于第二云对比与切换预留，不阻塞主用云；MVP 不引入自动主备切换。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "och.tts.tencent", name = "enabled", havingValue = "true")
public class TencentTtsProvider implements TtsServiceProvider {

    private static final int MAX_SEGMENT_LENGTH = 120;

    private final TtsProperties properties;
    private final SysSettingConfig sysSettingConfig;

    public TencentTtsProvider(TtsProperties properties, SysSettingConfig sysSettingConfig) {
        this.properties = properties;
        this.sysSettingConfig = sysSettingConfig;
    }

    @Override
    public TtsProviderType type() {
        return TtsProviderType.TENCENT;
    }

    @Override
    public boolean ready() {
        return hasCredentials(credentials());
    }

    @Override
    public TtsResult synthesize(TtsRequest request) {
        long start = System.currentTimeMillis();
        TxCloudConfig.TxCosConfig cos = credentials();
        if (!hasCredentials(cos)) {
            return failed(TtsErrorCode.AUTH_FAILED, "腾讯云 SecretId/SecretKey 缺失", start);
        }
        int sampleRate = request.getSampleRate() == null ? properties.getDefaultSampleRate() : request.getSampleRate();
        TtsAudioFormat format = request.getFormat() == null ? properties.getDefaultFormat() : request.getFormat();
        try {
            Credential cred = new Credential(cos.getAccessKey(), cos.getSecretKey());

            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint(StringUtils.isBlank(properties.getTencent().getEndpoint())
                    ? "tts.tencentcloudapi.com" : properties.getTencent().getEndpoint());

            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);

            String region = StringUtils.isBlank(cos.getRegionName())
                    ? properties.getTencent().getRegion() : cos.getRegionName();
            TtsClient client = new TtsClient(cred, region, clientProfile);

            ByteArrayOutputStream pcmOutput = new ByteArrayOutputStream();
            List<String> segments = splitText(request.getText(), MAX_SEGMENT_LENGTH);
            for (int index = 0; index < segments.size(); index++) {
                TextToVoiceRequest req = buildRequest(request, sampleRate, segments.get(index), index);
                TextToVoiceResponse resp = client.TextToVoice(req);
                String audioBase64 = resp.getAudio();
                if (StringUtils.isBlank(audioBase64)) {
                    return failed(TtsErrorCode.EMPTY_AUDIO,
                            "腾讯云返回音频为空，分段 " + (index + 1) + "/" + segments.size(), start);
                }
                pcmOutput.write(Base64.getDecoder().decode(audioBase64));
            }
            byte[] pcm = pcmOutput.toByteArray();
            byte[] audio = TtsAudioUtils.normalize(pcm, format, sampleRate);
            log.info("腾讯云 TTS 合成成功 文本长度={} 分段数={} 字节数={} 耗时={}ms",
                    request.getText().length(), segments.size(), audio.length, System.currentTimeMillis() - start);
            return TtsResult.builder()
                    .success(true)
                    .provider(TtsProviderType.TENCENT)
                    .voice(StringUtils.isBlank(request.getVoice())
                            ? properties.getTencent().getVoice() : request.getVoice())
                    .format(format)
                    .sampleRate(sampleRate)
                    .durationMs(TtsAudioUtils.estimateDurationMs(pcm.length, sampleRate))
                    .sizeBytes((long) audio.length)
                    .audioBytes(audio)
                    .providerStatus("success")
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } catch (TencentCloudSDKException e) {
            log.error("腾讯云 TTS 合成失败: {}", e.getMessage());
            return failed(classify(e.getMessage()), e.getMessage(), start);
        } catch (Exception e) {
            log.error("腾讯云 TTS 合成异常: {}", e.getMessage(), e);
            return failed(classify(e.getMessage()), e.getMessage(), start);
        }
    }

    private TextToVoiceRequest buildRequest(TtsRequest request, int sampleRate, String text, int segmentIndex) {
        TextToVoiceRequest req = new TextToVoiceRequest();
        req.setText(text);
        req.setSessionId(UUID.randomUUID().toString().replace("-", "") + segmentIndex);
        req.setVoiceType(parseVoiceType(StringUtils.isBlank(request.getVoice())
                ? properties.getTencent().getVoice() : request.getVoice()));
        req.setCodec("pcm");
        req.setSampleRate((long) sampleRate);
        req.setVolume(request.getVolume() == null ? 0F : mapVolume(request.getVolume()));
        req.setSpeed(request.getSpeed() == null ? 0F : mapTencentSpeed(request.getSpeed()));
        req.setEnableSubtitle(false);
        return req;
    }

    static List<String> splitText(String text, int maxLength) {
        List<String> segments = new ArrayList<>();
        String remaining = text == null ? "" : text.trim();
        while (remaining.length() > maxLength) {
            int splitAt = findSplitPosition(remaining, maxLength);
            String segment = remaining.substring(0, splitAt).trim();
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
            remaining = remaining.substring(splitAt).trim();
        }
        if (!remaining.isEmpty()) {
            segments.add(remaining);
        }
        return segments;
    }

    private static int findSplitPosition(String text, int maxLength) {
        int minimum = Math.max(1, maxLength / 2);
        for (int index = maxLength - 1; index >= minimum; index--) {
            if (isSentenceBoundary(text.charAt(index))) {
                return index + 1;
            }
        }
        return maxLength;
    }

    private static boolean isSentenceBoundary(char character) {
        return "。！？；，、,.!?;\n".indexOf(character) >= 0;
    }

    private TxCloudConfig.TxCosConfig credentials() {
        return sysSettingConfig.getTxConfig() == null ? null : sysSettingConfig.getTxConfig().getCos();
    }

    private static boolean hasCredentials(TxCloudConfig.TxCosConfig cos) {
        return cos != null && StringUtils.isNotBlank(cos.getAccessKey())
                && StringUtils.isNotBlank(cos.getSecretKey());
    }

    private static long parseVoiceType(String voice) {
        if (StringUtils.isBlank(voice)) {
            return 101001L;
        }
        try {
            return Long.parseLong(voice.trim());
        } catch (NumberFormatException e) {
            return 101001L;
        }
    }

    /** 归一化 [-1,1] → 腾讯 [-2,6]，0 为正常。 */
    private static float mapTencentSpeed(double v) {
        double clamped = Math.max(-1.0, Math.min(1.0, v));
        return (float) (clamped >= 0 ? clamped * 6.0 : clamped * 2.0);
    }

    /** 归一化 [0,1] → 腾讯音量 [0,100]，0 表平台默认。 */
    private static float mapVolume(double v) {
        return (float) Math.round(Math.max(0.0, Math.min(1.0, v)) * 100);
    }

    private static String classify(String msg) {
        if (msg == null) {
            return TtsErrorCode.UNKNOWN;
        }
        if (msg.contains("timeout") || msg.contains("Timeout")) {
            return TtsErrorCode.TIMEOUT;
        }
        if (msg.contains("LimitExceeded") || msg.contains("Throttl") || msg.contains("RequestLimit")) {
            return TtsErrorCode.RATE_LIMITED;
        }
        if (msg.contains("AuthFailure") || msg.contains("SecretId") || msg.contains("Unauthorized")) {
            return TtsErrorCode.AUTH_FAILED;
        }
        if (msg.contains("InternalError") || msg.contains("FailedOperation")) {
            return TtsErrorCode.SERVICE_UNAVAILABLE;
        }
        return TtsErrorCode.UNKNOWN;
    }

    private static TtsResult failed(String code, String msg, long start) {
        return TtsResult.builder()
                .success(false)
                .provider(TtsProviderType.TENCENT)
                .errorCode(code)
                .errorMsg(msg)
                .elapsedMs(System.currentTimeMillis() - start)
                .build();
    }
}
