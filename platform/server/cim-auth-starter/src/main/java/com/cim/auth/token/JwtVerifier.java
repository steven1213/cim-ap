package com.cim.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Base64;

/**
 * RS256 钉死的本地验签器（design.md §2.4 / T3.2）。
 *
 * <p>关键安全约束：</p>
 * <ul>
 *   <li>仅接受 {@code alg=RS256}；头部 alg 非 RS256（含 {@code none} / {@code HS256} 等）
 *       在解析头部阶段即拒绝，杜绝算法混淆（alg confusion）攻击。</li>
 *   <li>用 IAM JWKS 中对应 kid 的 RSA 公钥验签，不每请求回查 IAM。</li>
 *   <li>自动校验 exp，过期令牌抛 {@link InvalidTokenException}（映射 401）。</li>
 * </ul>
 */
public class JwtVerifier {

    private final JwksKeyProvider keyProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    public JwtVerifier(JwksKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public TokenClaims verify(String compact) {
        if (compact == null || compact.isBlank()) {
            throw new InvalidTokenException("empty token");
        }
        String[] parts = compact.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("malformed token");
        }

        // 1) 解析头部：钉死 RS256，拒绝算法混淆
        JsonNode header;
        try {
            header = mapper.readTree(
                    new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new InvalidTokenException("bad header", e);
        }
        String alg = header.path("alg").asText(null);
        if (!"RS256".equals(alg)) {
            throw new InvalidTokenException("unsupported alg: " + alg);
        }
        String kid = header.path("kid").asText(null);
        java.security.interfaces.RSAPublicKey key = keyProvider.getPublicKey(kid);

        // 2) 用对应 kid 的 RSA 公钥验签（jjwt 按密钥类型强制 RS256，拒绝 HS/none）
        Jws<Claims> jws;
        try {
            jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(compact);
        } catch (RuntimeException e) {
            throw new InvalidTokenException("signature verification failed", e);
        }
        return toClaims(jws.getPayload());
    }

    private TokenClaims toClaims(Claims c) {
        String userId = c.get(JwtClaimKeys.USER_ID, String.class);
        if (userId == null) {
            userId = c.getSubject();
        }
        String username = c.get(JwtClaimKeys.USERNAME, String.class);
        if (username == null) {
            username = c.getSubject();
        }
        Set<String> apps = asSet(c.get(JwtClaimKeys.APP_CODES));
        Set<String> roles = asSet(c.get(JwtClaimKeys.ROLES));
        String tenantId = c.get(JwtClaimKeys.TENANT_ID, String.class);
        Set<String> authorities = asSet(c.get(JwtClaimKeys.AUTHORITIES));
        Long version = c.get(JwtClaimKeys.VERSION, Long.class);
        Instant expiresAt = c.getExpiration() != null ? c.getExpiration().toInstant() : null;
        Map<String, Object> raw = new LinkedHashMap<>(c);
        return new TokenClaims(userId, username, apps, roles, tenantId, authorities, version, expiresAt, raw);
    }

    @SuppressWarnings("unchecked")
    private Set<String> asSet(Object o) {
        if (o == null) {
            return Set.of();
        }
        if (o instanceof Collection<?> col) {
            Set<String> s = new LinkedHashSet<>();
            for (Object x : col) {
                if (x != null) {
                    s.add(x.toString());
                }
            }
            return s;
        }
        return Set.of(o.toString());
    }
}
