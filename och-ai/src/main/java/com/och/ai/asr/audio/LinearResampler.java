package com.och.ai.asr.audio;

/**
 * 线性重采样器（方案 7.3）：在 8kHz 与 16kHz 之间转换线性 PCM。
 *
 * <p>电话音频常为 8kHz，多数云 ASR 期望 16kHz 以提升准确率。采用线性插值：
 * 升采样插入相邻样本均值，降采样取相邻均值。质量满足 ASR（非高保真），实现零依赖、可单测。</p>
 *
 * <p>仅支持 8000/16000 互转与同率直通；其它比率回退到最近邻。</p>
 */
public final class LinearResampler {

    private LinearResampler() {
    }

    /**
     * 将线性 PCM 重采样到目标采样率。
     *
     * @param samples   输入 16bit 线性样本
     * @param fromRate  输入采样率
     * @param toRate    目标采样率
     * @return 重采样后样本
     */
    public static short[] resample(short[] samples, int fromRate, int toRate) {
        if (samples == null || samples.length == 0) {
            return new short[0];
        }
        if (fromRate == toRate) {
            return samples;
        }
        double ratio = (double) toRate / (double) fromRate;
        int outLen = (int) Math.round(samples.length * ratio);
        if (outLen <= 0) {
            return new short[0];
        }
        short[] out = new short[outLen];
        for (int i = 0; i < outLen; i++) {
            double srcPos = i / ratio;
            int idx = (int) Math.floor(srcPos);
            double frac = srcPos - idx;
            short a = samples[Math.min(idx, samples.length - 1)];
            short b = samples[Math.min(idx + 1, samples.length - 1)];
            out[i] = (short) (a + (b - a) * frac);
        }
        return out;
    }
}
