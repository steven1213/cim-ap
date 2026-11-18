package com.cim.approval.server.console;

import com.cim.approval.server.security.ApprovalPermissions;
import com.cim.system.menu.MenuType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批平台控制台目录（菜单树 / 权限码 / 角色 / 译文）——种子的<b>单一事实源</b>。
 *
 * <p>与 MDS（{@code MdsConsoleCatalog}）/ RMS 同构：菜单节点只存 {@code i18n_code} 不存中文，
 * 文案在 {@code sys_i18n} 里按语言给（键与前端内置包 {@code lib/i18n/messages.ts} 对齐）。
 * IA 与前端 navConfig 对齐：工作台 + 审批中心 + 流程配置 + 合规治理 + 系统配置六页。</p>
 */
public final class ApprovalConsoleCatalog {

    private ApprovalConsoleCatalog() {}

    // ================================================================ 菜单树

    /** 菜单定义（与 MDS/RMS 同构，见彼处 javadoc）。 */
    public record MenuDef(String key, String parentKey, MenuType type, String path, String component,
                          String i18nCode, String icon, String permCode, int sortNo) {
    }

    /** 审批平台控制台菜单树（父先于子）。 */
    public static List<MenuDef> menus() {
        List<MenuDef> m = new ArrayList<>();
        // —— 工作台（不绑权限：登录后的落脚页）——
        m.add(dir("grp.portal", "nav.group.portal", 10));
        m.add(menu("dashboard", "grp.portal", "/", "DashboardPage", "nav.dashboard", "gauge", null, 10));

        // —— 审批中心（办事的人看这里）——
        m.add(dir("grp.approval", "nav.group.approval", 20));
        m.add(menu("todo", "grp.approval", "/todo", "TodoPage", "nav.todo", "inbox", null, 10));
        m.add(menu("done", "grp.approval", "/done", "DonePage", "nav.done", "check", null, 20));
        m.add(menu("initiated", "grp.approval", "/initiated", "InitiatedPage", "nav.initiated", "list", null, 30));

        // —— 流程配置（配置系统的人看这里）——
        m.add(dir("grp.process", "nav.group.process", 30));
        m.add(menu("definitions", "grp.process", "/definitions", "DefinitionsPage", "nav.definitions",
                "branch", ApprovalPermissions.DEFINITION_MANAGE, 10));
        m.add(menu("designer", "grp.process", "/designer", "DesignerPage", "nav.designer",
                "sliders", ApprovalPermissions.DEFINITION_MANAGE, 20));
        m.add(menu("callbacks", "grp.process", "/callbacks", "CallbacksPage", "nav.callbacks",
                "link", ApprovalPermissions.CALLBACK_MANAGE, 30));

        // —— 合规治理（审计看这里）——
        m.add(dir("grp.governance", "nav.group.governance", 40));
        m.add(menu("audit", "grp.governance", "/audit", "AuditPage", "nav.audit",
                "shield", ApprovalPermissions.AUDIT_VIEW, 10));

        // —— 系统配置（六页共享组件 @cim/admin；菜单/权限/角色/多语言走平台端点，
        //    组织/人员只读——主数据在 IAM，经本系统代理取目录 API）——
        m.add(dir("grp.settings", "nav.group.settings", 50));
        m.add(menu("menuAdmin", "grp.settings", "/menus", "MenusAdminPage",
                "nav.menus", "layers", ApprovalPermissions.SYS_MENU_LIST, 10));
        m.add(button("btn.menu.save", "menuAdmin", "nav.menus.action.save",
                ApprovalPermissions.SYS_MENU_SAVE, 10));
        m.add(button("btn.menu.delete", "menuAdmin", "nav.menus.action.delete",
                ApprovalPermissions.SYS_MENU_REMOVE, 20));
        m.add(button("btn.menu.grant", "menuAdmin", "nav.menus.action.grant",
                ApprovalPermissions.SYS_ROLE_GRANT, 30));
        m.add(menu("permissionsAdmin", "grp.settings", "/permissions", "PermissionsAdminPage",
                "nav.permissions", "shield", ApprovalPermissions.SYS_PERM_LIST, 20));
        m.add(button("btn.permission.save", "permissionsAdmin", "nav.permissions.action.save",
                ApprovalPermissions.SYS_PERM_SAVE, 10));
        m.add(button("btn.permission.delete", "permissionsAdmin", "nav.permissions.action.delete",
                ApprovalPermissions.SYS_PERM_REMOVE, 20));
        m.add(menu("rolesAdmin", "grp.settings", "/roles-admin", "RolesAdminPage",
                "nav.rolesAdmin", "users", ApprovalPermissions.SYS_ROLE_LIST, 30));
        m.add(button("btn.role.save", "rolesAdmin", "nav.rolesAdmin.action.save",
                ApprovalPermissions.SYS_ROLE_SAVE, 10));
        m.add(button("btn.role.delete", "rolesAdmin", "nav.rolesAdmin.action.delete",
                ApprovalPermissions.SYS_ROLE_REMOVE, 20));
        m.add(button("btn.role.grant", "rolesAdmin", "nav.rolesAdmin.action.grant",
                ApprovalPermissions.SYS_ROLE_GRANT, 30));
        m.add(menu("i18nAdmin", "grp.settings", "/i18n", "I18nAdminPage",
                "nav.i18n", "check", ApprovalPermissions.SYS_I18N_LIST, 40));
        m.add(button("btn.i18n.save", "i18nAdmin", "nav.i18n.action.save",
                ApprovalPermissions.SYS_I18N_SAVE, 10));
        m.add(button("btn.i18n.delete", "i18nAdmin", "nav.i18n.action.delete",
                ApprovalPermissions.SYS_I18N_REMOVE, 20));
        m.add(button("btn.i18n.locale", "i18nAdmin", "nav.i18n.action.locale",
                ApprovalPermissions.SYS_I18N_LOCALE, 30));
        // 组织/人员：只读（无 BUTTON 节点），入口码 approval:org:view / approval:user:view
        m.add(menu("orgs", "grp.settings", "/orgs", "OrgPage",
                "nav.orgs", "tree", ApprovalPermissions.ORG_VIEW, 50));
        m.add(menu("users", "grp.settings", "/users", "UsersPage",
                "nav.users", "user", ApprovalPermissions.USER_VIEW, 60));
        return m;
    }

