package com.cim.mds.server.console;

import com.cim.mds.server.security.MdsPermissions;
import com.cim.system.menu.MenuType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MDS 控制台目录（菜单树 / 权限码 / 角色 / 译文）——种子的<b>单一事实源</b>。
 *
 * <p>把「控制台长什么样、有哪些页面、要什么权限、各语言怎么显示」集中在此，由
 * {@link MdsConsoleSeedService} 幂等写入本 ap 的库；改菜单/权限/文案只改本文件，
 * <b>不再改前端代码</b>。与 {@code RmsConsoleCatalog} / {@code IamConsoleCatalog} 同构。</p>
 *
 * <p>与 {@code cim-system} 约定一致：菜单节点只存 {@code i18n_code}，<b>不存中文字面量</b>；
 * 文案在 {@code sys_i18n} 里按语言给（命名空间 {@code mds.*}，与前端内置包对齐）。</p>
 */
public final class MdsConsoleCatalog {

    private MdsConsoleCatalog() {}

    /** 超级管理员角色码。 */
    public static final String ROLE_ADMIN = "MDS_ADMIN";
    /** 操作员角色码（无删除权限）。 */
    public static final String ROLE_OPERATOR = "MDS_OPERATOR";
    /** 只读角色码。 */
    public static final String ROLE_VIEWER = "MDS_VIEWER";

    // ================================================================ 菜单树

    /**
     * 菜单定义。
     *
     * @param key       逻辑键（供父子引用与幂等定位；不落库）
     * @param parentKey 父节点逻辑键（{@code null} 为顶级）
     * @param type      {@code DIR} 目录 / {@code MENU} 菜单 / {@code BUTTON} 按钮
     * @param path      前端路由（{@code DIR}/{@code BUTTON} 为 {@code null}）
     * @param component 前端组件标识（供动态路由映射）
     * @param i18nCode  名称 i18n 键（<b>幂等自然键</b>，全库唯一）
     * @param icon      图标标识（前端图标注册表的键）
     * @param permCode  关联权限码（{@code BUTTON} 必填；{@code MENU} 可空）
     * @param sortNo    同级排序
     */
    public record MenuDef(String key, String parentKey, MenuType type, String path, String component,
                          String i18nCode, String icon, String permCode, int sortNo) {
    }

    /** MDS 控制台菜单树（父先于子；顺序即种子写入顺序）。 */
    public static List<MenuDef> menus() {
        List<MenuDef> m = new ArrayList<>();
        // —— 概览（不绑权限：登录后的落脚页）——
        m.add(menu("overview", null, "/", "Dashboard", "mds.nav.dashboard", "gauge", null, 10));
        // —— 场地主数据（Site → Fab → Area）——
        m.add(dir("grp.site", "mds.nav.group.site", 20));
        m.add(menu("sites", "grp.site", "/sites", "Site", "mds.nav.sites", "apps",
                MdsPermissions.SITE_VIEW, 10));
        m.add(menu("fabs", "grp.site", "/fabs", "Fab", "mds.nav.fabs", "monitor",
                MdsPermissions.FAB_VIEW, 20));
        m.add(menu("areas", "grp.site", "/areas", "Area", "mds.nav.areas", "tree",
                MdsPermissions.AREA_VIEW, 30));

        // 按钮节点：供授权界面按动作细分（前端菜单不渲染 BUTTON）
        m.add(button("btn.site.create", "sites", "mds.nav.sites", MdsPermissions.SITE_CREATE, 10));
        m.add(button("btn.site.update", "sites", "mds.nav.sites", MdsPermissions.SITE_UPDATE, 20));
        m.add(button("btn.site.delete", "sites", "mds.nav.sites", MdsPermissions.SITE_DELETE, 30));
        m.add(button("btn.fab.create", "fabs", "mds.nav.fabs", MdsPermissions.FAB_CREATE, 10));
        m.add(button("btn.fab.update", "fabs", "mds.nav.fabs", MdsPermissions.FAB_UPDATE, 20));
        m.add(button("btn.fab.delete", "fabs", "mds.nav.fabs", MdsPermissions.FAB_DELETE, 30));
        m.add(button("btn.area.create", "areas", "mds.nav.areas", MdsPermissions.AREA_CREATE, 10));
        m.add(button("btn.area.update", "areas", "mds.nav.areas", MdsPermissions.AREA_UPDATE, 20));
        m.add(button("btn.area.delete", "areas", "mds.nav.areas", MdsPermissions.AREA_DELETE, 30));
        return m;
    }

    private static MenuDef menu(String key, String parentKey, String path, String component,
                                String i18nCode, String icon, String permCode, int sortNo) {
        return new MenuDef(key, parentKey, MenuType.MENU, path, component, i18nCode, icon, permCode, sortNo);
    }

    private static MenuDef dir(String key, String i18nCode, int sortNo) {
        return new MenuDef(key, null, MenuType.DIR, null, null, i18nCode, null, null, sortNo);
    }

    /** 按钮节点的 i18n 码复用所属菜单名（{@code sys_menu.i18n_code} 需唯一，故加 {@code .action} 后缀）。 */
    private static MenuDef button(String key, String parentKey, String parentI18n, String permCode, int sortNo) {
        String action = permCode.substring(permCode.lastIndexOf(':') + 1);
        return new MenuDef(key, parentKey, MenuType.BUTTON, null, null,
                parentI18n + ".action." + action, null, permCode, sortNo);
    }

    // ================================================================ 权限

    /** 权限码目录（code 与 {@link MdsPermissions} 对齐）。 */
    public record PermDef(String code, String name) {
    }

