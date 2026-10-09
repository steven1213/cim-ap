// IAM 控制台共享类型（与 business/iam-ap/server 的 Result/Controller 契约对齐）

/** 登录 / 刷新返回：访问令牌 + 刷新令牌 + 有效期（秒）+ 类型。 */
export interface LoginResult {
  token: string;
  refreshToken: string;
  expiresInSeconds: number;
  tokenType: string;
}

/** GET /api/v1/me 返回。 */
export interface MeDto {
  userId: string;
  username: string;
  tenantId: string | null;
  /** 当前用户可进入的 ap 接入码列表（apps claim）。 */
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
  recentEvents: AuditEvent[];
}

/** 管理面用户行（GET /api/v1/admin/users）。 */
export interface AdminUserRow {
  userId: string;
  username: string;
  enabled: boolean;
  locked: boolean;
  lockedUntil: string | null;
  failCount: number;
  apps: string[];
  roles: Record<string, string[]>;
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
}
