package com.cim.iam.server.console;

import com.cim.iam.server.support.IamPermissionCodes;
import com.cim.system.menu.MenuType;
import com.cim.system.support.PermissionCodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.cim.iam.server.support.IamPermissionCodes.*;

/**
 * IAM 控制台目录（菜单树 / 权限码 / 角色 / 译文）——种子的<b>单一事实源</b>。
 *
 * <p>把「控制台长什么样、有哪些按钮、要什么权限、各语言怎么显示」集中在此，由
 * {@link IamConsoleSeedService} 幂等写入库；改菜单/权限/文案只改本文件（或后续经
 * 管理端在线维护），<b>不再改前端代码</b>。这正是本设计的目标（见
 * {@code docs/business/iam-ap/server/console-menu-perm-i18n.md} §0）。</p>
 *
 * <p>与 {@code cim-system} 的约定一致：菜单节点只存 {@code i18n_code}，<b>不存中文字面量</b>；
 * 文案在 {@code sys_i18n} 里按语言给。</p>
 */
public final class IamConsoleCatalog {

    // ================================================================ 菜单树

    /**
     * 菜单定义。
     *
     * @param key       逻辑键（供父子引用与幂等定位；不落库）
     * @param parentKey 父节点逻辑键（{@code null} 为顶级）
     * @param type      {@code DIR} 目录 / {@code MENU} 菜单 / {@code BUTTON} 按钮
     * @param path      前端路由（{@code DIR}/{@code BUTTON} 为 {@code null}）
     * @param component 前端组件标识（供动态路由映射；{@code DIR}/{@code BUTTON} 可为 {@code null}）
     * @param i18nCode  名称 i18n 键（<b>幂等自然键</b>，全库唯一）
     * @param icon      图标标识（前端图标注册表的键）
     * @param permCode  关联权限码（{@code BUTTON} 必填）
     * @param sortNo    同级排序
     */
    public record MenuDef(String key, String parentKey, MenuType type, String path, String component,
                          String i18nCode, String icon, String permCode, int sortNo) {
    }

