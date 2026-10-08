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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IamTokenVersionChecker} 对接 IAM 版本端点的行为验证（用 JDK 内置 HttpServer 桩）。
 */
class IamTokenVersionCheckerTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicLong currentVersion = new AtomicLong(1);
    private final AtomicLong calls = new AtomicLong(0);

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/internal/token-version", exchange -> {
            calls.incrementAndGet();
            String query = exchange.getRequestURI().getQuery(); // uid=xxx
            String uid = query != null && query.startsWith("uid=") ? query.substring(4) : "";
            String body = String.valueOf(currentVersion.get());
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        currentVersion.set(1);
        calls.set(0);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private TokenClaims claims(long ver) {
        return new TokenClaims("u1", "u1", null, null, null, null, null, ver, Instant.now().plusSeconds(60), Map.of());
    }

    @Test
    @DisplayName("无 jti claim 的旧令牌 → 放行（fail-open）")
    void legacyTokenWithoutJtiAccepted() {
        IamTokenVersionChecker checker =
                new IamTokenVersionChecker(baseUrl, "/api/v1/internal/token-version", Duration.ZERO);
        TokenClaims legacy = new TokenClaims("u1", "u1", null, null, null, null, null,
                null, Instant.now().plusSeconds(60), Map.of());
        assertThat(checker.isAcceptable(legacy)).isTrue();
    }

    @Test
    @DisplayName("版本一致 → 接受；令牌 ver 落后 → 拒绝")
    void versionMatchAndStale() {
        // TTL=0 强制每次实时拉取，便于验证 bump 后即时生效
        IamTokenVersionChecker checker =
                new IamTokenVersionChecker(baseUrl, "/api/v1/internal/token-version", Duration.ZERO);

        assertThat(checker.isAcceptable(claims(1))).isTrue();
        assertThat(checker.isAcceptable(claims(2))).isFalse(); // 旧令牌

        currentVersion.set(2); // IAM bump
        assertThat(checker.isAcceptable(claims(2))).isTrue();
        assertThat(checker.isAcceptable(claims(1))).isFalse(); // 更旧的令牌
    }

    @Test
    @DisplayName("无 ver claim 的旧令牌 → 放行（fail-open）")
    void legacyTokenWithoutVerAccepted() {
        IamTokenVersionChecker checker =
                new IamTokenVersionChecker(baseUrl, "/api/v1/internal/token-version", Duration.ZERO);
        TokenClaims legacy = new TokenClaims("u1", "u1", null, null, null, null, null,
                null, Instant.now().plusSeconds(60), Map.of());
        assertThat(checker.isAcceptable(legacy)).isTrue();
    }

    @Test
    @DisplayName("IAM 不可达 → fail-open 放行（版本端点抖动不影响可用性）")
    void iamUnreachableFailOpen() {
        server.stop(0);
        // 指向一个不存在的端口
        IamTokenVersionChecker checker =
                new IamTokenVersionChecker("http://127.0.0.1:1", "/api/v1/internal/token-version", Duration.ZERO);
        // 即便令牌 ver 与任何值都不匹配，也应放行
        assertThat(checker.isAcceptable(claims(99))).isTrue();
    }

    @Test
    @DisplayName("TTL 缓存生效：未过期不回源")
    void cacheHonored() {
        IamTokenVersionChecker checker =
                new IamTokenVersionChecker(baseUrl, "/api/v1/internal/token-version", Duration.ofMinutes(5));
        assertThat(checker.isAcceptable(claims(1))).isTrue();
        long firstCalls = calls.get();
        // 连续多次请求，TTL 内不回源
        for (int i = 0; i < 5; i++) {
            checker.isAcceptable(claims(1));
        }
        assertThat(calls.get()).isEqualTo(firstCalls); // 仅首次拉取
    }
}
