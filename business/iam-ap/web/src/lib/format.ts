// 通用格式化与文案映射（审计类型标签、时间格式化）。

/** 审计事件类型 → 中文标签。 */
export const AUDIT_LABELS: Record<string, string> = {
  LOGIN_SUCCESS: '登录成功',
  LOGIN_FAILURE: '登录失败',
  LOGIN_REJECTED: '登录被拒',
  LOGOUT: '登出',
  PASSWORD_CHANGED: '自助改密',
  PASSWORD_RESET: '重置口令',
  USER_CREATED: '创建账号',
  USER_ENABLED: '启用账号',
  USER_DISABLED: '禁用账号',
  USER_DELETED: '删除账号',
  USER_UNLOCKED: '解除锁定',
  APP_REGISTERED: '注册应用',
  APP_UPDATED: '更新应用',
  ADMISSION_GRANTED: '授予准入',
  ADMISSION_REVOKED: '撤销准入',
  SESSION_REVOKED: '强制下线',
  ORG_CREATED: '新建组织',
  ORG_UPDATED: '更新组织',
  ORG_MOVED: '移动组织',
  ORG_DELETED: '删除组织',
  PROFILE_CREATED: '新建档案',
  PROFILE_UPDATED: '更新档案',
  USER_ORGS_CHANGED: '变更归属',
  ORG_GRANT_GRANTED: '授予组织准入',
  ORG_GRANT_REVOKED: '撤销组织准入',
  DIRECTORY_SYNCED: '目录同步',
};

/** 组织节点类型 → 中文标签。 */
export const ORG_TYPE_LABELS: Record<string, string> = {
  AREA: '厂区',
  WORKSHOP: '车间',
  LINE: '产线',
  PROCESS: '工序',
  TEAM: '班组',
  DEPT: '部门',
};

export function orgTypeLabel(t: string | null | undefined): string {
  if (!t) return '—';
  return ORG_TYPE_LABELS[t] ?? t;
}

/** 数据来源 → 中文标签。 */
export function originLabel(src: string | null | undefined): string {
  switch (src) {
    case 'AD_SYNCED':
      return 'AD 同步';
    case 'IAM_MANAGED':
      return 'IAM 自建';
    default:
      return '—';
  }
}

export function auditLabel(type: string): string {
  return AUDIT_LABELS[type] ?? type;
}

/** 审计类型 → 语义色标（用于徽标）：ok=安全事件 / warn=需关注 / err=失败 / info=常规。 */
export function auditTone(type: string, result?: string | null): 'ok' | 'warn' | 'err' | 'info' {
  if (result === 'FAILURE') return 'err';
  if (type === 'LOGIN_FAILURE' || type === 'LOGIN_REJECTED') return 'err';
  if (type === 'SESSION_REVOKED' || type === 'USER_DISABLED' || type === 'ADMISSION_REVOKED'
    || type === 'USER_DELETED' || type === 'ORG_DELETED' || type === 'ORG_GRANT_REVOKED')
    return 'warn';
  if (type === 'LOGIN_SUCCESS' || type === 'ADMISSION_GRANTED' || type === 'APP_REGISTERED'
    || type === 'USER_CREATED' || type === 'ORG_GRANT_GRANTED')
    return 'ok';
  return 'info';
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** 本地时间格式化：YYYY-MM-DD HH:mm:ss。 */
export function fmtDateTime(s: string | null | undefined): string {
  if (!s) return '—';
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
    d.getMinutes(),
  )}:${pad(d.getSeconds())}`;
}

/** 相对时间（用于「多久之前」），超过 7 天回落到绝对时间。 */
export function fmtRelative(s: string | null | undefined): string {
  if (!s) return '—';
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  const diff = Date.now() - d.getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return '刚刚';
  if (min < 60) return `${min} 分钟前`;
  const h = Math.floor(min / 60);
  if (h < 24) return `${h} 小时前`;
  const day = Math.floor(h / 24);
  if (day <= 7) return `${day} 天前`;
  return fmtDateTime(s);
}
