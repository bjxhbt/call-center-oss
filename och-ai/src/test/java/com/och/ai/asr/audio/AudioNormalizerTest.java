package com.och.ai.asr.audio;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.CallAudioFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 音频归一化器测试（方案 7.3 入口编排）：PCM 解码+重采样+校验。
 */
class AudioNormalizerTest {

    private AsrProperties props16k() {
        AsrProperties p = new AsrProperties();
        p.setSampleRate(16000);
        return p;
    }

    private CallAudioFrame pcmFrame(byte[] data, int rate) {
        return CallAudioFrame.builder()
                .callId(1L)
                .uniqueId("u")
                .codec(AudioCodec.PCM)
                .sampleRate(rate)
                .channels(1)
                .data(data)
                .build();
    }

    @Test
    void emptyFrameInvalid() {
        AudioNormalizer n = new AudioNormalizer(props16k());
        AudioNormalizer.NormalizedAudio r = n.normalize(pcmFrame(new byte[0], 16000));
        assertFalse(r.valid());
    }

    @Test
    void upsample8kTo16k() {
        AudioNormalizer n = new AudioNormalizer(props16k());
        // 200 samples 8k → 400 samples 16k = 800 bytes
        short[] samples = new short[200];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = 1000;
        }
        byte[] pcm = AudioDecoder.shortsToBytes(samples);
        AudioNormalizer.NormalizedAudio r = n.normalize(pcmFrame(pcm, 8000));
        assertTrue(r.valid());
        assertEquals(16000, r.sampleRate());
        assertEquals(800, r.pcm().length);
    }

    @Test
    void invalidRateSkipped() {
        AudioNormalizer n = new AudioNormalizer(props16k());
        byte[] pcm = AudioDecoder.shortsToBytes(new short[]{1, 2, 3});
        AudioNormalizer.NormalizedAudio r = n.normalize(pcmFrame(pcm, 44100));
        assertFalse(r.valid());
    }

    @Test
    void silenceFlaggedButStillValid() {
        AudioNormalizer n = new AudioNormalizer(props16k());
        byte[] pcm = AudioDecoder.shortsToBytes(new short[320]); // 全零=静音
        AudioNormalizer.NormalizedAudio r = n.normalize(pcmFrame(pcm, 16000));
        assertTrue(r.valid());
        assertTrue(r.silent());
    }
}
