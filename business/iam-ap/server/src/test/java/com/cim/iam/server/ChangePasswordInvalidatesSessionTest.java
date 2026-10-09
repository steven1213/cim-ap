package com.cim.iam.server;

import com.cim.iam.server.auth.PasswordDerivation;
import com.cim.iam.server.config.IamAuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端验证「改密后 bump 令牌版本强制旧会话失效」（选项 ①）。
 *
 * <p>流程：bootstrap 管理员登录拿到令牌 T1 → 调 /me/password 改密（服务端 bump 版本）→
 * 用 T1 访问受保护端点应 401（旧 ver 失配）→ 以新口令重新登录拿到 T2 → 访问受保护端点应 200。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-cpw-session;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.iam.auth.bootstrap.admin-username=admin",
        "cim.iam.auth.bootstrap.admin-password=Admin@123456"
})
class ChangePasswordInvalidatesSessionTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IamAuthProperties authProps;

    private static final int ROUNDS = 100_000;

    @Test
    void changePassword_invalidatesOldSession_andReLoginRestoresAccess() throws Exception {
        // 1) 管理员登录（引导账号）：取盐 → 第一层派生 → 登录
        String salt = fetchSalt("admin");
        String t1 = login("admin", "Admin@123456", salt);
        assertThat(t1).isNotBlank();

        // 2) 改密：旧口令（以当前 salt 派生）+ 新口令（客户端随机盐派生）
        String newClientSalt = "new-client-salt-after-change-0123456789";
        String oldCredential = PasswordDerivation.derive("Admin@123456", salt, ROUNDS);
        String newCredential = PasswordDerivation.derive("NewPass@999", newClientSalt, ROUNDS);
        mvc.perform(post("/api/v1/me/password")
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oldCredential", oldCredential,
                                "newCredential", newCredential,
                                "newClientSalt", newClientSalt))))
                .andExpect(status().isOk());

        // 3) 旧令牌 T1 已失效：访问受保护端点应 401
        mvc.perform(get("/api/v1/apps").header("Authorization", "Bearer " + t1))
                .andExpect(status().isUnauthorized());

        // 4) 以新口令重新登录（salt 已更新为 newClientSalt）
        String salt2 = fetchSalt("admin");
        String t2 = login("admin", "NewPass@999", salt2);
        assertThat(t2).isNotBlank();

        // 5) 新令牌 T2 访问受保护端点应 200（旧会话被强制失效的意图达成，新会话正常）
        mvc.perform(get("/api/v1/apps").header("Authorization", "Bearer " + t2))
                .andExpect(status().isOk());
    }

    private String login(String username, String rawPassword, String clientSalt) throws Exception {
        String clientHash = PasswordDerivation.derive(rawPassword, clientSalt, authProps.getPassword().getRounds());
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username, "credential", clientHash, "clientSalt", clientSalt));
        String resp = mvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readDataField(resp, "token");
    }

    private String fetchSalt(String username) throws Exception {
        String resp = mvc.perform(get("/api/v1/login/salt").param("username", username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readDataField(resp, "clientSalt");
    }

    private String readDataField(String response, String field) throws Exception {
        JsonNode node = objectMapper.readTree(response).path("data").path(field);
        return node.isMissingNode() ? null : node.asText();
    }
}
