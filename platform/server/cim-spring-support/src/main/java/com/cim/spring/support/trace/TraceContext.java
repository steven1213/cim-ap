package com.cim.spring.support.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路上下文：以 MDC 承载 {@code traceId}，供统一响应、错误日志与可观测联动
 * （见 README §4 / §14 / §24）。
 */
public final class TraceContext {

    /** MDC key。 */
    public static final String TRACE_ID = "traceId";

    /** 请求头：上游透传（如网关、OpenTelemetry）。 */
    public static final String TRACE_HEADER = "X-Trace-Id";

    private TraceContext() {
    }

    /** 读取当前 traceId（不存在返回 {@code null}）。 */
    public static String currentTraceId() {
        return MDC.get(TRACE_ID);
    }

    /** 写入 traceId（空则生成新值）。 */
    public static String put(String traceId) {
        String value = (traceId == null || traceId.isBlank()) ? newTraceId() : traceId;
        MDC.put(TRACE_ID, value);
        return value;
    }

    /** 生成新的 traceId（无连字符 UUID）。 */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 清理（请求结束务必调用，避免线程池串号）。 */
    public static void clear() {
        MDC.remove(TRACE_ID);
    }
}
