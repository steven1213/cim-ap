package com.cim.auth.config;

import com.cim.auth.admission.AppAdmissionChecker;
import com.cim.auth.admission.AdmissionProperties;
import com.cim.auth.datapermission.DataPermissionAspect;
import com.cim.auth.filter.JwtAuthenticationFilter;
import com.cim.auth.rbac.ClaimLocalAuthorityLoader;
import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.auth.support.SecurityContextPortAdapter;
import com.cim.auth.token.IamTokenVersionChecker;
import com.cim.auth.token.IamTokenBlacklistChecker;
import com.cim.auth.token.JwksKeyProvider;
import com.cim.auth.token.JwtVerifier;
import com.cim.auth.token.TokenBlacklistChecker;
import com.cim.auth.token.TokenVersionChecker;
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

    /**
     * 令牌版本失效判定（§8.1(g) / T6.5 验证侧）——接入 IAM 版本端点时注册 {@link IamTokenVersionChecker}。
     *
     * <p><b>声明顺序约束</b>：本 Bean 必须位于默认实现之前。原因见下方默认 Bean 注释——
     * 默认 Bean 的 {@code @ConditionalOnProperty(havingValue="", matchIfMissing=true)} 在「属性已配置且
     * 为 truthy 值」时同样成立，会与本 Bean 同时命中；靠「本 Bean 先注册 + 默认 Bean 的
     * {@code @ConditionalOnMissingBean} 兜底」才能保证「有 IAM 实现则用之，否则放行」。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "cim.auth.token-version", name = "iam-base-url")
    @ConditionalOnMissingBean
    public TokenVersionChecker iamTokenVersionChecker(CimAuthProperties props) {
        CimAuthProperties.TokenVersion tv = props.getTokenVersion();
        return new IamTokenVersionChecker(
                tv.getIamBaseUrl(), tv.getEndpoint(), Duration.ofMinutes(tv.getCacheMinutes()));
    }

    /**
     * 令牌版本失效默认实现：未接入 IAM 版本端点时放行（不校验版本）。
     *
     * <p><b>为何只用 {@code @ConditionalOnMissingBean}、不再叠加 {@code @ConditionalOnProperty}</b>：
     * 若保留 {@code @ConditionalOnProperty(prefix, name, havingValue="", matchIfMissing=true)}，在
     * {@code cim.auth.token-version.iam-base-url} 被配置为真实 URL（truthy）时该条件也成立，会与上方
     * IAM 实现同时命中；最终因两者同类型 + {@code @ConditionalOnMissingBean} 竞争，默认实现抢先注册，
     * 导致「配了 IAM 端点却仍走 acceptAll」的隐蔽 bug。单靠 {@code @ConditionalOnMissingBean} 即足以
     * 表达「有 IAM 实现则用 IAM，无则用默认」的兜底语义。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenVersionChecker tokenVersionChecker() {
        return TokenVersionChecker.acceptAll();
    }

    /**
     * 令牌黑名单判定（§8.1(h) 验证端）——接入 IAM 黑名单端点时注册 {@link IamTokenBlacklistChecker}。
     * 声明顺序约束同 {@link #iamTokenVersionChecker}：必须先于默认实现。
     */
    @Bean
    @ConditionalOnProperty(prefix = "cim.auth.token-blacklist", name = "iam-base-url")
    @ConditionalOnMissingBean
    public TokenBlacklistChecker iamTokenBlacklistChecker(CimAuthProperties props) {
        CimAuthProperties.TokenBlacklist bl = props.getTokenBlacklist();
        return new IamTokenBlacklistChecker(
                bl.getIamBaseUrl(), bl.getEndpoint(), Duration.ofMinutes(bl.getCacheMinutes()));
    }

    /**
     * 令牌黑名单默认实现：未接入 IAM 黑名单端点时一律放行（放弃「按 jti 主动吊销」这一层）。
     * 原因同 {@link #tokenVersionChecker}：只用 {@code @ConditionalOnMissingBean} 兜底，
     * 避免「属性已配置（truthy）时默认实现反而抢注」的陷阱。
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenBlacklistChecker tokenBlacklistChecker() {
        return TokenBlacklistChecker.acceptAll();
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
                                                    TokenVersionChecker tokenVersionChecker,
                                                    TokenBlacklistChecker tokenBlacklistChecker,
                                                    AppAdmissionChecker admissionChecker,
                                                    LocalAuthorityLoader authorityLoader) throws Exception {
        JwtAuthenticationFilter jwtFilter =
                new JwtAuthenticationFilter(jwtVerifier, tokenVersionChecker, tokenBlacklistChecker,
                        admissionChecker, authorityLoader);
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
