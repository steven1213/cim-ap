package com.cim.iam.server;

import com.cim.iam.server.auth.PasswordDerivation;
import com.cim.iam.server.config.IamAuthProperties;
import com.cim.iam.server.console.IamConsoleSeedService;
import com.cim.iam.server.support.IamPermissionCodes;
import com.cim.system.menu.SysMenu;
import com.cim.system.menu.SysMenuRepository;
import com.cim.system.permission.SysPermission;
import com.cim.system.permission.SysPermissionRepository;
import com.cim.system.role.SysRole;
import com.cim.system.role.SysRolePermRepository;
import com.cim.system.role.SysRoleRepository;
import com.cim.system.role.SysUserRole;
import com.cim.system.role.SysUserRoleRepository;
import com.cim.system.user.SysUser;
import com.cim.system.user.SysUserRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 控制台菜单 / 按钮权限 / 多语言（W2）端到端测试。
 *
 * <p>覆盖：① 种子把菜单树/权限码/角色/译文入库且幂等；② 管理员在本 ap 建档并获 IAM_ADMIN（超管）；
 * ③ 权限源已切到库内 RBAC——{@code /sys/me/permissions} 回显含 {@code iam:*} 与 {@code sys:*}
 * （后者说明 cim-system 的权限目录也种上了，超管才不会被自身端点拒绝）；
 * ④ 新增的方法级 {@code @PreAuthorize} 对管理员放行、对未认证拒绝；
 * ⑤ 多语言：{@code en-US} 取到英文、未知语言回落中文兜底。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-console;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.iam.auth.bootstrap.admin-username=admin",
        "cim.iam.auth.bootstrap.admin-password=Admin@123456"
})
class ConsolePermissionI18nTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IamAuthProperties authProps;
    @Autowired
    private IamConsoleSeedService seedService;
    @Autowired
    private SysMenuRepository menuRepository;
    @Autowired
    private SysPermissionRepository permissionRepository;
    @Autowired
    private SysRoleRepository roleRepository;
    @Autowired
    private SysRolePermRepository sysRolePermRepository;
    @Autowired
    private SysUserRepository sysUserRepository;
    @Autowired
    private SysUserRoleRepository sysUserRoleRepository;

    // ------------------------------------------------------------------ 种子

    @Test
    void seedPersistsMenuTreePermissionsRolesAndTranslations() {
        assertThat(menuRepository.findByOrderBySortNoAsc())
                .extracting(SysMenu::getI18nCode)
                .contains("iam.menu.overview", "iam.menu.group.identity", "iam.menu.orgs",
                        "iam.menu.menus", "iam.menu.i18n");

        // 目录树：/orgs 的父节点是「身份管理」分组
        SysMenu orgs = byI18n("iam.menu.orgs");
        SysMenu identityGroup = byI18n("iam.menu.group.identity");
        assertThat(orgs.getParentId()).isEqualTo(identityGroup.getId());

        // 按钮节点承载权限码、不参与导航
        SysMenu createOrgBtn = byI18n("iam.btn.org.create");
        assertThat(createOrgBtn.getPermCode()).isEqualTo(IamPermissionCodes.ORG_CREATE);
        assertThat(createOrgBtn.getVisible()).isFalse();

        // 权限码入库（含类级兜底与一条 cim-system 的码）
        List<String> codes = permissionRepository.findAll().stream().map(SysPermission::getCode).toList();
        assertThat(codes).contains(IamPermissionCodes.CONSOLE_ADMIN, IamPermissionCodes.USER_LIST,
                IamPermissionCodes.I18N_LIST, com.cim.system.support.PermissionCodes.MENU_LIST);

        // 角色：IAM_ADMIN 为超管
        Optional<SysRole> adminRole = roleRepository.findFirstByCodeOrderByCreateTimeAsc("IAM_ADMIN");
        assertThat(adminRole).isPresent();
        assertThat(adminRole.get().getIsSuper()).isTrue();

        // 管理员授权档案 + 角色关联
        Optional<SysUser> admin = sysUserRepository.findFirstByUsernameOrderByCreateTimeAsc("admin");
        assertThat(admin).isPresent();
        assertThat(sysUserRoleRepository.findByUserId(admin.get().getId()))
                .extracting(SysUserRole::getRoleId)
                .contains(adminRole.get().getId());
    }

    @Test
    void seedIsIdempotent() {
        IamConsoleSeedService.SeedReport second = seedService.seed();
        assertThat(second.permissions()).isZero();
        assertThat(second.menus()).isZero();
        assertThat(second.grants()).isZero();
        assertThat(second.adminProfileCreated()).isFalse();
        assertThat(second.zhTexts()).isZero();
        assertThat(second.enTexts()).isZero();
    }

    // ------------------------------------------------------------------ 权限（库内 RBAC）

    @Test
    void mePermissionsExposeIamAndSysCodesForAdmin() throws Exception {
        String token = login("admin", "Admin@123456");
        String resp = json(mvc.perform(get("/sys/me/permissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode data = objectMapper.readTree(resp).path("data");
        List<String> authorities = objectMapper.convertValue(data,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

        // 库内 RBAC 生效：控制台细粒度码 + 类级兜底码
        assertThat(authorities).contains(
                IamPermissionCodes.CONSOLE_ADMIN,
                IamPermissionCodes.OVERVIEW_VIEW,
                IamPermissionCodes.USER_LIST,
                IamPermissionCodes.I18N_LIST);
        // 超管展开为「库内全部启用权限码」→ 含 cim-system 的 sys:*（否则 cim-system 端点会拒绝超管）
        assertThat(authorities).contains(com.cim.system.support.PermissionCodes.MENU_LIST);
    }

    @Test
    void meMenusReturnsBackendDrivenTree() throws Exception {
        String token = login("admin", "Admin@123456");
        String resp = json(mvc.perform(get("/sys/me/menus").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
        // 菜单由后端下发：含 i18n_code（前端据此翻译展示），不含 BUTTON 节点（include-buttons=false）
        assertThat(resp).contains("iam.menu.orgs").contains("iam.menu.menus");
        assertThat(resp).doesNotContain("iam.btn.org.create");
    }

    @Test
    void protectedAdminEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/admin/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/sys/me/permissions")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCallMethodLevelProtectedEndpoint() throws Exception {
        String token = login("admin", "Admin@123456");
        mvc.perform(get("/api/v1/admin/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/i18n/locales").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------- 平台配置端点（W4 配置页的数据面）

    /**
     * W4 的菜单/权限/角色三个配置页把数据面放在平台 `/sys/**` 上，用平台码 `sys:*` 鉴权。
     *
     * <p>这组断言锁住「超管确实能驱动这些端点」——若哪天真退化成
     * 「控制台管理员被平台端点拒绝」，本测试会先失败，而不是等用户打开页面才发现。</p>
     */
    @Test
    void adminCanDrivePlatformConfigEndpointsUsedByW4Pages() throws Exception {
        String token = login("admin", "Admin@123456");
        String auth = "Bearer " + token;

        mvc.perform(get("/sys/menus/tree").param("includeButtons", "true")
                .header("Authorization", auth)).andExpect(status().isOk());

        String permResp = json(mvc.perform(get("/sys/permissions/page")
                        .param("page", "1").param("size", "500").param("sort", "code,asc")
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andReturn());
        assertThat(permResp).contains(com.cim.system.support.PermissionCodes.MENU_LIST);

        String roleResp = json(mvc.perform(get("/sys/roles/page")
                        .param("page", "1").param("size", "500").param("sort", "sortNo,asc")
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andReturn());
        JsonNode roleRecords = objectMapper.readTree(roleResp).path("data").path("records");
        assertThat(roleRecords).isNotEmpty();

        String adminRoleId = roleRepository.findFirstByCodeOrderByCreateTimeAsc("IAM_ADMIN")
                .orElseThrow().getId();
        mvc.perform(get("/sys/roles/" + adminRoleId + "/permissions")
                .header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/sys/roles/" + adminRoleId + "/menus")
                .header("Authorization", auth)).andExpect(status().isOk());
    }

    /**
     * 只读运维角色（`IAM_OPERATOR`）必须同时持有平台只读码，否则「配置页可见但数据 403」。
     *
     * <p>控制台码 `iam:menu:list` 只决定入口显隐；菜单/权限/角色三页的真实数据来自平台端点，
     * 那些端点认 `sys:*`。故种子必须把平台只读码也授给只读角色（超管由「全部启用权限码」短路
     * 展开，天然具备）。</p>
     */
    @Test
    void readOnlyOperatorRoleIsGrantedPlatformReadCodes() {
        SysRole operator = roleRepository.findFirstByCodeOrderByCreateTimeAsc("IAM_OPERATOR")
                .orElseThrow(() -> new AssertionError("IAM_OPERATOR 角色未种子上"));
        assertThat(operator.getIsSuper()).isFalse();

        List<String> grantedCodes = sysRolePermRepository.findByRoleId(operator.getId()).stream()
                .map(link -> permissionRepository.findById(link.getPermId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(SysPermission::getCode)
                .toList();

        assertThat(grantedCodes).contains(
                IamPermissionCodes.MENU_LIST,
                com.cim.system.support.PermissionCodes.MENU_LIST,
                com.cim.system.support.PermissionCodes.PERMISSION_LIST,
                com.cim.system.support.PermissionCodes.ROLE_LIST);
    }

    // ------------------------------------------------------------------ 多语言

    @Test
    void englishBundleServedAndUnknownLocaleFallsBackToChinese() throws Exception {
        String en = json(mvc.perform(get("/api/i18n/messages").param("lang", "en-US"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(readMessage(en, "iam.menu.orgs")).isEqualTo("Organization");

        String zh = json(mvc.perform(get("/api/i18n/messages").param("lang", "zh-CN"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(readMessage(zh, "iam.menu.orgs")).isEqualTo("组织架构");

        // 未知语言：四级兜底链回落到 zh-CN（「只要有中文就一定看得懂」）
        String fr = json(mvc.perform(get("/api/i18n/messages").param("lang", "fr-FR"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(readMessage(fr, "iam.menu.orgs")).isEqualTo("组织架构");
    }

    private String readMessage(String response, String code) throws Exception {
        return objectMapper.readTree(response).path("data").path("messages").path(code).asText();
    }

    /**
     * 以 <b>UTF-8</b> 读取响应体（勿直接 {@code getContentAsString()}）。
     *
     * <p>⚠️ 踩坑：Boot 3 写出的 {@code Content-Type: application/json} <b>不带 charset</b>，
     * 而 {@code MockHttpServletResponse} 缺省按 ISO-8859-1 解码 → 中文变乱码（4 个汉字 → 12 个
     * 拉丁字符），断言必失败。生产端字节本身是 UTF-8（符合 RFC 8259），<b>仅测试侧</b>需显式指定
     * 字符集；纯 ASCII 断言看不出问题，含中日韩文案的断言才会暴露。</p>
     */
    private String json(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ 工具

    private SysMenu byI18n(String i18nCode) {
        return menuRepository.findByOrderBySortNoAsc().stream()
                .filter(m -> i18nCode.equals(m.getI18nCode()))
                .findFirst().orElseThrow(() -> new AssertionError("menu not seeded: " + i18nCode));
    }

    private String login(String username, String rawPassword) throws Exception {
        MvcResult saltRes = mvc.perform(get("/api/v1/login/salt").param("username", username))
                .andExpect(status().isOk()).andReturn();
        String clientSalt = objectMapper.readTree(json(saltRes))
                .path("data").path("clientSalt").asText();
        String clientHash = PasswordDerivation.derive(rawPassword, clientSalt,
                authProps.getPassword().getRounds());
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username, "credential", clientHash, "clientSalt", clientSalt));
        MvcResult loginRes = mvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(json(loginRes))
                .path("data").path("token").asText();
    }
}
