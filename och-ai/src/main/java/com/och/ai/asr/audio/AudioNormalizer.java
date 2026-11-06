package com.och.ai.asr.audio;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.CallAudioFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 音频归一化器（方案 7.3 入口编排）。
 *
 * <p>把采集层的 {@link CallAudioFrame} 统一处理为 <b>目标采样率、16bit 线性 PCM 字节</b>，
 * 供 Provider 分片发送。流程：解码(PCMA/PCMU/PCM) → 重采样(8k→16k) → 校验(静音/空/异常)。</p>
 *
 * <p>对非法采样率/异常帧不抛异常：返回 {@link NormalizedAudio#invalid}，由会话层决定跳过/记录，
 * 保证音频错误不会冒泡到通话链路（方案 7.15）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioNormalizer {

    private final AsrProperties properties;

    /**
     * 归一化一帧音频。
     *
     * @param frame 原始帧
     * @return 归一化结果（线性 PCM 字节 + 是否静音 + 是否有效）
     */
    public NormalizedAudio normalize(CallAudioFrame frame) {
        if (frame == null || frame.getData() == null || frame.getData().length == 0) {
            return NormalizedAudio.empty();
        }
        int sourceRate = frame.getSampleRate() == null ? 8000 : frame.getSampleRate();
        if (!AudioFrameValidator.isRateSupported(sourceRate)) {
            log.warn("跳过非法采样率帧 callId:{} rate:{}", frame.getCallId(), sourceRate);
            return NormalizedAudio.invalid();
        }
        try {
            short[] pcm = AudioDecoder.decodeToPcm(frame);
            if (AudioFrameValidator.isEmpty(pcm)) {
                return NormalizedAudio.empty();
            }
            int targetRate = properties.getSampleRate();
            short[] resampled = LinearResampler.resample(pcm, sourceRate, targetRate);
            boolean silent = AudioFrameValidator.isSilence(resampled);
            return new NormalizedAudio(AudioDecoder.shortsToBytes(resampled), targetRate, silent, true);
        } catch (Exception e) {
            log.warn("音频归一化失败 callId:{} err:{}", frame.getCallId(), e.getMessage());
            return NormalizedAudio.invalid();
        }
    }

    /**
     * 归一化结果。
     */
    public record NormalizedAudio(byte[] pcm, int sampleRate, boolean silent, boolean valid) {

        public static NormalizedAudio empty() {
            return new NormalizedAudio(new byte[0], 0, true, false);
        }

        public static NormalizedAudio invalid() {
            return new NormalizedAudio(new byte[0], 0, false, false);
        }
    }
}
