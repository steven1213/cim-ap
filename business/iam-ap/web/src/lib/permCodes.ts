// IAM 控制台权限码常量（前端侧镜像）。
//
// <p>与 server 端 `com.cim.iam.server.support.IamPermissionCodes` **一一对应**：那里是注解里写的码、
// 这里是 `<Perms>` 里写的码，两边不一致就会出现「按钮可见但接口 403」的错配。
// 集中一处拼装可把这类漂移降到最低（与后端同一考量，见该类的 javadoc）。</p>
//
// <p>⚠️ 新增权限码时**两侧都要加**；后端 `IamConsoleCatalog.permissions()` 负责入库，前端本文件负责引用。</p>

export const CONSOLE_ADMIN = 'iam:console:admin';

// ---- 概览 ----
export const OVERVIEW_VIEW = 'iam:overview:view';

// ---- 组织架构 ----
export const ORG_LIST = 'iam:org:list';
export const ORG_CREATE = 'iam:org:create';
export const ORG_UPDATE = 'iam:org:update';
export const ORG_MOVE = 'iam:org:move';
export const ORG_DELETE = 'iam:org:delete';
export const ORG_MEMBER = 'iam:org:member';

// ---- 用户与档案 ----
export const USER_LIST = 'iam:user:list';
export const USER_CREATE = 'iam:user:create';
export const USER_UPDATE = 'iam:user:update';
export const USER_STATUS = 'iam:user:status';
export const USER_RESET_PWD = 'iam:user:reset-pwd';
export const USER_UNLOCK = 'iam:user:unlock';
export const USER_ORGS = 'iam:user:orgs';
export const USER_DELETE = 'iam:user:delete';

// ---- 应用注册 ----
export const APP_LIST = 'iam:app:list';
export const APP_CREATE = 'iam:app:create';
export const APP_UPDATE = 'iam:app:update';

// ---- 个人准入 ----
export const ADMISSION_LIST = 'iam:admission:list';
export const ADMISSION_GRANT = 'iam:admission:grant';
export const ADMISSION_REVOKE = 'iam:admission:revoke';

// ---- 组织授予 ----
export const GRANT_LIST = 'iam:grant:list';
export const GRANT_GRANT = 'iam:grant:grant';
export const GRANT_REVOKE = 'iam:grant:revoke';

// ---- 角色组视图 ----
export const ROLE_LIST = 'iam:role:list';

// ---- 会话 ----
export const SESSION_LIST = 'iam:session:list';
export const SESSION_KICK = 'iam:session:kick';

// ---- 审计 ----
export const AUDIT_LIST = 'iam:audit:list';

// ---- 系统设置 ----
export const SETTINGS_VIEW = 'iam:settings:view';

// ---- 目录同步 ----
export const SYNC_RUN = 'iam:sync:run';

// ---- 菜单管理 ----
export const MENU_LIST = 'iam:menu:list';
export const MENU_CREATE = 'iam:menu:create';
export const MENU_UPDATE = 'iam:menu:update';
export const MENU_DELETE = 'iam:menu:delete';

// ---- 权限管理 ----
export const PERM_LIST = 'iam:perm:list';
export const PERM_CREATE = 'iam:perm:create';
export const PERM_UPDATE = 'iam:perm:update';
export const PERM_DELETE = 'iam:perm:delete';

// ---- 角色管理 ----
export const ROLE_ADMIN_LIST = 'iam:role-admin:list';
export const ROLE_ADMIN_CREATE = 'iam:role-admin:create';
export const ROLE_ADMIN_UPDATE = 'iam:role-admin:update';
export const ROLE_ADMIN_DELETE = 'iam:role-admin:delete';
export const ROLE_ADMIN_GRANT = 'iam:role-admin:grant';

// ---- 多语言 ----
export const I18N_LIST = 'iam:i18n:list';
export const I18N_CREATE = 'iam:i18n:create';
export const I18N_UPDATE = 'iam:i18n:update';
export const I18N_DELETE = 'iam:i18n:delete';
export const I18N_LOCALE = 'iam:i18n:locale';

/**
 * 平台系统域权限码（`sys:*`，镜像 `com.cim.system.support.PermissionCodes`）。
 *
 * <p><b>为什么控制台要引用平台码</b>：三个配置页（菜单/权限/角色）的**数据面**落在平台
 * `cim-system` 的 `/sys/**` 端点上（复用其 CRUD，见 `console-menu-perm-i18n.md` §6），
 * 而**那些端点用 `sys:*` 鉴权**。控制台自己的 `iam:menu:*` / `iam:perm:*` / `iam:role-admin:*`
 * 只决定「页面与按钮是否显示」。两者是**双闸门**：</p>
 *
 * <pre>能操作 ⟺ 持有 IAM 控制台码 ∧ 持有平台码</pre>
 *
 * <p>超管（`IAM_ADMIN`）由「库内全部启用权限码」短路展开，天然同时具备两族；
 * 自定义角色需在「角色管理」里把两族都勾上。只读运维角色（`IAM_OPERATOR`）由种子补平台只读码，
 * 故能查看但不能修改。前端按双闸门渲染，避免出现「按钮可见但接口 403」的错配。</p>
 */
export const SYS = {
  USER_LIST: 'sys:user:list',
  USER_SAVE: 'sys:user:save',
  USER_REMOVE: 'sys:user:remove',
  USER_GRANT: 'sys:user:grant',
  ROLE_LIST: 'sys:role:list',
  ROLE_SAVE: 'sys:role:save',
  ROLE_REMOVE: 'sys:role:remove',
  ROLE_GRANT: 'sys:role:grant',
  MENU_LIST: 'sys:menu:list',
  MENU_SAVE: 'sys:menu:save',
  MENU_REMOVE: 'sys:menu:remove',
  PERM_LIST: 'sys:permission:list',
  PERM_SAVE: 'sys:permission:save',
  PERM_REMOVE: 'sys:permission:remove',
} as const;
