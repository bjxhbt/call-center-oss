package com.och.ai.asr;

import lombok.Getter;

/**
 * 统一 ASR 受检异常。携带 {@link AsrErrorCode} 错误码，
 * 供业务层据此返回前端提示并决定是否降级（回退 Mock/保留录音离线补偿）。
 *
 * <p>关键约束：ASR 异常 <b>绝不</b>向上冒泡到通话链路（方案 7.15），
 * 所有面向通话的入口都吞掉异常并记录，避免影响基础通话状态。</p>
 */
@Getter
public class AsrException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public AsrException(String code) {
        super(AsrErrorCode.message(code));
        this.code = code;
    }

    public AsrException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AsrException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AsrException(String code, Throwable cause) {
        super(AsrErrorCode.message(code), cause);
        this.code = code;
    }
}
