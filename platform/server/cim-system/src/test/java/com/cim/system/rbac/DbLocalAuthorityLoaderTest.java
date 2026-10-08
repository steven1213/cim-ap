package com.cim.system.rbac;

import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.auth.rbac.ClaimLocalAuthorityLoader;
import com.cim.auth.token.TokenClaims;
import com.cim.system.TestApplication;
import com.cim.system.menu.MenuNode;
import com.cim.system.menu.MenuType;
import com.cim.system.menu.SysMenu;
import com.cim.system.menu.SysMenuRepository;
import com.cim.system.permission.SysPermission;
import com.cim.system.permission.SysPermissionRepository;
import com.cim.system.role.SysRole;
import com.cim.system.role.SysRoleMenu;
import com.cim.system.role.SysRoleMenuRepository;
import com.cim.system.role.SysRolePerm;
import com.cim.system.role.SysRolePermRepository;
import com.cim.system.role.SysRoleRepository;
import com.cim.system.role.SysUserRole;
import com.cim.system.role.SysUserRoleRepository;
import com.cim.system.support.EnableStatus;
import com.cim.system.user.SysUser;
import com.cim.system.user.SysUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC 授权链路集成测试（plan.md M6 里程碑 DoD：用户—角色—权限闭环 + 越权被拒）。
 *
 * <p>覆盖：模块是否真的用自己的 {@code DbLocalAuthorityLoader} 顶掉了认证 starter 的默认
 * {@code ClaimLocalAuthorityLoader}、三级授权解析、{@code is_super} 短路、停用拒绝、
 * 未建档策略、菜单可见性与树构建。</p>
 */
@SpringBootTest(classes = TestApplication.class)
class DbLocalAuthorityLoaderTest {

    /** 各用例用独立编码，避免共享内存库时的相互干扰。 */
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private LocalAuthorityLoader authorityLoader;

    @Autowired
    private SysRbacService rbacService;

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysRolePermRepository rolePermRepository;

    @Autowired
    private SysUserRoleRepository userRoleRepository;

    @Autowired
    private SysRoleMenuRepository roleMenuRepository;

    @Autowired
    private SysMenuRepository menuRepository;

    @Autowired
    private SysPermissionRepository permissionRepository;

    // ---------------------------------------------------------------- 装配

    @Test
    void moduleReplacesDefaultClaimLoaderWithDbLoader() {
        assertThat(authorityLoader)
                .as("cim-system 存在时，认证 starter 的 @ConditionalOnMissingBean 默认实现应让位")
                .isInstanceOf(DbLocalAuthorityLoader.class)
                .isNotInstanceOf(ClaimLocalAuthorityLoader.class);
    }

    // ---------------------------------------------------------------- 三级授权

    @Test
    void roleGrantsPermissionCodes() {
        String suffix = "a" + SEQ.incrementAndGet();
        SysPermission list = permission("mes:order:list", suffix);
        SysPermission save = permission("mes:order:save", suffix);
        SysRole role = role("mes_op_" + suffix, false);
        rolePermRepository.save(SysRolePerm.of(role.getId(), list.getId()));
        rolePermRepository.save(SysRolePerm.of(role.getId(), save.getId()));

        SysUser user = user("alice-" + suffix, "alice-ext-" + suffix);
        userRoleRepository.save(SysUserRole.of(user.getId(), role.getId()));

        Set<String> authorities = authorityLoader.loadAuthorities(
                tokenClaims("alice-ext-" + suffix, "alice-" + suffix, Set.of()));

        assertThat(authorities).containsExactlyInAnyOrder("mes:order:list", "mes:order:save");
    }

    @Test
    void fallsBackToUsernameWhenExternalIdNotRegistered() {
        String suffix = "b" + SEQ.incrementAndGet();
        SysPermission perm = permission("mes:wo:list", suffix);
        SysRole role = role("mes_wo_" + suffix, false);
        rolePermRepository.save(SysRolePerm.of(role.getId(), perm.getId()));

        // 建档只填 username（AD 账号），不填 externalId
        SysUser user = user("bob-" + suffix, null);
        userRoleRepository.save(SysUserRole.of(user.getId(), role.getId()));

        Set<String> authorities = authorityLoader.loadAuthorities(
                tokenClaims("unmatched-external-id", "bob-" + suffix, Set.of()));

        assertThat(authorities).containsExactly("mes:wo:list");
    }

