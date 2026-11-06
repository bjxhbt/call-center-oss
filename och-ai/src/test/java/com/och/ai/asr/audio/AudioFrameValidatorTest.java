package com.och.ai.asr.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 音频帧校验测试（方案 7.3 / 7.15）：静音/空帧/异常采样率检测。
 */
class AudioFrameValidatorTest {

    @Test
    void detectsSilence() {
        short[] zeros = new short[320];
        assertTrue(AudioFrameValidator.isSilence(zeros));
        assertTrue(AudioFrameValidator.isEmpty(new short[0]));
    }

    @Test
    void detectsNonSilence() {
        short[] loud = new short[320];
        for (int i = 0; i < loud.length; i++) {
            loud[i] = (short) (i % 2 == 0 ? 10000 : -10000);
        }
        assertFalse(AudioFrameValidator.isSilence(loud));
    }

    @Test
    void checkHealthClassifies() {
        short[] loud = new short[320];
        for (int i = 0; i < loud.length; i++) {
            loud[i] = 8000;
        }
        assertEquals(AudioFrameValidator.FrameHealth.OK, AudioFrameValidator.check(loud, 16000));
        assertEquals(AudioFrameValidator.FrameHealth.SILENCE, AudioFrameValidator.check(new short[320], 8000));
        assertEquals(AudioFrameValidator.FrameHealth.EMPTY, AudioFrameValidator.check(new short[0], 16000));
        assertEquals(AudioFrameValidator.FrameHealth.INVALID_RATE, AudioFrameValidator.check(loud, 44100));
    }

    @Test
    void rateSupport() {
        assertTrue(AudioFrameValidator.isRateSupported(8000));
        assertTrue(AudioFrameValidator.isRateSupported(16000));
        assertFalse(AudioFrameValidator.isRateSupported(44100));
        assertFalse(AudioFrameValidator.isRateSupported(null));
    }
}
