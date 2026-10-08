package com.cim.auth;

import com.cim.auth.it.TestApp;
import com.cim.auth.it.TestTokens;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3 全链路契约测试（design.md §8 / R3 缓解项）：用自签 RS256 令牌 + 假 JWKS，
 * 跑通「验签 → 版本 → 准入 → 业务权限 → 数据权限」全链路；并验证 alg 混淆拒绝、准入拒绝。
 *
 * <p>本类使用<b>默认</b> {@code TokenVersionChecker}（acceptAll），因此也隐式覆盖了
 * 「未接入版本表时令牌照常放行」这一行为；版本拒绝路径见
 * {@link TokenRevocationIntegrationTest}。</p>
 */
@SpringBootTest(classes = TestApp.class)
@AutoConfigureMockMvc
@Import(TestTokens.JwksConfig.class)
class CimAuthIntegrationTest {

    @Autowired
    MockMvc mvc;

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
        String token = TestTokens.sign("u1", List.of("mds-ap"), List.of("mds:order:read"), "t9");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string("u1"));
        mvc.perform(get("/api/order").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string("order-ok"));
        mvc.perform(get("/api/perm").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string("perm-ok"));
    }

    @Test
    void missingAuthority_403() throws Exception {
        String token = TestTokens.sign("u1", List.of("mds-ap"), List.of("other:perm"), "t9");
        mvc.perform(get("/api/order").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admissionDenied_403() throws Exception {
        // 令牌不含本 ap 接入码 mds-ap → 过滤器直接 403
        String token = TestTokens.sign("u1", List.of("mes-ap"), List.of("mds:order:read"), "t9");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void dataPermissionContext_isSet() throws Exception {
        String token = TestTokens.sign("u1", List.of("mds-ap"), List.of("mds:order:read"), "t9");
        mvc.perform(get("/api/scope").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string("t9"));
    }
}
