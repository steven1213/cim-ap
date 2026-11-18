// 权限判定语义：页面动作 → 需同时具备的权限码数组（双闸门 = 入口码 + 平台码）。
//
// 每个动作的默认值是平台码（业务系统直接可用）；IAM 等对入口有自己码的宿主，
// 经页面 props.perms 覆盖为 [入口码, 平台码]（与旧 useHasAllPermissions 行为一致）。
// 组织/人员页默认就是 iam:org:*/iam:user:*（组织与人员主数据在 IAM；业务系统
// 走 readOnly 模式，动作 UI 根本不渲染，覆盖与否不影响）。

/** 六页权限动作语义 → 默认码（可被宿主覆盖）。 */
export interface AdminPermOverrides {
  read?: string[];
  save?: string[];
  remove?: string[];
  /** 仅角色页。 */
  grant?: string[];
  /** 仅多语言页：语言目录增删改。 */
  locale?: string[];
  /** 仅组织页：移动节点。 */
  move?: string[];
  /** 组织/人员页：成员与归属维护。 */
  member?: string[];
  /** 仅组织页：新建节点。 */
  orgCreate?: string[];
  /** 仅人员页：新建账号。 */
  userCreate?: string[];
  /** 仅人员页：启停/重置口令/解锁等。 */
  status?: string[];
  resetPwd?: string[];
  unlock?: string[];
}

export const MENU_PERMS: Required<Pick<AdminPermOverrides, 'read' | 'save' | 'remove'>> = {
  read: ['sys:menu:list'],
  save: ['sys:menu:save'],
  remove: ['sys:menu:remove'],
};

export const PERM_PERMS: Required<Pick<AdminPermOverrides, 'read' | 'save' | 'remove'>> = {
  read: ['sys:permission:list'],
  save: ['sys:permission:save'],
  remove: ['sys:permission:remove'],
};

export const ROLE_PERMS: Required<Pick<AdminPermOverrides, 'read' | 'save' | 'remove' | 'grant'>> = {
  read: ['sys:role:list'],
  save: ['sys:role:save'],
  remove: ['sys:role:remove'],
  grant: ['sys:role:grant'],
};

export const I18N_PERMS: Required<Pick<AdminPermOverrides, 'read' | 'save' | 'remove' | 'locale'>> = {
  read: ['sys:i18n:list'],
  save: ['sys:i18n:save'],
  remove: ['sys:i18n:remove'],
  locale: ['sys:i18n:locale'],
};

/** 组织页默认码（IAM 语义；只读宿主传 readOnly 即可，不用覆盖）。 */
export const ORG_PERMS: Required<Pick<AdminPermOverrides, 'read' | 'save' | 'remove' | 'move' | 'member'>> = {
  read: ['iam:org:list'],
  save: ['iam:org:update'],
  remove: ['iam:org:delete'],
  move: ['iam:org:move'],
  member: ['iam:org:member'],
};

/** 人员页默认码（IAM 语义；只读宿主传 readOnly 即可）。 */
export const USER_PERMS: Required<Pick<AdminPermOverrides, 'read' | 'save' | 'remove' | 'status' | 'resetPwd' | 'unlock' | 'member'>> = {
  read: ['iam:user:list'],
  save: ['iam:user:update'],
  remove: ['iam:user:remove'],
  status: ['iam:user:status'],
  resetPwd: ['iam:user:reset-pwd'],
  unlock: ['iam:user:unlock'],
  member: ['iam:user:orgs'],
};

export const IAM_CREATE_PERMS = {
  orgCreate: ['iam:org:create'],
  userCreate: ['iam:user:create'],
};
