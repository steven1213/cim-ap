package com.cim.iam.server;

import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.auth.LocalCredentialService;
import com.cim.iam.server.auth.PasswordDerivation;
import com.cim.iam.server.config.IamAuthProperties;
import com.cim.iam.server.token.RsaKeyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理面鉴权（IAM 自签发 JWT + iam-ap:ADMIN 角色守护，design.md §8.1）。
 *
 * <p>验证：无令牌访问 {@code /api/v1/apps} → 401；首管理员（环境变量引导）登录后持 iam-ap:ADMIN
 * 令牌 → 200；非管理员令牌 → 403。IAM 验证自身令牌走 in-JVM 公钥（IamJwksKeyProvider），
 * 故 MockMvc 下无需真实监听端口。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-admin-sec;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.iam.auth.bootstrap.admin-username=admin",
        "cim.iam.auth.bootstrap.admin-password=Admin@123456"
})
class AdminSecurityTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LocalCredentialService credentialService;
    @Autowired
    private AppRegistrationService appService;
    @Autowired
    private IamAuthProperties authProps;
    @Autowired
    private RsaKeyService rsaKeyService;

    @BeforeEach
    void seedNonAdmin() throws Exception {
        // 准备一个「非 iam 管理员」用户：本地凭证 + 仅分配 mds-ap（无 iam-ap）
        if (appService.listApps().stream().noneMatch(a -> "mds-ap".equals(a.getAppCode()))) {
            appService.registerApp("mds-ap", "MDS", 1);
        }
        String opSalt = "salt-operator";
        String opClientHash = PasswordDerivation.derive("opsecret", opSalt, authProps.getPassword().getRounds());
        if (credentialService.findByUsername("operator").isEmpty()) {
            credentialService.registerLocalUser("operator", opClientHash, opSalt);
        }
        appService.assignUserToApp("operator", "mds-ap", Set.of("ADMIN"));
    }

    @Test
    void appsEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/apps"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapAdminCanAccessApps() throws Exception {
        String token = login("admin", "Admin@123456", fetchSalt("admin"));
        assertThat(token).isNotBlank();

        // 令牌携带 iam-ap:ADMIN 权威
        assertThat(parseAuthorities(token)).contains("iam-ap:ADMIN");

        mvc.perform(get("/api/v1/apps").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        String token = login("operator", "opsecret", fetchSalt("operator"));

        mvc.perform(get("/api/v1/apps").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCannotDeleteApp() throws Exception {
        String token = login("operator", "opsecret", fetchSalt("operator"));
        mvc.perform(delete("/api/v1/apps/mds-ap/users/operator")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
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

    @SuppressWarnings("unchecked")
    private Set<String> parseAuthorities(String token) {
        RSAPublicKey pub = (RSAPublicKey) rsaKeyService.getPublicKey();
        Jws<Claims> jws = Jwts.parser().verifyWith(pub).build().parseSignedClaims(token);
        List<String> auths = jws.getPayload().get("authorities", List.class);
        return new LinkedHashSet<>(auths);
    }

    private String readDataField(String response, String field) throws Exception {
        JsonNode node = objectMapper.readTree(response).path("data").path(field);
        return node.isMissingNode() ? null : node.asText();
    }
}
