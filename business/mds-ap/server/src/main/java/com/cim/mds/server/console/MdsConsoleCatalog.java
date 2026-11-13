package com.cim.mds.server.console;

import com.cim.mds.server.security.MdsPermissions;
import com.cim.system.menu.MenuType;
import com.cim.system.support.PermissionCodes;

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
        // —— 场地主数据（Site → Fab → Area → Bay → Stage）——
        // 2026-11-13 收敛为一页「位置拓扑」（左树右详情），原 sites/fabs/areas 三菜单退场
        m.add(dir("grp.site", "mds.nav.group.site", 20));
        m.add(menu("topology", "grp.site", "/topology", "Topology", "mds.nav.topology", "tree", null, 10));

        // 按钮节点：供授权界面按动作细分（前端菜单不渲染 BUTTON）
        // i18n 码须全库唯一，单菜单下五层动作显式命名 {layer}.{action}
        m.add(button("btn.site.create", "topology", "mds.nav.topology.action.site.create",
                MdsPermissions.SITE_CREATE, 10));
        m.add(button("btn.site.update", "topology", "mds.nav.topology.action.site.update",
                MdsPermissions.SITE_UPDATE, 20));
        m.add(button("btn.site.delete", "topology", "mds.nav.topology.action.site.delete",
                MdsPermissions.SITE_DELETE, 30));
        m.add(button("btn.fab.create", "topology", "mds.nav.topology.action.fab.create",
                MdsPermissions.FAB_CREATE, 40));
        m.add(button("btn.fab.update", "topology", "mds.nav.topology.action.fab.update",
                MdsPermissions.FAB_UPDATE, 50));
        m.add(button("btn.fab.delete", "topology", "mds.nav.topology.action.fab.delete",
                MdsPermissions.FAB_DELETE, 60));
        m.add(button("btn.area.create", "topology", "mds.nav.topology.action.area.create",
                MdsPermissions.AREA_CREATE, 70));
        m.add(button("btn.area.update", "topology", "mds.nav.topology.action.area.update",
                MdsPermissions.AREA_UPDATE, 80));
        m.add(button("btn.area.delete", "topology", "mds.nav.topology.action.area.delete",
                MdsPermissions.AREA_DELETE, 90));
        m.add(button("btn.bay.create", "topology", "mds.nav.topology.action.bay.create",
                MdsPermissions.BAY_CREATE, 100));
        m.add(button("btn.bay.update", "topology", "mds.nav.topology.action.bay.update",
                MdsPermissions.BAY_UPDATE, 110));
        m.add(button("btn.bay.delete", "topology", "mds.nav.topology.action.bay.delete",
                MdsPermissions.BAY_DELETE, 120));
        m.add(button("btn.stage.create", "topology", "mds.nav.topology.action.stage.create",
                MdsPermissions.STAGE_CREATE, 130));
        m.add(button("btn.stage.update", "topology", "mds.nav.topology.action.stage.update",
                MdsPermissions.STAGE_UPDATE, 140));
        m.add(button("btn.stage.delete", "topology", "mds.nav.topology.action.stage.delete",
                MdsPermissions.STAGE_DELETE, 150));

        // —— 产品管理（Technology 先行；Product / Route 预留）——
        m.add(dir("grp.product", "mds.nav.group.product", 30));
        m.add(menu("technology", "grp.product", "/technologies", "Technologies",
                "mds.nav.technology", "sliders", null, 10));
        m.add(button("btn.technology.create", "technology", "mds.nav.technology.action.create",
                MdsPermissions.TECH_CREATE, 10));
        m.add(button("btn.technology.update", "technology", "mds.nav.technology.action.update",
                MdsPermissions.TECH_UPDATE, 20));
        m.add(button("btn.technology.delete", "technology", "mds.nav.technology.action.delete",
                MdsPermissions.TECH_DELETE, 30));
        m.add(menu("products", "grp.product", "/products", "Products",
                "mds.nav.products", "flask", null, 20));
        m.add(button("btn.product.create", "products", "mds.nav.products.action.create",
                MdsPermissions.PRODUCT_CREATE, 10));
        m.add(button("btn.product.update", "products", "mds.nav.products.action.update",
                MdsPermissions.PRODUCT_UPDATE, 20));
        m.add(button("btn.product.delete", "products", "mds.nav.products.action.delete",
                MdsPermissions.PRODUCT_DELETE, 30));
        // —— 数据字典（平台 sys_dict，系统级参照数据；挂「系统管理」组，与业务域分组分开）——
        // 权限码沿用平台 sys:dict:*（@PreAuthorize 在 cim-system 的字典服务上，另立编码只会 403）
        m.add(dir("grp.system", "mds.nav.group.system", 40));
        m.add(menu("dicts", "grp.system", "/dicts", "Dicts",
                "mds.nav.dicts", "list", null, 10));
        m.add(button("btn.dict.save", "dicts", "mds.nav.dicts.action.save",
                PermissionCodes.DICT_SAVE, 10));
        m.add(button("btn.dict.delete", "dicts", "mds.nav.dicts.action.delete",
                PermissionCodes.DICT_REMOVE, 20));
        return m;
    }

    private static MenuDef menu(String key, String parentKey, String path, String component,
                                String i18nCode, String icon, String permCode, int sortNo) {
        return new MenuDef(key, parentKey, MenuType.MENU, path, component, i18nCode, icon, permCode, sortNo);
    }

    private static MenuDef dir(String key, String i18nCode, int sortNo) {
        return new MenuDef(key, null, MenuType.DIR, null, null, i18nCode, null, null, sortNo);
    }

    /** 按钮节点：单菜单承载五层动作，i18n 码显式传入（sys_menu.i18n_code 全库唯一）。 */
    private static MenuDef button(String key, String parentKey, String i18nCode, String permCode, int sortNo) {
        return new MenuDef(key, parentKey, MenuType.BUTTON, null, null, i18nCode, null, permCode, sortNo);
    }

    // ================================================================ 权限

    /** 权限码目录（code 与 {@link MdsPermissions} 对齐）。 */
    public record PermDef(String code, String name) {
    }

    /** MDS 权限码目录。 */
    public static List<PermDef> permissions() {
        List<PermDef> p = new ArrayList<>();
        // 厂区
        p.add(new PermDef(MdsPermissions.SITE_VIEW, "厂区查看"));
        p.add(new PermDef(MdsPermissions.SITE_CREATE, "厂区新建"));
        p.add(new PermDef(MdsPermissions.SITE_UPDATE, "厂区编辑"));
        p.add(new PermDef(MdsPermissions.SITE_DELETE, "厂区删除"));
        // Fab
        p.add(new PermDef(MdsPermissions.FAB_VIEW, "晶圆厂查看"));
        p.add(new PermDef(MdsPermissions.FAB_CREATE, "晶圆厂新建"));
        p.add(new PermDef(MdsPermissions.FAB_UPDATE, "晶圆厂编辑"));
        p.add(new PermDef(MdsPermissions.FAB_DELETE, "晶圆厂删除"));
        // Area
        p.add(new PermDef(MdsPermissions.AREA_VIEW, "Area 查看"));
        p.add(new PermDef(MdsPermissions.AREA_CREATE, "Area 新建"));
        p.add(new PermDef(MdsPermissions.AREA_UPDATE, "Area 编辑"));
        p.add(new PermDef(MdsPermissions.AREA_DELETE, "Area 删除"));
        // Bay
        p.add(new PermDef(MdsPermissions.BAY_VIEW, "Bay 查看"));
        p.add(new PermDef(MdsPermissions.BAY_CREATE, "Bay 新建"));
        p.add(new PermDef(MdsPermissions.BAY_UPDATE, "Bay 编辑"));
        p.add(new PermDef(MdsPermissions.BAY_DELETE, "Bay 删除"));
        // Stage
        p.add(new PermDef(MdsPermissions.STAGE_VIEW, "Stage 查看"));
        p.add(new PermDef(MdsPermissions.STAGE_CREATE, "Stage 新建"));
        p.add(new PermDef(MdsPermissions.STAGE_UPDATE, "Stage 编辑"));
        p.add(new PermDef(MdsPermissions.STAGE_DELETE, "Stage 删除"));
        // Technology（产品管理域）
        p.add(new PermDef(MdsPermissions.TECH_VIEW, "制程技术查看"));
        p.add(new PermDef(MdsPermissions.TECH_CREATE, "制程技术新建"));
        p.add(new PermDef(MdsPermissions.TECH_UPDATE, "制程技术编辑"));
        p.add(new PermDef(MdsPermissions.TECH_DELETE, "制程技术删除"));
        // Product（产品管理域）
        p.add(new PermDef(MdsPermissions.PRODUCT_VIEW, "产品查看"));
        p.add(new PermDef(MdsPermissions.PRODUCT_CREATE, "产品新建"));
        p.add(new PermDef(MdsPermissions.PRODUCT_UPDATE, "产品编辑"));
        p.add(new PermDef(MdsPermissions.PRODUCT_DELETE, "产品删除"));
        // 数据字典（平台域，码源 cim-system PermissionCodes）
        p.add(new PermDef(PermissionCodes.DICT_LIST, "数据字典查看"));
        p.add(new PermDef(PermissionCodes.DICT_SAVE, "数据字典维护"));
        p.add(new PermDef(PermissionCodes.DICT_REMOVE, "数据字典删除"));
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
                MdsPermissions.AREA_VIEW, MdsPermissions.AREA_CREATE, MdsPermissions.AREA_UPDATE,
                MdsPermissions.BAY_VIEW, MdsPermissions.BAY_CREATE, MdsPermissions.BAY_UPDATE,
                MdsPermissions.STAGE_VIEW, MdsPermissions.STAGE_CREATE, MdsPermissions.STAGE_UPDATE,
                MdsPermissions.TECH_VIEW, MdsPermissions.TECH_CREATE, MdsPermissions.TECH_UPDATE,
                MdsPermissions.PRODUCT_VIEW, MdsPermissions.PRODUCT_CREATE, MdsPermissions.PRODUCT_UPDATE,
                PermissionCodes.DICT_LIST, PermissionCodes.DICT_SAVE);
        // 只读：各域 view
        List<String> viewer = List.of(
                MdsPermissions.SITE_VIEW, MdsPermissions.FAB_VIEW, MdsPermissions.AREA_VIEW,
                MdsPermissions.BAY_VIEW, MdsPermissions.STAGE_VIEW, MdsPermissions.TECH_VIEW,
                MdsPermissions.PRODUCT_VIEW, PermissionCodes.DICT_LIST);
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
        t.add(new TextDef("mds.nav.topology", "位置拓扑", "Locations"));
        // —— 按钮动作（授权界面回显用；单菜单五层，显式命名）——
        t.add(new TextDef("mds.nav.topology.action.site.create", "厂区·新建", "Site · create"));
        t.add(new TextDef("mds.nav.topology.action.site.update", "厂区·编辑", "Site · update"));
        t.add(new TextDef("mds.nav.topology.action.site.delete", "厂区·删除", "Site · delete"));
        t.add(new TextDef("mds.nav.topology.action.fab.create", "Fab·新建", "Fab · create"));
        t.add(new TextDef("mds.nav.topology.action.fab.update", "Fab·编辑", "Fab · update"));
        t.add(new TextDef("mds.nav.topology.action.fab.delete", "Fab·删除", "Fab · delete"));
        t.add(new TextDef("mds.nav.topology.action.area.create", "区域·新建", "Area · create"));
        t.add(new TextDef("mds.nav.topology.action.area.update", "区域·编辑", "Area · update"));
        t.add(new TextDef("mds.nav.topology.action.area.delete", "区域·删除", "Area · delete"));
        t.add(new TextDef("mds.nav.topology.action.bay.create", "Bay·新建", "Bay · create"));
        t.add(new TextDef("mds.nav.topology.action.bay.update", "Bay·编辑", "Bay · update"));
        t.add(new TextDef("mds.nav.topology.action.bay.delete", "Bay·删除", "Bay · delete"));
        t.add(new TextDef("mds.nav.topology.action.stage.create", "Stage·新建", "Stage · create"));
        t.add(new TextDef("mds.nav.topology.action.stage.update", "Stage·编辑", "Stage · update"));
        t.add(new TextDef("mds.nav.topology.action.stage.delete", "Stage·删除", "Stage · delete"));
        // —— 产品管理 ——
        t.add(new TextDef("mds.nav.group.product", "产品管理", "Products"));
        t.add(new TextDef("mds.nav.group.system", "系统管理", "System"));
        t.add(new TextDef("mds.nav.technology", "制程技术", "Technologies"));
        t.add(new TextDef("mds.nav.technology.action.create", "制程技术·新建", "Technology · create"));
        t.add(new TextDef("mds.nav.technology.action.update", "制程技术·编辑", "Technology · update"));
        t.add(new TextDef("mds.nav.technology.action.delete", "制程技术·删除", "Technology · delete"));
        t.add(new TextDef("mds.nav.products", "产品", "Products"));
        t.add(new TextDef("mds.nav.products.action.create", "产品·新建", "Product · create"));
        t.add(new TextDef("mds.nav.products.action.update", "产品·编辑", "Product · update"));
        t.add(new TextDef("mds.nav.products.action.delete", "产品·删除", "Product · delete"));
        // —— 数据字典 ——
        t.add(new TextDef("mds.nav.dicts", "数据字典", "Data dictionaries"));
        t.add(new TextDef("mds.nav.dicts.action.save", "数据字典·维护", "Dictionary · save"));
        t.add(new TextDef("mds.nav.dicts.action.delete", "数据字典·删除", "Dictionary · delete"));
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
