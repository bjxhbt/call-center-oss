package com.och.ai.asr;

/**
 * 统一 ASR 错误码（方案 7.6 / 7.15）。
 *
 * <p>覆盖鉴权失败、超时、限流、连接断开、音频错误、会话冲突、Provider 未就绪等场景。
 * 前端、话单、转写记录依据 code 做中文提示与降级判定，不依赖 message 文案。</p>
 */
public final class AsrErrorCode {

    private AsrErrorCode() {
    }

    /** 鉴权失败（凭证缺失或被拒）。 */
    public static final String AUTH_FAILED = "ASR_001";
    /** 调用/最终结果等待超时。 */
    public static final String TIMEOUT = "ASR_002";
    /** 被限流。 */
    public static final String RATE_LIMITED = "ASR_003";
    /** 长连接断开（网络/对端关闭）。 */
    public static final String CONNECTION_BROKEN = "ASR_004";
    /** 音频错误（空帧/格式不支持/采样率非法）。 */
    public static final String AUDIO_ERROR = "ASR_005";
    /** 会话重复创建（同 callId/UUID 已存在）。 */
    public static final String SESSION_DUPLICATED = "ASR_006";
    /** 会话不存在（音频/结束命中空会话）。 */
    public static final String SESSION_NOT_FOUND = "ASR_007";
    /** Provider 未就绪（缺凭证/未装配）。 */
    public static final String PROVIDER_NOT_READY = "ASR_008";
    /** 参数非法。 */
    public static final String INVALID_PARAM = "ASR_009";
    /** 重连次数耗尽。 */
    public static final String RECONNECT_EXHAUSTED = "ASR_010";
    /** 发送音频失败。 */
    public static final String SEND_FAILED = "ASR_011";
    /** 其他未分类错误。 */
    public static final String UNKNOWN = "ASR_999";

    public static String message(String code) {
        return switch (code) {
            case AUTH_FAILED -> "ASR 鉴权失败";
            case TIMEOUT -> "ASR 超时";
            case RATE_LIMITED -> "ASR 被限流";
            case CONNECTION_BROKEN -> "ASR 连接断开";
            case AUDIO_ERROR -> "ASR 音频错误";
            case SESSION_DUPLICATED -> "ASR 会话重复";
            case SESSION_NOT_FOUND -> "ASR 会话不存在";
            case PROVIDER_NOT_READY -> "ASR 服务未就绪";
            case INVALID_PARAM -> "ASR 参数非法";
            case RECONNECT_EXHAUSTED -> "ASR 重连耗尽";
            case SEND_FAILED -> "ASR 发送失败";
            default -> "ASR 未知错误";
        };
    }
}
