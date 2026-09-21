package com.cim.spring.support.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户过滤器：请求入口将 {@link TenantResolver} 解析结果写入 {@link TenantContext}，
 * 请求结束清理（见 README §10）。
 *
 * <p>仅在 {@code cim.tenant.enabled=true} 时装配。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {

    private final TenantResolver resolver;

    public TenantFilter(TenantResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        TenantContext.set(resolver.resolve(request));
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
