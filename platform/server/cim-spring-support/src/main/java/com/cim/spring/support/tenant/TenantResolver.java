package com.cim.spring.support.tenant;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 租户解析策略（可插拔，见 README §8 / §10）。
 *
 * <p>默认实现 {@link HeaderTenantResolver} 从请求头读取；可按需替换为域名、JWT claim、
 * 子路径等策略。</p>
 */
@FunctionalInterface
public interface TenantResolver {

    /**
     * 从请求解析租户 ID。
     *
     * @param request 当前请求
     * @return 租户 ID；返回 {@code null} 表示不启用租户过滤
     */
    String resolve(HttpServletRequest request);
}
