package com.och.ai.tts.model;

/**
 * 统一 TTS 服务商标识。
 *
 * 业务代码只面向 {@code TtsServiceProvider}，不直接依赖任一云厂商 SDK；
 * 实际使用哪家由 {@code och.tts.provider} 配置（或请求内 provider 覆盖）。
 *
 * <ul>
 *   <li>{@link #MOCK}      —— 无凭证兜底/单测用，生成确定性 PCM/WAV，保证链路可联调。</li>
 *   <li>{@link #ALIYUN}    —— 主用云（阿里云 NLS），MVP 默认主用。</li>
 *   <li>{@link #TENCENT}   —— 第二云（腾讯云 TextToVoice），用于对比/预留切换。</li>
 *   <li>{@link #COSYVOICE} —— 本地 CosyVoice 服务，仅验证，不进 MVP 生产主链路。</li>
 * </ul>
 */
public enum TtsProviderType {

    MOCK,
    ALIYUN,
    TENCENT,
    COSYVOICE;

    public static TtsProviderType parse(String name, TtsProviderType fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return TtsProviderType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
