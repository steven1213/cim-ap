package com.cim.auth.filter;

import com.cim.auth.admission.AppAdmissionChecker;
import com.cim.auth.principal.CimUserPrincipal;
import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.auth.token.InvalidTokenException;
import com.cim.auth.token.JwtVerifier;
import com.cim.auth.token.TokenBlacklistChecker;
import com.cim.auth.token.TokenClaims;
import com.cim.auth.token.TokenVersionChecker;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * JWT 认证过滤器（design.md §2.4 / T3.3）：一次请求的完整链路——
 * 取出 Bearer 令牌 → JWKS 本地验签（RS256 钉死）→ {@code ver} 版本失效判定
 * → {@code jti} 黑名单判定（登出 / 主动吊销）→ {@code apps} claim 准入判定
 * → 加载本 ap 业务权限集 → 注入 {@link SecurityContext}（供 @PreAuthorize / 审计取用）。
 *
 * <p>无令牌放行（由 @PreAuthorize 决定端点是否需认证）；验签失败或版本失效 401；准入不通过 403。
 * 本类不注册为独立 {@code @Bean}，仅在 {@code SecurityFilterChain} 内实例化并插入链，
 * 避免被 Servlet 容器重复注册。</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtVerifier jwtVerifier;
    private final TokenVersionChecker tokenVersionChecker;
    private final TokenBlacklistChecker tokenBlacklistChecker;
    private final AppAdmissionChecker admissionChecker;
    private final LocalAuthorityLoader authorityLoader;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier,
                                  TokenVersionChecker tokenVersionChecker,
                                  TokenBlacklistChecker tokenBlacklistChecker,
                                  AppAdmissionChecker admissionChecker,
                                  LocalAuthorityLoader authorityLoader) {
        this.jwtVerifier = jwtVerifier;
        this.tokenVersionChecker = tokenVersionChecker;
        this.tokenBlacklistChecker = tokenBlacklistChecker;
        this.admissionChecker = admissionChecker;
        this.authorityLoader = authorityLoader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(7).trim();
        try {
            TokenClaims claims = jwtVerifier.verify(token);
            // 版本失效判定：IAM 在改权限/改密/踢人后 bump 版本，旧令牌 ver 不匹配即作废（§8.1(f) / T6.5）
            if (!tokenVersionChecker.isAcceptable(claims)) {
                throw new InvalidTokenException("token revoked: version no longer acceptable");
            }
            // 黑名单判定：登出 / 主动吊销的单条令牌（按 jti）即时失效（§8.1(h)）
            if (!tokenBlacklistChecker.isAcceptable(claims)) {
                throw new InvalidTokenException("token blacklisted");
            }
            if (!admissionChecker.check(claims)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "app admission denied");
                return;
            }
            Set<String> authorities = authorityLoader.loadAuthorities(claims);
            CimUserPrincipal principal = new CimUserPrincipal(claims, authorities);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } catch (InvalidTokenException | JwtException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid token");
        }
        // 注意：不要在此 clearContext()。请求级上下文由 SecurityContextPersistenceFilter
        // 在整条链结束后统一清理；过早清理会移除 AnonymousAuthenticationFilter 写入的
        // 匿名身份，导致 ExceptionTranslationFilter 无法将无令牌请求映射为 401。
    }
}