    /** 控制台菜单树（父先于子；顺序即种子写入顺序）。 */
    public static List<MenuDef> menus() {
        List<MenuDef> m = new ArrayList<>();
        // —— 概览 ——
        // 概览刻意**不绑权限码**：它是「登录后的落脚页」，任何拿到控制台角色的人都应能进；
        // 页内再按 iam:overview:view 决定显示「平台态势」（管理视角）还是「个人身份」（普通视角）。
        m.add(menu("overview", null, "/", "Overview", "iam.menu.overview", "gauge", null, 10));
        // —— 身份管理 ——
        m.add(dir("grp.identity", "iam.menu.group.identity", 20));
        m.add(menu("orgs", "grp.identity", "/orgs", "Orgs", "iam.menu.orgs", "tree", ORG_LIST, 10));
        m.add(menu("users", "grp.identity", "/users", "Users", "iam.menu.users", "users", USER_LIST, 20));
        m.add(menu("lockouts", "grp.identity", "/lockouts", "Lockouts", "iam.menu.lockouts",
                "lock", USER_LIST, 30));
        // —— 接入管理 ——
        m.add(dir("grp.access", "iam.menu.group.access", 30));
        m.add(menu("apps", "grp.access", "/apps", "Apps", "iam.menu.apps", "apps", APP_LIST, 10));
        m.add(menu("admissions", "grp.access", "/admissions", "Admissions", "iam.menu.admissions",
                "link", ADMISSION_LIST, 20));
        m.add(menu("orgGrants", "grp.access", "/org-grants", "OrgGrants", "iam.menu.orgGrants",
                "org-grant", GRANT_LIST, 30));
        m.add(menu("roles", "grp.access", "/roles", "Roles", "iam.menu.roles", "shield", ROLE_LIST, 40));
        // —— 安全与会话 ——
        m.add(dir("grp.security", "iam.menu.group.security", 40));
        m.add(menu("sessions", "grp.security", "/sessions", "Sessions", "iam.menu.sessions",
                "monitor", SESSION_LIST, 10));
        m.add(menu("audit", "grp.security", "/audit", "Audit", "iam.menu.audit", "list", AUDIT_LIST, 20));
        m.add(menu("settings", "grp.security", "/settings", "Settings", "iam.menu.settings",
                "sliders", SETTINGS_VIEW, 30));
        // —— 我的（排最后：配置类菜单属于管理动作，个人入口保持在底部） ——
        m.add(menu("profile", null, "/profile", "Profile", "iam.menu.profile", "user", null, 90));
        // —— 系统配置（本次新增，配置化自助闭环） ——
        m.add(dir("grp.system", "iam.menu.group.system", 60));
        m.add(menu("menus", "grp.system", "/menus", "MenusAdmin", "iam.menu.menus", "tree", MENU_LIST, 10));
        m.add(menu("permissions", "grp.system", "/permissions", "PermissionsAdmin", "iam.menu.permissions",
                "shield", PERM_LIST, 20));
        m.add(menu("rolesAdmin", "grp.system", "/roles-admin", "RolesAdmin", "iam.menu.rolesAdmin",
                "shield", ROLE_ADMIN_LIST, 30));
        m.add(menu("i18n", "grp.system", "/i18n", "I18nAdmin", "iam.menu.i18n", "list", I18N_LIST, 40));

        // —— 按钮节点（仅承载权限码，不出现在导航） ——
        m.addAll(buttons("orgs", new String[][]{
                {"iam.btn.org.create", ORG_CREATE},
                {"iam.btn.org.update", ORG_UPDATE},
                {"iam.btn.org.move", ORG_MOVE},
                {"iam.btn.org.delete", ORG_DELETE},
                {"iam.btn.org.member", ORG_MEMBER}}));
        m.addAll(buttons("users", new String[][]{
                {"iam.btn.user.create", USER_CREATE},
                {"iam.btn.user.update", USER_UPDATE},
                {"iam.btn.user.status", USER_STATUS},
                {"iam.btn.user.resetPwd", USER_RESET_PWD},
                {"iam.btn.user.orgs", USER_ORGS},
                {"iam.btn.user.delete", USER_DELETE}}));
        m.addAll(buttons("lockouts", new String[][]{
                {"iam.btn.lockout.unlock", USER_UNLOCK}}));
        m.addAll(buttons("apps", new String[][]{
                {"iam.btn.app.create", APP_CREATE},
                {"iam.btn.app.update", APP_UPDATE}}));
        m.addAll(buttons("admissions", new String[][]{
                {"iam.btn.admission.grant", ADMISSION_GRANT},
                {"iam.btn.admission.revoke", ADMISSION_REVOKE}}));
        m.addAll(buttons("orgGrants", new String[][]{
                {"iam.btn.grant.grant", GRANT_GRANT},
                {"iam.btn.grant.revoke", GRANT_REVOKE}}));
        m.addAll(buttons("sessions", new String[][]{
                {"iam.btn.session.kick", SESSION_KICK}}));
        m.addAll(buttons("settings", new String[][]{
                {"iam.btn.sync.run", SYNC_RUN}}));
        m.addAll(buttons("menus", new String[][]{
                {"iam.btn.menu.create", MENU_CREATE},
                {"iam.btn.menu.update", MENU_UPDATE},
                {"iam.btn.menu.delete", MENU_DELETE}}));
        m.addAll(buttons("permissions", new String[][]{
                {"iam.btn.perm.create", PERM_CREATE},
                {"iam.btn.perm.update", PERM_UPDATE},
                {"iam.btn.perm.delete", PERM_DELETE}}));
        m.addAll(buttons("rolesAdmin", new String[][]{
                {"iam.btn.rolesAdmin.create", ROLE_ADMIN_CREATE},
                {"iam.btn.rolesAdmin.update", ROLE_ADMIN_UPDATE},
                {"iam.btn.rolesAdmin.delete", ROLE_ADMIN_DELETE},
                {"iam.btn.rolesAdmin.grant", ROLE_ADMIN_GRANT}}));
        m.addAll(buttons("i18n", new String[][]{
                {"iam.btn.i18n.create", I18N_CREATE},
                {"iam.btn.i18n.update", I18N_UPDATE},
                {"iam.btn.i18n.delete", I18N_DELETE},
                {"iam.btn.i18n.locale", I18N_LOCALE}}));
        return m;
    }

    private static MenuDef menu(String key, String parentKey, String path, String component,
                               String i18nCode, String icon, String permCode, int sortNo) {
        return new MenuDef(key, parentKey, MenuType.MENU, path, component, i18nCode, icon, permCode, sortNo);
    }

    private static MenuDef dir(String key, String i18nCode, int sortNo) {
        return new MenuDef(key, null, MenuType.DIR, null, null, i18nCode, null, null, sortNo);
    }

    private static List<MenuDef> buttons(String parentKey, String[][] rows) {
        List<MenuDef> list = new ArrayList<>();
        int sort = 1;
        for (String[] row : rows) {
            // BUTTON 无路由/组件/图标；i18nCode 即落库键，permCode 为其承载的权限
            list.add(new MenuDef(parentKey + "::" + row[0], parentKey, MenuType.BUTTON, null, null,
                    row[0], null, row[1], sort++));
        }
        return list;
    }

    // ================================================================ 权限码

    /**
     * 权限定义。
     *
     * @param code 权限码 {@code module:res:action}
     * @param name 面向管理员的名称
     */
    public record PermDef(String code, String name) {
    }

