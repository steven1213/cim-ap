package com.cim.auth.config;

import com.cim.auth.rbac.CimPermissionEvaluator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 方法级安全配置（design.md §2.4 / T3.5）。
 *
 * <p>启用 {@code @PreAuthorize}/{@code @PostAuthorize}，并注入本 starter 的
 * {@link CimPermissionEvaluator}，使 {@code hasPermission('module:res:action')} 生效。</p>
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {

    @Bean
    public PermissionEvaluator cimPermissionEvaluator() {
        return new CimPermissionEvaluator();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(PermissionEvaluator evaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(evaluator);
        return handler;
    }
}
