package com.och.ai.tts;

import lombok.Getter;

/**
 * 统一 TTS 受检异常。携带 {@link TtsErrorCode} 错误码，
 * 供业务层据此返回前端错误提示并决定是否降级（如回退默认语音/最近成功结果）。
 */
@Getter
public class TtsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public TtsException(String code) {
        super(TtsErrorCode.message(code));
        this.code = code;
    }

    public TtsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public TtsException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public TtsException(String code, Throwable cause) {
        super(TtsErrorCode.message(code), cause);
        this.code = code;
    }
}
