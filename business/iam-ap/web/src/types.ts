// IAM 控制台共享类型（与 business/iam-ap/server 的 Result/Controller 契约对齐）

/** 登录 / 刷新返回：访问令牌 + 刷新令牌 + 有效期（秒）+ 类型。 */
export interface LoginResult {
  token: string;
  refreshToken: string;
  expiresInSeconds: number;
  tokenType: string;
}

/** 组织摘要（用户归属 / 目录 API 共用）。 */
export interface OrgBrief {
  orgId: string;
  code: string;
  name: string;
  nodeType: string | null;
  primary: boolean;
}

/** GET /api/v1/me 返回。 */
export interface MeDto {
  userId: string;
  username: string;
  tenantId: string | null;
  /** 姓名（来自用户档案）。 */
  displayName: string | null;
  /** 工号（AD employeeID）。 */
  employeeNo: string | null;
  /** 岗位（IAM 维护）。 */
  jobTitle: string | null;
  /** 档案来源：AD_SYNCED / IAM_MANAGED（无档案时为 null）。 */
  source: string | null;
  /** 组织归属（支持多归属）。 */
  orgs: OrgBrief[];
  /** 当前用户可进入的 ap 接入码列表（个人授予 ∪ 组织授予）。 */
  apps: string[];
  /** 按 ap 分组的角色：{ "iam-ap": ["ADMIN"] }。 */
  roles: Record<string, string[]>;
}

export type AppStatus = 'ENABLED' | 'DISABLED';

/** ap 注册表实体（继承基础数据：含 id 等）。 */
export interface AppRegistration {
  id: string;
  appCode: string;
  appName: string;
  status: AppStatus;
  sortNo: number;
}

/** GET /api/v1/login/salt 返回。 */
export interface SaltResponse {
  username: string;
  clientSalt: string;
}

/** POST /api/v1/internal/token-version/bump 返回。 */
export interface TokenVersionBump {
  uid: string;
  version: number;
}

/** POST /api/v1/me/password 请求体（自助改密，与登录一致收客户端第一层派生后的 clientHash）。 */
export interface ChangePasswordRequest {
  /** 旧口令第一层派生后的 clientHash。 */
  oldCredential: string;
  /** 新口令第一层派生后的 clientHash。 */
  newCredential: string;
  /** 新口令第一层派生所用的随机盐（由客户端生成，随请求下发）。 */
  newClientSalt: string;
}

/** 审计事件（GET /api/v1/admin/audit）。 */
export interface AuditEvent {
  id: string;
  type: string;
  actor: string | null;
  subject: string | null;
  result: 'SUCCESS' | 'FAILURE' | null;
  detail: string | null;
  createTime: string | null;
}

/** 平台态势概览（GET /api/v1/admin/overview）。 */
export interface OverviewDto {
  userCount: number;
  enabledUserCount: number;
  disabledUserCount: number;
  appCount: number;
  enabledAppCount: number;
  activeSessionCount: number;
  lockedCount: number;
  loginSuccessCount: number;
  loginFailureCount: number;
  /** 组织节点总数（含 AD 同步层与 IAM 自建层）。 */
  orgCount: number;
  /** IAM 自建组织节点数。 */
  orgManagedCount: number;
  /** 用户档案总数。 */
  profileCount: number;
  /** AD 同步来源的档案数。 */
  adProfileCount: number;
  recentEvents: AuditEvent[];
}

/** 管理面用户行（GET /api/v1/admin/users，本地凭证 ∪ 用户档案）。 */
export interface AdminUserRow {
  userId: string;
  username: string;
  enabled: boolean;
  /** 是否有本地凭证（false 表示 AD 同步来的纯档案用户）。 */
  hasCredential: boolean;
  locked: boolean;
  lockedUntil: string | null;
  failCount: number;
  displayName: string | null;
  employeeNo: string | null;
  jobTitle: string | null;
  /** AD_SYNCED / IAM_MANAGED / NONE（无档案）。 */
  source: string;
  /** ACTIVE / INACTIVE / null。 */
  status: string | null;
  orgNames: string[];
  apps: string[];
  roles: Record<string, string[]>;
}

/** 组织节点（GET /api/v1/admin/orgs，扁平含 parentId）。 */
export type OrgStatus = 'ENABLED' | 'DISABLED';
export type OrgNodeType = 'AREA' | 'WORKSHOP' | 'LINE' | 'PROCESS' | 'TEAM' | 'DEPT';
export type DataOrigin = 'AD_SYNCED' | 'IAM_MANAGED';

export interface OrgNode {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  nodeType: OrgNodeType;
  path: string;
  sortNo: number;
  source: DataOrigin;
  status: OrgStatus;
  updatedAt: string | null;
}

/** 组织级准入授予（GET /api/v1/admin/org-grants）。 */
export interface OrgGrantRow {
  orgId: string;
  orgName: string | null;
  orgCode: string | null;
  appCode: string;
  roles: string[];
  includeChildren: boolean;
  status: string;
}

/** 用户档案行（GET /api/v1/admin/profiles）。 */
export interface ProfileRow {
  userId: string;
  employeeNo: string | null;
  displayName: string | null;
  email: string | null;
  mobile: string | null;
  jobTitle: string | null;
  status: string;
  source: DataOrigin;
  syncedAt: string | null;
  orgNames: string[];
}

/** 目录水位（GET /api/v1/admin/sync/watermark）。 */
export interface WatermarkDto {
  user: number;
  org: number;
}

/** AD 同步结果（POST /api/v1/admin/sync/ad）。 */
export interface SyncResult {
  skipped: boolean;
  reason: string | null;
  orgCount: number;
  userCount: number;
  deactivated: number;
}

