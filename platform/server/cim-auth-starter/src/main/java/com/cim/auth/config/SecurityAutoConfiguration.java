package com.cim.auth.config;

import com.cim.auth.admission.AppAdmissionChecker;
import com.cim.auth.admission.AdmissionProperties;
import com.cim.auth.datapermission.DataPermissionAspect;
import com.cim.auth.filter.JwtAuthenticationFilter;
import com.cim.auth.rbac.ClaimLocalAuthorityLoader;
import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.auth.support.SecurityContextPortAdapter;
import com.cim.auth.token.JwksKeyProvider;
import com.cim.auth.token.JwtVerifier;
import com.cim.core.port.CurrentUserPort;
import com.cim.spring.support.config.SpringSupportAutoConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Duration;

/**
 * cim-auth-starter 自动装配（design.md §2.4 / §5）。
 *
 * <p>职责边界（ADR-7）：只做「验签 + 准入 + 业务鉴权 + 数据权限」；
 * 登录/口令/签发/吊销/JWKS 发布归 {@code business/iam-ap}。</p>
 *
 * <p>通过 {@code @AutoConfigureBefore(SpringSupportAutoConfiguration.class)} 抢占
 * {@code CurrentUserPort} 的注册，使本 starter 的 {@link SecurityContextPortAdapter}
 * 覆盖 cim-spring-support 的匿名实现。</p>
 */
@AutoConfiguration
@AutoConfigureBefore(SpringSupportAutoConfiguration.class)
@EnableConfigurationProperties(CimAuthProperties.class)
@ConditionalOnProperty(prefix = "cim.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(SecurityFilterChain.class)
@Import(MethodSecurityConfig.class)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwksKeyProvider jwksKeyProvider(CimAuthProperties props) {
        return new JwksKeyProvider(props.getJwksUri(), Duration.ofMinutes(props.getJwksCacheMinutes()));
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtVerifier jwtVerifier(JwksKeyProvider keyProvider) {
        return new JwtVerifier(keyProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public AppAdmissionChecker appAdmissionChecker(CimAuthProperties props) {
        AdmissionProperties admission = props.getAdmission();
        return new AppAdmissionChecker(admission);
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalAuthorityLoader localAuthorityLoader() {
        return new ClaimLocalAuthorityLoader();
    }

    @Bean
    @ConditionalOnMissingBean(CurrentUserPort.class)
    public CurrentUserPort currentUserPort() {
        return new SecurityContextPortAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    public DataPermissionAspect dataPermissionAspect() {
        return new DataPermissionAspect();
    }

    /**
     * 无状态安全过滤器链：关闭 CSRF、无会话；本 starter 的 JWT 过滤器在
     * {@link UsernamePasswordAuthenticationFilter} 之前插入。细粒度权限由方法级
     * {@code @PreAuthorize} 控制，故此处放行所有请求（permitAll）。
     *
     * <p>异常映射（资源服务器语义）：未认证（无令牌 / 匿名）→ 401；已认证但越权 → 403。
     * 由于 Spring Security 6 的方法安全拒绝统一走 {@code AccessDeniedHandler}，
     * 这里按其是否为匿名/未认证显式区分，确保无令牌场景得到 401 而非 403。</p>
     */
    @Bean
    public SecurityFilterChain cimSecurityFilterChain(HttpSecurity http,
                                                    JwtVerifier jwtVerifier,
                                                    AppAdmissionChecker admissionChecker,
                                                    LocalAuthorityLoader authorityLoader) throws Exception {
        JwtAuthenticationFilter jwtFilter =
                new JwtAuthenticationFilter(jwtVerifier, admissionChecker, authorityLoader);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            Authentication authentication =
                                    SecurityContextHolder.getContext().getAuthentication();
                            boolean anonymous = authentication == null
                                    || authentication instanceof AnonymousAuthenticationToken;
                            if (anonymous) {
                                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
                            } else {
                                response.sendError(HttpStatus.FORBIDDEN.value(), "Forbidden");
                            }
                        }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