    @Test
    void superRoleExpandsAllEnabledPermissionsAndAddsMarker() {
        String suffix = "c" + SEQ.incrementAndGet();
        SysPermission perm = permission("mes:super:any", suffix);
        SysRole superRole = role("super_" + suffix, true);

        SysUser user = user("root-" + suffix, "root-ext-" + suffix);
        userRoleRepository.save(SysUserRole.of(user.getId(), superRole.getId()));

        Set<String> authorities = authorityLoader.loadAuthorities(
                tokenClaims("root-ext-" + suffix, "root-" + suffix, Set.of()));

        assertThat(authorities)
                .as("超管须同时给出标记（hasPermission 短路）与全量权限码（hasAuthority 也放通）")
                .contains("SUPER_ADMIN")
                .contains(perm.getCode());
        assertThat(rbacService.resolveAuthorities(user.getId()).superRole()).isTrue();
    }

    @Test
    void disabledUserIsDeniedEvenIfRolesAreGranted() {
        String suffix = "d" + SEQ.incrementAndGet();
        SysPermission perm = permission("mes:disabled:any", suffix);
        SysRole role = role("mes_off_" + suffix, false);
        rolePermRepository.save(SysRolePerm.of(role.getId(), perm.getId()));

        SysUser user = user("off-" + suffix, "off-ext-" + suffix);
        user.setStatus(EnableStatus.DISABLED);
        userRepository.save(user);
        userRoleRepository.save(SysUserRole.of(user.getId(), role.getId()));

        // 即便令牌里带了权限 claim，停用用户也必须被拒（停用是明确管理动作）
        Set<String> authorities = authorityLoader.loadAuthorities(
                new TokenClaims("off-ext-" + suffix, "off-" + suffix, Set.of("mds-ap"), Set.of(),
                        "T1", null, Set.of("mes:disabled:any"), 1L, Instant.now().plusSeconds(600), Map.of()));

        assertThat(authorities).isEmpty();
    }

    @Test
    void unprovisionedIdentityDeniedWhenFallbackDisabled() {
        Set<String> authorities = authorityLoader.loadAuthorities(
                tokenClaims("ghost-ext", "ghost", Set.of("mes:whatever:list")));

        assertThat(authorities)
                .as("默认 fallback-to-claims=false：未建档即拒绝，令牌 claim 不被采纳")
                .isEmpty();
    }

    // ------------------------------------------------- 变更即时生效（T6.5 验收）

    /**
     * T6.5 验收「变更即时生效」：{@code DbLocalAuthorityLoader} <b>每请求从库解析、不缓存</b>，
     * 因此授权变更与用户停用在同一枚令牌（{@code ver} 不变）的下一次请求即生效，
     * 无需等令牌过期或重新签发。这是「版本失效机制」之外的即时性保证。
     */
    @Test
    void permissionChangeTakesEffectOnNextRequestWithSameToken() {
        String suffix = "f" + SEQ.incrementAndGet();
        SysRole role = role("mes_live_" + suffix, false);
        SysUser user = user("live-" + suffix, "live-ext-" + suffix);
        userRoleRepository.save(SysUserRole.of(user.getId(), role.getId()));

        // 同一枚令牌（claims 全程不变）
        TokenClaims claims = tokenClaims("live-ext-" + suffix, "live-" + suffix, Set.of());

        // ① 初始：角色尚未授予任何权限 → 无权
        assertThat(authorityLoader.loadAuthorities(claims)).isEmpty();

        // ② 运行期授权（模拟管理端给角色加权限）
        SysPermission perm = permission("mes:live:list", suffix);
        rolePermRepository.save(SysRolePerm.of(role.getId(), perm.getId()));
        assertThat(authorityLoader.loadAuthorities(claims))
                .as("授权后同一令牌下一次请求即生效")
                .containsExactly("mes:live:list");

        // ③ 运行期收权：同样即时
        rolePermRepository.deleteAll(rolePermRepository.findByRoleId(role.getId()));
        assertThat(authorityLoader.loadAuthorities(claims))
                .as("收回权限后立即不再放行")
                .isEmpty();
    }

