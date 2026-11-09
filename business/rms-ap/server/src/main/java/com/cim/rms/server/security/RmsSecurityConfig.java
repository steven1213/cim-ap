package com.cim.rms.server.security;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * RMS 业务接口鉴权链（生产加固）。
 *
 * <p><b>为什么需要这条链</b>：平台 {@code cim-auth-starter} 的默认链对 URL 一律 {@code permitAll}
 * （细粒度鉴权交给方法级 {@code @PreAuthorize}）。但 RMS 控制器遵循本项目约定<b>不使用</b>
 * 方法级注解（RMS 测试关认证时 {@code @EnableMethodSecurity} 会误拒 403，且 pom 无
 * spring-security-test），因此「无令牌也能读到全部业务数据」——这是生产不可接受的。</p>
 *
 * <p>本条链以 {@code securityMatcher("/api/**")} + {@code @Order(1)} <b>先于</b>平台链命中，
 * 复用平台同一套 JWT 过滤器（验签→版本失效→黑名单→{@code apps} 准入→加载权限），
 * 仅把 URL 层收紧为 {@code authenticated()}：匿名请求直接 401，带令牌则照常进入控制器。
 * 非 {@code /api/**} 路径（如 actuator）仍由平台链处理，不改动平台与 IAM 行为。</p>
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cim.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RmsSecurityConfig {

    private final JwtVerifier jwtVerifier;
    private final TokenVersionChecker tokenVersionChecker;
    private final TokenBlacklistChecker tokenBlacklistChecker;
    private final AppAdmissionChecker admissionChecker;
    private final LocalAuthorityLoader authorityLoader;
    private final BearerTokenQueryParamFilter bearerFilter;

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
                .addFilterBefore(bearerFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /** RMS 业务 API：必须携带有效令牌（验签 + 准入通过），否则 401。 */
    @Bean
    @Order(1)
    public SecurityFilterChain rmsApiSecurityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(
                jwtVerifier, tokenVersionChecker, tokenBlacklistChecker, admissionChecker, authorityLoader);
        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) ->
                        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
