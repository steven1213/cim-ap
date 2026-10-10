// 通用格式化与文案映射（审计类型标签、时间格式化）。
//
// 枚举标签与相对时间走 i18n（键见 IamConsoleCatalog.texts() 的 iam.audit.type.* /
// iam.org.type.* / iam.source.* / iam.common.*）。组件外调用 i18next.t 直接取译文。

import i18next from '@/lib/i18n';

/** 审计事件类型 → 已翻译标签（未知类型回退为原始码，不渲染裸键）。 */
export function auditLabel(type: string): string {
  return i18next.t(`iam.audit.type.${type}`, type);
}

/** 组织节点类型 → 已翻译标签。 */
export function orgTypeLabel(t: string | null | undefined): string {
  if (!t) return i18next.t('iam.common.dash');
  return i18next.t(`iam.org.type.${t}`, t);
}

/** 数据来源 → 已翻译标签。 */
export function originLabel(src: string | null | undefined): string {
  switch (src) {
    case 'AD_SYNCED':
      return i18next.t('iam.source.AD_SYNCED');
    case 'IAM_MANAGED':
      return i18next.t('iam.source.IAM_MANAGED');
    default:
      return i18next.t('iam.common.dash');
  }
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
  if (!s) return i18next.t('iam.common.dash');
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
    d.getMinutes(),
  )}:${pad(d.getSeconds())}`;
}

/** 相对时间（用于「多久之前」），超过 7 天回落到绝对时间。 */
export function fmtRelative(s: string | null | undefined): string {
  if (!s) return i18next.t('iam.common.dash');
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  const diff = Date.now() - d.getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return i18next.t('iam.common.justNow');
  if (min < 60) return i18next.t('iam.common.minutesAgo', { n: min });
  const h = Math.floor(min / 60);
  if (h < 24) return i18next.t('iam.common.hoursAgo', { n: h });
  const day = Math.floor(h / 24);
  if (day <= 7) return i18next.t('iam.common.daysAgo', { n: day });
  return fmtDateTime(s);
}
