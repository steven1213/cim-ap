package com.cim.spring.support.web;

import com.cim.spring.support.trace.TraceContext;

import java.io.Serializable;
import java.util.Map;

/**
 * 统一响应结构（见 README §4）。
 *
 * @param code      业务码（0 表示成功，见 {@link BizCode}）
 * @param msg       i18n 解析后的提示
 * @param data      业务数据
 * @param errors    字段级校验错误（field → 提示），无则为 {@code null}
 * @param traceId   链路追踪 ID（取自 MDC）
 * @param timestamp 服务器时间戳（毫秒）
 * @param <T>       数据类型
 */
public record Result<T>(
        int code,
        String msg,
        T data,
        Map<String, String> errors,
        String traceId,
        long timestamp
) implements Serializable {

    /** 成功（无数据）。 */
    public static Result<Void> ok() {
        return ok(null);
    }

    /** 成功（带数据）。 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(BizCode.SUCCESS.getCode(), BizCode.SUCCESS.getDefaultMessage(),
                data, null, TraceContext.currentTraceId(), System.currentTimeMillis());
    }

    /** 失败（业务码 + 已解析消息）。 */
    public static <T> Result<T> fail(BizCode code, String msg) {
        return new Result<>(code.getCode(), msg, null, null,
                TraceContext.currentTraceId(), System.currentTimeMillis());
    }

    /** 失败（业务码 + 字段级错误）。 */
    public static <T> Result<T> fail(BizCode code, String msg, Map<String, String> errors) {
        return new Result<>(code.getCode(), msg, null, errors,
                TraceContext.currentTraceId(), System.currentTimeMillis());
    }

    /** 是否成功。 */
    public boolean isSuccess() {
        return code == BizCode.SUCCESS.getCode();
    }
}
