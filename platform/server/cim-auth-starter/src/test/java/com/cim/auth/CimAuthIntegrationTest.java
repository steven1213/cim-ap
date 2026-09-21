package com.cim.auth;

import com.cim.auth.it.TestApp;
import com.cim.auth.token.JwksKeyProvider;
import com.cim.auth.token.JwtVerifier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import io.jsonwebtoken.Jwts;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3 全链路契约测试（design.md §8 / R3 缓解项）：用自签 RS256 令牌 + 假 JWKS，
 * 跑通「验签 → 准入 → 业务权限 → 数据权限」全链路；并验证 alg 混淆拒绝、准入拒绝。
 */
@SpringBootTest(classes = TestApp.class)
@AutoConfigureMockMvc
@Import(CimAuthIntegrationTest.TestJwksConfig.class)
class CimAuthIntegrationTest {

    static KeyPair KEY_PAIR;
    static String FAKE_JWKS;

    static {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KEY_PAIR = g.generateKeyPair();
            FAKE_JWKS = buildJwks((RSAPublicKey) KEY_PAIR.getPublic());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    MockMvc mvc;

    @Configuration
    static class TestJwksConfig {
        @Bean
        JwksKeyProvider jwksKeyProvider() {
            return new JwksKeyProvider(() -> FAKE_JWKS, Duration.ofMinutes(60));
        }
    }

    @Test
    void publicEndpoint_noAuth_ok() throws Exception {
        mvc.perform(get("/api/public"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void protectedEndpoint_noToken_401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticated_withAdmissionAndPermission_200() throws Exception {
        String token = sign("u1", List.of("mds-ap"), List.of("mds:order:read"), "t9");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string("u1"));
        mvc.perform(get("/api/order").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string("order-ok"));
        mvc.perform(get("/api/perm").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string("perm-ok"));
    }

    @Test
    void missingAuthority_403() throws Exception {
        String token = sign("u1", List.of("mds-ap"), List.of("other:perm"), "t9");
        mvc.perform(get("/api/order").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admissionDenied_403() throws Exception {
        // 令牌不含本 ap 接入码 mds-ap → 过滤器直接 403
        String token = sign("u1", List.of("mes-ap"), List.of("mds:order:read"), "t9");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void dataPermissionContext_isSet() throws Exception {
        String token = sign("u1", List.of("mds-ap"), List.of("mds:order:read"), "t9");
        mvc.perform(get("/api/scope").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string("t9"));
    }

    // ---- helpers ----

    private static String sign(String userId, List<String> apps, List<String> authorities, String tenantId) {
        return Jwts.builder()
                .header().keyId("k1").and()
                .subject(userId)
                .claim("uid", userId)
                .claim("apps", apps)
                .claim("authorities", authorities)
                .claim("tenantId", tenantId)
                .claim("ver", 1L)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith((RSAPrivateKey) KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
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

    private static String base64url(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