    private static MenuDef menu(String key, String parentKey, String path, String component,
                                String i18nCode, String icon, String permCode, int sortNo) {
        return new MenuDef(key, parentKey, MenuType.MENU, path, component, i18nCode, icon, permCode, sortNo);
    }

    private static MenuDef dir(String key, String i18nCode, int sortNo) {
        return new MenuDef(key, null, MenuType.DIR, null, null, i18nCode, null, null, sortNo);
    }

    /** 按钮节点：单菜单承载动作，i18n 码显式传入（sys_menu.i18n_code 全库唯一）。 */
    private static MenuDef button(String key, String parentKey, String i18nCode, String permCode, int sortNo) {
        return new MenuDef(key, parentKey, MenuType.BUTTON, null, null, i18nCode, null, permCode, sortNo);
    }

    // ================================================================ 权限

    /** 权限码目录（code 与 {@link ApprovalPermissions} 对齐）。 */
    public record PermDef(String code, String name) {
    }

    /** 审批平台权限码目录。 */
    public static List<PermDef> permissions() {
        List<PermDef> p = new ArrayList<>();
        // 审批域
        p.add(new PermDef(ApprovalPermissions.DEFINITION_MANAGE, "流程定义管理"));
        p.add(new PermDef(ApprovalPermissions.CALLBACK_MANAGE, "回调配置管理"));
        p.add(new PermDef(ApprovalPermissions.AUDIT_VIEW, "审计查看"));
        p.add(new PermDef(ApprovalPermissions.INSTANCE_ADMIN, "实例管理"));
        // 系统配置（平台域）
        p.add(new PermDef(ApprovalPermissions.SYS_MENU_LIST, "菜单查看"));
        p.add(new PermDef(ApprovalPermissions.SYS_MENU_SAVE, "菜单维护"));
        p.add(new PermDef(ApprovalPermissions.SYS_MENU_REMOVE, "菜单删除"));
        p.add(new PermDef(ApprovalPermissions.SYS_ROLE_LIST, "角色查看"));
        p.add(new PermDef(ApprovalPermissions.SYS_ROLE_SAVE, "角色维护"));
        p.add(new PermDef(ApprovalPermissions.SYS_ROLE_REMOVE, "角色删除"));
        p.add(new PermDef(ApprovalPermissions.SYS_ROLE_GRANT, "角色授权"));
        p.add(new PermDef(ApprovalPermissions.SYS_PERM_LIST, "权限查看"));
        p.add(new PermDef(ApprovalPermissions.SYS_PERM_SAVE, "权限维护"));
        p.add(new PermDef(ApprovalPermissions.SYS_PERM_REMOVE, "权限删除"));
        p.add(new PermDef(ApprovalPermissions.SYS_I18N_LIST, "译文查看"));
        p.add(new PermDef(ApprovalPermissions.SYS_I18N_SAVE, "译文维护"));
        p.add(new PermDef(ApprovalPermissions.SYS_I18N_REMOVE, "译文删除"));
        p.add(new PermDef(ApprovalPermissions.SYS_I18N_LOCALE, "语言目录维护"));
        p.add(new PermDef(ApprovalPermissions.ORG_VIEW, "组织目录查看（只读，主数据在 IAM）"));
        p.add(new PermDef(ApprovalPermissions.USER_VIEW, "人员目录查看（只读，主数据在 IAM）"));
        return p;
    }

