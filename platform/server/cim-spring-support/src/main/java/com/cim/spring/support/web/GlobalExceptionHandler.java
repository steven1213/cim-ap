package com.cim.spring.support.web;

import com.cim.spring.support.i18n.MessageResolver;
import com.cim.spring.support.trace.TraceContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器（见 README §4）。
 *
 * <p>分层处理：{@link BizException}（已知业务）→ 业务码 + i18n 文案；
 * 校验异常 → 字段级 {@code errors} + 400；其余 → 500 + {@code traceId} + 通用语（不泄露细节）。
 * 所有响应统一经 {@link Result}，并写入 MDC {@code traceId}。</p>
 *
 * <p>安全异常（401/403）由 {@code cim-auth-starter} 的专用 advice 处理，避免本模块引入
 * Spring Security 依赖。</p>
 */
@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageResolver messageResolver;

    /** 业务异常。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex) {
        BizCode code = ex.getBizCode();
        String msg = messageResolver.resolve(code.getI18nKey(), ex.getArgs(), code.getDefaultMessage());
        // 业务异常属预期分支，记 warn 且不打栈
        log.warn("[BizException] code={} msg={} traceId={}", code.getCode(), msg, TraceContext.currentTraceId());
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code, msg));
    }

    /** 参数校验异常（携带字段级错误）。 */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result<Void>> handleValidation(ValidationException ex) {
        BizCode code = BizCode.PARAM_INVALID;
        String msg = messageResolver.resolve(code.getI18nKey(), null, code.getDefaultMessage());
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code, msg, ex.getErrors()));
    }

    /** {@code @Valid} 请求体校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return bindFailure(ex.getBindingResult().getFieldErrors());
    }

    /** {@code @ModelAttribute} 等模型绑定校验失败。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException ex) {
        return bindFailure(ex.getBindingResult().getFieldErrors());
    }

    /** 校验错误的统一响应构造。 */
    private ResponseEntity<Result<Void>> bindFailure(java.util.List<FieldError> fieldErrors) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : fieldErrors) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        BizCode code = BizCode.PARAM_INVALID;
        String msg = messageResolver.resolve(code.getI18nKey(), null, code.getDefaultMessage());
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code, msg, errors));
    }

    /** 方法级参数约束（{@code @Validated} + {@code @RequestParam} 等）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            errors.putIfAbsent(String.valueOf(v.getPropertyPath()), v.getMessage());
        }
        BizCode code = BizCode.PARAM_INVALID;
        String msg = messageResolver.resolve(code.getI18nKey(), null, code.getDefaultMessage());
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code, msg, errors));
    }

    /** 请求参数缺失 / 类型不匹配 / 报文不可读。 */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception ex) {
        BizCode code = BizCode.PARAM_INVALID;
        String msg = messageResolver.resolve(code.getI18nKey(), null, code.getDefaultMessage());
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code, msg));
    }

    /** 请求方法不支持。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        BizCode code = BizCode.OPERATION_NOT_ALLOWED;
        String msg = messageResolver.resolve(code.getI18nKey(), null, code.getDefaultMessage());
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code, msg));
    }

    /** 未预期异常：仅返回 traceId + 通用语，详情落日志。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        // 安全异常（401/403）不是「业务错误」，应交由 Spring Security 过滤器链
        // （ExceptionTranslationFilter）转换成 401/403；此处若吞掉会变成 500。
        // 按类名识别，避免本模块直接依赖 Spring Security（见 README §4）。
        if (isSecurityException(ex)) {
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(ex);
        }
        BizCode code = BizCode.SYSTEM_ERROR;
        String traceId = TraceContext.currentTraceId();
        log.error("[SystemError] traceId={} ", traceId, ex);
        String msg = messageResolver.resolve(code.getI18nKey(), null, code.getDefaultMessage());
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code, msg));
    }

    /** 安全相关异常（不引入 Spring Security 依赖，按包名/类型名识别，并沿 cause 链检查）。 */
    private boolean isSecurityException(Exception ex) {
        Throwable t = ex;
        while (t != null) {
            String name = t.getClass().getName();
            // 覆盖 org.springframework.security.*（含 Spring Security 6.1+ 的
            // authorization.AuthorizationDeniedException 等新类型）
            if (name.startsWith("org.springframework.security.")) {
                return true;
            }
            if (name.endsWith("AccessDeniedException") || name.endsWith("AuthenticationException")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
