package com.och.security.handler;

import com.och.common.base.ResResult;
import com.och.common.constant.HttpStatus;
import com.och.common.enums.ExceptionStatusEnum;
import com.och.common.exception.CommonException;
import com.och.common.exception.LoginException;
import com.och.common.exception.RequestException;
import com.och.system.constant.ErrorCode;
import com.och.system.domain.vo.FieldErrorVo;
import com.och.system.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 全局异常处理器
 *
 * @author danmo
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResResult handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        //增加异常信息
        log.warn("Unsupported request method uri={} method={} supported={}",
                requestURI, e.getMethod(), e.getSupportedHttpMethods());
        // A method mismatch is a caller error, not an opaque server-side failure.
        return ResResult.error(HttpStatus.BAD_METHOD, e.getMessage());
    }


    /**
     * 拦截未知的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResResult handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        //增加异常信息
        log.error("请求地址'{}',发生未知异常.", requestURI, e);
        String msg = e.getMessage();
        if (StringUtils.isEmpty(msg)) {
            msg = "未知异常";
        }
        return ResResult.error(msg);
    }


    /**
     * 数据库操作异常
     */
    @ExceptionHandler(DataAccessException.class)
    public ResResult handleDataAccessException(DataAccessException e) {
        log.error("数据库操作异常：{}", e.getMessage(), e);
        //增加异常信息
        return ResResult.error("数据库操作异常，请联系管理员！");
    }


    /**
     * 登录异常
     */
    @ExceptionHandler(LoginException.class)
    public ResResult handleLoginException(LoginException e) {
        return ResResult.error(e.getCode(), e.getMessage());
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public ResResult handleException(Exception e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生系统异常.", requestURI, e);
        return ResResult.error(e.getMessage());
    }

    /**
     * 权限访问异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResResult handleAccessDeniedException(Exception e, HttpServletRequest request) {
        return ResResult.error(HttpStatus.FORBIDDEN, "没有权限，请联系管理员授权");
    }

    @ExceptionHandler(CommonException.class)
    public ResResult commonExceptionException(Exception e, HttpServletRequest request) {
        return ResResult.error(e.getMessage());
    }

    /**
     * 统一业务异常（携带错误码）。返回 code + 中文提示；若指定了字段则同时返回字段级错误，
     * 供前端请求层在对应表单项展示提示（任务 10.2）。
     */
    @ExceptionHandler(BusinessException.class)
    public ResResult handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 code:{}, field:{}, msg:{}", e.getCode(), e.getField(), e.getMessage());
        ResResult resResult = ResResult.error(e.getCode(), e.getMessage());
        if (StringUtils.isNotBlank(e.getField())) {
            List<FieldErrorVo> errors = new ArrayList<>();
            errors.add(new FieldErrorVo(e.getField(), e.getMessage()));
            resResult.setData(errors);
        }
        return resResult;
    }

    @ExceptionHandler(RequestException.class)
    public ResResult requestExceptionException(Exception e, HttpServletRequest request) {
        return ResResult.error(e.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResResult authenticationExceptionException(Exception e, HttpServletRequest request) {
        return ResResult.error(ExceptionStatusEnum.ERROR_LOGIN_USER.getCode(), ExceptionStatusEnum.ERROR_LOGIN_USER.getMsg());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResResult methodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<FieldErrorVo> errors = new ArrayList<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorVo(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        // 兜底：解析失败时给出通用提示
        if (errors.isEmpty()) {
            errors.add(new FieldErrorVo(null, "参数校验失败"));
        }
        String firstMsg = errors.get(0).getMessage();
        log.warn("参数校验失败 uri:{}, errors:{}", request.getRequestURI(), errors);
        ResResult resResult = ResResult.error(ErrorCode.PARAM_INVALID.getCode(), firstMsg);
        resResult.setData(errors);
        return resResult;
    }

    /**
     * 表单参数绑定异常（非 @RequestBody 的表单提交校验失败）。
     */
    @ExceptionHandler(BindException.class)
    public ResResult bindException(BindException e, HttpServletRequest request) {
        List<FieldErrorVo> errors = new ArrayList<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorVo(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        if (errors.isEmpty()) {
            errors.add(new FieldErrorVo(null, "参数校验失败"));
        }
        log.warn("参数绑定失败 uri:{}, errors:{}", request.getRequestURI(), errors);
        ResResult resResult = ResResult.error(ErrorCode.PARAM_INVALID.getCode(), errors.get(0).getMessage());
        resResult.setData(errors);
        return resResult;
    }

    /**
     * 约束违反异常（@Validated 标注在方法参数/路径变量上的校验失败）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResResult constraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        List<FieldErrorVo> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            String fullPath = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : null;
            String field = fullPath != null && fullPath.contains(".")
                    ? fullPath.substring(fullPath.lastIndexOf('.') + 1) : fullPath;
            errors.add(new FieldErrorVo(field, violation.getMessage()));
        }
        if (errors.isEmpty()) {
            errors.add(new FieldErrorVo(null, "参数校验失败"));
        }
        log.warn("约束校验失败 uri:{}, errors:{}", request.getRequestURI(), errors);
        ResResult resResult = ResResult.error(ErrorCode.PARAM_INVALID.getCode(), errors.get(0).getMessage());
        resResult.setData(errors);
        return resResult;
    }

}
