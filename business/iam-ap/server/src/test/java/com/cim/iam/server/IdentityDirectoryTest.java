package com.cim.iam.server;

import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.app.EffectiveAccessResolver;
import com.cim.iam.server.auth.LocalCredentialService;
import com.cim.iam.server.auth.PasswordDerivation;
import com.cim.iam.server.config.IamAuthProperties;
import com.cim.iam.server.directory.AdDirectorySyncService;
import com.cim.iam.server.org.OrgGrantService;
import com.cim.iam.server.org.OrgNode;
import com.cim.iam.server.org.OrgNodeService;
import com.cim.iam.server.org.OrgNodeType;
import com.cim.iam.server.profile.ProfileService;
import com.cim.iam.server.token.TokenVersionService;
import com.cim.iam.server.watermark.DirectoryWatermarkService;
import com.cim.iam.server.watermark.WatermarkScope;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 身份目录与组织架构（Wave 0）端到端。
 *
 * <p>覆盖：组织树 + 物化路径 + 祖先链授予展开（含 {@code includeChildren} 语义）、
 * 跨源保护（AD 同步节点不可本地编辑）、组织变更 bump 后旧令牌 401、目录水位递增、
 * 目录只读 API 的服务身份（{@code X-Directory-Key}）、未配置 AD 时同步跳过不阻塞。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-directory;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.iam.auth.bootstrap.admin-username=admin",
        "cim.iam.auth.bootstrap.admin-password=Admin@123456",
        "cim.iam.directory.api.key=test-directory-key"
})
class IdentityDirectoryTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OrgNodeService orgNodeService;
    @Autowired
    private OrgGrantService orgGrantService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private EffectiveAccessResolver resolver;
    @Autowired
    private AppRegistrationService appRegistrationService;
    @Autowired
    private DirectoryWatermarkService watermarkService;
    @Autowired
    private AdDirectorySyncService adSyncService;
    @Autowired
    private LocalCredentialService credentialService;
    @Autowired
    private TokenVersionService tokenVersionService;
    @Autowired
    private IamAuthProperties authProps;

    @Test
    void orgGrantExpandsToMemberThroughAncestorChain() {
        OrgNode area = orgNodeService.createManaged(null, "AREA-T1", "A 厂", OrgNodeType.AREA, 0);
        OrgNode ws = orgNodeService.createManaged(area.getId(), "WS-T1", "蚀刻车间", OrgNodeType.WORKSHOP, 0);
        OrgNode line = orgNodeService.createManaged(ws.getId(), "LINE-T1", "蚀刻 1 线", OrgNodeType.LINE, 0);

        appRegistrationService.registerApp("mds-ap", "MDS", 1);
        appRegistrationService.registerApp("mes-ap", "MES", 2);

        // 车间级授予（含子组织）→ 应覆盖挂在「孙级产线」的用户
        orgGrantService.grant(ws.getId(), "mds-ap", Set.of("OPERATOR"), true);
        // 厂区级授予但「不含子组织」→ 不应覆盖孙级用户
        orgGrantService.grant(area.getId(), "mes-ap", Set.of("VIEWER"), false);

        profileService.createManaged("dir-u1", "张三", "1001", null, null, "操作员");
        profileService.setUserOrgs("dir-u1", List.of(new ProfileService.OrgRef(line.getId(), true)));

        assertThat(resolver.effectiveApps("dir-u1")).contains("mds-ap");
        assertThat(resolver.effectiveApps("dir-u1")).doesNotContain("mes-ap");
        assertThat(resolver.effectiveRoles("dir-u1").get("mds-ap")).containsExactly("OPERATOR");

        // 把用户改挂到厂区直属 → includeChildren=false 的厂区授予此时生效
        profileService.setUserOrgs("dir-u1", List.of(new ProfileService.OrgRef(area.getId(), true)));
        assertThat(resolver.effectiveApps("dir-u1")).contains("mes-ap");
        // 改挂后不再位于车间下 → mds-ap（车间级含子组织）授予不再覆盖
        assertThat(resolver.effectiveApps("dir-u1")).doesNotContain("mds-ap");
    }

    @Test
    void crossSourceProtectionKeepsAdNodesReadOnly() {
        OrgNode adNode = orgNodeService.upsertAdSynced(
                "OU=制造部,DC=corp,DC=com", null, "制造部", "制造部", OrgNodeType.DEPT, 0);
        assertThat(adNode.getId()).isNotBlank();

        // AD 同步节点不可在 IAM 侧修改 / 删除
        assertThatThrownBy(() -> orgNodeService.update(adNode.getId(), "改名", null, null))
                .isInstanceOf(BizException.class)
                .extracting("bizCode")
                .isEqualTo(BizCode.PARAM_INVALID);
        assertThatThrownBy(() -> orgNodeService.delete(adNode.getId()))
                .isInstanceOf(BizException.class)
                .extracting("bizCode")
                .isEqualTo(BizCode.PARAM_INVALID);

        // 同 code 的 IAM 自建节点可与 AD 节点共存（source 隔离，互不覆盖）
        OrgNode managed = orgNodeService.createManaged(null, "制造部", "制造部（自建）", OrgNodeType.WORKSHOP, 0);
        assertThat(managed.getId()).isNotEqualTo(adNode.getId());
        assertThat(managed.getSource().name()).isEqualTo("IAM_MANAGED");
    }

    @Test
    void orgChangeBumpsTokenVersionAndWatermark() {
        OrgNode area = orgNodeService.createManaged(null, "AREA-T3", "B 厂", OrgNodeType.AREA, 0);
        OrgNode ws = orgNodeService.createManaged(area.getId(), "WS-T3", "薄膜车间", OrgNodeType.WORKSHOP, 0);
        profileService.createManaged("dir-u3", "李四", "1003", null, null, "技师");
        profileService.setUserOrgs("dir-u3", List.of(new ProfileService.OrgRef(ws.getId(), true)));

        long verBefore = tokenVersionService.currentVersion("dir-u3");
        long wmBefore = watermarkService.current(WatermarkScope.ORG);

        orgNodeService.move(ws.getId(), null); // 提出为根节点，子树路径重写

        assertThat(tokenVersionService.currentVersion("dir-u3")).isGreaterThan(verBefore);
        assertThat(watermarkService.current(WatermarkScope.ORG)).isGreaterThan(wmBefore);
    }

    @Test
    void adSyncSkippedWhenUnconfigured() {
        AdDirectorySyncService.SyncResult r = adSyncService.syncNow();
        assertThat(r.skipped()).isTrue();
        assertThat(r.reason()).contains("跳过");
    }

    @Test
    void directoryApiRequiresServiceKey() throws Exception {
        // 无请求头 → 401
        mvc.perform(get("/api/v1/directory/watermark")).andExpect(status().isUnauthorized());
        // 错误密钥 → 401
        mvc.perform(get("/api/v1/directory/watermark").header("X-Directory-Key", "wrong"))
                .andExpect(status().isUnauthorized());
        // 正确密钥 → 200 且水位可用
        String resp = mvc.perform(get("/api/v1/directory/watermark").header("X-Directory-Key", "test-directory-key"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(resp).path("data");
        assertThat(data.path("user").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(data.path("org").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void directoryBatchUsersAndAdminEndpoints() throws Exception {
        // 批量档案
        String body = objectMapper.writeValueAsString(Map.of("userIds", List.of("dir-u1")));
        mvc.perform(post("/api/v1/directory/users/batch").header("X-Directory-Key", "test-directory-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // 管理端点：无令牌 401；管理员令牌 200
        mvc.perform(get("/api/v1/admin/orgs")).andExpect(status().isUnauthorized());
        String token = adminToken();
        String orgs = mvc.perform(get("/api/v1/admin/orgs").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(orgs).path("data").isArray()).isTrue();

        // 管理端点创建组织 → 出现在列表
        String createBody = objectMapper.writeValueAsString(Map.of(
                "code", "AREA-T6", "name", "C 厂", "nodeType", "AREA", "sortNo", 1));
        mvc.perform(post("/api/v1/admin/orgs").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isOk());
        String after = mvc.perform(get("/api/v1/admin/orgs").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        assertThat(after).contains("AREA-T6");
    }

    @Test
    void orgGrantChangeInvalidatesExistingToken() throws Exception {
        OrgNode area = orgNodeService.createManaged(null, "AREA-T7", "D 厂", OrgNodeType.AREA, 0);
        appRegistrationService.registerApp("eap-ap", "EAP", 7);
        profileService.createManaged("dir-u7", "王五", "1007", null, null, "工程师");

        // 建本地凭证并登录
        String salt = "salt-dir-u7";
        String clientHash = PasswordDerivation.derive("DirU7@123", salt, rounds());
        if (credentialService.findByUsername("dir-u7").isEmpty()) {
            credentialService.registerLocalUser("dir-u7", clientHash, salt);
        }
        String loginBody = objectMapper.writeValueAsString(Map.of(
                "username", "dir-u7",
                "credential", PasswordDerivation.derive("DirU7@123", salt, rounds()),
                "clientSalt", salt));
        String loginResp = mvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResp).path("data").path("token").asText();

        // 令牌有效
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        // 组织级授予 → bump 该组织内用户（该用户尚未归属，故先归属再授予）
        profileService.setUserOrgs("dir-u7", List.of(new ProfileService.OrgRef(area.getId(), true)));
        orgGrantService.grant(area.getId(), "eap-ap", Set.of("OPERATOR"), true);

        // 旧令牌因版本 bump 失效 → 401
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
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
