package com.cim.approval.server.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 关闭 {@link ApiAuthFilter} 的容器级自动注册。
 *
 * <p>它是 {@code Filter} 类型的 bean，Spring Boot 会把容器里所有 Filter bean 自动注册到 servlet
 * 容器，执行时机<b>早于</b> Spring Security 的过滤链。而 Security 6 的
 * {@code SecurityContextHolderFilter} 在链首会用 {@code SecurityContextRepository} 装载并
 * <b>覆盖</b> SecurityContext——在容器层设置的服务身份会被清掉，表现为「开放 API 签名校验
 * 全部通过、接口仍然 401」。</p>
 *
 * <p>所以它必须作为链内过滤器注册（见 {@code ApprovalSecurityConfig} 与
 * {@code ApprovalDevSecurityConfig}）。若不禁用自动注册，同一请求会被校验两次，
 * 限流配额也会被双倍消耗。</p>
 */
@Configuration
public class ApiAuthFilterRegistration {

    @Bean
    @ConditionalOnBean(ApiAuthFilter.class)
    public FilterRegistrationBean<ApiAuthFilter> apiAuthFilterContainerRegistration(ApiAuthFilter filter) {
        FilterRegistrationBean<ApiAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // 改由 Security 链注册，见类注释
        return registration;
    }
}
