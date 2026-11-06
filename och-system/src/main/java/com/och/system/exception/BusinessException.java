package com.och.system.exception;

import com.och.system.constant.ErrorCode;
import lombok.Getter;

/**
 * 业务异常，携带统一错误码 {@link ErrorCode}（任务 10.2）。
 *
 * <p>Service / Controller 通过 {@code throw new BusinessException(ErrorCode.AGENT_SIP_BIND_REPEAT)}
 * 抛出，由 {@code GlobalExceptionHandler} 统一转换为 {@code ResResult.error(code, msg)}，
 * 前端据 code 展示对应中文提示。</p>
 *
 * <p>可选传入 {@link #field} 用于字段级错误（如 SIP 账号重复时定位到 agentNumber 字段）。</p>
 *
 * @author och
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    private final String field;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.field = null;
    }

    public BusinessException(ErrorCode errorCode, String field) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.field = field;
    }

    public BusinessException(ErrorCode errorCode, String field, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.field = field;
    }

    public int getCode() {
        return errorCode.getCode();
    }
}
