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
        t.add(new TextDef("iam.common.expandAll", "全部展开", "Expand all"));
        t.add(new TextDef("iam.common.collapseAll", "全部收起", "Collapse all"));
        t.add(new TextDef("iam.common.prevPage", "上一页", "Previous"));
        t.add(new TextDef("iam.common.nextPage", "下一页", "Next"));
        t.add(new TextDef("iam.common.pagerInfo", "第 {{page}} / {{pages}} 页 · 共 {{total}} 条",
                "Page {{page}} of {{pages}} · {{total}} in total"));
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
        t.add(new TextDef("iam.admin.perms.ifaceTag", "接口级", "API-level"));
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
        t.add(new TextDef("iam.admin.roles.groupByPage", "按所属页面分组，可整组勾选", "Grouped by linked page; toggle per group"));
        t.add(new TextDef("iam.admin.roles.tabBasic", "基本信息", "Profile"));
        t.add(new TextDef("iam.admin.roles.tabPermGrant", "权限授权", "Permissions"));
        t.add(new TextDef("iam.admin.roles.tabMenuGrant", "菜单授权", "Menus"));
        // —— 多语言页 ——
        t.add(new TextDef("iam.admin.i18n.locales", "语言目录", "Locales"));
        t.add(new TextDef("iam.admin.i18n.allLocales", "全部语言", "All locales"));
        t.add(new TextDef("iam.admin.i18n.clickToFilter", "点击筛选该语言的译文",
                "Click to filter messages by this locale"));
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
        t.add(new TextDef("iam.shell.toggleGroup", "展开或收起该分组", "Expand or collapse this group"));
        t.add(new TextDef("iam.shell.toggleTheme", "切换主题", "Toggle theme"));
        t.add(new TextDef("iam.shell.theme.dark", "切换到深色主题", "Switch to dark theme"));
        t.add(new TextDef("iam.shell.theme.light", "切换到浅色主题", "Switch to light theme"));
        t.add(new TextDef("iam.shell.language", "语言", "Language"));
        t.add(new TextDef("iam.shell.adminHint", "控制台管理员：持有 iam:console:admin 兜底权限码，可进入全部配置页", "Console admin: holds the iam:console:admin fallback code, can enter all config pages"));
        t.add(new TextDef("iam.shell.adminBadge", "管理员", "ADMIN"));
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
        // ============================================================ W4b：存量业务页文案
        // 命名空间 iam.<page>.* 与页面组件一一对应；动态文案用 {{var}} 占位（i18next 插值）。
        // 仅在「用户可见」处使用；代码注释/键名（ASCII）不在此列。

        // —— format.ts 枚举标签（被多页复用，集中在此）——
        t.add(new TextDef("iam.audit.type.LOGIN_SUCCESS", "登录成功", "Sign-in succeeded"));
        t.add(new TextDef("iam.audit.type.LOGIN_FAILURE", "登录失败", "Sign-in failed"));
        t.add(new TextDef("iam.audit.type.LOGIN_REJECTED", "登录被拒", "Sign-in rejected"));
        t.add(new TextDef("iam.audit.type.LOGOUT", "登出", "Signed out"));
        t.add(new TextDef("iam.audit.type.PASSWORD_CHANGED", "自助改密", "Self password change"));
        t.add(new TextDef("iam.audit.type.PASSWORD_RESET", "重置口令", "Password reset"));
        t.add(new TextDef("iam.audit.type.USER_CREATED", "创建账号", "Account created"));
        t.add(new TextDef("iam.audit.type.USER_ENABLED", "启用账号", "Account enabled"));
        t.add(new TextDef("iam.audit.type.USER_DISABLED", "禁用账号", "Account disabled"));
        t.add(new TextDef("iam.audit.type.USER_DELETED", "删除账号", "Account deleted"));
        t.add(new TextDef("iam.audit.type.USER_UNLOCKED", "解除锁定", "Unlocked"));
        t.add(new TextDef("iam.audit.type.APP_REGISTERED", "注册应用", "App registered"));
        t.add(new TextDef("iam.audit.type.APP_UPDATED", "更新应用", "App updated"));
        t.add(new TextDef("iam.audit.type.ADMISSION_GRANTED", "授予准入", "Admission granted"));
        t.add(new TextDef("iam.audit.type.ADMISSION_REVOKED", "撤销准入", "Admission revoked"));
        t.add(new TextDef("iam.audit.type.SESSION_REVOKED", "强制下线", "Forced logout"));
        t.add(new TextDef("iam.audit.type.ORG_CREATED", "新建组织", "Org created"));
        t.add(new TextDef("iam.audit.type.ORG_UPDATED", "更新组织", "Org updated"));
        t.add(new TextDef("iam.audit.type.ORG_MOVED", "移动组织", "Org moved"));
        t.add(new TextDef("iam.audit.type.ORG_DELETED", "删除组织", "Org deleted"));
        t.add(new TextDef("iam.audit.type.PROFILE_CREATED", "新建档案", "Profile created"));
        t.add(new TextDef("iam.audit.type.PROFILE_UPDATED", "更新档案", "Profile updated"));
        t.add(new TextDef("iam.audit.type.USER_ORGS_CHANGED", "变更归属", "Membership changed"));
        t.add(new TextDef("iam.audit.type.ORG_GRANT_GRANTED", "授予组织准入", "Org admission granted"));
        t.add(new TextDef("iam.audit.type.ORG_GRANT_REVOKED", "撤销组织准入", "Org admission revoked"));
        t.add(new TextDef("iam.audit.type.DIRECTORY_SYNCED", "目录同步", "Directory synced"));
        t.add(new TextDef("iam.org.type.AREA", "厂区", "Site"));
        t.add(new TextDef("iam.org.type.WORKSHOP", "车间", "Workshop"));
        t.add(new TextDef("iam.org.type.LINE", "产线", "Line"));
        t.add(new TextDef("iam.org.type.PROCESS", "工序", "Process"));
        t.add(new TextDef("iam.org.type.TEAM", "班组", "Team"));
        t.add(new TextDef("iam.org.type.DEPT", "部门", "Department"));
        t.add(new TextDef("iam.source.AD_SYNCED", "AD 同步", "AD synced"));
        t.add(new TextDef("iam.source.IAM_MANAGED", "IAM 自建", "IAM managed"));
        t.add(new TextDef("iam.common.justNow", "刚刚", "Just now"));
        t.add(new TextDef("iam.common.minutesAgo", "{{n}} 分钟前", "{{n}} min ago"));
        t.add(new TextDef("iam.common.hoursAgo", "{{n}} 小时前", "{{n}} h ago"));
        t.add(new TextDef("iam.common.daysAgo", "{{n}} 天前", "{{n}} d ago"));
        t.add(new TextDef("iam.common.dash", "—", "—"));

        // —— UsersPage（iam.users.*）——
        t.add(new TextDef("iam.users.title", "用户与档案", "Users & profiles"));
        t.add(new TextDef("iam.users.sub", "本地凭证账号 ∪ 用户档案（AD 同步来的员工无本地凭证，改密在目录侧）",
                "Local credentials ∪ profiles (AD-synced staff have no local credential; password change is on the directory side)"));
        t.add(new TextDef("iam.users.count", "{{n}} 人", "{{n}} users"));
        t.add(new TextDef("iam.users.enabled", "{{n}} 启用", "{{n}} enabled"));
        t.add(new TextDef("iam.users.adSynced", "{{n}} AD 同步", "{{n}} AD synced"));
        t.add(new TextDef("iam.users.locked", "{{n}} 锁定", "{{n}} locked"));
        t.add(new TextDef("iam.users.searchPH", "搜索 姓名/工号/组织", "Search name / emp no / org"));
        t.add(new TextDef("iam.users.refresh", "刷新", "Refresh"));
        t.add(new TextDef("iam.users.refreshing", "刷新中", "Refreshing"));
        t.add(new TextDef("iam.users.newAccount", "新建账号", "New account"));
        t.add(new TextDef("iam.users.th.user", "用户", "User"));
        t.add(new TextDef("iam.users.th.empNo", "工号", "Emp No."));
        t.add(new TextDef("iam.users.th.source", "来源", "Source"));
        t.add(new TextDef("iam.users.th.org", "组织", "Org"));
        t.add(new TextDef("iam.users.th.status", "状态", "Status"));
        t.add(new TextDef("iam.users.th.admission", "准入", "Admission"));
        t.add(new TextDef("iam.users.th.actions", "操作", "Actions"));
        t.add(new TextDef("iam.users.statusEnabled", "启用", "Enabled"));
        t.add(new TextDef("iam.users.statusDisabled", "禁用", "Disabled"));
        t.add(new TextDef("iam.users.tagLocked", "锁定", "Locked"));
        t.add(new TextDef("iam.users.tagProfileDisabled", "档案停用", "Profile disabled"));
        t.add(new TextDef("iam.users.tagProfileOnly", "仅档案", "Profile only"));
        t.add(new TextDef("iam.users.noOrg", "未归属", "Unassigned"));
        t.add(new TextDef("iam.users.btnProfile", "档案", "Profile"));
        t.add(new TextDef("iam.users.btnOrgs", "归属", "Org"));
        t.add(new TextDef("iam.users.btnDisable", "禁用", "Disable"));
        t.add(new TextDef("iam.users.btnEnable", "启用", "Enable"));
        t.add(new TextDef("iam.users.btnReset", "重置", "Reset"));
        t.add(new TextDef("iam.users.btnUnlock", "解锁", "Unlock"));
        t.add(new TextDef("iam.users.emptyMatch", "无匹配用户", "No matching users"));
        t.add(new TextDef("iam.users.emptyNone", "暂无用户", "No users yet"));
        t.add(new TextDef("iam.users.emptyHintMatch", "换个关键词试试", "Try another keyword"));
        t.add(new TextDef("iam.users.emptyHintNone", "点击右上「新建账号」创建第一个账号", "Click “New account” to create the first one"));
        t.add(new TextDef("iam.users.createTitle", "新建账号", "New account"));
        t.add(new TextDef("iam.users.createSub", "口令在浏览器内完成第一层 PBKDF2 派生后上传；档案字段可留空后续补充",
                "Password is first-layer PBKDF2 derived in the browser before upload; profile fields may be filled later"));
        t.add(new TextDef("iam.users.f.username", "用户名", "Username"));
        t.add(new TextDef("iam.users.f.password", "初始口令", "Initial password"));
        t.add(new TextDef("iam.users.f.name", "姓名", "Name"));
        t.add(new TextDef("iam.users.f.empNo", "工号", "Emp No."));
        t.add(new TextDef("iam.users.f.job", "岗位", "Job title"));
        t.add(new TextDef("iam.users.createSubmit", "创建", "Create"));
        t.add(new TextDef("iam.users.creating", "创建中…", "Creating…"));
        t.add(new TextDef("iam.users.cancel", "取消", "Cancel"));
        t.add(new TextDef("iam.users.createHint",
                "新建账号默认无任何准入。建议：在「组织架构」把该用户挂到工序 → 由「组织授权」自动获得准入；或到「准入授权」单独授予。",
                "New accounts have no admission by default. Suggest: attach the user to a process in “Organization”, then “Org Grants” grants admission automatically; or grant directly in “Admissions”."));
        t.add(new TextDef("iam.users.resetTitle", "重置口令 · {{id}}", "Reset password · {{id}}"));
        t.add(new TextDef("iam.users.resetSub", "重置成功后该用户所有会话立即失效，需以新口令重登",
                "After reset, all sessions are invalidated; the user must sign in again"));
        t.add(new TextDef("iam.users.resetNew", "新口令", "New password"));
        t.add(new TextDef("iam.users.resetSubmit", "确认重置", "Confirm reset"));
        t.add(new TextDef("iam.users.submitting", "提交中…", "Submitting…"));
        t.add(new TextDef("iam.users.profileTitle", "档案 · {{id}}", "Profile · {{id}}"));
        t.add(new TextDef("iam.users.profileSubAd", "AD 同步档案：姓名/邮箱/手机只读，仅岗位可在 IAM 侧维护",
                "AD-synced profile: name/email/phone are read-only; only job title is maintained on IAM"));
        t.add(new TextDef("iam.users.profileSubIam", "IAM 自建档案：可自由维护", "IAM-managed profile: freely maintained"));
        t.add(new TextDef("iam.users.f.email", "邮箱", "Email"));
        t.add(new TextDef("iam.users.f.mobile", "手机", "Mobile"));
        t.add(new TextDef("iam.users.save", "保存", "Save"));
        t.add(new TextDef("iam.users.saving", "保存中…", "Saving…"));
        t.add(new TextDef("iam.users.profileHint", "工号 {{no}}、来源 {{src}}。岗位由 IAM 维护（AD 常无此维度），可用于按岗位批量授权。",
                "Emp No. {{no}}, source {{src}}. Job title is maintained by IAM (AD often lacks this dimension) and can drive batch authorization by job."));
        t.add(new TextDef("iam.users.orgsTitle", "组织归属 · {{id}}", "Org membership · {{id}}"));
        t.add(new TextDef("iam.users.orgsSub", "支持多归属（多能工 / 跨线支援）；保存后该用户会话失效，重登即按新归属获得准入",
                "Supports multiple memberships (multi-skill / cross-line); after save the session is invalidated and re-login applies the new membership"));
        t.add(new TextDef("iam.users.primaryOrg", "主属组织", "Primary org"));
        t.add(new TextDef("iam.users.primaryNone", "（不指定）", "(none)"));
        t.add(new TextDef("iam.users.saveOrgs", "保存归属", "Save membership"));
        t.add(new TextDef("iam.users.orgsNone", "尚未建立组织，请先到「组织架构」创建", "No org yet; create one in “Organization” first"));
        t.add(new TextDef("iam.users.orgsHint", "主属组织用于展示与默认数据权限范围。归属变更会 bump 该用户令牌版本 → 旧令牌 401 → 重登即带新准入。",
                "Primary org is used for display and default data scope. Membership change bumps the token version → old tokens 401 → re-login carries new admission."));
        t.add(new TextDef("iam.users.usageTitle", "使用说明", "Usage notes"));
        t.add(new TextDef("iam.users.usageSub", "身份生命周期", "Identity lifecycle"));
        t.add(new TextDef("iam.users.usageOnboard", "入职：建账号（或由 AD 同步自动建档案）→ 在「组织架构」挂到工序 → 组织授权自动生效；例外再走「准入授权」。",
                "Onboarding: create an account (or AD sync auto-creates the profile) → attach to a process in “Organization” → org grant applies automatically; exceptions go through “Admissions”."));
        t.add(new TextDef("iam.users.usageTransfer", "转岗：调整组织归属即可——组织授权随归属自动增减（个人授予需手动处理）。",
                "Transfer: just adjust org membership — org grants follow automatically (personal grants need manual handling)."));
        t.add(new TextDef("iam.users.usageOffboard", "停用/离职：禁用账号或把档案置为停用，会自动 bump 令牌版本 → 所有存量令牌立即失效。",
                "Offboarding: disable the account or set the profile inactive; this bumps the token version → all existing tokens are invalidated immediately."));
        t.add(new TextDef("iam.users.usagePwd", "口令遗忘：重置口令（自动强制下线）；AD 账号请在企业目录侧改密，IAM 不代管。",
                "Forgot password: reset it (forces logout automatically); for AD accounts change it in the corporate directory — IAM does not manage it."));
        t.add(new TextDef("iam.users.msgCreated", "已创建账号 {{name}}", "Account {{name}} created"));
        t.add(new TextDef("iam.users.msgEnabled", "已启用", "Enabled"));
        t.add(new TextDef("iam.users.msgDisabled", "已禁用（已强制下线）", "Disabled (forced logout)"));
        t.add(new TextDef("iam.users.msgUnlocked", "{{user}} 已解锁", "{{user}} unlocked"));
        t.add(new TextDef("iam.users.msgNoLock", "{{user}} 无锁定记录", "{{user}} has no lock record"));
        t.add(new TextDef("iam.users.msgResetDone", "已重置 {{id}} 的口令（该用户已强制下线）", "Password for {{id}} reset (user forced offline)"));
        t.add(new TextDef("iam.users.msgProfileSaved", "已保存档案", "Profile saved"));
        t.add(new TextDef("iam.users.msgOrgsSaved", "已保存组织归属（该用户已强制下线，重登后生效）", "Membership saved (user forced offline; takes effect after re-login)"));
        t.add(new TextDef("iam.users.msgDeleted", "已删除账号 {{name}}", "Account {{name}} deleted"));
        t.add(new TextDef("iam.users.confirmDelete", "确认删除账号 {{name}}？该操作不可恢复，其存量令牌将立即失效。",
                "Delete account {{name}}? This cannot be undone; its existing tokens will be invalidated immediately."));
        t.add(new TextDef("iam.users.confirmAdDelete", "{{name}} 是 AD 同步用户（无本地凭证），IAM 侧不提供删除。请在企业目录中处理。",
                "{{name}} is AD-synced (no local credential); IAM does not delete it. Handle it in the corporate directory."));
        t.add(new TextDef("iam.users.errLoad", "加载用户列表失败", "Failed to load users"));
        t.add(new TextDef("iam.users.errCreate", "创建失败", "Create failed"));
        t.add(new TextDef("iam.users.errOp", "操作失败", "Operation failed"));
        t.add(new TextDef("iam.users.errReset", "重置失败", "Reset failed"));
        t.add(new TextDef("iam.users.errUnlock", "解锁失败", "Unlock failed"));
        t.add(new TextDef("iam.users.errSaveProfile", "保存失败", "Save failed"));
        t.add(new TextDef("iam.users.errSaveOrgs", "保存归属失败", "Failed to save membership"));
        t.add(new TextDef("iam.users.errDelete", "删除失败", "Delete failed"));
        t.add(new TextDef("iam.users.phName", "张三", "Zhang San"));
        t.add(new TextDef("iam.users.phJob", "蚀刻操作员", "Etch operator"));

        // —— OrgPage（iam.orgs.*）——
        t.add(new TextDef("iam.orgs.title", "组织树", "Org tree"));
        t.add(new TextDef("iam.orgs.sub", "AD 同步的行政部门只读；制造组织由 IAM 自建", "AD-synced departments are read-only; manufacturing orgs are IAM-managed"));
        t.add(new TextDef("iam.orgs.count", "{{n}} 节点", "{{n}} nodes"));
        t.add(new TextDef("iam.orgs.refresh", "刷新", "Refresh"));
        t.add(new TextDef("iam.orgs.refreshing", "刷新中", "Refreshing"));
        t.add(new TextDef("iam.orgs.newNode", "新建节点", "New node"));
        t.add(new TextDef("iam.orgs.emptyTitle", "暂无组织节点", "No org nodes yet"));
        t.add(new TextDef("iam.orgs.emptyHint", "点击右上「新建节点」建立第一个厂区", "Click “New node” to create the first site"));
        t.add(new TextDef("iam.orgs.nodeTitle", "节点 · {{name}}", "Node · {{name}}"));
        t.add(new TextDef("iam.orgs.nodeSub", "{{src}}{{ro}}", "{{src}}{{ro}}"));
        t.add(new TextDef("iam.orgs.nodeReadOnly", "（只读）", "(read-only)"));
        t.add(new TextDef("iam.orgs.selectNode", "在左侧选择一个节点", "Select a node on the left"));
        t.add(new TextDef("iam.orgs.dt.code", "编码", "Code"));
        t.add(new TextDef("iam.orgs.dt.type", "类型", "Type"));
        t.add(new TextDef("iam.orgs.dt.source", "来源", "Source"));
        t.add(new TextDef("iam.orgs.dt.status", "状态", "Status"));
        t.add(new TextDef("iam.orgs.dt.path", "物化路径", "Materialized path"));
        t.add(new TextDef("iam.orgs.dt.updated", "最近变更", "Last changed"));
        t.add(new TextDef("iam.orgs.statusEnabled", "启用", "Enabled"));
        t.add(new TextDef("iam.orgs.statusDisabled", "停用", "Disabled"));
        t.add(new TextDef("iam.orgs.f.name", "名称", "Name"));
        t.add(new TextDef("iam.orgs.f.sort", "排序", "Order"));
        t.add(new TextDef("iam.orgs.f.status", "状态", "Status"));
        t.add(new TextDef("iam.orgs.optEnabled", "启用", "Enabled"));
        t.add(new TextDef("iam.orgs.optDisabled", "停用", "Disabled"));
        t.add(new TextDef("iam.orgs.save", "保存", "Save"));
        t.add(new TextDef("iam.orgs.moveTo", "移动到父节点", "Move to parent"));
        t.add(new TextDef("iam.orgs.noParent", "（无 / 作为根节点）", "(none / as root)"));
        t.add(new TextDef("iam.orgs.move", "移动", "Move"));
        t.add(new TextDef("iam.orgs.delete", "删除", "Delete"));
        t.add(new TextDef("iam.orgs.adReadOnly", "该节点由 AD 同步而来，IAM 侧只读——请在企业目录（AD/HR）中维护，同步器只写自己那一份。",
                "This node is AD-synced and read-only on IAM — maintain it in the corporate directory (AD/HR); the syncer only writes its own share."));
        t.add(new TextDef("iam.orgs.membersTitle", "组织成员", "Org members"));
        t.add(new TextDef("iam.orgs.membersCount", "{{n}} 人", "{{n}} members"));
        t.add(new TextDef("iam.orgs.memberPH", "输入或选择用户 ID", "Type or pick a user ID"));
        t.add(new TextDef("iam.orgs.addMember", "挂到该组织", "Add to org"));
        t.add(new TextDef("iam.orgs.removeMember", "移除", "Remove"));
        t.add(new TextDef("iam.orgs.noMembers", "暂无成员", "No members yet"));
        t.add(new TextDef("iam.orgs.memberHint", "成员归属支持多归属（一人可同属多个工序/产线）。归属变更会 bump 用户令牌版本，其会话立即失效。",
                "Membership supports multiple orgs (one person in several processes/lines). A change bumps the user's token version and invalidates the session immediately."));
        t.add(new TextDef("iam.orgs.newTitle", "新建组织节点", "New org node"));
        t.add(new TextDef("iam.orgs.newSub", "IAM 自建（制造维度）；编码建议直接沿用 MES 侧既定编码，不另造第二套",
                "IAM-managed (manufacturing dimension); reuse the MES-side code directly, do not invent a second one"));
        t.add(new TextDef("iam.orgs.f.parent", "父节点", "Parent"));
        t.add(new TextDef("iam.orgs.f.code", "编码", "Code"));
        t.add(new TextDef("iam.orgs.namePH", "蚀刻 1 线", "Etch line 1"));
        t.add(new TextDef("iam.orgs.f.type", "类型", "Type"));
        t.add(new TextDef("iam.orgs.create", "创建", "Create"));
        t.add(new TextDef("iam.orgs.creating", "创建中…", "Creating…"));
        t.add(new TextDef("iam.orgs.cancel", "取消", "Cancel"));
        t.add(new TextDef("iam.orgs.hint", "组织是「按组织批量授权」与数据权限的上游：把树建对 → 挂人 → 在「组织授权」给组织授予 ap 与角色组。",
                "Orgs are the upstream of org-based batch grants and data scope: build the tree right → attach people → grant ap & role groups in “Org Grants”."));
        t.add(new TextDef("iam.orgs.msgCreated", "已新建组织 {{name}}", "Org {{name}} created"));
        t.add(new TextDef("iam.orgs.msgSaved", "已保存组织信息", "Org info saved"));
        t.add(new TextDef("iam.orgs.msgMoved", "已移动组织（子树路径已重写，相关用户令牌已失效需重登）", "Org moved (subtree paths rewritten; affected users' tokens invalidated, re-login required)"));
        t.add(new TextDef("iam.orgs.msgDeleted", "已删除组织", "Org deleted"));
        t.add(new TextDef("iam.orgs.msgMemberAdded", "{{uid}} 已归属该组织", "{{uid}} now belongs to this org"));
        t.add(new TextDef("iam.orgs.msgMemberMoved", "已把 {{uid}} 挂到该组织", "{{uid}} attached to this org"));
        t.add(new TextDef("iam.orgs.msgMemberRemoved", "已从该组织移除 {{uid}}", "{{uid}} removed from this org"));
        t.add(new TextDef("iam.orgs.confirmDelete", "确认删除组织「{{name}}」？须无子节点、无人员归属、无组织授予。",
                "Delete org “{{name}}”? It must have no children, no members, and no org grants."));
        t.add(new TextDef("iam.orgs.errLoad", "加载组织失败", "Failed to load orgs"));
        t.add(new TextDef("iam.orgs.errCreate", "新建失败", "Create failed"));
        t.add(new TextDef("iam.orgs.errSave", "保存失败", "Save failed"));
        t.add(new TextDef("iam.orgs.errMove", "移动失败", "Move failed"));
        t.add(new TextDef("iam.orgs.errDelete", "删除失败", "Delete failed"));
        t.add(new TextDef("iam.orgs.errMember", "挂人失败", "Failed to add member"));
        t.add(new TextDef("iam.orgs.errRemove", "移除失败", "Failed to remove"));
        t.add(new TextDef("iam.orgs.nodeDetail", "节点详情", "Node details"));
        t.add(new TextDef("iam.orgs.nodeNotSelected", "未选择节点", "No node selected"));
        t.add(new TextDef("iam.orgs.selectHint", "选择左侧任一节点查看与维护", "Select any node on the left to view & manage"));
        t.add(new TextDef("iam.orgs.collapse", "折叠", "Collapse"));
        t.add(new TextDef("iam.orgs.expand", "展开", "Expand"));

        // —— DashboardPage（iam.dashboard.*）——
        t.add(new TextDef("iam.dashboard.platformTitle", "平台概览", "Platform overview"));
        t.add(new TextDef("iam.dashboard.loadingPlatform", "正在加载平台态势…", "Loading platform status…"));
        t.add(new TextDef("iam.dashboard.kpiUser", "用户账号", "User accounts"));
        t.add(new TextDef("iam.dashboard.kpiUserSub", "{{n}} 已禁用", "{{n}} disabled"));
        t.add(new TextDef("iam.dashboard.kpiProfile", "用户档案", "Profiles"));
        t.add(new TextDef("iam.dashboard.kpiProfileSub", "{{n}} AD 同步", "{{n}} AD synced"));
        t.add(new TextDef("iam.dashboard.kpiOrg", "组织节点", "Org nodes"));
        t.add(new TextDef("iam.dashboard.kpiOrgSub", "{{n}} IAM 自建", "{{n}} IAM-managed"));
        t.add(new TextDef("iam.dashboard.kpiApp", "接入应用", "Apps"));
        t.add(new TextDef("iam.dashboard.kpiAppSub", "启用 / 总数", "enabled / total"));
        t.add(new TextDef("iam.dashboard.kpiSession", "在线会话", "Sessions"));
        t.add(new TextDef("iam.dashboard.kpiSessionSub", "活跃刷新令牌", "active refresh tokens"));
        t.add(new TextDef("iam.dashboard.kpiLock", "锁定账户", "Locked accounts"));
        t.add(new TextDef("iam.dashboard.kpiLockSub", "暴力破解防护", "brute-force protection"));
        t.add(new TextDef("iam.dashboard.kpiLoginOk", "登录成功", "Sign-in success"));
        t.add(new TextDef("iam.dashboard.kpiLoginFail", "登录失败", "Sign-in failure"));
        t.add(new TextDef("iam.dashboard.kpiLoginFailSub", "累计，需关注", "cumulative, watch"));
        t.add(new TextDef("iam.dashboard.recentAudit", "最近审计事件", "Recent audit events"));
        t.add(new TextDef("iam.dashboard.recentAuditSub", "登录、授权、改密等关键动作流水", "Key actions: sign-in, grants, password changes"));
        t.add(new TextDef("iam.dashboard.viewAll", "查看全部", "View all"));
        t.add(new TextDef("iam.dashboard.th.time", "时间", "Time"));
        t.add(new TextDef("iam.dashboard.th.type", "类型", "Type"));
        t.add(new TextDef("iam.dashboard.th.operator", "操作人", "Operator"));
        t.add(new TextDef("iam.dashboard.th.target", "对象", "Target"));
        t.add(new TextDef("iam.dashboard.th.detail", "说明", "Detail"));
        t.add(new TextDef("iam.dashboard.noAudit", "暂无审计事件", "No audit events yet"));
        t.add(new TextDef("iam.dashboard.profileTitle", "概览", "Overview"));
        t.add(new TextDef("iam.dashboard.loadingUser", "正在加载用户信息…", "Loading user info…"));
        t.add(new TextDef("iam.dashboard.kpiUserId", "用户 ID", "User ID"));
        t.add(new TextDef("iam.dashboard.kpiApps", "可进入应用", "Accessible apps"));
        t.add(new TextDef("iam.dashboard.kpiRoles", "角色组", "Role groups"));
        t.add(new TextDef("iam.dashboard.kpiOrgs", "组织归属", "Org membership"));
        t.add(new TextDef("iam.dashboard.identityTitle", "身份信息", "Identity"));
        t.add(new TextDef("iam.dashboard.identitySub", "来自用户档案与会话", "From profile & session"));
        t.add(new TextDef("iam.dashboard.mk.userId", "用户 ID", "User ID"));
        t.add(new TextDef("iam.dashboard.mk.username", "用户名", "Username"));
        t.add(new TextDef("iam.dashboard.mk.name", "姓名", "Name"));
        t.add(new TextDef("iam.dashboard.mk.empNo", "工号", "Emp No."));
        t.add(new TextDef("iam.dashboard.mk.job", "岗位", "Job title"));
        t.add(new TextDef("iam.dashboard.mk.source", "档案来源", "Profile source"));
        t.add(new TextDef("iam.dashboard.mk.tenant", "租户", "Tenant"));
        t.add(new TextDef("iam.dashboard.myOrgsTitle", "我的组织归属", "My org membership"));
        t.add(new TextDef("iam.dashboard.myOrgsSub", "支持多归属（多能工 / 跨线支援）", "Multi-membership supported (multi-skill / cross-line)"));
        t.add(new TextDef("iam.dashboard.th.org", "组织", "Org"));
        t.add(new TextDef("iam.dashboard.th.primary", "主属", "Primary"));
        t.add(new TextDef("iam.dashboard.yes", "是", "Yes"));
        t.add(new TextDef("iam.dashboard.noOrgs", "尚未归属任何组织", "Not assigned to any org"));
        t.add(new TextDef("iam.dashboard.noOrgsHint", "请联系管理员在「组织架构」中把你挂到工序/产线", "Ask an admin to attach you to a process/line in “Organization”"));
        t.add(new TextDef("iam.dashboard.sessionTitle", "会话与安全", "Session & security"));
        t.add(new TextDef("iam.dashboard.sessionSub", "令牌签发与验证方式", "Token issuance & verification"));
        t.add(new TextDef("iam.dashboard.mk.authSrc", "认证源", "Auth source"));
        t.add(new TextDef("iam.dashboard.v.localCred", "本地凭证（两层 PBKDF2）", "Local credential (two-layer PBKDF2)"));
        t.add(new TextDef("iam.dashboard.mk.signAlgo", "签名算法", "Signing algo"));
        t.add(new TextDef("iam.dashboard.mk.verify", "验签方式", "Verification"));
        t.add(new TextDef("iam.dashboard.v.jwks", "JWKS 公钥，业务侧本地验签", "JWKS public key, verified locally by services"));
        t.add(new TextDef("iam.dashboard.mk.admission", "准入判定", "Admission rule"));
        t.add(new TextDef("iam.dashboard.v.appsClaim", "apps claim（个人授予 ∪ 组织授予）", "apps claim (personal ∪ org grants)"));
        t.add(new TextDef("iam.dashboard.mk.invalid", "失效机制", "Invalidation"));
        t.add(new TextDef("iam.dashboard.v.version", "版本号校验 + 黑名单（jti）", "Version check + blacklist (jti)"));
        t.add(new TextDef("iam.dashboard.matrixTitle", "准入与角色矩阵", "Admission & role matrix"));
        t.add(new TextDef("iam.dashboard.matrixSub", "按接入码分组的准入状态与角色组", "Admission status & role groups by app code"));
        t.add(new TextDef("iam.dashboard.th.appCode", "接入码", "App code"));
        t.add(new TextDef("iam.dashboard.th.admission", "准入", "Admission"));
        t.add(new TextDef("iam.dashboard.th.roles", "角色组", "Role groups"));
        t.add(new TextDef("iam.dashboard.admitted", "已准入", "Admitted"));
        t.add(new TextDef("iam.dashboard.noRole", "无角色", "No role"));
        t.add(new TextDef("iam.dashboard.noApp", "暂无准入应用", "No admitted apps"));
        t.add(new TextDef("iam.dashboard.noAppHint", "请联系管理员在「准入授权」中分配", "Ask an admin to assign one in “Admissions”"));
        t.add(new TextDef("iam.dashboard.errLoad", "加载概览失败", "Failed to load overview"));
        t.add(new TextDef("iam.dashboard.kpiCumulative", "累计", "Cumulative"));
        t.add(new TextDef("iam.dashboard.matrixCount", "{{n}} 个接入码", "{{n}} app codes"));

        // —— SettingsPage（iam.settings.*）——
        t.add(new TextDef("iam.settings.title", "系统设置", "System settings"));
        t.add(new TextDef("iam.settings.loading", "正在加载生效配置…", "Loading effective config…"));
        t.add(new TextDef("iam.settings.authTitle", "认证与口令", "Auth & password"));
        t.add(new TextDef("iam.settings.authSub", "登录认证源与两层派生参数", "Sign-in source & two-layer derivation params"));
        t.add(new TextDef("iam.settings.mk.authSrc", "认证源", "Auth source"));
        t.add(new TextDef("iam.settings.mk.pbkdf2Rounds", "PBKDF2 轮数", "PBKDF2 rounds"));
        t.add(new TextDef("iam.settings.mk.pepper", "服务端 pepper", "Server pepper"));
        t.add(new TextDef("iam.settings.configured", "已配置", "Configured"));
        t.add(new TextDef("iam.settings.pepperMissing", "未配置（不安全）", "Not configured (insecure)"));
        t.add(new TextDef("iam.settings.mk.bootstrap", "首管理员引导", "First-admin bootstrap"));
        t.add(new TextDef("iam.settings.enabled", "启用", "Enabled"));
        t.add(new TextDef("iam.settings.disabled", "停用", "Disabled"));
        t.add(new TextDef("iam.settings.tokenTitle", "令牌与签名", "Tokens & signing"));
        t.add(new TextDef("iam.settings.tokenSub", "JWT 签发参数与密钥来源", "JWT issuance params & key source"));
        t.add(new TextDef("iam.settings.mk.accessTtl", "访问令牌 TTL", "Access token TTL"));
        t.add(new TextDef("iam.settings.v.minutes", "{{n}} 分钟", "{{n}} min"));
        t.add(new TextDef("iam.settings.mk.refreshTtl", "刷新令牌 TTL", "Refresh token TTL"));
        t.add(new TextDef("iam.settings.v.days", "{{n}} 天", "{{n}} d"));
        t.add(new TextDef("iam.settings.mk.rsaSrc", "RSA 私钥来源", "RSA private key source"));
        t.add(new TextDef("iam.settings.kms", "KMS 注入", "KMS injected"));
        t.add(new TextDef("iam.settings.rsaTemp", "启动临时生成", "Generated at startup (temp)"));
        t.add(new TextDef("iam.settings.mk.jwks", "JWKS 端点", "JWKS endpoint"));
        t.add(new TextDef("iam.settings.lockTitle", "登录锁定策略", "Lockout policy"));
        t.add(new TextDef("iam.settings.lockSub", "暴力破解防护（滑动窗口）", "Brute-force protection (sliding window)"));
        t.add(new TextDef("iam.settings.mk.threshold", "失败阈值", "Failure threshold"));
        t.add(new TextDef("iam.settings.mk.lockMin", "锁定时长", "Lock duration"));
        t.add(new TextDef("iam.settings.mk.window", "计数窗口", "Counting window"));
        t.add(new TextDef("iam.settings.corsTitle", "跨域白名单", "CORS allowlist"));
        t.add(new TextDef("iam.settings.corsSub", "允许访问本服务的前端源", "Frontend origins allowed to access this service"));
        t.add(new TextDef("iam.settings.notConfigured", "未配置", "Not configured"));
        t.add(new TextDef("iam.settings.dirApiTitle", "身份目录 · 只读 API", "Identity directory · read-only API"));
        t.add(new TextDef("iam.settings.dirApiSub", "供业务 ap 拉取档案与组织（服务身份认证）", "For business aps to pull profiles & orgs (service auth)"));
        t.add(new TextDef("iam.settings.mk.dirApi", "目录 API", "Directory API"));
        t.add(new TextDef("iam.settings.dirEnabled", "已启用", "Enabled"));
        t.add(new TextDef("iam.settings.dirDisabled", "未启用（未配置密钥）", "Disabled (no key configured)"));
        t.add(new TextDef("iam.settings.mk.authMethod", "认证方式", "Auth method"));
        t.add(new TextDef("iam.settings.v.dirKey", "X-Directory-Key（方案 A）", "X-Directory-Key (scheme A)"));
        t.add(new TextDef("iam.settings.mk.dirEp", "目录端点", "Directory endpoint"));
        t.add(new TextDef("iam.settings.mk.watermark", "水位机制", "Watermark"));
        t.add(new TextDef("iam.settings.v.watermark", "user / org 单调版本号，业务侧比对后重拉", "Monotonic user/org version numbers; services re-pull after comparing"));
        t.add(new TextDef("iam.settings.adSyncTitle", "身份目录 · AD 同步", "Identity directory · AD sync"));
        t.add(new TextDef("iam.settings.adSyncSub", "只读同步人员与行政组织（未配置即跳过）", "Read-only sync of people & admin orgs (skipped if unconfigured)"));
        t.add(new TextDef("iam.settings.mk.syncSwitch", "同步开关", "Sync switch"));
        t.add(new TextDef("iam.settings.mk.connCfg", "可连接配置", "Connectivity"));
        t.add(new TextDef("iam.settings.ready", "已就绪", "Ready"));
        t.add(new TextDef("iam.settings.syncNotCfg", "未配置 → 同步跳过", "Unconfigured → sync skipped"));
        t.add(new TextDef("iam.settings.mk.baseDn", "检索基址", "Search base DN"));
        t.add(new TextDef("iam.settings.mk.interval", "同步间隔", "Sync interval"));
        t.add(new TextDef("iam.settings.mk.crossSrc", "跨源保护", "Cross-source protection"));
        t.add(new TextDef("iam.settings.v.crossSrc", "只写 AD_SYNCED，绝不覆盖 IAM 自建节点", "Only writes AD_SYNCED; never overwrites IAM-managed nodes"));
        t.add(new TextDef("iam.settings.sync", "立即同步", "Sync now"));
        t.add(new TextDef("iam.settings.syncing", "同步中…", "Syncing…"));
        t.add(new TextDef("iam.settings.noteTitle", "说明", "Notes"));
        t.add(new TextDef("iam.settings.noteSub", "为何是只读", "Why read-only"));
        t.add(new TextDef("iam.settings.note1", "这些参数影响签发与验证的一致性，在线修改会与已签发令牌/业务验证端产生漂移。",
                "These params affect signing/verification consistency; online edits would drift from issued tokens / verifiers."));
        t.add(new TextDef("iam.settings.note2", "如需调整，请通过配置中心 / 环境变量发布，并重启生效。",
                "To change them, publish via config center / env vars and restart."));
        t.add(new TextDef("iam.settings.note3", "本页用于让管理员核对当前生效值，便于排障与合规审计。",
                "This page lets admins verify current values for troubleshooting & compliance."));
        t.add(new TextDef("iam.settings.msgSkipped", "已跳过：{{reason}}", "Skipped: {{reason}}"));
        t.add(new TextDef("iam.settings.msgDone", "同步完成：组织 {{orgs}}、人员 {{users}}、停用 {{deactivated}}", "Sync done: orgs {{orgs}}, people {{users}}, deactivated {{deactivated}}"));
        t.add(new TextDef("iam.settings.errLoad", "加载设置失败", "Failed to load settings"));
        t.add(new TextDef("iam.settings.errSync", "同步失败", "Sync failed"));
        // W4b 补充：数值单位与开关态（catalog 中无现成 key）
        t.add(new TextDef("iam.settings.v.times", "{{n}} 次", "{{n}} times"));
        t.add(new TextDef("iam.settings.v.seconds", "{{n}} 秒", "{{n}} s"));
        t.add(new TextDef("iam.settings.on", "开启", "On"));

        // —— OrgGrantsPage（iam.orgGrants.*）——
        t.add(new TextDef("iam.orgGrants.title", "组织授予清单", "Org grant list"));
        t.add(new TextDef("iam.orgGrants.sub", "组织 → ap → 粗角色组；签发令牌时按用户归属（含祖先链）实时展开",
                "Org → app → coarse role group; expanded at token issuance by user membership (incl. ancestor chain)"));
        t.add(new TextDef("iam.orgGrants.count", "{{n}} 条", "{{n}} rows"));
        t.add(new TextDef("iam.orgGrants.refresh", "刷新", "Refresh"));
        t.add(new TextDef("iam.orgGrants.refreshing", "刷新中", "Refreshing"));
        t.add(new TextDef("iam.orgGrants.th.org", "组织", "Org"));
        t.add(new TextDef("iam.orgGrants.th.code", "编码", "Code"));
        t.add(new TextDef("iam.orgGrants.th.appCode", "接入码", "App code"));
        t.add(new TextDef("iam.orgGrants.th.roles", "角色组", "Role groups"));
        t.add(new TextDef("iam.orgGrants.th.children", "覆盖子组织", "Include children"));
        t.add(new TextDef("iam.orgGrants.th.actions", "操作", "Actions"));
        t.add(new TextDef("iam.orgGrants.onlyAdmission", "（仅准入，无角色）", "(admission only, no role)"));
        t.add(new TextDef("iam.orgGrants.yes", "是", "Yes"));
        t.add(new TextDef("iam.orgGrants.no", "否", "No"));
        t.add(new TextDef("iam.orgGrants.revoke", "撤销", "Revoke"));
        t.add(new TextDef("iam.orgGrants.emptyTitle", "暂无组织授予", "No org grants yet"));
        t.add(new TextDef("iam.orgGrants.emptyHint", "在下方按组织批量授予准入", "Grant admission by org below"));
        t.add(new TextDef("iam.orgGrants.grantTitle", "授予组织准入", "Grant org admission"));
        t.add(new TextDef("iam.orgGrants.grantSub", "先把组织树建对并挂人，再给组织授权——人员进出组织自动继承/失去准入",
                "Build the org tree and attach people first, then grant — membership changes auto-apply/revoke admission"));
        t.add(new TextDef("iam.orgGrants.f.org", "组织", "Org"));
        t.add(new TextDef("iam.orgGrants.selectOrg", "请选择组织", "Select an org"));
        t.add(new TextDef("iam.orgGrants.f.appCode", "接入码", "App code"));
        t.add(new TextDef("iam.orgGrants.selectApp", "请选择应用", "Select an app"));
        t.add(new TextDef("iam.orgGrants.f.roles", "角色组（逗号分隔）", "Role groups (comma-separated)"));
        t.add(new TextDef("iam.orgGrants.includeChildren", "覆盖子组织", "Include child orgs"));
        t.add(new TextDef("iam.orgGrants.grant", "授予", "Grant"));
        t.add(new TextDef("iam.orgGrants.granting", "授予中…", "Granting…"));
        t.add(new TextDef("iam.orgGrants.hint", "勾选「覆盖子组织」时，授予作用于该组织及其全部后代；不勾选则仅作用于本组织直属人员。",
                "With “include children”, the grant covers the org and all descendants; otherwise only its direct members."));
        t.add(new TextDef("iam.orgGrants.affected", "当前所选组织（含子组织）共 {{n}} 人，授予后其令牌版本将被 bump、需重新登录。",
                "The selected org (incl. children) has {{n}} people; after grant their token version is bumped and they must re-login."));
        t.add(new TextDef("iam.orgGrants.divTitle", "与「准入授权」的分工", "Division of labor with “Admissions”"));
        t.add(new TextDef("iam.orgGrants.divSub", "个人 vs 组织", "Personal vs org"));
        t.add(new TextDef("iam.orgGrants.divPersonal", "个人准入（准入授权页）：给某个用户单独开某 ap 的准入与角色组，用于例外与临时授权。",
                "Personal admission (Admissions page): grant a specific user an app's admission & roles, for exceptions & temporary access."));
        t.add(new TextDef("iam.orgGrants.divOrg", "组织授予（本页）：给组织整体开准入，组织内人员（含子组织）自动生效——入职即通、转岗即变、离职即断。",
                "Org grant (this page): grant the whole org; members (incl. children) get it automatically — onboard=granted, transfer=changed, offboard=revoked."));
        t.add(new TextDef("iam.orgGrants.divUnion", "两者取并集：个人授予只能追加、不能扣减。要收回权限请撤销组织授予或把该用户移出组织。",
                "The two are unioned: personal grants can only add, never subtract. To revoke, remove the org grant or detach the user."));
        t.add(new TextDef("iam.orgGrants.divAd", "AD 组织（标记［AD］）由同步器维护，可作为授权目标；但 IAM 侧不能改其名称与层级。",
                "AD orgs (marked [AD]) are maintained by the syncer and can be grant targets; IAM cannot change their name/hierarchy."));
        t.add(new TextDef("iam.orgGrants.divIam", "IAM 只管准入，不介入业务系统内部权限", "IAM only manages admission, not business-internal permissions"));
        t.add(new TextDef("iam.orgGrants.msgGranted", "已授予 {{app}}（受影响用户令牌已失效，需重登生效）", "Granted {{app}} (affected users' tokens invalidated; re-login required)"));
        t.add(new TextDef("iam.orgGrants.msgRevoked", "已撤销组织授予", "Org grant revoked"));
        t.add(new TextDef("iam.orgGrants.confirmRevoke", "确认撤销「{{org}}」的 {{app}} 授予？", "Revoke the grant of {{app}} for “{{org}}”?"));
        t.add(new TextDef("iam.orgGrants.errLoad", "加载组织授权失败", "Failed to load org grants"));
        t.add(new TextDef("iam.orgGrants.errGrant", "授予失败", "Grant failed"));
        t.add(new TextDef("iam.orgGrants.errRevoke", "撤销失败", "Revoke failed"));
        // W4b 补充：组织类型参考行的片段（orgTypeLabel/originLabel 由 format.ts 内部翻译，此处仅包文字）
        t.add(new TextDef("iam.orgGrants.typeRef", "组织类型参考：", "Org types:"));
        t.add(new TextDef("iam.orgGrants.localMaintain", "；来源 {{src}} 可本地维护。", "; source {{src}} can be maintained locally."));

        // —— ProfilePage（iam.profile.*）——
        t.add(new TextDef("iam.profile.infoTitle", "账户信息", "Account info"));
        t.add(new TextDef("iam.profile.infoSub", "当前登录身份与档案", "Current identity & profile"));
        t.add(new TextDef("iam.profile.mk.userId", "用户 ID", "User ID"));
        t.add(new TextDef("iam.profile.mk.username", "用户名", "Username"));
        t.add(new TextDef("iam.profile.mk.name", "姓名", "Name"));
        t.add(new TextDef("iam.profile.mk.empNo", "工号", "Emp No."));
        t.add(new TextDef("iam.profile.mk.job", "岗位", "Job title"));
        t.add(new TextDef("iam.profile.mk.source", "档案来源", "Profile source"));
        t.add(new TextDef("iam.profile.mk.tenant", "租户", "Tenant"));
        t.add(new TextDef("iam.profile.mk.apps", "准入应用", "Admitted apps"));
        t.add(new TextDef("iam.profile.subTitleOrgs", "我的组织归属", "My org membership"));
        t.add(new TextDef("iam.profile.noOrgs", "尚未归属任何组织——归属由管理员在「组织架构」中维护。", "Not assigned to any org — an admin maintains it in “Organization”."));
        t.add(new TextDef("iam.profile.secTitle", "安全提示", "Security notes"));
        t.add(new TextDef("iam.profile.sec1", "改密后全部已签发令牌立即失效，需重新登录。", "After a password change all issued tokens are invalidated; re-login required."));
        t.add(new TextDef("iam.profile.sec2", "AD / LDAP 账号的口令变更请在目录侧完成。", "For AD / LDAP accounts, change the password on the directory side."));
        t.add(new TextDef("iam.profile.pwdTitle", "修改口令", "Change password"));
        t.add(new TextDef("iam.profile.pwdSub", "修改成功后需以新口令重新登录", "After success you must sign in with the new password"));
        t.add(new TextDef("iam.profile.f.old", "原口令", "Current password"));
        t.add(new TextDef("iam.profile.f.new", "新口令", "New password"));
        t.add(new TextDef("iam.profile.f.confirm", "确认新口令", "Confirm new password"));
        t.add(new TextDef("iam.profile.submit", "提交修改", "Submit change"));
        t.add(new TextDef("iam.profile.submitting", "提交中…", "Submitting…"));
        t.add(new TextDef("iam.profile.hint", "口令在浏览器内完成第一层 PBKDF2 派生（与登录一致），仅密文派生值上传；明文口令不离开浏览器。",
                "The first-layer PBKDF2 derivation runs in the browser (like sign-in); only the derived ciphertext is uploaded; the plain password never leaves the browser."));
        t.add(new TextDef("iam.profile.errSession", "当前会话缺失用户名，请重新登录", "Session has no username; please sign in again"));
        t.add(new TextDef("iam.profile.errMismatch", "两次输入的新口令不一致", "The two new passwords do not match"));
        t.add(new TextDef("iam.profile.errChange", "改密失败", "Password change failed"));
        // W4b 补充：改密成功后的跳转提示（经路由 state 透传到登录页展示）
        t.add(new TextDef("iam.profile.pwdUpdatedNotice", "口令已更新，请重新登录", "Password updated, please sign in again"));

        // —— AppMgmtPage（iam.appMgmt.*）——
        t.add(new TextDef("iam.appMgmt.title", "应用清单", "App list"));
        t.add(new TextDef("iam.appMgmt.sub", "已注册的业务接入码；接入码即令牌 apps claim 的取值", "Registered business app codes; the code is the value of the token's apps claim"));
        t.add(new TextDef("iam.appMgmt.count", "{{n}} 条", "{{n}} rows"));
        t.add(new TextDef("iam.appMgmt.enabled", "{{n}} 启用", "{{n}} enabled"));
        t.add(new TextDef("iam.appMgmt.refresh", "刷新", "Refresh"));
        t.add(new TextDef("iam.appMgmt.refreshing", "刷新中", "Refreshing"));
        t.add(new TextDef("iam.appMgmt.th.appCode", "接入码", "App code"));
        t.add(new TextDef("iam.appMgmt.th.name", "应用名称", "App name"));
        t.add(new TextDef("iam.appMgmt.th.status", "状态", "Status"));
        t.add(new TextDef("iam.appMgmt.th.sort", "排序", "Order"));
        t.add(new TextDef("iam.appMgmt.th.actions", "操作", "Actions"));
        t.add(new TextDef("iam.appMgmt.enabled2", "启用", "Enabled"));
        t.add(new TextDef("iam.appMgmt.disabled2", "停用", "Disabled"));
        t.add(new TextDef("iam.appMgmt.disable", "停用", "Disable"));
        t.add(new TextDef("iam.appMgmt.enable", "启用", "Enable"));
        t.add(new TextDef("iam.appMgmt.emptyTitle", "暂无应用", "No apps yet"));
        t.add(new TextDef("iam.appMgmt.emptyHint", "在下方「注册应用」中添加第一个业务接入码", "Add the first app code under “Register app”"));
        t.add(new TextDef("iam.appMgmt.registerTitle", "注册应用", "Register app"));
        t.add(new TextDef("iam.appMgmt.registerSub", "接入码将写入令牌的 apps claim", "The app code is written into the token's apps claim"));
        t.add(new TextDef("iam.appMgmt.f.appCode", "接入码", "App code"));
        t.add(new TextDef("iam.appMgmt.f.name", "应用名称", "App name"));
        t.add(new TextDef("iam.appMgmt.namePH", "MDS 设备数据服务", "MDS device data service"));
        t.add(new TextDef("iam.appMgmt.f.sort", "排序号", "Sort order"));
        t.add(new TextDef("iam.appMgmt.register", "注册应用", "Register app"));
        t.add(new TextDef("iam.appMgmt.registering", "注册中…", "Registering…"));
        t.add(new TextDef("iam.appMgmt.hint", "接入码全局唯一，注册后即纳入 IAM 的 ap 注册表，用于准入判定。", "The app code is globally unique and enters IAM's ap registry for admission checks."));
        t.add(new TextDef("iam.appMgmt.guideTitle", "接入指引", "Integration guide"));
        t.add(new TextDef("iam.appMgmt.guideSub", "业务 ap 如何对接 IAM 令牌", "How a business ap integrates with IAM tokens"));
        t.add(new TextDef("iam.appMgmt.guide1", "业务 ap 从 /.well-known/jwks.json 拉取公钥，本地验签（RS256），无需每请求回查 IAM。", "The ap fetches the public key from /.well-known/jwks.json and verifies locally (RS256), no per-request IAM call."));
        t.add(new TextDef("iam.appMgmt.guide2", "准入判定：令牌 apps claim 含本 ap 接入码即放行；否则 403。", "Admission: if the token's apps claim contains this ap's code, allow; otherwise 403."));
        t.add(new TextDef("iam.appMgmt.guide3", "角色组：令牌 roles claim 携带本 ap 内的粗角色组，业务内部权限据此再细分。", "Role groups: the token's roles claim carries this ap's coarse groups; business-internal rights refine from there."));
        t.add(new TextDef("iam.appMgmt.msgRegistered", "已注册应用 {{code}}", "App {{code}} registered"));
        t.add(new TextDef("iam.appMgmt.msgToggled", "{{code}} 已{{state}}", "{{code}} {{state}}"));
        t.add(new TextDef("iam.appMgmt.stateEnabled", "启用", "enabled"));
        t.add(new TextDef("iam.appMgmt.stateDisabled", "停用", "disabled"));
        t.add(new TextDef("iam.appMgmt.confirmDisable", "确认停用 {{code}}？其下所有用户对该应用的准入将立即失效。", "Disable {{code}}? All of its users' admission to this app is revoked immediately."));
        t.add(new TextDef("iam.appMgmt.errLoad", "加载应用列表失败", "Failed to load apps"));
        t.add(new TextDef("iam.appMgmt.errRegister", "注册失败", "Register failed"));
        t.add(new TextDef("iam.appMgmt.errUpdate", "更新失败", "Update failed"));

        // —— SessionsPage（iam.sessions.*）——
        t.add(new TextDef("iam.sessions.title", "活跃会话", "Active sessions"));
        t.add(new TextDef("iam.sessions.sub", "以未撤销且未过期的刷新令牌近似表示一个登录会话", "Approximated by un-revoked, unexpired refresh tokens"));
        t.add(new TextDef("iam.sessions.count", "{{n}} 个", "{{n}}"));
        t.add(new TextDef("iam.sessions.refresh", "刷新", "Refresh"));
        t.add(new TextDef("iam.sessions.refreshing", "刷新中", "Refreshing"));
        t.add(new TextDef("iam.sessions.th.user", "用户", "User"));
        t.add(new TextDef("iam.sessions.th.userId", "用户 ID", "User ID"));
        t.add(new TextDef("iam.sessions.th.jti", "访问令牌 jti", "Access token jti"));
        t.add(new TextDef("iam.sessions.th.expire", "到期时间", "Expires"));
        t.add(new TextDef("iam.sessions.th.actions", "操作", "Actions"));
        t.add(new TextDef("iam.sessions.kick", "强制下线", "Force logout"));
        t.add(new TextDef("iam.sessions.emptyTitle", "当前无活跃会话", "No active sessions"));
        t.add(new TextDef("iam.sessions.emptyHint", "用户登录后会在此出现", "Appears after users sign in"));
        t.add(new TextDef("iam.sessions.kickTitle", "按用户 ID 强制下线", "Force logout by user ID"));
        t.add(new TextDef("iam.sessions.kickSub", "bump 令牌版本，验证端进程内即时校验，无缓存滞后", "Bumps the token version; verified in-process by the verifier with no cache lag"));
        t.add(new TextDef("iam.sessions.highRisk", "高危操作", "High-risk operation"));
        t.add(new TextDef("iam.sessions.f.userId", "用户 ID", "User ID"));
        t.add(new TextDef("iam.sessions.userIdPH", "admin（本地账号即用户名）", "admin (local account = username)"));
        t.add(new TextDef("iam.sessions.process", "踢下线", "Kick"));
        t.add(new TextDef("iam.sessions.processing", "处理中…", "Processing…"));
        t.add(new TextDef("iam.sessions.bumpDone", "已 bump 用户 {{uid}}，当前令牌版本号 {{version}}", "Bumped user {{uid}}; current token version {{version}}"));
        t.add(new TextDef("iam.sessions.note1", "作用对象：该用户已签发的全部访问令牌与刷新令牌。", "Target: all access & refresh tokens the user has issued."));
        t.add(new TextDef("iam.sessions.note2", "生效方式：验证端比对令牌 ver claim 与库内版本号，不一致即返回 401。", "Effect: the verifier compares the token's ver claim with the stored version; mismatch → 401."));
        t.add(new TextDef("iam.sessions.note3", "后续影响：用户需重新登录；令牌版本号单调递增，不回滚。", "Aftermath: the user must re-login; the version is monotonic and never rolls back."));
        t.add(new TextDef("iam.sessions.confirm", "确认强制 {{username}} 下线？其所有已签发令牌将立即失效。", "Force {{username}} offline? All issued tokens are invalidated immediately."));
        t.add(new TextDef("iam.sessions.msgKicked", "已强制 {{username}} 下线，令牌版本 → {{version}}", "Forced {{username}} offline; token version → {{version}}"));
        t.add(new TextDef("iam.sessions.errLoad", "加载会话失败", "Failed to load sessions"));
        t.add(new TextDef("iam.sessions.errOp", "操作失败", "Operation failed"));
        t.add(new TextDef("iam.sessions.errKick", "强制下线失败", "Force logout failed"));

        // —— AdmissionsPage（iam.admissions.*）——
        t.add(new TextDef("iam.admissions.title", "按用户授权", "Authorize by user"));
        t.add(new TextDef("iam.admissions.sub", "输入用户 ID 查看其准入矩阵，逐条授予 / 撤销", "Enter a user ID to see their admission matrix; grant / revoke per row"));
        t.add(new TextDef("iam.admissions.f.userId", "用户 ID", "User ID"));
        t.add(new TextDef("iam.admissions.query", "查询", "Query"));
        t.add(new TextDef("iam.admissions.th.appCode", "接入码", "App code"));
        t.add(new TextDef("iam.admissions.th.appName", "应用名称", "App name"));
        t.add(new TextDef("iam.admissions.th.admission", "准入", "Admission"));
        t.add(new TextDef("iam.admissions.th.roles", "角色组（逗号分隔，可编辑）", "Role groups (comma-separated, editable)"));
        t.add(new TextDef("iam.admissions.th.actions", "操作", "Actions"));
        t.add(new TextDef("iam.admissions.admitted", "已准入", "Admitted"));
        t.add(new TextDef("iam.admissions.notAdmitted", "未准入", "Not admitted"));
        t.add(new TextDef("iam.admissions.update", "更新", "Update"));
        t.add(new TextDef("iam.admissions.grant", "授予", "Grant"));
        t.add(new TextDef("iam.admissions.revoke", "撤销", "Revoke"));
        t.add(new TextDef("iam.admissions.emptyTitle", "输入用户 ID 后点击「查询」", "Enter a user ID then click “Query”"));
        t.add(new TextDef("iam.admissions.emptyHint", "将列出全部已注册应用及其准入 / 角色状态", "Lists all registered apps with their admission / role status"));
        t.add(new TextDef("iam.admissions.impactTitle", "变更影响", "Change impact"));
        t.add(new TextDef("iam.admissions.impactSub", "准入变更即时生效", "Admission changes take effect immediately"));
        t.add(new TextDef("iam.admissions.impact1", "授予 / 更新 / 撤销准入都会 bump 该用户的令牌版本，其存量令牌立即失效（需重新登录）。", "Grant / update / revoke all bump the user's token version; existing tokens invalidated (re-login required)."));
        t.add(new TextDef("iam.admissions.impact2", "禁用某应用后，其下所有已分配用户的准入一并失效（登录时 apps claim 不再包含该接入码）。", "Disabling an app revokes admission for all its assigned users (apps claim drops the code at sign-in)."));
        t.add(new TextDef("iam.admissions.impact3", "角色组为 ap 内粗粒度分组（如 ADMIN / OPERATOR），业务系统的菜单/按钮权限由业务 ap 自行控制。", "Role groups are coarse ap-internal groups (e.g. ADMIN / OPERATOR); the ap controls its own menu/button rights."));
        t.add(new TextDef("iam.admissions.msgGranted", "已授予 {{user}} 进入 {{app}}（角色：{{roles}}）", "Granted {{user}} access to {{app}} (roles: {{roles}})"));
        t.add(new TextDef("iam.admissions.msgRevoked", "已撤销 {{user}} 在 {{app}} 的准入", "Revoked {{user}}'s admission to {{app}}"));
        t.add(new TextDef("iam.admissions.errLoad", "加载应用失败", "Failed to load apps"));
        t.add(new TextDef("iam.admissions.errQuery", "查询失败", "Query failed"));
        t.add(new TextDef("iam.admissions.errGrant", "授予失败", "Grant failed"));
        t.add(new TextDef("iam.admissions.errRevoke", "撤销失败", "Revoke failed"));
        t.add(new TextDef("iam.admissions.noRole", "无", "None"));

        // —— LockoutsPage（iam.lockouts.*）——
        t.add(new TextDef("iam.lockouts.title", "锁定中的账号", "Locked accounts"));
        t.add(new TextDef("iam.lockouts.sub", "连续登录失败达阈值触发的临时锁定；到期自动解除，也可手动解锁", "Temporary lock from consecutive failures past threshold; auto-released on expiry, or manually unlocked"));
        t.add(new TextDef("iam.lockouts.count", "{{n}} 个", "{{n}}"));
        t.add(new TextDef("iam.lockouts.refresh", "刷新", "Refresh"));
        t.add(new TextDef("iam.lockouts.refreshing", "刷新中", "Refreshing"));
        t.add(new TextDef("iam.lockouts.th.username", "用户名", "Username"));
        t.add(new TextDef("iam.lockouts.th.failures", "失败次数", "Failures"));
        t.add(new TextDef("iam.lockouts.th.first", "首次失败", "First failure"));
        t.add(new TextDef("iam.lockouts.th.last", "最近失败", "Last failure"));
        t.add(new TextDef("iam.lockouts.th.until", "锁定至", "Locked until"));
        t.add(new TextDef("iam.lockouts.th.actions", "操作", "Actions"));
        t.add(new TextDef("iam.lockouts.unlock", "解锁", "Unlock"));
        t.add(new TextDef("iam.lockouts.emptyTitle", "当前无锁定账号", "No locked accounts"));
        t.add(new TextDef("iam.lockouts.emptyHint", "登录失败达阈值后会自动出现在此处", "Appears automatically once failures hit the threshold"));
        t.add(new TextDef("iam.lockouts.policyTitle", "锁定策略", "Lockout policy"));
        t.add(new TextDef("iam.lockouts.policySub", "滑动窗口内累计失败触发", "Triggered by cumulative failures within a sliding window"));
        t.add(new TextDef("iam.lockouts.policy1", "窗口内连续失败达阈值即锁定；窗口过后历史失败清零（滑动窗口）。", "Consecutive failures hit the threshold → lock; after the window, history clears (sliding window)."));
        t.add(new TextDef("iam.lockouts.policy2", "登录成功会立即清零计数，不会因成功登录而长期锁定。", "A successful sign-in clears the count immediately; success never causes a long lock."));
        t.add(new TextDef("iam.lockouts.policy3", "锁定期间即使口令正确也会被拒（不区分用户是否存在，防账号枚举）。", "During lock, even the correct password is rejected (user existence is not disclosed, anti-enumeration)."));
        t.add(new TextDef("iam.lockouts.policy4", "策略参数（阈值 / 锁定时长 / 窗口）见「系统设置」。", "Policy params (threshold / duration / window) are in “System settings”."));
        t.add(new TextDef("iam.lockouts.msgUnlocked", "已解锁 {{user}}", "Unlocked {{user}}"));
        t.add(new TextDef("iam.lockouts.msgNoLock", "{{user}} 无锁定记录", "{{user}} has no lock record"));
        t.add(new TextDef("iam.lockouts.errLoad", "加载锁定列表失败", "Failed to load lockouts"));
        t.add(new TextDef("iam.lockouts.errUnlock", "解锁失败", "Unlock failed"));

        // —— AuditPage（iam.audit.*）——
        t.add(new TextDef("iam.audit.title", "审计流水", "Audit trail"));
        t.add(new TextDef("iam.audit.sub", "登录、登出、改密、应用与准入变更、强制下线等动作的追溯记录", "Trace of sign-in/out, password changes, app & admission changes, forced logout"));
        t.add(new TextDef("iam.audit.filterType", "全部类型", "All types"));
        t.add(new TextDef("iam.audit.recent100", "最近 100", "Last 100"));
        t.add(new TextDef("iam.audit.recent200", "最近 200", "Last 200"));
        t.add(new TextDef("iam.audit.recent500", "最近 500", "Last 500"));
        t.add(new TextDef("iam.audit.refresh", "刷新", "Refresh"));
        t.add(new TextDef("iam.audit.refreshing", "刷新中", "Refreshing"));
        t.add(new TextDef("iam.audit.th.time", "时间", "Time"));
        t.add(new TextDef("iam.audit.th.type", "类型", "Type"));
        t.add(new TextDef("iam.audit.th.result", "结果", "Result"));
        t.add(new TextDef("iam.audit.th.operator", "操作人", "Operator"));
        t.add(new TextDef("iam.audit.th.target", "对象", "Target"));
        t.add(new TextDef("iam.audit.th.detail", "说明", "Detail"));
        t.add(new TextDef("iam.audit.resultFail", "失败", "Failure"));
        t.add(new TextDef("iam.audit.resultOk", "成功", "Success"));
        t.add(new TextDef("iam.audit.emptyTitle", "暂无审计事件", "No audit events yet"));
        t.add(new TextDef("iam.audit.emptyHint", "关键动作发生后会自动记录", "Key actions are recorded automatically"));
        t.add(new TextDef("iam.audit.scopeTitle", "记录范围", "Coverage"));
        t.add(new TextDef("iam.audit.scopeSub", "审计埋点覆盖的关键路径", "Key paths covered by audit instrumentation"));
        t.add(new TextDef("iam.audit.errLoad", "加载审计日志失败", "Failed to load audit log"));

        // —— RolesPage（iam.rolesView.*）——
        t.add(new TextDef("iam.rolesView.title", "角色组分布", "Role group distribution"));
        t.add(new TextDef("iam.rolesView.sub", "按接入码聚合，展示各 app 内实际分配的粗角色组及人数", "Aggregated by app code; shows actual coarse role groups per app and headcount"));
        t.add(new TextDef("iam.rolesView.countApps", "{{n}} 个接入码", "{{n}} app codes"));
        t.add(new TextDef("iam.rolesView.countRoles", "{{n}} 个角色组", "{{n}} role groups"));
        t.add(new TextDef("iam.rolesView.refresh", "刷新", "Refresh"));
        t.add(new TextDef("iam.rolesView.refreshing", "刷新中", "Refreshing"));
        t.add(new TextDef("iam.rolesView.th.appCode", "接入码", "App code"));
        t.add(new TextDef("iam.rolesView.th.appName", "应用名称", "App name"));
        t.add(new TextDef("iam.rolesView.th.roles", "角色组与人数", "Role groups & headcount"));
        t.add(new TextDef("iam.rolesView.userCount", "{{n}} 名用户", "{{n}} users"));
        t.add(new TextDef("iam.rolesView.noRole", "未被分配任何角色组", "No role group assigned"));
        t.add(new TextDef("iam.rolesView.emptyTitle", "暂无应用", "No apps yet"));
        t.add(new TextDef("iam.rolesView.emptyHint", "先在「应用注册」登记接入码", "Register an app code in “App registration” first"));
        t.add(new TextDef("iam.rolesView.aboutTitle", "关于角色组", "About role groups"));
        t.add(new TextDef("iam.rolesView.aboutSub", "IAM 只管准入，不管业务内部权限", "IAM manages admission only, not business-internal rights"));
        t.add(new TextDef("iam.rolesView.about1", "角色组是「ap 内的粗粒度分组」，由准入授权时写入，随令牌 roles claim 下发。", "Role groups are coarse ap-internal groups, written at admission time and carried by the token's roles claim."));
        t.add(new TextDef("iam.rolesView.about2", "本视图不维护独立字典，而是聚合实际在用值，避免「定义与实际脱节」。", "This view keeps no separate dictionary; it aggregates live values to avoid drift between definition and reality."));
        t.add(new TextDef("iam.rolesView.about3", "业务系统内的菜单 / 按钮 / 数据行权限由各业务 ap 自行控制（IAM 不介入）。", "Menu/button/data-row rights inside a business ap are controlled by that ap (IAM does not intervene)."));
        t.add(new TextDef("iam.rolesView.errLoad", "加载角色组失败", "Failed to load role groups"));

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
