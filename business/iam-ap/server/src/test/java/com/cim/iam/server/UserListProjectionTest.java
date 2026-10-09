package com.cim.iam.server;

import com.cim.iam.server.auth.AccountLockService;
import com.cim.iam.server.auth.LocalCredentialRepository;
import com.cim.iam.server.auth.LocalCredentialService;
import com.cim.iam.server.auth.PasswordDerivation;
import com.cim.iam.server.common.DataOrigin;
import com.cim.iam.server.config.IamAuthProperties;
import com.cim.iam.server.org.OrgNode;
import com.cim.iam.server.org.OrgNodeService;
import com.cim.iam.server.org.OrgNodeType;
import com.cim.iam.server.profile.ProfileService;
import com.cim.iam.server.profile.UserProfile;
import com.cim.iam.server.profile.UserProfileRepository;
import com.cim.iam.server.profile.UserStatus;
import com.cim.iam.server.support.IamPermissionCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户清单投影（{@code GET /api/v1/admin/users}）的 service / repository 级测试。
 *
 * <p><b>被测契约</b>：清单行 =「本地凭证 ∪ 用户档案」的<b>并集</b>，一人一行，按 username 升序。
 * 这条契约是 W4「用户与档案」页的数据前提——它不是 {@code select * from local_credential}，
 * 而是两个存储（凭证表 / 档案表）在服务层的投影合并。因此测试同时覆盖两层：</p>
 * <ol>
 *   <li><b>repository 层</b>：两张表各自的 keyset 与并集，是清单 id 集合的来源；</li>
 *   <li><b>service/投影层</b>：{@code UserAdminController#list} 对每个 id 的字段解析
 *       （{@code hasCredential} / {@code source} / {@code enabled} / 锁定态 / 组织全路径）。</li>
 * </ol>
 *
 * <p>另覆盖分层鉴权：类级伞码 {@code iam:console:admin} ∧ 方法级 {@code iam:user:list}，
 * 两者缺一即 403——用显式授权集发起请求来锁定该语义（而非只对超管/匿名做黑盒断言）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-user-list;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.iam.auth.bootstrap.admin-username=admin",
        "cim.iam.auth.bootstrap.admin-password=Admin@123456"
})
class UserListProjectionTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IamAuthProperties authProps;
    @Autowired
    private LocalCredentialService credentialService;
    @Autowired
    private LocalCredentialRepository credentialRepository;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private UserProfileRepository profileRepository;
    @Autowired
    private AccountLockService accountLockService;
    @Autowired
    private OrgNodeService orgNodeService;

    // ------------------------------------------------ 并集投影（service 层）

    /**
     * 一人一行：凭证-only、档案-only（多由 AD 同步而来）、两者皆有，三种来源都要出现。
     *
     * <p>特别注意「凭证-only」这一支——它没有档案，若投影实现退化成「遍历档案表」，
     * 这类账号会从控制台凭空消失（而它们恰恰是 IAM 自建的非 AD 人员 / 服务账号）。</p>
     */
    @Test
    void listUnionsCredentialOnlyProfileOnlyAndBoth() throws Exception {
        String token = login("admin", "Admin@123456");

        // ① 凭证-only：本地账号，无档案
        credentialService.registerLocalUser("credonly", hash("Cred@12345", "s1"), "s1");

        // ② 档案-only：AD 同步来的员工（无本地凭证）
        adProfile("adonly", "E-1001", "AD 员工甲", UserStatus.ACTIVE);

        // ③ 两者皆有：本地账号 + IAM 自建档案
        credentialService.registerLocalUser("bothsrc", hash("Both@12345", "s2"), "s2");
        profile("bothsrc", "E-1002", "自建人员丙", UserStatus.ACTIVE);

        List<JsonNode> rows = rows(token);
        JsonNode only = row(rows, "credonly");
        JsonNode ad = row(rows, "adonly");
        JsonNode both = row(rows, "bothsrc");

        // 凭证-only
        assertThat(only.path("hasCredential").asBoolean()).isTrue();
        assertThat(only.path("enabled").asBoolean()).isTrue();
        assertThat(only.path("source").asText()).isEqualTo("NONE");
        assertThat(only.path("displayName").isNull()).isTrue();

        // 档案-only：出现在清单里，但明确标注「无本地凭证」
        assertThat(ad.path("hasCredential").asBoolean()).isFalse();
        assertThat(ad.path("source").asText()).isEqualTo(DataOrigin.AD_SYNCED.name());
        assertThat(ad.path("status").asText()).isEqualTo(UserStatus.ACTIVE.name());
        assertThat(ad.path("enabled").asBoolean()).isTrue();
        assertThat(ad.path("displayName").asText()).isEqualTo("AD 员工甲");
        assertThat(ad.path("employeeNo").asText()).isEqualTo("E-1001");

        // 两者皆有
        assertThat(both.path("hasCredential").asBoolean()).isTrue();
        assertThat(both.path("username").asText()).isEqualTo("bothsrc");
        assertThat(both.path("source").asText()).isEqualTo(DataOrigin.IAM_MANAGED.name());
    }

    /** 清单按 username 升序，且不出现重复行。 */
    @Test
    void listIsSortedByUsernameAndHasNoDuplicateRows() throws Exception {
        String token = login("admin", "Admin@123456");
        credentialService.registerLocalUser("zzz", hash("Zzz@123456", "s3"), "s3");
        credentialService.registerLocalUser("aaa", hash("Aaa@123456", "s4"), "s4");

        List<String> usernames = new ArrayList<>();
        for (JsonNode r : rows(token)) {
            usernames.add(r.path("username").asText());
        }
        assertThat(usernames).isSorted();
        assertThat(new LinkedHashSet<>(usernames)).hasSameSizeAs(usernames);
    }

    /** 禁用的凭证账号 → {@code enabled=false}；停用的 AD 档案 → 同样 {@code enabled=false}。 */
    @Test
    void disabledCredentialAndInactiveProfileBothReportDisabled() throws Exception {
        String token = login("admin", "Admin@123456");

        credentialService.registerLocalUser("discred", hash("Dis@123456", "s5"), "s5");
        credentialService.setEnabled("discred", false);

        adProfile("inactivead", "E-2002", "AD 员工乙（已离职）", UserStatus.INACTIVE);

        List<JsonNode> rows = rows(token);
        assertThat(row(rows, "discred").path("enabled").asBoolean()).isFalse();
        assertThat(row(rows, "inactivead").path("enabled").asBoolean()).isFalse();
        assertThat(row(rows, "inactivead").path("status").asText())
                .isEqualTo(UserStatus.INACTIVE.name());
    }

    /** 锁定态投影：达到失败阈值后 {@code locked=true} 且给出 {@code lockedUntil} 与 {@code failCount}。 */
    @Test
    void lockedCredentialExposesLockStateUntilAndFailCount() throws Exception {
        String token = login("admin", "Admin@123456");
        credentialService.registerLocalUser("lockme", hash("Lock@123456", "s6"), "s6");

        for (int i = 0; i < 10 && !accountLockService.isLocked("lockme"); i++) {
            accountLockService.onFailure("lockme");
        }
        assertThat(accountLockService.isLocked("lockme"))
                .as("连续失败应触发锁定（阈值取自 cim.iam.auth.lockout）").isTrue();

        JsonNode locked = row(rows(token), "lockme");
        assertThat(locked.path("locked").asBoolean()).isTrue();
        assertThat(locked.path("lockedUntil").isNull()).isFalse();
        assertThat(locked.path("failCount").asInt()).isGreaterThan(0);

        // 解锁端点生效后，投影同步回落（locked=false 且 failCount 清零）
        mvc.perform(post("/api/v1/admin/users/lockme/unlock")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        JsonNode unlocked = row(rows(token), "lockme");
        assertThat(unlocked.path("locked").asBoolean()).isFalse();
        assertThat(unlocked.path("failCount").asInt()).isZero();
    }

    /** 组织归属投影为**全路径名**（"A厂 / 蚀刻车间"），而非裸 id——前端表格直接展示该字段。 */
    @Test
    void orgMembershipIsProjectedAsFullPathName() throws Exception {
        String token = login("admin", "Admin@123456");
        credentialService.registerLocalUser("orgmem", hash("Org@123456", "s7"), "s7");

        OrgNode factory = orgNodeService.createManaged(null, "T-FAC", "A厂", OrgNodeType.AREA, 1);
        OrgNode workshop = orgNodeService.createManaged(factory.getId(), "T-WS", "蚀刻车间",
                OrgNodeType.WORKSHOP, 1);
        profileService.setUserOrgs("orgmem", List.of(new ProfileService.OrgRef(workshop.getId(), true)));

        List<String> orgNames = new ArrayList<>();
        row(rows(token), "orgmem").path("orgNames").forEach(n -> orgNames.add(n.asText()));
        assertThat(orgNames).contains("A厂 / 蚀刻车间");
    }

    /** 删除本地凭证后，档案仍在 → 该用户**不消失**，只是回落为「无本地凭证」的档案行。 */
    @Test
    void deletingCredentialKeepsProfileBackedUserInList() throws Exception {
        String token = login("admin", "Admin@123456");
        credentialService.registerLocalUser("keepme", hash("Keep@12345", "s8"), "s8");
        profile("keepme", "E-3003", "自建档案人", UserStatus.ACTIVE);

        mvc.perform(delete("/api/v1/admin/users/keepme")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        JsonNode kept = row(rows(token), "keepme");
        assertThat(kept.path("hasCredential").asBoolean()).isFalse();
        assertThat(kept.path("displayName").asText()).isEqualTo("自建档案人");
        assertThat(kept.path("source").asText()).isEqualTo(DataOrigin.IAM_MANAGED.name());
    }

    // ------------------------------------------------ 并集来源（repository 层）

    /**
     * repository 层：清单的 id 集合必须**恰好等于**两张表 keyset 的并集。
     *
     * <p>把「存储侧真相」与「接口侧投影」对齐断言，可防止投影实现漏掉某一侧的账号
     * （典型退化：只查凭证表 → AD 同步人员全部消失）。</p>
     */
    @Test
    void projectedUserIdsEqualRepositoryKeysetUnion() throws Exception {
        String token = login("admin", "Admin@123456");

        credentialService.registerLocalUser("repocred", hash("Repo@1234", "s9"), "s9");
        adProfile("repoad", "E-4004", "仓库侧 AD 员工", UserStatus.ACTIVE);

        Set<String> fromCredentialTable = new TreeSet<>();
        credentialRepository.findAll().forEach(c -> fromCredentialTable.add(c.getUserId()));
        Set<String> fromProfileTable = new TreeSet<>();
        profileRepository.findAll().forEach(p -> fromProfileTable.add(p.getUserId()));

        assertThat(fromCredentialTable).as("凭证表应能按 userId 检索到刚建的账号")
                .contains("repocred");
        assertThat(profileRepository.findByUserId("repoad")).as("档案表按 userId 可查").isPresent();
        assertThat(profileRepository.findBySource(DataOrigin.AD_SYNCED))
                .extracting(UserProfile::getUserId).contains("repoad");

        Set<String> union = new TreeSet<>(fromCredentialTable);
        union.addAll(fromProfileTable);

        Set<String> projected = new TreeSet<>();
        for (JsonNode r : rows(token)) {
            projected.add(r.path("userId").asText());
        }
        assertThat(projected).as("接口投影的 userId 集合 = 两表并集").isEqualTo(union);
    }

    // ------------------------------------------------ 分层鉴权契约

    /**
     * 分层注解的真实语义：**方法级 `@PreAuthorize` 覆盖类级**（不是 AND 叠加）。
     *
     * <p>Spring Security 取「最具体」的注解（`AuthorizationAnnotationUtils` 先找方法、再找类，
     * 命中即返回），故类级伞码 {@code iam:console:admin} 只是<b>无方法级注解时的兜底</b>。
     * 本控制器每个端点都带自己的细码，于是实际闸门 = 该端点的细码单独成立。</p>
     *
     * <p>⚠️ 这条实测结论与直觉相反（很多人以为类级与方法级是「与」关系），在此钉死：
     * 只持细码必须 <b>200</b>；只持伞码必须 <b>403</b>。若哪天真被改成 AND 语义，
     * 本测试会先失败，提示同步修正前端入口闸门与文档口径。</p>
     */
    @Test
    void methodLevelPermissionOverridesClassLevelUmbrella() throws Exception {
        // 只持细码、无伞码 → 200（方法级覆盖类级）
        mvc.perform(get("/api/v1/admin/users")
                        .with(authentication(principal(IamPermissionCodes.USER_LIST))))
                .andExpect(status().isOk());

        // 只持伞码 → 403（类级不参与「与」运算，方法级细码才是实际闸门）
        mvc.perform(get("/api/v1/admin/users")
                        .with(authentication(principal(IamPermissionCodes.CONSOLE_ADMIN))))
                .andExpect(status().isForbidden());

        // 两码齐备 → 200，且响应体是 Result 包装（data 为数组）
        var bothCodes = get("/api/v1/admin/users")
                .with(authentication(principal(IamPermissionCodes.USER_LIST,
                        IamPermissionCodes.CONSOLE_ADMIN)));
        String body = utf8(mvc.perform(bothCodes).andExpect(status().isOk()).andReturn());
        assertThat(objectMapper.readTree(body).path("data").isArray()).isTrue();
    }

    /** 匿名访问用户清单必须被拒（401），与鉴权源无关——闸门在此之前。 */
    @Test
    void anonymousCannotListUsers() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------ 工具

    private static UsernamePasswordAuthenticationToken principal(String... authorities) {
        return new UsernamePasswordAuthenticationToken("contract-probe", "n/a",
                AuthorityUtils.createAuthorityList(authorities));
    }

    /** 直接走 service 建**档案**（source=IAM_MANAGED），绕过控制器以便造数。 */
    private void profile(String userId, String employeeNo, String displayName, UserStatus status) {
        profileService.createManaged(userId, displayName, employeeNo, null, null, null);
        if (status != UserStatus.ACTIVE) {
            profileService.setStatus(userId, status);
        }
    }

    /**
     * 直接走 service 建**AD 同步来源**的档案（source=AD_SYNCED）。
     *
     * <p>与 {@link #profile} 的区别是本测试的核心变量之一：AD 同步人员<b>没有本地凭证</b>，
     * 若投影实现只看凭证表，这批人（生产里占绝大多数）会整批消失。</p>
     */
    private void adProfile(String userId, String employeeNo, String displayName, UserStatus status) {
        profileService.upsertAdProfile(userId, employeeNo, displayName, null, null, status);
    }

    private String hash(String raw, String salt) {
        return PasswordDerivation.derive(raw, salt, authProps.getPassword().getRounds());
    }

    private List<JsonNode> rows(String token) throws Exception {
        String body = utf8(mvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.isArray()).as("用户清单 data 应为数组").isTrue();
        List<JsonNode> list = new ArrayList<>();
        data.forEach(list::add);
        return list;
    }

    private JsonNode row(List<JsonNode> rows, String userId) {
        return rows.stream().filter(r -> userId.equals(r.path("userId").asText()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "清单中缺少 userId=" + userId + "（并集投影漏掉一侧？实际 = "
                                + rows.stream().map(r -> r.path("userId").asText()).toList() + "）"));
    }

    /**
     * 以 <b>UTF-8</b> 读取响应体（勿直接 {@code getContentAsString()}）。
     *
     * <p>MockHttpServletResponse 缺省按 ISO-8859-1 解码，中文断言会变乱码。</p>
     */
    private String utf8(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String login(String username, String rawPassword) throws Exception {
        MvcResult saltRes = mvc.perform(get("/api/v1/login/salt").param("username", username))
                .andExpect(status().isOk()).andReturn();
        String clientSalt = objectMapper.readTree(utf8(saltRes))
                .path("data").path("clientSalt").asText();
        String clientHash = PasswordDerivation.derive(rawPassword, clientSalt,
                authProps.getPassword().getRounds());
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username, "credential", clientHash, "clientSalt", clientSalt));
        MvcResult loginRes = mvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(utf8(loginRes))
                .path("data").path("token").asText();
    }
}
