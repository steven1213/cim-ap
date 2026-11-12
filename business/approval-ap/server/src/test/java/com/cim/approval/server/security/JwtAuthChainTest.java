package com.cim.approval.server.security;

import com.cim.auth.it.TestTokens;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证开启（{@code cim.auth.enabled=true}）时的 JWT 用户态链路。
 *
 * <p><b>为什么必须有这个测试</b>：这一层此前只靠人工起 IAM + 真登录跑过一次。它的故障形态
 * 全是「不报错但不对」——令牌解析不出来表现为「登录成功但待办全空」，域判定认错权威格式表现为
 * 「所有列表 403」，而单元测试（{@code ApprovalMeControllerTest}）直接往 SecurityContext 塞
 * principal，绕过了验签、准入、域解析整条链。只有「认证开 + 真发 HTTP + 真令牌」才看得见。</p>
 *
 * <p>密钥与 JWKS 来自 {@link TestTokens}（{@code cim-auth-starter} 发布的 test-jar），
 * 与平台自己的集成测试用同一份签发实现——复制一份会让两份密钥生成逻辑漂移，故障表现成
 * 「令牌验不过」而不是「测试写错了」。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "cim.auth.enabled=true",
        // 真实 JWKS 由 @Import 的 TestTokens.JwksConfig 覆盖，这里只需一个非空地址占位
        "cim.auth.jwks-uri=http://127.0.0.1:1/jwks",
        "cim.auth.admission.app-code=approval-ap",
        "spring.datasource.url=jdbc:h2:mem:approval_jwt_auth;DB_CLOSE_DELAY=-1;MODE=MySQL",
})
@Import(TestTokens.JwksConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JwtAuthChainTest {

    private static final String ME = "/api/v1/approval/me";
    private static final String TODO = "/api/v1/approval/tasks/todo";

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("无令牌 → 401（/api/** 链确实收紧为 authenticated）")
    void anonymousIsRejected() {
        assertThat(get(ME, null).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("合法令牌 → /me 200，身份取自 JWT（不是查询参数）")
    void jwtUserIsResolvedFromToken() {
        String token = TestTokens.sign("u-1001", List.of("approval-ap"),
                List.of("approval-ap:ADMIN"), "default");

        ResponseEntity<String> res = get(ME, token);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody())
                .contains("\"userId\":\"u-1001\"")
                .contains("\"tenantId\":\"default\"")
                // 前端据 authEnabled 决定走 SSO 还是 dev 身份切换，错了会表现为「刷新即登出」
                .contains("\"authEnabled\":true");
    }

    @Test
    @DisplayName("合法令牌 → 本域待办 200（域断言与查询条件用同一个域）")
    void jwtUserReadsOwnDomain() {
        String token = TestTokens.sign("u-1001", List.of("approval-ap"),
                List.of("approval-ap:ADMIN"), "default");

        assertThat(get(TODO + "?tenantId=default", token).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("合法令牌 → 越域待办 403（G3 在认证态真的生效，不是只有入口断言）")
    void jwtUserCannotCrossDomain() {
        String token = TestTokens.sign("u-1001", List.of("approval-ap"),
                List.of("approval-ap:ADMIN"), "default");

        // 令牌 apps 只含 approval-ap，tenantId=default → 允许集 {approval-ap, default}
        assertThat(get(TODO + "?tenantId=mes-ap", token).getStatusCode().value()).isEqualTo(403);
        assertThat(get(TODO + "?tenantId=rms-ap", token).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("篡改签名 → 401（验签真的在跑，不是要求存在即通过）")
    void tamperedTokenIsRejected() {
        String token = TestTokens.sign("u-1001", List.of("approval-ap"),
                List.of("approval-ap:ADMIN"), "default");

        // 改签名段的**首**字符，不要改末字符：RS256 签名 256 字节 = 342 个 base64url 字符，
        // 末字符只有 2 位有效，改动可能落在 padding 位上——解码出同样的字节，验签照样通过。
        // （第一版就是这么写的，单独跑时随机命中过，全量跑时失败。）
        String[] parts = token.split("\\.");
        char head = parts[2].charAt(0);
        parts[2] = (head == 'A' ? 'B' : 'A') + parts[2].substring(1);
        String tampered = String.join(".", parts);

        assertThat(get(ME, tampered).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("令牌 apps 不含本 ap → 403（IAM 侧漏登记 bootstrap.apps 的形态）")
    void admissionDeniedWhenAppCodeMissing() {
        // 接入新 ap 要改两处：ap 侧 admission.app-code + IAM 侧 bootstrap.apps。
        // 漏掉后者时登录明明成功，却在这里被挡——失败形态是 403 而不是 401。
        String token = TestTokens.sign("u-1001", List.of("rms-ap"),
                List.of("rms-ap:ADMIN"), "default");

        assertThat(get(ME, token).getStatusCode().value()).isEqualTo(403);
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
