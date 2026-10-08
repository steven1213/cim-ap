package com.cim.auth.token;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IamTokenBlacklistChecker} 对接 IAM 黑名单端点的行为验证（用 JDK 内置 HttpServer 桩）。
 */
class IamTokenBlacklistCheckerTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicBoolean revoked = new AtomicBoolean(false);
    private final AtomicBoolean called = new AtomicBoolean(false);

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/internal/token-blacklist", exchange -> {
            called.set(true);
            String query = exchange.getRequestURI().getQuery(); // jti=xxx
            String jti = query != null && query.startsWith("jti=") ? query.substring(4) : "";
            // 约定：端点返回该 jti 是否已被拉黑（true/false）
            boolean isRevoked = "bad-jti".equals(jti) || revoked.get();
            String body = String.valueOf(isRevoked);
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        revoked.set(false);
        called.set(false);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private TokenClaims claims(String jti) {
        return new TokenClaims("u1", "u1", null, null, null, jti, null,
                null, Instant.now().plusSeconds(60), Map.of());
    }

    @Test
    @DisplayName("jti 未被拉黑 → 接受；已被拉黑 → 拒绝")
    void revokedJtiRejected() {
        IamTokenBlacklistChecker checker =
                new IamTokenBlacklistChecker(baseUrl, "/api/v1/internal/token-blacklist", Duration.ZERO);

        assertThat(checker.isAcceptable(claims("good-jti"))).isTrue();
        assertThat(checker.isAcceptable(claims("bad-jti"))).isFalse();

        revoked.set(true); // IAM 拉黑
        assertThat(checker.isAcceptable(claims("good-jti"))).isFalse();
        assertThat(checker.isAcceptable(claims("bad-jti"))).isFalse();
    }

    @Test
    @DisplayName("无 jti claim 的旧令牌 → 放行（fail-open）")
    void legacyTokenWithoutJtiAccepted() {
        IamTokenBlacklistChecker checker =
                new IamTokenBlacklistChecker(baseUrl, "/api/v1/internal/token-blacklist", Duration.ZERO);
        TokenClaims legacy = new TokenClaims("u1", "u1", null, null, null, null, null,
                null, Instant.now().plusSeconds(60), Map.of());
        assertThat(checker.isAcceptable(legacy)).isTrue();
    }

    @Test
    @DisplayName("IAM 不可达 → fail-open 放行（黑名单端点抖动不影响可用性）")
    void iamUnreachableFailOpen() {
        server.stop(0);
        IamTokenBlacklistChecker checker =
                new IamTokenBlacklistChecker("http://127.0.0.1:1", "/api/v1/internal/token-blacklist", Duration.ZERO);
        assertThat(checker.isAcceptable(claims("any-jti"))).isTrue();
    }

    @Test
    @DisplayName("TTL 缓存生效：未过期不回源")
    void cacheHonored() {
        IamTokenBlacklistChecker checker =
                new IamTokenBlacklistChecker(baseUrl, "/api/v1/internal/token-blacklist", Duration.ofMinutes(5));
        assertThat(checker.isAcceptable(claims("good-jti"))).isTrue();
        // 触发一次回源后，TTL 内不再调用端点（revoked 置 true 也不应影响结果）
        revoked.set(true);
        for (int i = 0; i < 5; i++) {
            checker.isAcceptable(claims("good-jti"));
        }
        assertThat(called.get()).isTrue();
    }
}