    /** IAM 控制台权限码目录（含类级兜底码 {@code iam:console:admin}）。 */
    public static List<PermDef> permissions() {
        List<PermDef> p = new ArrayList<>();
        p.add(new PermDef(CONSOLE_ADMIN, "控制台管理员（兜底）"));
        p.add(new PermDef(OVERVIEW_VIEW, "概览查看"));
        // 组织
        p.add(new PermDef(ORG_LIST, "组织查询"));
        p.add(new PermDef(ORG_CREATE, "组织新建"));
        p.add(new PermDef(ORG_UPDATE, "组织编辑"));
        p.add(new PermDef(ORG_MOVE, "组织移动"));
        p.add(new PermDef(ORG_DELETE, "组织删除"));
        p.add(new PermDef(ORG_MEMBER, "组织成员维护"));
        // 用户与档案
        p.add(new PermDef(USER_LIST, "用户查询"));
        p.add(new PermDef(USER_CREATE, "用户新建"));
        p.add(new PermDef(USER_UPDATE, "用户编辑"));
        p.add(new PermDef(USER_STATUS, "用户启停"));
        p.add(new PermDef(USER_RESET_PWD, "重置口令"));
        p.add(new PermDef(USER_UNLOCK, "解锁账号"));
        p.add(new PermDef(USER_ORGS, "用户组织归属"));
        p.add(new PermDef(USER_DELETE, "用户删除"));
        // 应用注册
        p.add(new PermDef(APP_LIST, "应用查询"));
        p.add(new PermDef(APP_CREATE, "应用注册"));
        p.add(new PermDef(APP_UPDATE, "应用编辑"));
        // 个人准入
        p.add(new PermDef(ADMISSION_LIST, "准入查询"));
        p.add(new PermDef(ADMISSION_GRANT, "准入授予"));
        p.add(new PermDef(ADMISSION_REVOKE, "准入撤销"));
        // 组织授予
        p.add(new PermDef(GRANT_LIST, "组织授予查询"));
        p.add(new PermDef(GRANT_GRANT, "组织授予"));
        p.add(new PermDef(GRANT_REVOKE, "组织授予撤销"));
        // 角色组
        p.add(new PermDef(ROLE_LIST, "角色组查询"));
        // 会话
        p.add(new PermDef(SESSION_LIST, "会话查询"));
        p.add(new PermDef(SESSION_KICK, "强制下线"));
        // 审计
        p.add(new PermDef(AUDIT_LIST, "审计查询"));
        // 设置
        p.add(new PermDef(SETTINGS_VIEW, "设置查看"));
        // 同步
        p.add(new PermDef(SYNC_RUN, "目录同步执行"));
        // 菜单管理
        p.add(new PermDef(MENU_LIST, "菜单查询"));
        p.add(new PermDef(MENU_CREATE, "菜单新建"));
        p.add(new PermDef(MENU_UPDATE, "菜单编辑"));
        p.add(new PermDef(MENU_DELETE, "菜单删除"));
        // 权限管理
        p.add(new PermDef(PERM_LIST, "权限查询"));
        p.add(new PermDef(PERM_CREATE, "权限新建"));
        p.add(new PermDef(PERM_UPDATE, "权限编辑"));
        p.add(new PermDef(PERM_DELETE, "权限删除"));
        // 角色管理
        p.add(new PermDef(ROLE_ADMIN_LIST, "角色查询"));
        p.add(new PermDef(ROLE_ADMIN_CREATE, "角色新建"));
        p.add(new PermDef(ROLE_ADMIN_UPDATE, "角色编辑"));
        p.add(new PermDef(ROLE_ADMIN_DELETE, "角色删除"));
        p.add(new PermDef(ROLE_ADMIN_GRANT, "角色授权"));
        // 多语言
        p.add(new PermDef(I18N_LIST, "译文查询"));
        p.add(new PermDef(I18N_CREATE, "译文新建"));
        p.add(new PermDef(I18N_UPDATE, "译文编辑"));
        p.add(new PermDef(I18N_DELETE, "译文删除"));
        p.add(new PermDef(I18N_LOCALE, "语言维护"));
        return p;
    }

    // ================================================================ 角色

    /** 控制台管理员角色码（超管：短路放通本 ap 全部权限码）。 */
    public static final String ROLE_ADMIN = "IAM_ADMIN";

    /** 控制台只读运维角色码。 */
    public static final String ROLE_OPERATOR = "IAM_OPERATOR";

    /**
     * 角色定义。
     *
     * @param code      角色码
     * @param name      名称
     * @param superRole 是否超管（短路展开为全部权限码）
     * @param perms     显式授予的权限码（超管可不填，此处仍给全量以便授权界面回显）
     * @param allMenus  是否授予全部菜单可见性（含按钮节点）
     */
    public record RoleDef(String code, String name, boolean superRole, List<String> perms, boolean allMenus) {
    }

