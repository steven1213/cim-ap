package com.cim.iam.server.auth;

import com.cim.iam.server.config.IamAuthProperties;
import com.cim.iam.server.config.IamProperties;
import com.cim.iam.server.token.RsaKeyService;
import com.cim.iam.server.token.TokenBlacklistService;
import com.cim.iam.server.token.TokenIssueRequest;
import com.cim.iam.server.token.TokenIssuerService;
import com.cim.iam.server.token.TokenVersionService;
import com.cim.spring.support.web.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

/**
 * 登录编排（M-login + M-refresh 核心）。
 *
 * <p>流程：按 {@code cim.iam.auth.source} 选择认证源 → 认证失败抛 {@code noAdmission}
 * → 登录成功先 {@link TokenVersionService#bump(String) bump} 当前用户令牌版本（使旧令牌即时失效，
 * 单会话语义）→ 签发访问令牌 + 刷新令牌（刷新令牌库内仅存散列）。</p>
 *
 * <p>登出（{@link #logout}）：解析当前访问令牌取 jti + uid → 拉黑该 jti（即时杀当前会话）
 * + bump 用户版本（兜底杀其余存量令牌）+ 撤销刷新令牌。与 design.md §8.1(h) 一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final List<AuthenticationSource> sources;
    private final IamAuthProperties properties;
    private final IamProperties jwtProperties;
    private final TokenVersionService tokenVersionService;
    private final TokenIssuerService tokenIssuerService;
    private final RsaKeyService rsaKeyService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AccountLockService accountLockService;

    public LoginResult login(LoginCredentials creds) {
        // 0. 锁定预检：已锁定直接拒绝（不泄露是否为口令错误；消息不区分用户是否存在）
        if (accountLockService.isLocked(creds.username())) {
            throw BizException.accountLocked(creds.username());
        }

        AuthenticationSource source = sources.stream()
                .filter(s -> s.sourceType().equals(properties.getSource()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未配置认证源: " + properties.getSource()));

        AuthenticationResult result = source.authenticate(creds);
        if (!result.success()) {
            accountLockService.onFailure(creds.username());
            throw BizException.noAdmission("用户名或口令错误");
        }

        // 登录成功：清零失败计数（解锁暴力破解防护）
        accountLockService.onSuccess(creds.username());

        // 登录即让该用户旧令牌失效：先 bump 再签发，签发令牌携带最新 ver
        tokenVersionService.bump(result.userId());

        String access = tokenIssuerService.issue(
                new TokenIssueRequest(result.userId(), result.username(), null));
        String refresh = refreshTokenService.issue(result.userId(), access);

        log.info("[login] 登录成功 user={} source={}", result.username(), source.sourceType());
        return new LoginResult(access, refresh, jwtProperties.getAccessTokenTtlMinutes() * 60L, "Bearer");
    }

    /** 用刷新令牌换取新访问令牌 + 新刷新令牌（轮转）。 */
    public LoginResult refresh(String refreshToken) {
        RefreshTokenService.RefreshedTokens tokens = refreshTokenService.rotate(refreshToken);
        log.info("[login] 刷新令牌成功");
        return new LoginResult(tokens.accessToken(), tokens.refreshToken(),
                jwtProperties.getAccessTokenTtlMinutes() * 60L, "Bearer");
    }

    /**
     * 登出：拉黑当前访问令牌 jti（即时失效）+ bump 用户版本（兜底）+ 撤销刷新令牌。
     *
     * @param accessToken  Bearer 令牌原文（Authorization 头，可为空：仅撤销刷新令牌）
     * @param refreshToken 刷新令牌原文（可为空：仅拉黑访问令牌）
     */
    public void logout(String accessToken, String refreshToken) {
        String uid = null;
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                Jws<Claims> jws = Jwts.parser()
                        .verifyWith((RSAPublicKey) rsaKeyService.getPublicKey())
                        .build()
                        .parseSignedClaims(accessToken);
                String jti = jws.getPayload().get("jti", String.class);
                uid = jws.getPayload().get("uid", String.class);
                if (jti != null) {
                    Instant exp = jws.getPayload().getExpiration() != null
                            ? jws.getPayload().getExpiration().toInstant() : null;
                    tokenBlacklistService.revoke(jti, uid, exp);
                }
            } catch (Exception e) {
                log.warn("[logout] 访问令牌解析失败（仅撤销刷新令牌）：{}", e.getMessage());
            }
        }
        // 撤销刷新令牌（若存在）
        refreshTokenService.revoke(refreshToken);
        // 兜底：bump 用户版本，使该用户所有存量令牌在验证端缓存到期后失效
        if (uid != null) {
            tokenVersionService.bump(uid);
        }
        log.info("[logout] 登出 user={}", uid);
    }

    /** 登录 / 刷新返回：访问令牌 + 刷新令牌 + 有效期（秒）+ 类型。 */
    public record LoginResult(String token, String refreshToken, long expiresInSeconds, String tokenType) {
    }
}
