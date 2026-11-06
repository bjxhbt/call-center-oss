package com.och.ai.tts;

/**
 * 统一 TTS 错误码。
 *
 * 覆盖方案 8.10 要求的全部异常场景：空文本、超长文本、非法参数、
 * 鉴权失败、超时、限流、服务不可用、文件/播放失败。
 * 前端与话单依据 code 做中文提示与降级判定，不依赖 message 文案。
 */
public final class TtsErrorCode {

    private TtsErrorCode() {
    }

    /** 文本为空。 */
    public static final String EMPTY_TEXT = "TTS_001";
    /** 文本超长。 */
    public static final String TEXT_TOO_LONG = "TTS_002";
    /** 参数非法（语速/音调/音量/采样率越界等）。 */
    public static final String INVALID_PARAM = "TTS_003";
    /** 不支持的服务商。 */
    public static final String UNSUPPORTED_PROVIDER = "TTS_004";
    /** 鉴权失败（凭证缺失或被拒）。 */
    public static final String AUTH_FAILED = "TTS_005";
    /** 调用超时。 */
    public static final String TIMEOUT = "TTS_006";
    /** 被限流。 */
    public static final String RATE_LIMITED = "TTS_007";
    /** 服务不可用（网络/上游 5xx）。 */
    public static final String SERVICE_UNAVAILABLE = "TTS_008";
    /** 合成返回空音频。 */
    public static final String EMPTY_AUDIO = "TTS_009";
    /** 文件落盘失败。 */
    public static final String FILE_WRITE_FAILED = "TTS_010";
    /** 文件不存在/已删除。 */
    public static final String FILE_NOT_FOUND = "TTS_011";
    /** 文件被业务引用，禁止删除。 */
    public static final String FILE_IN_USE = "TTS_012";
    /** 通道播放失败。 */
    public static final String PLAYBACK_FAILED = "TTS_013";
    /** 其他未分类错误。 */
    public static final String UNKNOWN = "TTS_999";

    public static String message(String code) {
        return switch (code) {
            case EMPTY_TEXT -> "合成文本不能为空";
            case TEXT_TOO_LONG -> "合成文本超过长度限制";
            case INVALID_PARAM -> "TTS 参数非法";
            case UNSUPPORTED_PROVIDER -> "不支持的 TTS 服务商";
            case AUTH_FAILED -> "TTS 鉴权失败";
            case TIMEOUT -> "TTS 调用超时";
            case RATE_LIMITED -> "TTS 被限流";
            case SERVICE_UNAVAILABLE -> "TTS 服务不可用";
            case EMPTY_AUDIO -> "TTS 返回空音频";
            case FILE_WRITE_FAILED -> "TTS 文件落盘失败";
            case FILE_NOT_FOUND -> "TTS 文件不存在";
            case FILE_IN_USE -> "TTS 文件被引用，禁止删除";
            case PLAYBACK_FAILED -> "通道播放失败";
            default -> "TTS 未知错误";
        };
    }
}
