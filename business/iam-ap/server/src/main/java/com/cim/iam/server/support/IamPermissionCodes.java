package com.cim.iam.server.support;

/**
 * IAM 控制台权限码（{@code module:res:action}，对齐 {@code com.cim.system.support.PermissionCodes}）。
 *
 * <p><b>归属澄清</b>：这些码描述的是「IAM <b>这个 ap 内部</b>的菜单/按钮/接口权限」，
 * 不是「跨业务准入」。准入仍由令牌 {@code apps} claim 决定（IAM 只管准入，见
 * {@code docs/business/iam-ap/server/README.md} §0）。控制台的内部权限复用平台
 * {@code cim-system} 的 RBAC，故权限码建在 IAM 自己的库里。</p>
 *
 * <p>集中一处拼装，避免「注解里写的码」与「库里种的码」漂移——
 * 这是 RBAC 最常见的线上故障来源（同 {@code cim-system} 的 {@code PermissionCodes}）。</p>
 */
public final class IamPermissionCodes {

    /** 模块名：IAM 控制台。 */
    public static final String MODULE_IAM = "iam";

    /**
     * 控制台粗管理员码（<b>类级兜底</b>）。
     *
     * <p>各管理控制器上仍保留类级 {@code @PreAuthorize("hasAuthority(...)")} 作为
     * <b>安全网</b>：方法级注解优先，未逐个标注的方法退回本码。由此「新增端点忘了写方法级注解」
     * 的最坏结果是「只有控制台管理员能调」，而不是「任何人都能调」。</p>
     *
     * <p>刻意用合法的三段式（{@code module:res:action}）而非旧的两段式 {@code iam-ap:ADMIN}，
     * 以通过 {@code cim-system} 的权限码校验；灰度期由种子写入并授予 {@code IAM_ADMIN}。</p>
     */
    public static final String CONSOLE_ADMIN = "iam:console:admin";

    // ---- 概览 ----
    public static final String OVERVIEW_VIEW = "iam:overview:view";

    // ---- 组织架构 ----
    public static final String ORG_LIST = "iam:org:list";
    public static final String ORG_CREATE = "iam:org:create";
    public static final String ORG_UPDATE = "iam:org:update";
    public static final String ORG_MOVE = "iam:org:move";
    public static final String ORG_DELETE = "iam:org:delete";
    public static final String ORG_MEMBER = "iam:org:member";

    // ---- 用户与档案 ----
    public static final String USER_LIST = "iam:user:list";
    public static final String USER_CREATE = "iam:user:create";
    public static final String USER_UPDATE = "iam:user:update";
    public static final String USER_STATUS = "iam:user:status";
    public static final String USER_RESET_PWD = "iam:user:reset-pwd";
    public static final String USER_UNLOCK = "iam:user:unlock";
    public static final String USER_ORGS = "iam:user:orgs";
    public static final String USER_DELETE = "iam:user:delete";

    // ---- 应用注册 ----
    public static final String APP_LIST = "iam:app:list";
    public static final String APP_CREATE = "iam:app:create";
    public static final String APP_UPDATE = "iam:app:update";

    // ---- 个人准入 ----
    public static final String ADMISSION_LIST = "iam:admission:list";
    public static final String ADMISSION_GRANT = "iam:admission:grant";
    public static final String ADMISSION_REVOKE = "iam:admission:revoke";

    // ---- 组织授予 ----
    public static final String GRANT_LIST = "iam:grant:list";
    public static final String GRANT_GRANT = "iam:grant:grant";
    public static final String GRANT_REVOKE = "iam:grant:revoke";

    // ---- 角色组（准入角色视图） ----
    public static final String ROLE_LIST = "iam:role:list";

    // ---- 会话 ----
    public static final String SESSION_LIST = "iam:session:list";
    public static final String SESSION_KICK = "iam:session:kick";

    // ---- 审计 ----
    public static final String AUDIT_LIST = "iam:audit:list";

    // ---- 系统设置 ----
    public static final String SETTINGS_VIEW = "iam:settings:view";

    // ---- 目录同步（扩展：设计稿 §4.4 未列，实现时补充） ----
    public static final String SYNC_RUN = "iam:sync:run";

    // ---- 菜单管理 ----
    public static final String MENU_LIST = "iam:menu:list";
    public static final String MENU_CREATE = "iam:menu:create";
    public static final String MENU_UPDATE = "iam:menu:update";
    public static final String MENU_DELETE = "iam:menu:delete";

    // ---- 权限管理 ----
    public static final String PERM_LIST = "iam:perm:list";
    public static final String PERM_CREATE = "iam:perm:create";
    public static final String PERM_UPDATE = "iam:perm:update";
    public static final String PERM_DELETE = "iam:perm:delete";

    // ---- 角色管理（控制台角色 CRUD + 授权） ----
    public static final String ROLE_ADMIN_LIST = "iam:role-admin:list";
    public static final String ROLE_ADMIN_CREATE = "iam:role-admin:create";
    public static final String ROLE_ADMIN_UPDATE = "iam:role-admin:update";
    public static final String ROLE_ADMIN_DELETE = "iam:role-admin:delete";
    public static final String ROLE_ADMIN_GRANT = "iam:role-admin:grant";

    // ---- 多语言 ----
    public static final String I18N_LIST = "iam:i18n:list";
    public static final String I18N_CREATE = "iam:i18n:create";
    public static final String I18N_UPDATE = "iam:i18n:update";
    public static final String I18N_DELETE = "iam:i18n:delete";
    public static final String I18N_LOCALE = "iam:i18n:locale";

    private IamPermissionCodes() {
    }

    /** 拼装权限码（{@code module:res:action}）。 */
    public static String of(String module, String res, String action) {
        return module + ":" + res + ":" + action;
    }
}