    // ================================================================ 角色

    /** 审批平台管理员角色码（超管：短路展开为库内全部启用权限码）。 */
    public static final String ROLE_ADMIN = "APPROVAL_ADMIN";

    /** 角色定义。 */
    public record RoleDef(String code, String name, boolean superRole, List<String> perms) {
    }

    /** 审批平台内置角色。 */
    public static List<RoleDef> roles() {
        List<String> allPerms = permissions().stream().map(PermDef::code).toList();
        List<RoleDef> r = new ArrayList<>();
        r.add(new RoleDef(ROLE_ADMIN, "审批平台管理员", true, allPerms));
        return r;
    }

    // ================================================================ 译文

    /** 译文定义（双语成对，便于人工校对时对照）。 */
    public record TextDef(String code, String zh, String en) {
    }

    /** 审批平台控制台文案（菜单名 / 分组名 / 系统配置组）。 */
    public static List<TextDef> texts() {
        List<TextDef> t = new ArrayList<>();
        // —— 菜单与分组（与前端内置包 nav.* 对齐）——
        t.add(new TextDef("nav.group.portal", "工作台", "Workbench"));
        t.add(new TextDef("nav.dashboard", "工作台", "Dashboard"));
        t.add(new TextDef("nav.group.approval", "审批中心", "My Approvals"));
        t.add(new TextDef("nav.todo", "我的待办", "My Todo"));
        t.add(new TextDef("nav.done", "已办事项", "Done"));
        t.add(new TextDef("nav.initiated", "我发起的", "Initiated"));
        t.add(new TextDef("nav.group.process", "流程配置", "Process Config"));
        t.add(new TextDef("nav.definitions", "流程模板", "Process Templates"));
        t.add(new TextDef("nav.designer", "流程设计器", "Designer"));
        t.add(new TextDef("nav.callbacks", "回调管理", "Callbacks"));
        t.add(new TextDef("nav.group.governance", "合规治理", "Governance"));
        t.add(new TextDef("nav.audit", "审计追溯", "Audit Trail"));
        // —— 系统配置组（六页共享组件 @cim/admin）——
        t.add(new TextDef("nav.group.settings", "系统配置", "System settings"));
        t.add(new TextDef("nav.menus", "菜单管理", "Menus"));
        t.add(new TextDef("nav.menus.action.save", "菜单·维护", "Menu · save"));
        t.add(new TextDef("nav.menus.action.delete", "菜单·删除", "Menu · delete"));
        t.add(new TextDef("nav.menus.action.grant", "菜单·授权", "Menu · grant"));
        t.add(new TextDef("nav.permissions", "权限管理", "Permissions"));
        t.add(new TextDef("nav.permissions.action.save", "权限·维护", "Permission · save"));
        t.add(new TextDef("nav.permissions.action.delete", "权限·删除", "Permission · delete"));
        t.add(new TextDef("nav.rolesAdmin", "角色管理", "Roles"));
        t.add(new TextDef("nav.rolesAdmin.action.save", "角色·维护", "Role · save"));
        t.add(new TextDef("nav.rolesAdmin.action.delete", "角色·删除", "Role · delete"));
        t.add(new TextDef("nav.rolesAdmin.action.grant", "角色·授权", "Role · grant"));
        t.add(new TextDef("nav.i18n", "多语言", "Languages & i18n"));
        t.add(new TextDef("nav.i18n.action.save", "多语言·维护", "i18n · save"));
        t.add(new TextDef("nav.i18n.action.delete", "多语言·删除", "i18n · delete"));
        t.add(new TextDef("nav.i18n.action.locale", "多语言·语言目录", "i18n · locales"));
        t.add(new TextDef("nav.orgs", "组织管理", "Organization"));
        t.add(new TextDef("nav.users", "人员管理", "Users"));
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
