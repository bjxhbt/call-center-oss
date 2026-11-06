package com.och.ai.asr.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线性重采样测试（方案 7.3）：8k↔16k 互转、同率直通、插值单调。
 */
class LinearResamplerTest {

    @Test
    void sameRatePassthrough() {
        short[] in = new short[]{1, 2, 3, 4};
        short[] out = LinearResampler.resample(in, 16000, 16000);
        assertEquals(in.length, out.length);
        for (int i = 0; i < in.length; i++) {
            assertEquals(in[i], out[i]);
        }
    }

    @Test
    void upsample8kTo16kDoublesLength() {
        short[] in = new short[100];
        for (int i = 0; i < in.length; i++) {
            in[i] = (short) i;
        }
        short[] out = LinearResampler.resample(in, 8000, 16000);
        assertEquals(200, out.length, "升采样应翻倍");
        // 端点保持
        assertEquals((short) 0, out[0]);
    }

    @Test
    void downsample16kTo8kHalvesLength() {
        short[] in = new short[200];
        for (int i = 0; i < in.length; i++) {
            in[i] = (short) i;
        }
        short[] out = LinearResampler.resample(in, 16000, 8000);
        assertEquals(100, out.length, "降采样应减半");
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertEquals(0, LinearResampler.resample(new short[0], 8000, 16000).length);
        assertEquals(0, LinearResampler.resample(null, 8000, 16000).length);
    }

    @Test
    void rampUpsampleIsMonotonic() {
        short[] ramp = new short[50];
        for (int i = 0; i < ramp.length; i++) {
            ramp[i] = (short) (i * 10);
        }
        short[] out = LinearResampler.resample(ramp, 8000, 16000);
        for (int i = 1; i < out.length; i++) {
            assertTrue(out[i] >= out[i - 1], "斜坡升采样应单调不减 idx=" + i);
        }
    }
}
