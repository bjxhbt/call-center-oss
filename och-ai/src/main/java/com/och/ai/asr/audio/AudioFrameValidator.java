package com.och.ai.asr.audio;

/**
 * 音频帧校验器（方案 7.3 / 7.15）：检测空帧、静音、异常帧与采样率非法。
 *
 * <p>采集层与 ASR 入口共用：空帧/静音可跳过发送以节省配额；异常帧不抛断链路，仅标注并记录。</p>
 */
public final class AudioFrameValidator {

    /** 静音 RMS 阈值（线性 16bit 满量程 32767 的比例）。 */
    private static final double SILENCE_RMS_RATIO = 0.012;

    private AudioFrameValidator() {
    }

    /** 是否为空帧（null/长度 0）。 */
    public static boolean isEmpty(short[] samples) {
        return samples == null || samples.length == 0;
    }

    /** 是否为静音（RMS 低于阈值）。空帧视为静音。 */
    public static boolean isSilence(short[] samples) {
        if (isEmpty(samples)) {
            return true;
        }
        double sumSq = 0;
        for (short s : samples) {
            sumSq += (double) s * s;
        }
        double rms = Math.sqrt(sumSq / samples.length);
        return rms < (SILENCE_RMS_RATIO * 32767.0);
    }

    /**
     * 是否采样率合法（MVP 仅支持 8000/16000）。
     */
    public static boolean isRateSupported(Integer rate) {
        return rate != null && (rate == 8000 || rate == 16000);
    }

    /**
     * 综合判定：返回帧健康度（OK/SILENCE/EMPTY/INVALID_RATE）。
     */
    public static FrameHealth check(short[] samples, Integer sampleRate) {
        if (!isRateSupported(sampleRate)) {
            return FrameHealth.INVALID_RATE;
        }
        if (isEmpty(samples)) {
            return FrameHealth.EMPTY;
        }
        if (isSilence(samples)) {
            return FrameHealth.SILENCE;
        }
        return FrameHealth.OK;
    }

    public enum FrameHealth {
        /** 正常。 */
        OK,
        /** 静音。 */
        SILENCE,
        /** 空帧。 */
        EMPTY,
        /** 采样率非法。 */
        INVALID_RATE
    }
}
