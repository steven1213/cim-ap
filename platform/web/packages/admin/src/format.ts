// 通用格式化与文案映射（组织类型、数据来源、时间）。
// 组件外调用 i18next.t 直接取译文（全局单例，与宿主 createI18n 同一实例）。
import i18next from 'i18next';

/** 组织节点类型 → 已翻译标签。 */
export function orgTypeLabel(t: string | null | undefined): string {
  if (!t) return i18next.t('admin.common.dash');
  return i18next.t(`admin.org.type.${t}`, t);
}

/** 数据来源 → 已翻译标签。 */
export function originLabel(src: string | null | undefined): string {
  switch (src) {
    case 'AD_SYNCED':
      return i18next.t('admin.source.AD_SYNCED');
    case 'IAM_MANAGED':
      return i18next.t('admin.source.IAM_MANAGED');
    default:
      return i18next.t('admin.common.dash');
  }
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** 本地时间格式化：YYYY-MM-DD HH:mm:ss。 */
export function fmtDateTime(s: string | null | undefined): string {
  if (!s) return i18next.t('admin.common.dash');
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
    d.getMinutes(),
  )}:${pad(d.getSeconds())}`;
}

/** 相对时间（用于「多久之前」），超过 7 天回落到绝对时间。 */
export function fmtRelative(s: string | null | undefined): string {
  if (!s) return i18next.t('admin.common.dash');
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  const diff = Date.now() - d.getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return i18next.t('admin.common.justNow');
  if (min < 60) return i18next.t('admin.common.minutesAgo', { n: min });
  const h = Math.floor(min / 60);
  if (h < 24) return i18next.t('admin.common.hoursAgo', { n: h });
  const day = Math.floor(h / 24);
  if (day <= 7) return i18next.t('admin.common.daysAgo', { n: day });
  return fmtDateTime(s);
}
