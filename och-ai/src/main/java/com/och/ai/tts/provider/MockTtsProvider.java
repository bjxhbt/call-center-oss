package com.och.ai.tts.provider;

import com.och.ai.tts.TtsErrorCode;
import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;
import com.och.ai.tts.util.TtsAudioUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock TTS Provider。
 *
 * <p>无需任何云凭证即可生成可播放音频（确定性正弦波 PCM/WAV），
 * 用于：本方案前置联调、前端 Mock 试听、单测与无凭证环境回归。
 *
 * <p>声学质量非真实语音，但格式严格满足 FreeSWITCH（8k/16bit/mono WAV）与浏览器试听，
 * 因此可验证整条“合成→落盘→试听→播放”链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockTtsProvider implements TtsServiceProvider {

    private final TtsProperties properties;

    @Override
    public TtsProviderType type() {
        return TtsProviderType.MOCK;
    }

    @Override
    public TtsResult synthesize(TtsRequest request) {
        long start = System.currentTimeMillis();
        int sampleRate = request.getSampleRate() == null ? properties.getDefaultSampleRate() : request.getSampleRate();
        TtsAudioFormat format = request.getFormat() == null ? properties.getDefaultFormat() : request.getFormat();
        try {
            // 不同文本对应不同基频，便于在缓存/引用场景区分产物
            double freq = 220.0 + (Math.abs(hash(request.getText())) % 220);
            byte[] pcm = TtsAudioUtils.generateTonePcm(request.getText(), sampleRate, freq);
            byte[] audio = TtsAudioUtils.normalize(pcm, format, sampleRate);

            return TtsResult.builder()
                    .success(true)
                    .provider(TtsProviderType.MOCK)
                    .voice(request.getVoice() == null ? "mock-default" : request.getVoice())
                    .format(format)
                    .sampleRate(sampleRate)
                    .durationMs(TtsAudioUtils.estimateDurationMs(pcm.length, sampleRate))
                    .sizeBytes((long) audio.length)
                    .audioBytes(audio)
                    .providerStatus("mock-ok")
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("Mock TTS 合成失败: {}", e.getMessage(), e);
            return TtsResult.builder()
                    .success(false)
                    .provider(TtsProviderType.MOCK)
                    .errorCode(TtsErrorCode.UNKNOWN)
                    .errorMsg("Mock 合成失败: " + e.getMessage())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private static int hash(String s) {
        return s == null ? 0 : s.hashCode();
    }
}
