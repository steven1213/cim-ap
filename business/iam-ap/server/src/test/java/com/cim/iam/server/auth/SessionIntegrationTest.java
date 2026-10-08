package com.cim.iam.server.auth;

import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.auth.LoginController.LoginRequest;
import com.cim.iam.server.auth.LoginController.LogoutRequest;
import com.cim.iam.server.auth.LoginController.RefreshRequest;
import com.cim.iam.server.auth.LoginService.LoginResult;
import com.cim.iam.server.auth.LocalCredentialService;
import com.cim.iam.server.config.IamAuthProperties;
import com.cim.iam.server.token.RsaKeyService;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话生命周期端到端（M-refresh：登录 → 刷新轮转 → 登出拉黑 → 验证侧拒绝，design.md §8.1(h)）。
 *
 * <p>关键：本测试以真实端口启动 IAM，并把验证侧的 {@code jwks-uri} 与
 * {@code token-blacklist.iam-base-url} 都指向 IAM 自身，从而真正走通
 * 「IAM 拉黑 jti → 验证侧 IamTokenBlacklistChecker 拒绝该令牌」的跨模块契约。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-session;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "server.port=18999"
})
class SessionIntegrationTest {

    /** 固定端口：@DynamicPropertySource 在 WebServer 起来前执行，@LocalServerPort 此时仍为 0，
     * 若用随机端口拼接 jwks-uri 会得到 http://localhost:0 导致 JWKS 拉取失败。故用 DEFINE_PORT + 固定端口。 */
    private static final int PORT = 18999;

    @Autowired
    TestRestTemplate rest;
    @Autowired
    AppRegistrationService appService;
    @Autowired
    LocalCredentialService credentialService;
    @Autowired
    IamAuthProperties authProps;
    @Autowired
    RsaKeyService rsaKeyService;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // 验证侧 JWKS 指向 IAM 自身（同一进程、固定端口）
        r.add("cim.auth.jwks-uri", () -> "http://localhost:" + PORT + "/.well-known/jwks.json");
        // 验证侧黑名单端点也指向 IAM 自身
        r.add("cim.auth.token-blacklist.iam-base-url", () -> "http://localhost:" + PORT);
        // 黑名单缓存置 0：登出请求本身会先以「未吊销」状态缓存当前 jti（校验发生在控制器拉黑之前），
        // 若不禁用缓存，同一次测试中后续「拉黑后拒绝」断言会被陈旧缓存误判为通过。生产环境保留合理 TTL。
        r.add("cim.auth.token-blacklist.cache-minutes", () -> "0");
    }

    @BeforeEach
    void seed() {
        try {
            appService.registerApp("mds-ap", "MDS", 1);
        } catch (Exception ignored) {
        }
        try {
            appService.assignUserToApp("alice", "mds-ap", Set.of("ADMIN"));
        } catch (Exception ignored) {
        }
        try {
            String salt = "salt-alice";
            String clientHash = PasswordDerivation.derive("secret", salt, authProps.getPassword().getRounds());
            credentialService.registerLocalUser("alice", clientHash, salt);
        } catch (Exception ignored) {
        }
    }

    @Test
    void loginRefreshLogoutFlow() {
        String clientHash = PasswordDerivation.derive("secret", "salt-alice", authProps.getPassword().getRounds());

        // 1) 登录 → 访问令牌 + 刷新令牌
        LoginResult lr = login("alice", clientHash, "salt-alice");
        assertThat(lr.token()).isNotBlank();
        assertThat(lr.refreshToken()).isNotBlank();
        assertThat(jtiOf(lr.token())).isNotBlank();

        // 2) 刷新 → 新访问令牌 + 新刷新令牌；旧刷新令牌立即失效
        LoginResult lr2 = refresh(lr.refreshToken());
        assertThat(lr2.token()).isNotEqualTo(lr.token());
        assertThat(lr2.refreshToken()).isNotEqualTo(lr.refreshToken());
        assertThat(refreshStatus(lr.refreshToken())).isEqualTo(HttpStatus.FORBIDDEN.value()); // 旧刷新已作废

        // 3) 登出（携带访问令牌 + 刷新令牌）→ 200
        ResponseEntity<String> logoutResp = rest.postForEntity("/api/v1/logout",
                jsonEntity(new LogoutRequest(lr2.refreshToken()), lr2.token()), String.class);
        assertThat(logoutResp.getStatusCode().value()).isEqualTo(200);

        // 4) 访问令牌 jti 已被 IAM 拉黑 → 内部端点返回 true
        assertThat(blacklistOf(jtiOf(lr2.token()))).isEqualTo("true");

        // 5) 验证侧（IamTokenBlacklistChecker）现在拒绝该令牌 → 401
        assertThat(protectedStatusWithBearer(lr2.token())).isEqualTo(HttpStatus.UNAUTHORIZED.value());

        // 6) 登出后，新刷新的刷新令牌也应已撤销 → 使用应 403
        assertThat(refreshStatus(lr2.refreshToken())).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    private LoginResult login(String username, String credential, String salt) {
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/login",
                jsonEntity(new LoginRequest(username, credential, salt)), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        return readLoginResult(resp.getBody());
    }

    private LoginResult refresh(String refreshToken) {
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/token/refresh",
                jsonEntity(new RefreshRequest(refreshToken)), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        return readLoginResult(resp.getBody());
    }

    private int refreshStatus(String refreshToken) {
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/token/refresh",
                jsonEntity(new RefreshRequest(refreshToken)), String.class);
        return resp.getStatusCode().value();
    }

    private String blacklistOf(String jti) {
        ResponseEntity<String> resp = rest.getForEntity("/api/v1/internal/token-blacklist?jti=" + jti, String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        return resp.getBody();
    }

    private int protectedStatusWithBearer(String token) {
        RequestEntity<Void> req = RequestEntity
                .method(HttpMethod.GET, java.net.URI.create("http://localhost:" + PORT + "/api/v1/internal/token-blacklist?jti=unused"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ResponseEntity<String> resp = rest.exchange(req, String.class);
        return resp.getStatusCode().value();
    }

    private LoginResult readLoginResult(String body) {
        try {
            JsonNode data = objectMapper().readTree(body).get("data");
            return objectMapper().treeToValue(data, LoginResult.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }

    private HttpEntity<String> jsonEntity(Object body) {
        return jsonEntity(body, null);
    }

    private HttpEntity<String> jsonEntity(Object body, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        try {
            return new HttpEntity<>(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String jtiOf(String token) {
        RSAPublicKey pub = (RSAPublicKey) rsaKeyService.getPublicKey();
        Jws<Claims> jws = Jwts.parser().verifyWith(pub).build().parseSignedClaims(token);
        return jws.getPayload().get("jti", String.class);
    }
}
