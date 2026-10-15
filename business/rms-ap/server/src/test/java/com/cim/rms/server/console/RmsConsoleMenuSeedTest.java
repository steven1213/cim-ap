package com.cim.rms.server.console;

import com.cim.i18n.model.SysI18n;
import com.cim.i18n.model.SysI18nRepository;
import com.cim.i18n.model.SysLocaleRepository;
import com.cim.system.menu.MenuType;
import com.cim.system.menu.SysMenu;
import com.cim.system.menu.SysMenuRepository;
import com.cim.system.role.SysRole;
import com.cim.system.role.SysRoleMenuRepository;
import com.cim.system.role.SysRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RMS 控制台「菜单树 + 多语言译文」种子验证（与 IAM 同构的配置化控制面）。
 *
 * <p>断言 RMS <b>自己的库</b>里确有：{@code sys_menu} 菜单树（i18n_code 命名空间 {@code rms.nav.*}）、
 * {@code sys_i18n} 中/英译文、{@code sys_role_menu} 菜单可见性授权。这是「与 iam 一致的多语言菜单」
 * 在 RMS 侧落地的端到端证据。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rms-console-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        // 关闭 cim 认证链后，Spring Boot 缺省安全链会顶上（401/403）→ 一并排除
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
class RmsConsoleMenuSeedTest {

    @Autowired
    SysMenuRepository menuRepository;
    @Autowired
    SysI18nRepository i18nRepository;
    @Autowired
    SysLocaleRepository localeRepository;
    @Autowired
    SysRoleRepository roleRepository;
    @Autowired
    SysRoleMenuRepository roleMenuRepository;

    @Test
    void menuTree_seeded_withRmsI18nCodes() {
        List<SysMenu> menus = menuRepository.findByOrderBySortNoAsc();
        Set<String> codes = menus.stream().map(SysMenu::getI18nCode).collect(Collectors.toSet());

        // 8 个导航菜单 + 3 个分组目录，全部以 rms.nav.* 命名
        assertThat(codes).contains(
                "rms.nav.dashboard",
                "rms.nav.recipes", "rms.nav.deviceTypes", "rms.nav.deviceAreas", "rms.nav.devices",
                "rms.nav.signoff", "rms.nav.compare", "rms.nav.audit",
                "rms.nav.group.recipe", "rms.nav.group.device", "rms.nav.group.advanced");

        // 分组为 DIR 目录节点
        assertThat(byI18n(menus, "rms.nav.group.recipe").getType()).isEqualTo(MenuType.DIR);
        // 配方库为 MENU，且绑定 rms:recipe:view
        SysMenu recipes = byI18n(menus, "rms.nav.recipes");
        assertThat(recipes.getType()).isEqualTo(MenuType.MENU);
        assertThat(recipes.getPermCode()).isEqualTo("rms:recipe:view");
        assertThat(recipes.getPath()).isEqualTo("/recipes");

        // 父子挂载：配方库挂在「配方管理」目录下（父 ID 由运行期 IdGenerator 生成，测试据此校验关系）
        SysMenu groupRecipe = byI18n(menus, "rms.nav.group.recipe");
        assertThat(recipes.getParentId()).isEqualTo(groupRecipe.getId());

        // 概览为顶级（无父）
        assertThat(byI18n(menus, "rms.nav.dashboard").getParentId()).isNull();
    }

    @Test
    void translations_seeded_forZhAndEn() {
        // 语言目录已建，zh-CN 为默认兜底
        assertThat(localeRepository.findByCode("zh-CN")).isPresent();
        assertThat(localeRepository.findByCode("en-US")).isPresent();

        // 本 ap 的译文按 module=rms 落库（zh + en）
        assertThat(i18nRepository.findByLocaleCodeAndModule("zh-CN", "rms")).isNotEmpty();
        assertThat(i18nRepository.findByLocaleCodeAndModule("en-US", "rms")).isNotEmpty();

        // 抽样校验中文/英文键值（菜单名）
        assertThat(content("zh-CN", "rms.nav.dashboard")).isEqualTo("概览");
        assertThat(content("zh-CN", "rms.nav.recipes")).isEqualTo("配方库");
        assertThat(content("zh-CN", "rms.nav.group.device")).isEqualTo("设备管理");
        assertThat(content("en-US", "rms.nav.dashboard")).isEqualTo("Overview");
        assertThat(content("en-US", "rms.nav.recipes")).isEqualTo("Recipes");
    }

    @Test
    void adminRole_hasMenuVisibilityGrants() {
        SysRole admin = roleRepository.findFirstByCodeOrderByCreateTimeAsc(RmsConsoleCatalog.ROLE_ADMIN)
                .orElseThrow();
        // 超管须拥有全部导航菜单可见性（含全量 11 个节点）
        assertThat(roleMenuRepository.findByRoleId(admin.getId())).hasSize(RmsConsoleCatalog.menus().size());
    }

    private static SysMenu byI18n(List<SysMenu> menus, String code) {
        return menus.stream()
                .filter(m -> code.equals(m.getI18nCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("菜单缺失：" + code));
    }

    private String content(String locale, String code) {
        Optional<SysI18n> row = i18nRepository.findByLocaleCodeAndCode(locale, code);
        assertThat(row).as("译文缺失 %s/%s", locale, code).isPresent();
        return row.get().getContent();
    }
}
