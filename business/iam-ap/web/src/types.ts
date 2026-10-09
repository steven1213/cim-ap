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
