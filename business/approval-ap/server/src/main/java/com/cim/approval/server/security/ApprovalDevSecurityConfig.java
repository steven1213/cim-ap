package com.cim.approval.server.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 开发/测试态放行链：{@code cim.auth.enabled=false}（或缺省未配置）时生效，无需平台鉴权 bean。
 *
 * <p>必须显式提供一条 SecurityFilterChain，否则 Spring Security 会回退默认链要求认证（401）。
 * 仅对 {@code /api/**} 放行，其余路径仍由平台链处理。</p>
 *
 * <p>放行不等于不受 G9 约束：{@link ApiAuthFilter}（{@code approval.api.security.enabled=true}
 * 且请求带 {@code X-App-Key} 时生效）仍挂在本链内——它负责写入域作用域，跨域判定依赖它。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "cim.auth", name = "enabled", havingValue = "false", matchIfMissing = true)
public class ApprovalDevSecurityConfig {

    private final ObjectProvider<ApiAuthFilter> apiAuthFilter;

    public ApprovalDevSecurityConfig(ObjectProvider<ApiAuthFilter> apiAuthFilter) {
        this.apiAuthFilter = apiAuthFilter;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain approvalDevSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        // 与认证链同一处理：开放 API 过滤器必须在链内，否则域作用域可能在容器层就被丢弃。
        ApiAuthFilter openApi = apiAuthFilter.getIfAvailable();
        if (openApi != null) {
            http.addFilterBefore(openApi, UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }
}
