package com.cim.auth;

import com.cim.auth.it.TestApp;
import com.cim.auth.it.TestTokens;
import com.cim.auth.token.TokenVersionChecker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 令牌版本失效链路集成测试（design.md §8.1(f) / plan.md T6.5 的<b>验证侧</b>）。
 *
 * <p>用自定义 {@link TokenVersionChecker} 模拟「IAM 版本表」：低于当前版本的令牌一律作废。
 * 这是「权限/角色变更、改密、强制下线后旧令牌立即失效」在 <b>platform 侧</b>的全部机制——
 * 版本号的存储与递增在 {@code business/iam-ap}，本 starter 只负责比对并映射为 401。</p>
 *
 * <p>自定义 Bean 能生效，正因自动装配的默认实现声明了 {@code @ConditionalOnMissingBean}
 * （与 {@code LocalAuthorityLoader} 同一套让位机制）。</p>
 */
@SpringBootTest(classes = TestApp.class)
@AutoConfigureMockMvc
@Import({TestTokens.JwksConfig.class, TokenRevocationIntegrationTest.RevocationConfig.class})
class TokenRevocationIntegrationTest {

    /** 模拟 IAM 侧「当前令牌版本」。 */
    static final long CURRENT_VERSION = 3L;

    @TestConfiguration
    static class RevocationConfig {
        @Bean
        TokenVersionChecker tokenVersionChecker() {
            // 版本表语义：ver 缺失或低于当前版本 → 已失效
            return claims -> claims.version() != null && claims.version() >= CURRENT_VERSION;
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void currentVersionToken_200() throws Exception {
        String token = TestTokens.sign("u1", List.of("mds-ap"), List.of("mds:order:read"), "t9", CURRENT_VERSION);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string("u1"));
    }

    @Test
    void staleVersionToken_401() throws Exception {
        // 管理动作后 IAM 已把版本 bump 到 CURRENT_VERSION，旧令牌（ver=CURRENT_VERSION-1）应立即失效
        String token = TestTokens.sign("u1", List.of("mds-ap"), List.of("mds:order:read"), "t9",
                CURRENT_VERSION - 1);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void versionCheckPrecedesAdmission_401NotForbidden() throws Exception {
        // 令牌同时「版本失效」且「准入不含本 ap」时，应以 401（令牌已废）为准，而非 403（无准入）
        String token = TestTokens.sign("u1", List.of("mes-ap"), List.of("mds:order:read"), "t9",
                CURRENT_VERSION - 1);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
