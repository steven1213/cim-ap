package com.cim.iam.server.config;

import com.cim.auth.token.JwksKeyProvider;
import com.cim.auth.token.TokenVersionChecker;
import com.cim.iam.server.token.LocalTokenVersionChecker;
import com.cim.iam.server.token.RsaKeyService;
import com.cim.iam.server.token.TokenVersionService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * IAM 自有 Web 安全补充配置（消费者侧主链由 cim-auth-starter 的 {@code SecurityAutoConfiguration} 提供）。
 *
 * <p>本配置只做两件事：</p>
 * <ul>
 *   <li><b>本地验签密钥</b>：用 {@link IamJwksKeyProvider} 顶替 starter 默认的 HTTP JWKS 提供器，
 *       使 IAM 验证自身签发的令牌走 in-JVM 公钥（见 {@link IamJwksKeyProvider} 说明）。</li>
 *   <li><b>跨域</b>：独立 {@link CorsFilter}（最高优先级，先于安全过滤器链）放行前端开发源，
 *       使 business/iam-ap/web（端口 5171）可调用本服务。</li>
 * </ul>
 *
 * <p>管理端点（{@code /api/v1/apps/**}）的鉴权由 cim-auth-starter 的 JWT 过滤器 + 方法级
 * {@code @PreAuthorize("hasAuthority('iam-ap:ADMIN')")} 负责，不在此处重复配置。</p>
 */
@Configuration
public class IamSecurityConfig {

    @Bean
    public JwksKeyProvider iamJwksKeyProvider(RsaKeyService rsaKeyService) {
        return new IamJwksKeyProvider(rsaKeyService);
    }

    /**
     * 令牌版本失效判定（验证端，design.md §8.1(f) / §8 边界表）。
     *
     * <p>顶替 cim-auth-starter 的默认 {@code acceptAll()}（@ConditionalOnMissingBean 兜底），使 IAM
     * 验证自身签发的 admin 令牌时按 in-JVM 版本表严格比对 {@code ver} claim；改密 / 踢人后版本 bump，
     * 旧令牌即被过滤器判为失效（→ 401）。与 {@link IamJwksKeyProvider} 同理：验证侧密钥与版本存储
     * 都走本地，避免每请求回查 IAM。</p>
     */
    @Bean
    public TokenVersionChecker iamLocalTokenVersionChecker(TokenVersionService tokenVersionService) {
        return new LocalTokenVersionChecker(tokenVersionService);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(IamWebProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.getAllowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    /** 独立 CORS 过滤器：置于安全链之前，确保预检(OPTIONS)与跨域头优先处理。 */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter(CorsConfigurationSource corsConfigurationSource) {
        return new CorsFilter(corsConfigurationSource);
    }
}
