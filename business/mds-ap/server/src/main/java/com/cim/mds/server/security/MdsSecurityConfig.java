package com.cim.mds.server.security;

import com.cim.auth.admission.AppAdmissionChecker;
import com.cim.auth.filter.JwtAuthenticationFilter;
import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.auth.token.JwtVerifier;
import com.cim.auth.token.TokenBlacklistChecker;
import com.cim.auth.token.TokenVersionChecker;
import com.cim.notify.security.BearerTokenQueryParamFilter;
import com.cim.notify.transport.SseController;

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
 * MDS 业务接口鉴权链（与 RMS 同构）。
 *
 * <p><b>为什么需要自己一条链</b>：平台 {@code cim-auth-starter} 的默认链对 URL 一律
 * {@code permitAll}（细粒度鉴权交给方法级 {@code @PreAuthorize}）。而主数据接口若匿名可达，
 * 等于「无令牌也能读到全部主数据」——生产不可接受。本链以 {@code securityMatcher("/api/**")}
 * + {@code @Order(1)} 先于平台链命中，复用平台同一套 JWT 过滤器
 * （验签→版本失效→黑名单→{@code apps} 准入→加载权限），仅把 URL 层收紧为
 * {@code authenticated()}。非 {@code /api/**} 路径（如 actuator）仍由平台链处理。</p>
 *
 * <p><b>SSE 端点单独一条</b>：{@code /sys/notify/stream} 不在 {@code /api/**} 内，平台默认链对它
 * 是 {@code permitAll}，故 {@code @Order(0)} 单独收紧，并用
 * {@link BearerTokenQueryParamFilter} 把 EventSource 的 {@code ?token=} 还原成 Bearer 头。</p>
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cim.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MdsSecurityConfig {

    private final JwtVerifier jwtVerifier;
    private final TokenVersionChecker tokenVersionChecker;
    private final TokenBlacklistChecker tokenBlacklistChecker;
    private final AppAdmissionChecker admissionChecker;
    private final LocalAuthorityLoader authorityLoader;
    private final BearerTokenQueryParamFilter bearerFilter;

    /** SSE 实时推送端点：先把 {@code ?token=} 还原成 Bearer，再走同一套 JWT 过滤器。 */
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
                // 按调用顺序插入，故 bearer 先于 jwt。
                .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** MDS 业务 API：必须携带有效令牌（验签 + 准入通过），否则 401。 */
    @Bean
    @Order(1)
    public SecurityFilterChain mdsApiSecurityFilterChain(HttpSecurity http) throws Exception {
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