/** 在线会话行（GET /api/v1/admin/sessions）。 */
export interface SessionRow {
  userId: string;
  username: string;
  accessTokenJti: string | null;
  expiresAt: string;
}

/** 强制下线结果。 */
export interface KickResult {
  uid: string;
  version: number;
}

/** 锁定行（GET /api/v1/admin/lockouts）。 */
export interface LockRow {
  username: string;
  failCount: number;
  firstFailAt: string | null;
  lastFailAt: string | null;
  lockedUntil: string | null;
}

/** 角色组聚合（GET /api/v1/admin/roles）。 */
export interface AppRoleGroup {
  appCode: string;
  appName: string;
  roles: { role: string; userCount: number }[];
}

/** 用户的单条准入明细（GET /api/v1/apps/users/{userId}/assignments）。 */
export interface AssignmentDto {
  appCode: string;
  roles: string[];
}

// ---------------------------------------------------------------- 菜单 / 权限 / 多语言
// 以下三块与平台侧契约对齐：菜单与权限来自 cim-system（无 /api/v1 前缀，见 lib/api.ts 的 rootApi），
// 译文来自 cim-i18n-starter 的公开端点 /api/i18n/**。

/** 菜单节点类型（对齐 com.cim.system.menu.MenuType）。 */
export type MenuNodeType = 'DIR' | 'MENU' | 'BUTTON';

/**
 * 菜单实体（`GET /sys/me/menus` 的节点载荷，对齐 com.cim.system.menu.SysMenu）。
 *
 * <p>名称走 i18n：本表**只存 `i18nCode`**，前端用 `t(i18nCode)` 渲染，
 * 故列表里的 `SysMenuDto` 没有任何中文字面量字段。</p>
 */
export interface SysMenuDto {
  id: string;
  parentId: string | null;
  /** 前端路由（DIR/BUTTON 为 null）。 */
  path: string | null;
  /** 前端组件标识（供 `pageRegistry` 映射）。 */
  component: string | null;
  /** 名称 i18n 键。 */
  i18nCode: string | null;
  /** 图标注册表键。 */
  icon: string | null;
  type: MenuNodeType;
  /** 关联权限码（BUTTON 必有；MENU 可有）。 */
  permCode: string | null;
  sortNo: number;
  visible: boolean;
  status: 'ENABLED' | 'DISABLED';
}

/** 菜单树节点（对齐 com.cim.system.menu.MenuNode）。 */
export interface MenuTreeNode {
  menu: SysMenuDto;
  children: MenuTreeNode[];
}

/**
 * 平台权限点（对齐 com.cim.system.permission.SysPermission）。
 *
 * <p>三段被 {@code applyCode} 从 `code` 拆出后落库，保证「注解里的码」与「库里的码」永不漂移。</p>
 */
export interface SysPermissionDto {
  id: string;
  /** 权限码 `module:res:action`。 */
  code: string;
  module: string | null;
  res: string | null;
  action: string | null;
  name: string | null;
  status: 'ENABLED' | 'DISABLED';
}

/** 平台角色（对齐 com.cim.system.role.SysRole）。 */
export interface SysRoleDto {
  id: string;
  code: string;
  name: string | null;
  /** 超管：解析时短路展开为「库内全部启用权限码」。 */
  isSuper: boolean;
  sortNo: number;
  status: 'ENABLED' | 'DISABLED';
}

/** 译文行（对齐 com.cim.i18n.model.SysI18n）。 */
export interface SysI18nDto {
  id: string;
  localeCode: string;
  code: string;
  /** 级别：SYSTEM（程序种子）/ USER（业务维护）。 */
  scope: 'SYSTEM' | 'USER';
  module: string | null;
  content: string | null;
}

/** 平台分页结果（对齐 com.cim.core.shared.PageResult）。 */
export interface PageResultDto<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

/**
 * 语言目录（`GET /api/i18n/locales` 公开端点，对齐 com.cim.i18n.model.SysLocale）。
 */
export interface SysLocaleDto {
  id: string;
  code: string;
  name: string | null;
  isDefault: boolean;
  sortNo: number;
  status: 'ENABLED' | 'DISABLED';
}

/** 译文包（`GET /api/i18n/messages?lang=&v=`，对齐 I18nController.I18nBundle）。 */
export interface I18nBundle {
  /** 译文版本号（前端本地缓存比对用）。 */
  version: string;
  /** 实际生效语言。 */
  lang: string;
  /** 键 → 译文；版本未变时为空对象（省流量）。 */
  messages: Record<string, string>;
}

/** 系统设置（GET /api/v1/admin/settings，只读的生效参数）。 */
export interface SettingsDto {
  authSource: string;
  passwordRounds: number;
  pepperConfigured: boolean;
  accessTokenTtlMinutes: number;
  refreshTokenTtlMinutes: number;
  jwtIssuer: string;
  jwtKid: string;
  rsaKeyInjected: boolean;
  lockoutMaxAttempts: number;
  lockoutLockMinutes: number;
  lockoutWindowMinutes: number;
  bootstrapEnabled: boolean;
  bootstrapAdminUsername: string;
  webAllowedOrigins: string[];
  jwksPath: string;
  /** 目录只读 API 是否启用（配置了共享密钥即启用）。 */
  directoryApiEnabled: boolean;
  /** AD 同步开关。 */
  adSyncEnabled: boolean;
  /** AD 是否已具备可连接配置（未配置则同步跳过，IAM 自建照常可用）。 */
  adSyncConfigured: boolean;
  adBaseDn: string;
  adSyncIntervalMs: number;
}
