package com.cim.auth;

import com.cim.auth.token.InvalidTokenException;
import com.cim.auth.token.JwksKeyProvider;
import com.cim.auth.token.JwtVerifier;
import com.cim.auth.token.TokenClaims;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T3.2：RS256 钉死验签；拒绝 alg 混淆（HS256 / none）与过期令牌。 */
class JwtVerifierTest {

    static KeyPair KEY_PAIR;
    static String FAKE_JWKS;

    static {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KEY_PAIR = g.generateKeyPair();
            RSAPublicKey pub = (RSAPublicKey) KEY_PAIR.getPublic();
            String n = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(unsignedBytes(pub.getModulus()));
            String e = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(unsignedBytes(pub.getPublicExponent()));
            FAKE_JWKS = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\",\"alg\":\"RS256\",\"use\":\"sig\","
                    + "\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private final JwksKeyProvider provider = new JwksKeyProvider(() -> FAKE_JWKS, Duration.ofMinutes(60));
    private final JwtVerifier verifier = new JwtVerifier(provider);

    @Test
    void validToken_returnsClaims() {
        String token = sign(Map.of("uid", "u1", "apps", List.of("mds-ap"),
                "authorities", List.of("mds:order:read"), "tenantId", "t9"), 3600);
        TokenClaims c = verifier.verify(token);
        assertEquals("u1", c.userId());
        assertTrue(c.apps().contains("mds-ap"));
        assertEquals("t9", c.tenantId());
    }

    @Test
    void algConfusion_HS256_rejected() {
        // 攻击者用 RSA 公钥字节作为 HMAC 密钥签 HS256
        byte[] keyBytes = KEY_PAIR.getPublic().getEncoded();
        SecretKey hmac = new SecretKeySpec(keyBytes, "HmacSHA256");
        String token = Jwts.builder().subject("x").signWith(hmac, Jwts.SIG.HS256).compact();
        assertThrows(InvalidTokenException.class, () -> verifier.verify(token));
    }

    @Test
    void algNone_rejected() {
        String header = base64url("{\"alg\":\"none\"}");
        String payload = base64url("{\"sub\":\"x\"}");
        String token = header + "." + payload + ".";
        assertThrows(InvalidTokenException.class, () -> verifier.verify(token));
    }

    @Test
    void expired_rejected() {
        String token = sign(Map.of("uid", "u1"), -10);
        assertThrows(InvalidTokenException.class, () -> verifier.verify(token));
    }

    private String sign(Map<String, Object> claims, long expOffsetSeconds) {
        var b = Jwts.builder().header().keyId("k1").and().subject("x");
        claims.forEach(b::claim);
        return b.issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(expOffsetSeconds)))
                .signWith((java.security.interfaces.RSAPrivateKey) KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
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

    private static String base64url(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
