// @cim/admin 数据契约 —— 六页系统配置组件共用的 DTO。
//
// 菜单/权限/角色三块与平台 cim-system 契约对齐；多语言与 cim-i18n-starter 模型对齐；
// 组织/人员与 IAM admin 契约对齐（业务系统只读时由各 ap 服务端代理映射成同一形状）。

// ---------------------------------------------------------------- 菜单 / 权限 / 角色

/** 菜单节点类型（对齐 com.cim.system.menu.MenuType）。 */
export type MenuNodeType = 'DIR' | 'MENU' | 'BUTTON';

/**
 * 菜单实体（对齐 com.cim.system.menu.SysMenu）。
 *
 * <p>名称走 i18n：本表<b>只存 `i18nCode`</b>，前端用 `t(i18nCode)` 渲染。</p>
 */
export interface SysMenuDto {
  id: string;
  parentId: string | null;
  /** 前端路由（DIR/BUTTON 为 null）。 */
  path: string | null;
  /** 前端组件标识。 */
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
 * <p>三段被 `applyCode` 从 `code` 拆出后落库，保证「注解里的码」与「库里的码」永不漂移。</p>
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

/** 平台分页结果（对齐 com.cim.core.shared.PageResult）。 */
export interface PageResultDto<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

// ---------------------------------------------------------------- 多语言

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

/** 语言目录行（对齐 com.cim.i18n.model.SysLocale）。 */
export interface SysLocaleDto {
  id: string;
  code: string;
  name: string | null;
  isDefault: boolean;
  sortNo: number;
  status: 'ENABLED' | 'DISABLED';
}

/**
 * 单个语言的译文覆盖率（对齐 com.cim.i18n.model.LocaleCoverage）。
 *
 * <p>与「缺失译文」不是一回事：缺失译文记的是「四级兜底全未命中」，这里记的是
 * <b>本语言自己没有值、靠默认语言兜底</b>的键——表现是英文界面显示中文。</p>
 */
export interface LocaleCoverageDto {
  locale: string;
  name: string | null;
  base: boolean;
  total: number;
  translated: number;
  missing: string[];
  truncated: boolean;
}

// ---------------------------------------------------------------- 组织 / 人员

export type OrgStatus = 'ENABLED' | 'DISABLED';
export type OrgNodeType = 'AREA' | 'WORKSHOP' | 'LINE' | 'PROCESS' | 'TEAM' | 'DEPT';
export type DataOrigin = 'AD_SYNCED' | 'IAM_MANAGED';

/** 组织节点（扁平含 parentId，前端建树）。 */
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

/**
 * 管理面用户行（IAM：GET /api/v1/admin/users 本地凭证 ∪ 档案并集；
 * 业务系统：只读代理把 IAM 目录档案映射到同一形状——凭证/锁定相关列恒空、
 * email/mobile 由目录档案填充）。
 */
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
  /** 目录档案补充列（IAM 管理面行不含，恒 undefined 渲染 —）。 */
  email?: string | null;
  mobile?: string | null;
  jobTitle: string | null;
  /** AD_SYNCED / IAM_MANAGED / NONE（无档案）。 */
  source: string;
  /** ACTIVE / INACTIVE / null。 */
  status: string | null;
  orgNames: string[];
  apps: string[];
  roles: Record<string, string[]>;
}
