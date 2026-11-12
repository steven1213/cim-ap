package com.cim.approval.server.security;

import com.cim.auth.admission.AppAdmissionChecker;
import com.cim.auth.filter.JwtAuthenticationFilter;
import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.auth.token.JwtVerifier;
import com.cim.auth.token.TokenBlacklistChecker;
import com.cim.auth.token.TokenVersionChecker;
import com.cim.notify.security.BearerTokenQueryParamFilter;
import com.cim.notify.transport.SseController;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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
    private final BearerTokenQueryParamFilter bearerFilter;
    /**
     * 开放 API 过滤器（{@code approval.api.security.enabled=true} 时才存在）。
     *
     * <p>用 {@link ObjectProvider} 而非直接注入：开关关闭时容器里没有该 bean，直接注入会让本配置类
     * 在「只关开放面、开着认证」的部署下启动失败。</p>
     */
    private final ObjectProvider<ApiAuthFilter> apiAuthFilter;

    /**
     * SSE 实时推送端点 {@code /sys/notify/stream} 的安全链（先于 /api 链）。
     *
     * <p>端点不在 /api/** 内，且平台默认链对 /sys/** 为 permitAll；故必须在此显式收紧为
     * authenticated，并复用 {@link BearerTokenQueryParamFilter} 把 EventSource 的
     * ?token= 还原为 Bearer 头（与 /api 链同一套 JWT 过滤器）。dev 态（auth 关闭）下端点由
     * {@code cim.notify.dev-accept-query-identity} 允许 ?userId=&tenantId= 直连。</p>
     */
    @Bean
    @Order(0)
    public SecurityFilterChain notifySecurityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(
                jwtVerifier, tokenVersionChecker, tokenBlacklistChecker, admissionChecker, authorityLoader);
        http
                .securityMatcher(SseController.STREAM)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) ->
                        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized")))
                // ⚠️ 锚点必须是 Spring Security **已注册顺序**的过滤器。写成
                // `JwtAuthenticationFilter.class` 会在启动时抛
                // 「The Filter class ... does not have a registered order」——它只是本平台
                // 自定义过滤器，注册表里没有它的位置。两次 addFilterBefore 指向同一锚点时
                // 按调用顺序插入，故 bearer 先于 jwt（EventSource 的 ?token= 先还原成 Bearer 头）。
                .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

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
                }, UsernamePasswordAuthenticationFilter.class);
        // 开放 API（X-App-Key）校验：必须在链内、且先于 JWT 过滤器——它负责建立服务身份，
        // 放进容器层会被 SecurityContextHolderFilter 覆盖掉（见 ApiAuthFilterRegistration）。
        // 锚点仍用 UsernamePasswordAuthenticationFilter：自定义过滤器类没有注册顺序，会启动失败。
        ApiAuthFilter openApi = apiAuthFilter.getIfAvailable();
        if (openApi != null) {
            http.addFilterBefore(openApi, UsernamePasswordAuthenticationFilter.class);
        }
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
