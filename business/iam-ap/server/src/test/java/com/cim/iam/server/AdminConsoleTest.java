package com.cim.iam.server;

import com.cim.iam.server.auth.AccountLockService;
import com.cim.iam.server.auth.LocalCredentialService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理控制台端到端（用户 / 会话 / 锁定 / 审计 / 概览 / 设置，需 iam-ap:ADMIN）。
 *
 * <p>覆盖：管理员可视用户清单并创建账号；禁用后该账号登录被拒；手动解锁后恢复；
 * 审计流水记录登录成功；概览/设置/角色组端点可用；非管理员一律 403。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-admin-console;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.iam.auth.bootstrap.admin-username=admin",
        "cim.iam.auth.bootstrap.admin-password=Admin@123456"
})
class AdminConsoleTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LocalCredentialService credentialService;
    @Autowired
    private AccountLockService accountLockService;
    @Autowired
    private IamAuthProperties authProps;

    @Test
    void adminListsAndCreatesUser() throws Exception {
        String token = adminToken();

        String before = mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(before).contains("admin");

        // 创建新账号（客户端第一层派生 → clientHash）
        String salt = "salt-newbie";
        String clientHash = PasswordDerivation.derive("Newbie@123", salt, rounds());
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "newbie", "credential", clientHash, "clientSalt", salt));
        mvc.perform(post("/api/v1/admin/users").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        String after = mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(after).contains("newbie");
        assertThat(credentialService.findByUsername("newbie")).isPresent();
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        String token = adminToken();
        String salt = "salt-victim";
        String clientHash = PasswordDerivation.derive("Victim@123", salt, rounds());
        credentialService.registerLocalUser("victim", clientHash, salt);

        mvc.perform(put("/api/v1/admin/users/victim/status").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        // 禁用后即便口令正确也被拒（noAdmission → 403）
        String loginBody = objectMapper.writeValueAsString(Map.of(
                "username", "victim",
                "credential", PasswordDerivation.derive("Victim@123", salt, rounds()),
                "clientSalt", salt));
        mvc.perform(post("/api/v1/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void lockoutListedAndUnlockable() throws Exception {
        String token = adminToken();
        // 直接制造锁定（避免 5 次真实登录的耗时）
        for (int i = 0; i < 5; i++) {
            accountLockService.onFailure("lockeduser");
        }
        assertThat(accountLockService.isLocked("lockeduser")).isTrue();

        String list = mvc.perform(get("/api/v1/admin/lockouts").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).contains("lockeduser");

        mvc.perform(delete("/api/v1/admin/lockouts/lockeduser").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        assertThat(accountLockService.isLocked("lockeduser")).isFalse();
    }

    @Test
    void auditRecordsLoginAndOverviewSettingsRolesWork() throws Exception {
        String token = adminToken();

        // 登录成功已落审计
        String audit = mvc.perform(get("/api/v1/admin/audit")
                        .param("type", "LOGIN_SUCCESS").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode auditData = objectMapper.readTree(audit).path("data");
        assertThat(auditData.isArray()).isTrue();
        assertThat(auditData.size()).isGreaterThanOrEqualTo(1);

        // 概览
        String overview = mvc.perform(get("/api/v1/admin/overview").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode ov = objectMapper.readTree(overview).path("data");
        assertThat(ov.path("userCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(ov.path("recentEvents").isArray()).isTrue();

        // 设置（只读生效参数）
        String settings = mvc.perform(get("/api/v1/admin/settings").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(settings).path("data").path("authSource").asText()).isEqualTo("local");

        // 角色组
        mvc.perform(get("/api/v1/admin/roles").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        // 在线会话
        mvc.perform(get("/api/v1/admin/sessions").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminForbiddenOnAdminConsole() throws Exception {
        // 建一个无 iam 权限的账号并登录
        String salt = "salt-plain";
        String clientHash = PasswordDerivation.derive("Plain@123", salt, rounds());
        if (credentialService.findByUsername("plain").isEmpty()) {
            credentialService.registerLocalUser("plain", clientHash, salt);
        }
        String loginBody = objectMapper.writeValueAsString(Map.of(
                "username", "plain",
                "credential", PasswordDerivation.derive("Plain@123", salt, rounds()),
                "clientSalt", salt));
        String resp = mvc.perform(post("/api/v1/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(resp).path("data").path("token").asText();

        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/audit").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private String adminToken() throws Exception {
        String salt = fetchSalt("admin");
        String clientHash = PasswordDerivation.derive("Admin@123456", salt, rounds());
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "admin", "credential", clientHash, "clientSalt", salt));
        String resp = mvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("token").asText();
    }

    private String fetchSalt(String username) throws Exception {
        String resp = mvc.perform(get("/api/v1/login/salt").param("username", username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("clientSalt").asText();
    }

    private int rounds() {
        return authProps.getPassword().getRounds();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
