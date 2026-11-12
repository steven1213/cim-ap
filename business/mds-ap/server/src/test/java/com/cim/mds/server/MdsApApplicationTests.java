package com.cim.mds.server;

import com.cim.mds.server.console.MdsConsoleCatalog;
import com.cim.mds.server.security.MdsPermissions;
import com.cim.system.menu.SysMenu;
import com.cim.system.menu.SysMenuRepository;
import com.cim.system.permission.SysPermissionRepository;
import com.cim.system.role.SysRoleRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MDS 上下文装配 + 控制台种子验证。
 *
 * <p>断言的是「种子的效果」而不是「种子跑过」：权限码真的落库、超管角色真的建出来、
 * 菜单真的按父子关系挂上——这些缺一个，前端菜单或后端 {@code @PreAuthorize} 就会静默失效
 * （有按钮点下去 403，或菜单空着）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-context-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        // 关认证链：否则需要 JWT 与 IAM JWKS；同时 MdsSecurityConfig 也不装配
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        // 关实时推送：notifyFanoutContainer 在上下文启动阶段就同步连 Redis，测试机不保证有
        "cim.notify.enabled=false"
})
class MdsApApplicationTests {

    @Autowired
    SysPermissionRepository permissionRepository;
    @Autowired
    SysMenuRepository menuRepository;
    @Autowired
    SysRoleRepository roleRepository;

    @Test
    void contextLoads() {
        assertThat(permissionRepository).isNotNull();
    }

    @Test
    void allPermissionCodesSeeded() {
        for (String code : MdsPermissions.all()) {
            assertThat(permissionRepository.findFirstByCodeOrderByCreateTimeAsc(code))
                    .as("权限码 %s 应已入库（否则 @PreAuthorize 永远 403）", code)
                    .isPresent();
        }
    }

    @Test
    void builtinRolesSeededWithSuperFlag() {
        var admin = roleRepository.findFirstByCodeOrderByCreateTimeAsc(MdsConsoleCatalog.ROLE_ADMIN);
        assertThat(admin).isPresent();
        assertThat(admin.get().getIsSuper()).isTrue();

        var viewer = roleRepository.findFirstByCodeOrderByCreateTimeAsc(MdsConsoleCatalog.ROLE_VIEWER);
        assertThat(viewer).isPresent();
        assertThat(viewer.get().getIsSuper()).isFalse();
    }

    @Test
    void menuTreeSeededAndButtonsHidden() {
        Map<String, SysMenu> byI18n = menuRepository.findByOrderBySortNoAsc().stream()
                .filter(m -> m.getI18nCode() != null)
                .collect(Collectors.toMap(SysMenu::getI18nCode, Function.identity(), (a, b) -> a));

        assertThat(byI18n).containsKeys("mds.nav.dashboard", "mds.nav.group.site",
                "mds.nav.sites", "mds.nav.fabs", "mds.nav.areas");

        SysMenu group = byI18n.get("mds.nav.group.site");
        SysMenu sites = byI18n.get("mds.nav.sites");
        assertThat(sites.getParentId()).isEqualTo(group.getId());
        // 按钮节点只用于授权细分，不应出现在导航里
        assertThat(byI18n.get("mds.nav.sites.action.create").getVisible()).isFalse();
    }
}
