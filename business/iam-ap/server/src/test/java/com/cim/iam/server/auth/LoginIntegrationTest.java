package com.cim.iam.server.auth;

import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.config.IamAuthProperties;
import com.cim.iam.server.token.RsaKeyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 独立上下文 + 独立 H2（避免与其它 IAM 测试共享库导致数据串扰）
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-login-it;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
class LoginIntegrationTest {

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
    void seed() throws Exception {
        // H2 命名内存库在本类各测试方法间共享，故 seed 必须幂等（已存在则跳过注册，避免「已存在」异常）
        String salt = "salt-alice";
        String clientHash = PasswordDerivation.derive("secret", salt, authProps.getPassword().getRounds());
        if (credentialService.findByUsername("alice").isEmpty()) {
            credentialService.registerLocalUser("alice", clientHash, salt);
        }
        if (appService.listApps().stream().noneMatch(a -> "mds-ap".equals(a.getAppCode()))) {
            appService.registerApp("mds-ap", "MDS", 1);
        }
        appService.assignUserToApp("alice", "mds-ap", Set.of("ADMIN"));
    }

    @Test
    void loginOverHttpReturnsToken() throws Exception {
        String clientHash = PasswordDerivation.derive("secret", "salt-alice", authProps.getPassword().getRounds());
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "alice",
                "credential", clientHash));

        String response = mvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = readToken(response);
        assertThat(token).isNotBlank();
        assertThat(parseUid(token)).isEqualTo("alice");
        assertThat(parseApps(token)).contains("mds-ap");
    }

    @Test
    void loginWrongPasswordReturnsForbidden() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "alice",
                "credential", "wrong-client-hash"));

        mvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void saltEndpointReturnsClientSalt() throws Exception {
        String response = mvc.perform(get("/api/v1/login/salt").param("username", "alice"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("clientSalt").asText()).isEqualTo("salt-alice");
    }

    private String readToken(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode data = root.get("data");
        if (data != null && data.has("token")) {
            return data.get("token").asText();
        }
        return root.get("token").asText();
    }

    private String parseUid(String token) {
        RSAPublicKey pub = (RSAPublicKey) rsaKeyService.getPublicKey();
        return io.jsonwebtoken.Jwts.parser().verifyWith(pub).build()
                .parseSignedClaims(token).getPayload().get("uid", String.class);
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseApps(String token) {
        RSAPublicKey pub = (RSAPublicKey) rsaKeyService.getPublicKey();
        java.util.List<String> apps = io.jsonwebtoken.Jwts.parser().verifyWith(pub).build()
                .parseSignedClaims(token).getPayload().get("apps", java.util.List.class);
        return new java.util.LinkedHashSet<>(apps);
    }
}
