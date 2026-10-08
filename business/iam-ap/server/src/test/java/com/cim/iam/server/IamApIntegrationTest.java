package com.cim.iam.server;

import com.cim.iam.server.app.AppRegistrationController.AssignRequest;
import com.cim.iam.server.app.AppRegistrationController.RegisterAppRequest;
import com.cim.iam.server.token.RsaKeyService;
import com.cim.iam.server.token.TokenIssueController.IssuedToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端验证（design.md §8.1(g) + §8 边界表）：
 * 注册 ap → 分配用户 → 签发令牌（含 apps + ver）→ 版本端点下发 → bump 后版本递增且旧令牌失效。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:iam-it;DB_CLOSE_DELAY=-1;MODE=MySQL")
class IamApIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    RsaKeyService rsaKeyService;

    @Test
    void fullFlowIssueTokenWithAppsAndVersion() throws Exception {
        // 1) 注册 ap
        mvc.perform(post("/api/v1/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterAppRequest("mds-ap", "MDS", 1))))
                .andExpect(status().isOk());

        // 2) 分配用户到 ap（粗角色 ADMIN）
        mvc.perform(post("/api/v1/apps/mds-ap/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AssignRequest("u1", Set.of("ADMIN")))))
                .andExpect(status().isOk());

        // 3) 签发令牌
        String token1 = issue("u1", "u1", null);
        assertClaims(token1, "u1", Set.of("mds-ap"), 1L);

        // 4) 版本端点下发当前版本（纯文本）
        mvc.perform(get("/api/v1/internal/token-version").param("uid", "u1"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        // 5) bump → 版本递增
        mvc.perform(post("/api/v1/internal/token-version/bump").param("uid", "u1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/internal/token-version").param("uid", "u1"))
                .andExpect(status().isOk())
                .andExpect(content().string("2"));

        // 6) 重新签发，ver 同步为 2
        String token2 = issue("u1", "u1", null);
        assertClaims(token2, "u1", Set.of("mds-ap"), 2L);

        // 7) 撤销分配后，apps claim 不再包含 mds-ap
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/apps/mds-ap/users/u1"))
                .andExpect(status().isOk());
        String token3 = issue("u1", "u1", null);
        assertClaims(token3, "u1", Set.of(), 3L); // ver 因撤销 bump 到 3，apps 为空
    }

    private String issue(String userId, String username, String tenantId) throws Exception {
        String body = mvc.perform(post("/api/v1/token/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId, "username", username))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, IssuedToken.class).token();
    }

    @SuppressWarnings("unchecked")
    private void assertClaims(String token, String uid, Set<String> apps, Long ver) {
        RSAPublicKey pub = (RSAPublicKey) rsaKeyService.getPublicKey();
        Jws<Claims> jws = Jwts.parser().verifyWith(pub).build().parseSignedClaims(token);
        Claims c = jws.getPayload();
        assertThat(c.get("uid")).isEqualTo(uid);
        assertThat(new LinkedHashSet<>((List<?>) c.get("apps"))).isEqualTo(apps);
        // JJWT 解析小整数 JSON 数字时返回 Integer，统一按数值比较，避免 1L vs 1 类型不符
        assertThat(((Number) c.get("ver")).longValue()).isEqualTo(ver);
        // 算法固定 RS256（与验证端钉死一致）
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("RS256");
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
