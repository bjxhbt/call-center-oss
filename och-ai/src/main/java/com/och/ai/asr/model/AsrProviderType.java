package com.och.ai.asr.model;

/**
 * 统一 ASR 服务商标识（方案 7.6）。
 *
 * <p>业务代码只面向 {@code IAsrProvider}，不直接依赖任一云厂商 SDK；
 * 实际使用哪家由 {@code och.asr.provider} 配置（或会话内 provider 覆盖）。</p>
 *
 * <ul>
 *   <li>{@link #MOCK}    —— 无凭证兜底/单测用，按脚本产生增量/最终结果，保证整条链路可联调。</li>
 *   <li>{@link #TENCENT} —— 腾讯云一句话/实时 ASR（WebSocket）。MVP 可配置为主用。</li>
 *   <li>{@link #ALIYUN}  —— 阿里云 NLS 实时 ASR（WebSocket）。第二云，验证用。</li>
 *   <li>{@link #FUNASR}  —— 本地 FunASR runtime（WebSocket），仅验证，不进 MVP 生产主链路。</li>
 * </ul>
 */
public enum AsrProviderType {

    MOCK,
    TENCENT,
    ALIYUN,
    FUNASR;

    public static AsrProviderType parse(String name, AsrProviderType fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return AsrProviderType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