    /** 控制台内置角色。 */
    public static List<RoleDef> roles() {
        List<String> allPerms = permissions().stream().map(PermDef::code).toList();
        // 只读运维：列表/查看类
        List<String> readOnly = List.of(
                OVERVIEW_VIEW, ORG_LIST, USER_LIST, APP_LIST, ADMISSION_LIST,
                GRANT_LIST, ROLE_LIST, SESSION_LIST, AUDIT_LIST, SETTINGS_VIEW,
                MENU_LIST, PERM_LIST, ROLE_ADMIN_LIST, I18N_LIST,
                // ⚠️ 配置页（菜单/权限/角色）的**数据面落在平台 `sys/**` 端点**上，
                // 那些端点用平台码 `sys:*` 鉴权（见 console-menu-perm-i18n.md §6）。
                // 故只读角色必须同时持有平台只读码，否则「页面可见但数据 403」。
                // 超管（IAM_ADMIN）由「库内全部启用权限码」短路展开，天然具备，无需列在此。
                PermissionCodes.MENU_LIST, PermissionCodes.PERMISSION_LIST, PermissionCodes.ROLE_LIST);
        List<RoleDef> r = new ArrayList<>();
        r.add(new RoleDef(ROLE_ADMIN, "控制台管理员", true, allPerms, true));
        r.add(new RoleDef(ROLE_OPERATOR, "控制台只读运维", false, readOnly, true));
        return r;
    }

    // ================================================================ 译文

    /**
     * 译文定义（双语成对，便于人工校对时对照）。
     *
     * @param code 键
     * @param zh   简体中文（<b>强制兜底，必填</b>）
     * @param en   英文
     */
    public record TextDef(String code, String zh, String en) {
    }

