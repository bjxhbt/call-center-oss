package com.och.ai.asr.model;

/**
 * ASR 入口音频编码（方案 7.3）。
 *
 * <p>FreeSWITCH 通道音频通常为 G.711a(PCMA)/G.711u(PCMU)，8kHz；
 * 统一接口在入口解码为线性 PCM 后送入 ASR，OPUS 等暂不入 MVP 主链路。</p>
 */
public enum AudioCodec {

    /** G.711 A-law（中国 E1/SIP 主流）。 */
    PCMA,
    /** G.711 U-law。 */
    PCMU,
    /** 线性 PCM（16bit 有符号）。 */
    PCM,
    /** OPUS（暂不支持，仅占位）。 */
    OPUS;

    public static AudioCodec parse(String name, AudioCodec fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return AudioCodec.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public boolean isG711() {
        return this == PCMA || this == PCMU;
    }
}
