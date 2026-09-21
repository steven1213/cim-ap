package com.cim.spring.support.tenant;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认租户解析：从请求头读取租户 ID。
 *
 * <p>请求头名由 {@link TenantProperties#getHeaderName()} 配置。</p>
 */
public class HeaderTenantResolver implements TenantResolver {

    private final TenantProperties properties;

    public HeaderTenantResolver(TenantProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String value = request.getHeader(properties.getHeaderName());
        if (value == null || value.isBlank()) {
            return properties.getDefaultTenant();
        }
        return value.trim();
    }
}