    /** IAM 控制台文案（菜单名 / 分组名 / 按钮名 / 页面标题与字段）。 */
    public static List<TextDef> texts() {
        List<TextDef> t = new ArrayList<>();
        // —— 菜单与分组 ——
        t.add(new TextDef("iam.menu.overview", "概览", "Overview"));
        t.add(new TextDef("iam.menu.group.identity", "身份管理", "Identity"));
        t.add(new TextDef("iam.menu.orgs", "组织架构", "Organization"));
        t.add(new TextDef("iam.menu.users", "用户与档案", "Users & Profiles"));
        t.add(new TextDef("iam.menu.lockouts", "登录锁定", "Lockouts"));
        t.add(new TextDef("iam.menu.group.access", "接入管理", "Access"));
        t.add(new TextDef("iam.menu.apps", "应用注册", "Applications"));
        t.add(new TextDef("iam.menu.admissions", "准入授权", "Admissions"));
        t.add(new TextDef("iam.menu.orgGrants", "组织授权", "Org Grants"));
        t.add(new TextDef("iam.menu.roles", "角色组", "Role Groups"));
        t.add(new TextDef("iam.menu.group.security", "安全与会话", "Security & Sessions"));
        t.add(new TextDef("iam.menu.sessions", "在线会话", "Sessions"));
        t.add(new TextDef("iam.menu.audit", "审计日志", "Audit Log"));
        t.add(new TextDef("iam.menu.settings", "系统设置", "Settings"));
        t.add(new TextDef("iam.menu.profile", "我的", "My Profile"));
        t.add(new TextDef("iam.menu.group.system", "系统配置", "Configuration"));
        t.add(new TextDef("iam.menu.menus", "菜单管理", "Menus"));
        t.add(new TextDef("iam.menu.permissions", "权限管理", "Permissions"));
        t.add(new TextDef("iam.menu.rolesAdmin", "角色管理", "Roles"));
        t.add(new TextDef("iam.menu.i18n", "多语言", "Localization"));
        // —— 菜单副标题（侧栏第二行 / 页头说明；键 = 菜单 i18n_code + ".desc"）——
        t.add(new TextDef("iam.menu.overview.desc", "平台态势总览", "Platform overview"));
        t.add(new TextDef("iam.menu.orgs.desc", "厂区→产线→工序", "Site → line → process"));
        t.add(new TextDef("iam.menu.users.desc", "账号与档案", "Accounts & profiles"));
        t.add(new TextDef("iam.menu.lockouts.desc", "锁定与解锁", "Lock & unlock"));
        t.add(new TextDef("iam.menu.apps.desc", "业务接入码", "App access codes"));
        t.add(new TextDef("iam.menu.admissions.desc", "用户 × 应用", "User × app"));
        t.add(new TextDef("iam.menu.orgGrants.desc", "组织 × 应用", "Org × app"));
        t.add(new TextDef("iam.menu.roles.desc", "角色分布", "Role distribution"));
        t.add(new TextDef("iam.menu.sessions.desc", "会话与下线", "Sessions & logout"));
        t.add(new TextDef("iam.menu.audit.desc", "动作流水", "Action trail"));
        t.add(new TextDef("iam.menu.settings.desc", "策略与参数", "Policies & params"));
        t.add(new TextDef("iam.menu.profile.desc", "账户与口令", "Account & password"));
        t.add(new TextDef("iam.menu.menus.desc", "菜单树与图标", "Menu tree & icons"));
        t.add(new TextDef("iam.menu.permissions.desc", "权限码目录", "Permission catalog"));
        t.add(new TextDef("iam.menu.rolesAdmin.desc", "角色与授权", "Roles & grants"));
        t.add(new TextDef("iam.menu.i18n.desc", "语言与译文", "Locales & texts"));
        // —— 按钮 ——
        t.add(new TextDef("iam.btn.org.create", "新建组织", "New org"));
        t.add(new TextDef("iam.btn.org.update", "编辑组织", "Edit org"));
        t.add(new TextDef("iam.btn.org.move", "移动组织", "Move org"));
        t.add(new TextDef("iam.btn.org.delete", "删除组织", "Delete org"));
        t.add(new TextDef("iam.btn.org.member", "维护成员", "Manage members"));
        t.add(new TextDef("iam.btn.user.create", "新建用户", "New user"));
        t.add(new TextDef("iam.btn.user.update", "编辑用户", "Edit user"));
        t.add(new TextDef("iam.btn.user.status", "启用/停用", "Enable / disable"));
        t.add(new TextDef("iam.btn.user.resetPwd", "重置口令", "Reset password"));
        t.add(new TextDef("iam.btn.user.orgs", "组织归属", "Organization"));
        t.add(new TextDef("iam.btn.user.delete", "删除用户", "Delete user"));
        t.add(new TextDef("iam.btn.lockout.unlock", "解锁", "Unlock"));
        t.add(new TextDef("iam.btn.app.create", "注册应用", "Register app"));
        t.add(new TextDef("iam.btn.app.update", "编辑应用", "Edit app"));
        t.add(new TextDef("iam.btn.admission.grant", "授予准入", "Grant admission"));
        t.add(new TextDef("iam.btn.admission.revoke", "撤销准入", "Revoke admission"));
        t.add(new TextDef("iam.btn.grant.grant", "组织授权", "Grant"));
        t.add(new TextDef("iam.btn.grant.revoke", "撤销授权", "Revoke"));
        t.add(new TextDef("iam.btn.session.kick", "强制下线", "Force logout"));
        t.add(new TextDef("iam.btn.sync.run", "立即同步", "Sync now"));
        t.add(new TextDef("iam.btn.menu.create", "新建菜单", "New menu"));
        t.add(new TextDef("iam.btn.menu.update", "编辑菜单", "Edit menu"));
        t.add(new TextDef("iam.btn.menu.delete", "删除菜单", "Delete menu"));
        t.add(new TextDef("iam.btn.perm.create", "新建权限", "New permission"));
        t.add(new TextDef("iam.btn.perm.update", "编辑权限", "Edit permission"));
        t.add(new TextDef("iam.btn.perm.delete", "删除权限", "Delete permission"));
        t.add(new TextDef("iam.btn.rolesAdmin.create", "新建角色", "New role"));
        t.add(new TextDef("iam.btn.rolesAdmin.update", "编辑角色", "Edit role"));
        t.add(new TextDef("iam.btn.rolesAdmin.delete", "删除角色", "Delete role"));
        t.add(new TextDef("iam.btn.rolesAdmin.grant", "角色授权", "Grant role"));
        t.add(new TextDef("iam.btn.i18n.create", "新建译文", "New text"));
        t.add(new TextDef("iam.btn.i18n.update", "编辑译文", "Edit text"));
        t.add(new TextDef("iam.btn.i18n.delete", "删除译文", "Delete text"));
        t.add(new TextDef("iam.btn.i18n.locale", "语言维护", "Manage locales"));
        // —— 管理页标题与常用字段（供 W4 页面复用） ——
        t.add(new TextDef("iam.page.menus.title", "菜单管理", "Menu management"));
        t.add(new TextDef("iam.page.menus.desc", "维护控制台菜单树（父子节点）、图标与绑定权限",
                "Maintain the console menu tree, icons and bound permissions"));
        t.add(new TextDef("iam.page.permissions.title", "权限管理", "Permission management"));
        t.add(new TextDef("iam.page.permissions.desc", "维护权限码（模块:资源:动作，接口与按钮共用）",
                "Maintain permission codes (module:resource:action)"));
        t.add(new TextDef("iam.page.rolesAdmin.title", "角色管理", "Role management"));
        t.add(new TextDef("iam.page.rolesAdmin.desc", "角色 CRUD 与菜单/权限授权（控制台内部权限）",
                "Role CRUD and menu/permission grants"));
        t.add(new TextDef("iam.page.i18n.title", "多语言", "Localization"));
        t.add(new TextDef("iam.page.i18n.desc", "语言目录与译文维护，中文兜底，缺失自动上报",
                "Manage locales and translations; Chinese fallback; auto missing report"));
        t.add(new TextDef("iam.field.code", "编码", "Code"));
        t.add(new TextDef("iam.field.name", "名称", "Name"));
        t.add(new TextDef("iam.field.module", "模块", "Module"));
        t.add(new TextDef("iam.field.path", "路由", "Path"));
        t.add(new TextDef("iam.field.component", "组件", "Component"));
        t.add(new TextDef("iam.field.icon", "图标", "Icon"));
        t.add(new TextDef("iam.field.type", "类型", "Type"));
        t.add(new TextDef("iam.field.sortNo", "排序", "Order"));
        t.add(new TextDef("iam.field.visible", "可见", "Visible"));
        t.add(new TextDef("iam.field.permCode", "权限码", "Permission"));
        t.add(new TextDef("iam.field.i18nCode", "文案键", "Text key"));
        t.add(new TextDef("iam.field.locale", "语言", "Locale"));
        t.add(new TextDef("iam.field.content", "译文", "Text"));
        t.add(new TextDef("iam.type.dir", "目录", "Directory"));
        t.add(new TextDef("iam.type.menu", "菜单", "Menu"));
        t.add(new TextDef("iam.type.button", "按钮", "Button"));
        t.add(new TextDef("iam.i18n.missing", "缺失译文", "Missing translations"));
        t.add(new TextDef("iam.i18n.default", "默认语言", "Default locale"));
        // —— 通用动作 / 状态（W4 页面复用；避免各页重复造词）——
        t.add(new TextDef("iam.common.actions", "操作", "Actions"));
        t.add(new TextDef("iam.common.add", "新增", "Add"));
        t.add(new TextDef("iam.common.edit", "编辑", "Edit"));
        t.add(new TextDef("iam.common.delete", "删除", "Delete"));
        t.add(new TextDef("iam.common.save", "保存", "Save"));
        t.add(new TextDef("iam.common.cancel", "取消", "Cancel"));
        t.add(new TextDef("iam.common.close", "关闭", "Close"));
        t.add(new TextDef("iam.common.search", "搜索", "Search"));
        t.add(new TextDef("iam.common.refresh", "刷新", "Refresh"));
        t.add(new TextDef("iam.common.reset", "重置", "Reset"));
        t.add(new TextDef("iam.common.selectAll", "全选", "Select all"));
        t.add(new TextDef("iam.common.clearAll", "清空", "Clear"));
        t.add(new TextDef("iam.common.all", "全部", "All"));
        t.add(new TextDef("iam.common.enabled", "启用", "Enabled"));
        t.add(new TextDef("iam.common.disabled", "停用", "Disabled"));
        t.add(new TextDef("iam.common.none", "无", "None"));
        t.add(new TextDef("iam.common.saving", "保存中…", "Saving…"));
        t.add(new TextDef("iam.common.confirmDelete", "确认删除？此操作不可撤销。",
                "Delete? This cannot be undone."));
        t.add(new TextDef("iam.common.saved", "已保存", "Saved"));
        t.add(new TextDef("iam.common.deleted", "已删除", "Deleted"));
        t.add(new TextDef("iam.common.required", "必填", "Required"));
        t.add(new TextDef("iam.common.loadFailed", "加载失败", "Failed to load"));
        t.add(new TextDef("iam.common.noPlatformPerm",
                "当前角色缺少平台配置权限（sys:*），数据无法读取；请联系管理员为角色补授平台码。",
                "Missing platform configuration permission (sys:*); ask an administrator to grant it."));
        // —— 菜单管理页 ——
        t.add(new TextDef("iam.admin.menus.tree", "菜单树", "Menu tree"));
        t.add(new TextDef("iam.admin.menus.nodeCount", "{{n}} 个节点", "{{n}} nodes"));
        t.add(new TextDef("iam.admin.menus.newRoot", "新建根节点", "New root node"));
        t.add(new TextDef("iam.admin.menus.addChild", "添加子节点", "Add child"));
        t.add(new TextDef("iam.admin.menus.parent", "上级", "Parent"));
        t.add(new TextDef("iam.admin.menus.root", "根节点", "Root"));
        t.add(new TextDef("iam.admin.menus.selectNode", "请选择左侧节点", "Select a node on the left"));
        t.add(new TextDef("iam.admin.menus.i18nCodeHint",
                "文案键（前端以 t(键) 渲染名称；副标题用「键 + .desc」）",
                "Text key (the frontend renders t(key); subtitle uses key + '.desc')"));
        t.add(new TextDef("iam.admin.menus.iconHint",
                "图标标识须存在于前端图标注册表，否则回退为警示图标",
                "Icon key must exist in the frontend registry, otherwise a fallback icon is used"));
        t.add(new TextDef("iam.admin.menus.permHint",
                "菜单可留空（仅可见性）；按钮节点必须绑定权限码",
                "Menus may omit it (visibility only); button nodes require a permission code"));
        t.add(new TextDef("iam.admin.menus.buttonNeedsPerm", "按钮节点必须填写权限码",
                "A button node requires a permission code"));
        t.add(new TextDef("iam.admin.menus.visibleHint", "关闭后不出现在侧栏导航（路由仍可达）",
                "When off, the entry is hidden from the sidebar (the route stays reachable)"));
        // —— 权限管理页 ——
        t.add(new TextDef("iam.admin.perms.new", "新建权限码", "New permission code"));
        t.add(new TextDef("iam.admin.perms.count", "权限点", "Permissions"));
        t.add(new TextDef("iam.admin.perms.codeHint",
                "三段式 module:res:action，如 iam:user:create；接口与按钮共用同一码",
                "Three segments, module:res:action, e.g. iam:user:create; shared by APIs and buttons"));
        t.add(new TextDef("iam.admin.perms.invalidCode",
                "权限码必须是三段式 module:res:action", "Code must be module:res:action"));
        t.add(new TextDef("iam.admin.perms.moduleOwner",
                "iam:* 属 IAM 控制台内部权限；sys:* 属平台系统域（配置页数据面用它鉴权）",
                "iam:* belongs to the IAM console; sys:* belongs to the platform system domain"));
        // —— 角色管理页 ——
        t.add(new TextDef("iam.admin.roles.list", "角色列表", "Roles"));
        t.add(new TextDef("iam.admin.roles.new", "新建角色", "New role"));
        t.add(new TextDef("iam.admin.roles.selectRole", "请选择左侧角色", "Select a role on the left"));
        t.add(new TextDef("iam.admin.roles.super", "超管", "Super"));
        t.add(new TextDef("iam.admin.roles.superHint",
                "超管自动获得全部启用权限码，无需逐项授权",
                "A super role automatically holds every enabled permission code"));
        t.add(new TextDef("iam.admin.roles.permGrant", "权限授权", "Permission grants"));
        t.add(new TextDef("iam.admin.roles.menuGrant", "菜单可见性", "Menu visibility"));
        t.add(new TextDef("iam.admin.roles.permNote",
                "权限码决定接口与按钮是否可用（覆盖式保存）",
                "Permission codes decide whether APIs and buttons work (replace-all save)"));
        t.add(new TextDef("iam.admin.roles.menuNote",
                "菜单可见性决定侧栏与路由是否出现；与权限码相互独立（有菜单无码 → 可见但 403）",
                "Menu visibility controls the sidebar and routes; independent from permission codes"));
        t.add(new TextDef("iam.admin.roles.grantSave", "保存授权", "Save grants"));
        t.add(new TextDef("iam.admin.roles.groupByModule", "按模块分组", "Grouped by module"));
        // —— 多语言页 ——
        t.add(new TextDef("iam.admin.i18n.locales", "语言目录", "Locales"));
        t.add(new TextDef("iam.admin.i18n.messages", "译文", "Texts"));
        t.add(new TextDef("iam.admin.i18n.newLocale", "新增语言", "Add locale"));
        t.add(new TextDef("iam.admin.i18n.newMessage", "新增译文", "Add text"));
        t.add(new TextDef("iam.admin.i18n.scope", "级别", "Scope"));
        t.add(new TextDef("iam.admin.i18n.localeCodeHint",
                "BCP-47 代码，如 zh-CN / en-US / zh-TW", "BCP-47 code, e.g. zh-CN / en-US / zh-TW"));
        t.add(new TextDef("iam.admin.i18n.defaultLocked", "默认兜底语言不可删除",
                "The default fallback locale cannot be deleted"));
        t.add(new TextDef("iam.admin.i18n.missingHint",
                "四级兜底链全未命中的键（由真实访问上报，可据此生成待翻译工单）",
                "Keys missed by all four fallback layers (reported from real traffic)"));
        t.add(new TextDef("iam.admin.i18n.clearMissing", "清空缺失记录", "Clear missing report"));
        t.add(new TextDef("iam.admin.i18n.reloadHint",
                "译文保存后即时失效缓存，前端下次拉取或刷新页面即生效",
                "Saving invalidates the cache; the frontend picks it up on the next fetch or reload"));
        t.add(new TextDef("iam.admin.i18n.systemScope",
                "内置级别（SYSTEM）由程序种子写入，可在此覆盖为业务文案",
                "Built-in (SYSTEM) rows come from code seeds and may be overridden here"));
        t.add(new TextDef("iam.admin.i18n.filterLocale", "按语言筛选", "Filter by locale"));
        t.add(new TextDef("iam.admin.i18n.filterModule", "按模块筛选", "Filter by module"));
        t.add(new TextDef("iam.admin.i18n.keyword", "键或译文关键字", "Key or text keyword"));
        t.add(new TextDef("iam.admin.i18n.missingCount", "缺失 {{n}} 条", "{{n}} missing"));
        // —— 控制台外壳（Layout / 语言切换 / 引导与错误页）——
        t.add(new TextDef("iam.shell.brandSub", "IAM-AP / 统一身份准入", "IAM-AP / Identity & Admission"));
        t.add(new TextDef("iam.shell.tagline", "统一登录与准入控制", "Unified sign-in & admission"));
        t.add(new TextDef("iam.shell.crypto", "RS256 / JWKS 本地验签", "RS256 / local JWKS verification"));
        t.add(new TextDef("iam.shell.consoleTitle", "IAM 控制台", "IAM Console"));
        t.add(new TextDef("iam.shell.toggleNav", "收起 / 展开菜单", "Collapse / expand menu"));
        t.add(new TextDef("iam.shell.toggleTheme", "切换主题", "Toggle theme"));
        t.add(new TextDef("iam.shell.theme.dark", "切换到深色主题", "Switch to dark theme"));
        t.add(new TextDef("iam.shell.theme.light", "切换到浅色主题", "Switch to light theme"));
        t.add(new TextDef("iam.shell.language", "语言", "Language"));
        t.add(new TextDef("iam.shell.adminHint", "具备 IAM 管理面权限", "Has IAM console privileges"));
        t.add(new TextDef("iam.shell.logout", "退出登录", "Sign out"));
        t.add(new TextDef("iam.shell.navGroupOther", "其他", "Other"));
        t.add(new TextDef("iam.common.notLoggedIn", "未登录", "Not signed in"));
        t.add(new TextDef("iam.common.loading", "加载中…", "Loading…"));
        t.add(new TextDef("iam.common.retry", "重试", "Retry"));
        t.add(new TextDef("iam.common.menuFailed", "菜单加载失败", "Failed to load menu"));
        t.add(new TextDef("iam.common.permsLoading", "正在校验权限…", "Verifying permissions…"));
        t.add(new TextDef("iam.common.forbidden.title", "无权访问", "Access denied"));
        t.add(new TextDef("iam.common.forbidden.desc", "你没有该页面的权限，如需访问请联系控制台管理员。",
                "You do not have permission for this page. Contact a console administrator."));
        t.add(new TextDef("iam.common.notfound.title", "页面不存在", "Page not found"));
        t.add(new TextDef("iam.common.notfound.desc", "该地址不属于当前控制台，或对应菜单已被移除。",
                "This URL is not part of the console, or the menu entry has been removed."));
        t.add(new TextDef("iam.common.backHome", "返回概览", "Back to overview"));
        t.add(new TextDef("iam.common.wip", "功能建设中，即将上线。", "Under construction — coming soon."));
        // —— 登录页 ——
        t.add(new TextDef("iam.login.brand", "CIM · 统一身份与准入", "CIM · Identity & Admission"));
        t.add(new TextDef("iam.login.hero.title", "一次登录，通达各业务系统", "One sign-in, every business system"));
        t.add(new TextDef("iam.login.hero.desc",
                "企业内统一身份认证与跨业务准入控制中枢。令牌由 IAM 签发，各业务系统本地验签并据此判定准入。",
                "The central hub for enterprise identity and cross-application admission. Tokens are issued "
                        + "by IAM and verified locally by each business system."));
        t.add(new TextDef("iam.login.point.rs256", "RS256 令牌签发 · JWKS 公钥分发 · 业务侧本地验签",
                "RS256 token issuing · JWKS key distribution · local verification"));
        t.add(new TextDef("iam.login.point.pbkdf2", "口令两层派生（PBKDF2），明文口令不出浏览器",
                "Two-layer PBKDF2 derivation — the plain password never leaves the browser"));
        t.add(new TextDef("iam.login.point.admission", "应用准入注册 · 用户角色分配 · 令牌即时踢下线",
                "App admission registry · role assignment · instant token revocation"));
        t.add(new TextDef("iam.login.title", "登录", "Sign in"));
        t.add(new TextDef("iam.login.lead", "请输入企业账号以进入 IAM 控制台",
                "Enter your corporate account to open the IAM console"));
        t.add(new TextDef("iam.login.username", "用户名", "Username"));
        t.add(new TextDef("iam.login.password", "口令", "Password"));
        t.add(new TextDef("iam.login.passwordPlaceholder", "请输入口令", "Enter your password"));
        t.add(new TextDef("iam.login.showPwd", "显示口令", "Show password"));
        t.add(new TextDef("iam.login.hidePwd", "隐藏口令", "Hide password"));
        t.add(new TextDef("iam.login.capsOn", "大写锁定（Caps Lock）已开启", "Caps Lock is on"));
        t.add(new TextDef("iam.login.remember", "记住用户名", "Remember username"));
        t.add(new TextDef("iam.login.submit", "登录", "Sign in"));
        t.add(new TextDef("iam.login.submitting", "登录中…", "Signing in…"));
        t.add(new TextDef("iam.login.hint",
                "口令在浏览器内完成第一层 PBKDF2 派生，明文不会上传到服务端。",
                "The first PBKDF2 layer runs in your browser; the plain password is never uploaded."));
        t.add(new TextDef("iam.login.failed", "登录失败", "Sign-in failed"));
        return t;
    }

    /** 键 → 中文（{@code zh-CN}，强制兜底）。 */
    public static Map<String, String> zhMap() {
        Map<String, String> map = new LinkedHashMap<>();
        texts().forEach(x -> map.put(x.code(), x.zh()));
        return map;
    }

    /** 键 → 英文（{@code en-US}）。 */
    public static Map<String, String> enMap() {
        Map<String, String> map = new LinkedHashMap<>();
        texts().forEach(x -> map.put(x.code(), x.en()));
        return map;
    }

    private IamConsoleCatalog() {
    }
}
