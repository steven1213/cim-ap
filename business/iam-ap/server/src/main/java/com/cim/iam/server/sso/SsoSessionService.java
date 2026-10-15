package com.cim.iam.server.sso;

import com.cim.iam.server.config.IamProperties;
import com.cim.iam.server.config.SsoProperties;
import com.cim.iam.server.token.RsaKeyService;
import com.cim.iam.server.token.TokenVersionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * SSO 会话（跨 ap 单点登录）的签发 / 校验 / Cookie 管理。
 *
 * <p><b>为何需要</b>：各接入 ap 的 SPA 把令牌存在<b>各自源</b>的 localStorage（5171 与 5174 是不同源），
 * 因此在一处登录不会让另一处自动获得令牌。本服务在 IAM 后端（8081）登录成功时种下
 * 一枚<b>同站共享</b>的 SSO 会话 Cookie；当任一 ap 中继到 8081 的 {@code /login} 且此 Cookie 有效时，
 * 后端直接为该 ap 签发令牌并回跳，实现「一次登录、多 ap 直接进入」。</p>
 *
 * <p><b>会话令牌</b>：一枚 RS256 签名的 JWT（复用 IAM 私钥），claim 复用访问令牌的身份三元组
 * {@code uid/uname/ver}，仅 TTL 取 {@code cim.iam.sso.cookie-ttl-minutes}（远长于访问令牌）。
 * 校验时除签名/签发方/过期外，<b>额外比对 {@code ver} 与当前用户令牌版本</b>——
 * 用户登出或被 bump 后版本变更，SSO 会话立即失效（与 access token 同生命周期语义）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoSessionService {

    private final RsaKeyService rsaKeyService;
    private final IamProperties iamProperties;
    private final SsoProperties ssoProperties;
    private final TokenVersionService tokenVersionService;

    /** 经校验的 SSO 主体（用于回跳时签发令牌）。 */
    public record SsoPrincipal(String userId, String username, long version) {
    }

    /**
     * 由已签发的访问令牌派生 SSO 会话令牌（复用其 uid/uname/ver，TTL 取会话配置）。
     * 用于登录成功时：拿 login 刚签发的 access token 直接转一枚长效 SSO cookie。
     */
    public String createFromAccessToken(String accessToken) {
        Claims c = parseClaims(accessToken);
        String uid = c.get("uid", String.class);
        String uname = c.get("uname", String.class);
        long ver = toLong(c.get("ver"));
        return create(uid, uname, ver);
    }

    /** 显式签发 SSO 会话令牌。 */
    public String create(String userId, String username, long version) {
        long now = System.currentTimeMillis();
        long exp = now + ssoProperties.getCookieTtlMinutes() * 60_000L;
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .header().keyId(rsaKeyService.getKid()).and()
                .issuer(iamProperties.getIssuer())
                .subject(userId)
                .claim("uid", userId)
                .claim("uname", username)
                .claim("ver", version)
                .claim("jti", jti)
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(rsaKeyService.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 校验 SSO 会话令牌。
     * @return 有效则返回主体；签名错误 / 过期 / 签发方不符 / 版本已变 均返回 empty。
     */
    public Optional<SsoPrincipal> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims c = parseClaims(token);
            if (!iamProperties.getIssuer().equals(c.getIssuer())) {
                return Optional.empty();
            }
            String uid = c.get("uid", String.class);
            String uname = c.get("uname", String.class);
            long ver = toLong(c.get("ver"));
            // 版本失效：用户登出 / bump 后，SSO 会话随之作废
            if (tokenVersionService.currentVersion(uid) != ver) {
                return Optional.empty();
            }
            return Optional.of(new SsoPrincipal(uid, uname, ver));
        } catch (Exception e) {
            log.debug("[sso] 会话校验失败：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 在响应上种下 SSO 会话 Cookie（HttpOnly + SameSite=Lax + 可选 secure/domain）。 */
    public void attach(HttpServletResponse response, String ssoToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(ssoToken,
                (int) Duration.ofMinutes(ssoProperties.getCookieTtlMinutes()).getSeconds()).toString());
    }

    /** 清除 SSO 会话 Cookie（登出时调用）。 */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
    }

    private ResponseCookie buildCookie(String value, int maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(ssoProperties.getCookieName(), value)
                .path("/")
                .maxAge(maxAgeSeconds)
                .httpOnly(true)
                .secure(ssoProperties.isCookieSecure())
                .sameSite("Lax");
        if (ssoProperties.getCookieDomain() != null && !ssoProperties.getCookieDomain().isBlank()) {
            b.domain(ssoProperties.getCookieDomain());
        }
        return b.build();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith((RSAPublicKey) rsaKeyService.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }
}
