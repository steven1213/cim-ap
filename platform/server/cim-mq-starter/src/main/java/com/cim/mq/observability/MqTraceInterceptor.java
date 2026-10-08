package com.cim.mq.observability;

import com.cim.mq.core.IntegrationEvent;
import org.slf4j.MDC;

/**
 * 跨 MQ 的链路透传：把当前 MDC（traceId / tenantId / userId）写入事件头，
 * 消费端再还原到 MDC，保证链路在消息边界不断链（对齐 README §14 OTel 思路）。
 */
public final class MqTraceInterceptor {

    public static final String TRACE_ID = "traceId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";

    private MqTraceInterceptor() {
    }

    /** 发送前：把 MDC 上下文注入事件头。 */
    public static void propagateTo(IntegrationEvent event) {
        putIfPresent(event, TRACE_ID);
        putIfPresent(event, TENANT_ID);
        putIfPresent(event, USER_ID);
    }

    /** 接收后：把事件头还原到 MDC。 */
    public static void restoreFrom(IntegrationEvent event) {
        if (event.header(TRACE_ID) != null) {
            MDC.put(TRACE_ID, event.header(TRACE_ID));
        }
        if (event.header(TENANT_ID) != null) {
            MDC.put(TENANT_ID, event.header(TENANT_ID));
        }
        if (event.header(USER_ID) != null) {
            MDC.put(USER_ID, event.header(USER_ID));
        }
    }

    private static void putIfPresent(IntegrationEvent event, String key) {
        String v = MDC.get(key);
        if (v != null) {
            event.setHeader(key, v);
        }
    }
}
