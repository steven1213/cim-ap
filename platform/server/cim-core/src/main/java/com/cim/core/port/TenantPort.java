package com.cim.core.port;

/**
 * 当前租户端口（实现由 {@code cim-spring-support} 的 {@code TenantContext} 提供）。
 *
 * <p>供审计填充与应用层读取；JPA 租户过滤器据此自动追加 {@code tenant_id} 条件，
 * 业务层零感知（见 README §10）。</p>
 */
public interface TenantPort {

    /** 当前租户 ID（无租户上下文返回 {@code null}，表示不启用租户过滤）。 */
    String tenantId();
}
