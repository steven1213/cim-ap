package com.cim.approval.server.security;

import com.cim.auth.admission.AppAdmissionChecker;
import com.cim.auth.filter.JwtAuthenticationFilter;
import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.auth.token.JwtVerifier;
import com.cim.auth.token.TokenBlacklistChecker;
import com.cim.auth.token.TokenVersionChecker;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 审批平台业务接口鉴权链（生产加固，镜像 rms-ap 约定）。
 *
 * <p>以 {@code securityMatcher("/api/**")} + {@code @Order(1)} 先于平台默认链命中，复用平台同一套
 * JWT 过滤器（验签→版本失效→黑名单→{@code apps} 准入→加载权限），仅把 URL 层收紧为
 * {@code authenticated()}：匿名请求直接 401。非 {@code /api/**} 路径仍由平台链处理。</p>
 *
 * <p>仅在 {@code cim.auth.enabled=true} 时生效（此时代理平台鉴权所需的 JwtVerifier 等 bean 已装配）。
 * 开发/测试态（enabled=false）由 {@link ApprovalDevSecurityConfig} 提供放行链。</p>
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cim.auth", name = "enabled", havingValue = "true")
public class ApprovalSecurityConfig {

    private final JwtVerifier jwtVerifier;
    private final TokenVersionChecker tokenVersionChecker;
    private final TokenBlacklistChecker tokenBlacklistChecker;
    private final AppAdmissionChecker admissionChecker;
    private final LocalAuthorityLoader authorityLoader;

    @Bean
    @Order(1)
    public SecurityFilterChain approvalApiSecurityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(
                jwtVerifier, tokenVersionChecker, tokenBlacklistChecker, admissionChecker, authorityLoader);
        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) ->
                        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized")))
                // EventSource(SSE) 与 <a download> 无法携带 Authorization 头：允许 ?token=<JWT> 注入
                .addFilterBefore((request, response, chain) -> {
                    if (request instanceof jakarta.servlet.http.HttpServletRequest req) {
                        String token = req.getParameter("token");
                        String existing = req.getHeader(HttpHeaders.AUTHORIZATION);
                        if (token != null && !token.isBlank()
                                && (existing == null || !existing.startsWith("Bearer "))) {
                            String auth = "Bearer " + token;
                            request = new jakarta.servlet.http.HttpServletRequestWrapper(req) {
                                @Override public String getHeader(String name) {
                                    return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) ? auth : super.getHeader(name);
                                }
                                @Override public java.util.Enumeration<String> getHeaders(String name) {
                                    if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                                        return java.util.Collections.enumeration(java.util.List.of(auth));
                                    }
                                    return super.getHeaders(name);
                                }
                            };
                        }
                    }
                    chain.doFilter(request, response);
                }, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
