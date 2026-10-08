package com.cim.auth.it;

import com.cim.auth.token.JwksKeyProvider;

import io.jsonwebtoken.Jwts;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * 认证集成测试共用的「自签 RS256 令牌 + 假 JWKS」工具。
 *
 * <p>把密钥对、JWKS 构造与签发逻辑收在一处，供 {@code CimAuthIntegrationTest}（全链路）
 * 与 {@code TokenRevocationIntegrationTest}（版本失效）复用——两者需用<b>同一把</b>公钥，
 * 否则各自的 {@code JwksKeyProvider} 会与对方的令牌不匹配。</p>
 */
public final class TestTokens {

    /** 测试签名密钥对（进程内固定，唯一）。 */
    public static final KeyPair KEY_PAIR;

    /** 对应的 JWKS JSON（kid=k1）。 */
    public static final String JWKS;

    static {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KEY_PAIR = g.generateKeyPair();
            JWKS = buildJwks((RSAPublicKey) KEY_PAIR.getPublic());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TestTokens() {
    }

    /** 签发一枚 RS256 令牌（{@code ver} 缺省为 1）。 */
    public static String sign(String userId, List<String> apps, List<String> authorities, String tenantId) {
        return sign(userId, apps, authorities, tenantId, 1L);
    }

    /** 签发一枚带指定令牌版本（{@code ver}）的 RS256 令牌。 */
    public static String sign(String userId, List<String> apps, List<String> authorities,
                              String tenantId, long version) {
        return Jwts.builder()
                .header().keyId("k1").and()
                .subject(userId)
                .claim("uid", userId)
                .claim("apps", apps)
                .claim("authorities", authorities)
                .claim("tenantId", tenantId)
                .claim("ver", version)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith((RSAPrivateKey) KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /** 指向本类固定 JWKS 的公钥提供者（不发起网络请求）。 */
    public static JwksKeyProvider jwksKeyProvider() {
        return new JwksKeyProvider(() -> JWKS, Duration.ofMinutes(60));
    }

    /** 用固定 JWKS 覆盖自动装配的 {@code JwksKeyProvider}（后者默认去 HTTP 拉取）。 */
    @TestConfiguration
    public static class JwksConfig {
        @Bean
        public JwksKeyProvider jwksKeyProvider() {
            return TestTokens.jwksKeyProvider();
        }
    }

    private static String buildJwks(RSAPublicKey pub) {
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(unsignedBytes(pub.getModulus()));
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(unsignedBytes(pub.getPublicExponent()));
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\",\"alg\":\"RS256\",\"use\":\"sig\","
                + "\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";
    }

    private static byte[] unsignedBytes(java.math.BigInteger v) {
        byte[] b = v.toByteArray();
        if (b.length > 1 && b[0] == 0) {
            byte[] c = new byte[b.length - 1];
            System.arraycopy(b, 1, c, 0, c.length);
            return c;
        }
        return b;
    }
}
