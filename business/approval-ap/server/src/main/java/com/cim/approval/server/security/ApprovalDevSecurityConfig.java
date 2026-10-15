package com.cim.approval.server.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 开发/测试态放行链：{@code cim.auth.enabled=false}（或缺省未配置）时生效，无需平台鉴权 bean。
 *
 * <p>必须显式提供一条 SecurityFilterChain，否则 Spring Security 会回退默认链要求认证（401）。
 * 仅对 {@code /api/**} 放行，其余路径仍由平台链处理。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "cim.auth", name = "enabled", havingValue = "false", matchIfMissing = true)
public class ApprovalDevSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain approvalDevSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
