package com.cim.system.log;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;

/**
 * {@link OperationLogged} 切面：环绕记录「成功 / 失败」与耗时到 {@code sys_operation_log}。
 *
 * <p><b>审计不得影响业务</b>：日志写入被整体 try/catch 包裹，失败只 warn 不抛——
 * 若审计异常把业务事务带崩，是最典型的本末倒置。</p>
 *
 * <p>HTTP 上下文（URI / 方法 / IP）经 {@code RequestContextHolder} 尽力获取；非 Web 线程
 * （定时任务、MQ 消费）下为 null，不视为异常。</p>
 */
@Slf4j
@Aspect
public class OperationLogAspect {

    private final OperationLogService operationLogService;

    public OperationLogAspect(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Around("@annotation(operationLogged)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLogged operationLogged) throws Throwable {
        long started = System.currentTimeMillis();
        boolean success = true;
        String errorMsg = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            success = false;
            errorMsg = t.getClass().getSimpleName() + ": " + t.getMessage();
            throw t;
        } finally {
            try {
                record(joinPoint, operationLogged, success, errorMsg,
                        System.currentTimeMillis() - started);
            } catch (Exception e) {
                log.warn("[cim-system] write operation log failed: {}", e.getMessage());
            }
        }
    }

    private void record(ProceedingJoinPoint joinPoint, OperationLogged annotation,
                        boolean success, String errorMsg, long durationMs) {
        OperationLog entry = new OperationLog();
        entry.setModule(annotation.module());
        entry.setAction(annotation.action());
        entry.setEventType(annotation.module() + ":" + annotation.action());
        entry.setSuccess(success);
        entry.setErrorMsg(truncate(errorMsg, 512));
        entry.setDurationMs(durationMs);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        entry.setTargetType(method.getDeclaringClass().getSimpleName() + "#" + method.getName());

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entry.setRequestUri(truncate(request.getRequestURI(), 512));
            entry.setHttpMethod(request.getMethod());
            entry.setClientIp(clientIp(request));
        }
        operationLogService.record(entry);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /** 优先取反向代理传递的真实 IP（§15 网关部署约定），回退远端地址。 */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