    /** MDS 权限码目录。 */
    public static List<PermDef> permissions() {
        List<PermDef> p = new ArrayList<>();
        // 基地
        p.add(new PermDef(MdsPermissions.SITE_VIEW, "基地查看"));
        p.add(new PermDef(MdsPermissions.SITE_CREATE, "基地新建"));
        p.add(new PermDef(MdsPermissions.SITE_UPDATE, "基地编辑"));
        p.add(new PermDef(MdsPermissions.SITE_DELETE, "基地删除"));
        // Fab
        p.add(new PermDef(MdsPermissions.FAB_VIEW, "Fab 查看"));
        p.add(new PermDef(MdsPermissions.FAB_CREATE, "Fab 新建"));
        p.add(new PermDef(MdsPermissions.FAB_UPDATE, "Fab 编辑"));
        p.add(new PermDef(MdsPermissions.FAB_DELETE, "Fab 删除"));
        // Area
        p.add(new PermDef(MdsPermissions.AREA_VIEW, "Area 查看"));
        p.add(new PermDef(MdsPermissions.AREA_CREATE, "Area 新建"));
        p.add(new PermDef(MdsPermissions.AREA_UPDATE, "Area 编辑"));
        p.add(new PermDef(MdsPermissions.AREA_DELETE, "Area 删除"));
        return p;
    }

    // ================================================================ 角色

    /**
     * 角色定义。
     *
     * @param code      角色码
     * @param name      显示名
     * @param superRole 是否超管（平台侧会把超管展开为库内全部权限码）
     * @param perms     显式授予的权限码（超管此处仍给全量，便于授权界面回显）
     */
    public record RoleDef(String code, String name, boolean superRole, List<String> perms) {
    }

    /** MDS 内置角色。 */
    public static List<RoleDef> roles() {
        List<String> allPerms = permissions().stream().map(PermDef::code).toList();
        // 操作员：可维护但不能删（主数据删除的影响面比新建大，默认不给）
        List<String> operator = List.of(
                MdsPermissions.SITE_VIEW, MdsPermissions.SITE_CREATE, MdsPermissions.SITE_UPDATE,
                MdsPermissions.FAB_VIEW, MdsPermissions.FAB_CREATE, MdsPermissions.FAB_UPDATE,
                MdsPermissions.AREA_VIEW, MdsPermissions.AREA_CREATE, MdsPermissions.AREA_UPDATE);
        // 只读：各域 view
        List<String> viewer = List.of(
                MdsPermissions.SITE_VIEW, MdsPermissions.FAB_VIEW, MdsPermissions.AREA_VIEW);
        List<RoleDef> r = new ArrayList<>();
        r.add(new RoleDef(ROLE_ADMIN, "MDS 管理员", true, allPerms));
        r.add(new RoleDef(ROLE_OPERATOR, "MDS 操作员", false, operator));
        r.add(new RoleDef(ROLE_VIEWER, "MDS 只读", false, viewer));
        return r;
    }

    // ================================================================ 译文

    /**
     * 译文定义（双语成对，便于人工校对时对照）。
     *
     * @param code 键（与菜单 i18n_code / 前端 {@code t(key)} 对齐）
     * @param zh   简体中文（<b>强制兜底，必填</b>）
     * @param en   英文
     */
    public record TextDef(String code, String zh, String en) {
    }

    /** MDS 控制台文案（菜单名 / 分组名 / 按钮动作 / 外壳文案）。 */
    public static List<TextDef> texts() {
        List<TextDef> t = new ArrayList<>();
        // —— 菜单与分组 ——
        t.add(new TextDef("mds.nav.dashboard", "概览", "Overview"));
        t.add(new TextDef("mds.nav.group.site", "场地主数据", "Site master data"));
        t.add(new TextDef("mds.nav.sites", "基地", "Sites"));
        t.add(new TextDef("mds.nav.fabs", "晶圆厂", "Fabs"));
        t.add(new TextDef("mds.nav.areas", "区域", "Areas"));
        // —— 按钮动作（授权界面回显用）——
        t.add(new TextDef("mds.nav.sites.action.create", "基地·新建", "Site · create"));
        t.add(new TextDef("mds.nav.sites.action.update", "基地·编辑", "Site · update"));
        t.add(new TextDef("mds.nav.sites.action.delete", "基地·删除", "Site · delete"));
        t.add(new TextDef("mds.nav.fabs.action.create", "Fab·新建", "Fab · create"));
        t.add(new TextDef("mds.nav.fabs.action.update", "Fab·编辑", "Fab · update"));
        t.add(new TextDef("mds.nav.fabs.action.delete", "Fab·删除", "Fab · delete"));
        t.add(new TextDef("mds.nav.areas.action.create", "区域·新建", "Area · create"));
        t.add(new TextDef("mds.nav.areas.action.update", "区域·编辑", "Area · update"));
        t.add(new TextDef("mds.nav.areas.action.delete", "区域·删除", "Area · delete"));
        // —— 外壳 ——
        t.add(new TextDef("mds.shell.brandSub", "主数据服务", "Master Data Service"));
        t.add(new TextDef("mds.shell.tagline", "定义向下、实绩向上", "Definitions down, actuals up"));
        t.add(new TextDef("mds.shell.envDev", "开发环境", "Development"));
        return t;
    }

    /** 简体中文译文包（key → 中文）。 */
    public static Map<String, String> zhMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (TextDef t : texts()) {
            m.put(t.code(), t.zh());
        }
        return m;
    }

    /** 英文译文包（key → 英文）。 */
    public static Map<String, String> enMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (TextDef t : texts()) {
            m.put(t.code(), t.en());
        }
        return m;
    }
}