    @Test
    void userDisableTakesEffectOnNextRequestWithSameToken() {
        String suffix = "g" + SEQ.incrementAndGet();
        SysPermission perm = permission("mes:kick:any", suffix);
        SysRole role = role("mes_kick_" + suffix, false);
        rolePermRepository.save(SysRolePerm.of(role.getId(), perm.getId()));
        SysUser user = user("kick-" + suffix, "kick-ext-" + suffix);
        userRoleRepository.save(SysUserRole.of(user.getId(), role.getId()));

        TokenClaims claims = tokenClaims("kick-ext-" + suffix, "kick-" + suffix, Set.of());
        assertThat(authorityLoader.loadAuthorities(claims)).containsExactly("mes:kick:any");

        // 运行期停用用户 → 同一令牌下一次请求立即被拒（停用是明确管理动作，优先于一切回退策略）
        user.setStatus(EnableStatus.DISABLED);
        userRepository.save(user);
        assertThat(authorityLoader.loadAuthorities(claims))
                .as("停用后无需等令牌过期，立即拒绝")
                .isEmpty();
    }

    // ---------------------------------------------------------------- 菜单

    @Test
    void menuTreeFollowsRoleMenuGrantsAndDropsButtonsWhenAsked() {
        String suffix = "e" + SEQ.incrementAndGet();
        SysMenu root = menu("mes_root_" + suffix, null, MenuType.DIR, 1);
        SysMenu child = menu("mes_child_" + suffix, root.getId(), MenuType.MENU, 2);
        SysMenu button = menu("mes_button_" + suffix, child.getId(), MenuType.BUTTON, 3);
        SysMenu other = menu("mes_hidden_" + suffix, null, MenuType.DIR, 4);

        SysRole role = role("mes_menu_" + suffix, false);
        roleMenuRepository.save(SysRoleMenu.of(role.getId(), root.getId()));
        roleMenuRepository.save(SysRoleMenu.of(role.getId(), child.getId()));
        roleMenuRepository.save(SysRoleMenu.of(role.getId(), button.getId()));
        // other 刻意不授权

        SysUser user = user("viewer-" + suffix, "viewer-ext-" + suffix);
        userRoleRepository.save(SysUserRole.of(user.getId(), role.getId()));

        List<MenuNode> withButtons = rbacService.menuTree(user.getId(), true);
        assertThat(withButtons).hasSize(1);
        MenuNode rootNode = withButtons.get(0);
        assertThat(rootNode.menu().getI18nCode()).isEqualTo(root.getI18nCode());
        assertThat(rootNode.children()).hasSize(1);
        assertThat(rootNode.children().get(0).children()).hasSize(1);
        assertThat(flatCodes(withButtons)).doesNotContain(other.getI18nCode());

        List<MenuNode> withoutButtons = rbacService.menuTree(user.getId(), false);
        assertThat(flatCodes(withoutButtons)).contains(root.getI18nCode(), child.getI18nCode());
        assertThat(flatCodes(withoutButtons)).doesNotContain(button.getI18nCode());
    }

    // ---------------------------------------------------------------- helpers

    private static TokenClaims tokenClaims(String externalId, String username, Set<String> claimAuthorities) {
        return new TokenClaims(externalId, username, Set.of("mds-ap"), Set.of(), "T1", null,
                claimAuthorities, 1L, Instant.now().plusSeconds(600), Map.of());
    }

    private Set<String> flatCodes(List<MenuNode> nodes) {
        Set<String> codes = new java.util.LinkedHashSet<>();
        collect(nodes, codes);
        return codes;
    }

    private void collect(List<MenuNode> nodes, Set<String> sink) {
        for (MenuNode node : nodes) {
            sink.add(node.menu().getI18nCode());
            collect(node.children(), sink);
        }
    }

    private SysPermission permission(String code, String suffix) {
        SysPermission permission = new SysPermission();
        permission.applyCode(code);
        permission.setName("perm-" + suffix);
        return permissionRepository.save(permission);
    }

    private SysRole role(String code, boolean superRole) {
        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(code);
        role.setIsSuper(superRole);
        return roleRepository.save(role);
    }

    private SysUser user(String username, String externalId) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setExternalId(externalId);
        user.setDisplayName(username);
        return userRepository.save(user);
    }

    private SysMenu menu(String i18nCode, String parentId, MenuType type, int sortNo) {
        SysMenu menu = new SysMenu();
        menu.setI18nCode(i18nCode);
        menu.setParentId(parentId);
        menu.setType(type);
        menu.setSortNo(sortNo);
        menu.setPath("/" + i18nCode);
        if (type == MenuType.BUTTON) {
            menu.setPermCode("mes:" + i18nCode + ":x");
        }
        return menuRepository.save(menu);
    }
}
