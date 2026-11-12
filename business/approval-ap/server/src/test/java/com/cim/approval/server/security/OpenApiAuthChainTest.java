package com.cim.approval.server.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证开启（{@code cim.auth.enabled=true}）时的开放 API 链路。
 *
 * <p><b>为什么必须有这个测试</b>：{@code ApiAuthFilter} 曾经只写 {@code RequestScopeHolder}、
 * 不写 {@code SecurityContext}，而 {@code /api/**} 链是 {@code anyRequest().authenticated()}。
 * 结果认证一开，所有带**合法签名**的服务间请求都拿到 401——签名、重放、限流全过，就是进不去。
 * 这个故障在 dev 放行态与单元测试里都看不见（前者不认证、后者不装配 Security 链），
 * 只有「认证开 + 真发 HTTP」才暴露。</p>
 *
 * <p>本测试刻意不签发 JWT：开放 API 的凭据是 appKey，不该依赖令牌。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // 认证开启：让 ApprovalSecurityConfig（anyRequest().authenticated()）生效
        "cim.auth.enabled=true",
        // JWKS 懒加载，指向一个不可达地址即可（本测试不验 JWT）
        "cim.auth.jwks-uri=http://127.0.0.1:1/jwks",
        "cim.auth.admission.app-code=approval-ap",
        // 开放 API 开启并配一把测试密钥
        "approval.api.security.enabled=true",
        "approval.api.security.app-keys.rms-ap=it-secret-rms",
        "approval.api.security.rate-limit.rms-ap=100",
        "spring.datasource.url=jdbc:h2:mem:approval_openapi_auth;DB_CLOSE_DELAY=-1;MODE=MySQL",
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenApiAuthChainTest {

    private static final String APP_KEY = "rms-ap";
    private static final String SECRET = "it-secret-rms";
    private static final String PATH = "/api/v1/approval/definitions";

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("认证开启：无凭据访问 /api/** → 401（认证链确实生效）")
    void anonymousIsRejected() {
        ResponseEntity<String> res = rest.getForEntity(PATH, String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("认证开启：带合法 X-App-Key 签名 → 200（服务身份能过 authenticated）")
    void openApiPassesAuthenticatedChain() {
        ResponseEntity<String> res = call(PATH, "GET", "");
        assertThat(res.getStatusCode().value())
                .as("开放 API 必须在认证开启时可用；401 说明 ApiAuthFilter 没建立服务身份")
                .isEqualTo(200);
        assertThat(res.getBody()).contains("\"code\":0");
    }

    @Test
    @DisplayName("认证开启：签名正确但请求参数跨域 → 403（G3 不受身份改动影响）")
    void openApiStillEnforcesDomain() {
        ResponseEntity<String> res = call(PATH + "?appCode=mes-ap", "GET", "");
        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("认证开启：签名错误 → 403（校验链仍在过滤器里）")
    void badSignatureIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-App-Key", APP_KEY);
        headers.set("X-Timestamp", String.valueOf(System.currentTimeMillis()));
        headers.set("X-Signature", "deadbeef".repeat(8));
        ResponseEntity<String> res = rest.exchange(PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }

    private ResponseEntity<String> call(String path, String method, String body) {
        long ts = System.currentTimeMillis();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-App-Key", APP_KEY);
        headers.set("X-Timestamp", String.valueOf(ts));
        // 契约：HMAC-SHA256(ts|METHOD|requestURI|body)
        headers.set("X-Signature", hmac(ts + "|" + method + "|" + uriOf(path) + "|" + body));
        return rest.exchange(path, HttpMethod.valueOf(method), new HttpEntity<>(headers), String.class);
    }

    /** 签名用的是 {@code request.getRequestURI()}，不含 query。 */
    private static String uriOf(String path) {
        int q = path.indexOf('?');
        return q < 0 ? path : path.substring(0, q);
    }

    private static String hmac(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
