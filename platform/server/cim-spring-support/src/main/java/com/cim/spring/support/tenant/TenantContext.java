package com.cim.spring.support.tenant;

import com.cim.core.port.TenantPort;

/**
 * 租户上下文（ThreadLocal）。
 *
 * <p>由 {@link TenantResolver} 在请求入口写入，供审计填充与 JPA 租户过滤器读取；
 * 业务层零感知（见 README §10）。同时实现 {@link TenantPort} 供 {@code cim-core} 使用。</p>
 *
 * <p>⚠️ 异步 / 线程池场景须显式传递，或在任务装饰器中复制。</p>
 */
public final class TenantContext implements TenantPort {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    /** 获取当前租户（可能为 {@code null}）。 */
    public static String get() {
        return CURRENT.get();
    }

    /** 设置当前租户。 */
    public static void set(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            CURRENT.remove();
        } else {
            CURRENT.set(tenantId);
        }
    }

    /** 清理（请求结束务必调用）。 */
    public static void clear() {
        CURRENT.remove();
    }

    @Override
    public String tenantId() {
        return CURRENT.get();
    }
}
