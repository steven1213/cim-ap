// 时间格式化（RMS 侧）。
//
// 这里曾经是 IAM `lib/format.ts` 的**整份拷贝**（72 行逐字节相同）。实测后发现自己站不住：
//
//   1. 其中 5 个函数（auditLabel / orgTypeLabel / originLabel / auditTone / fmtRelative）
//      在本应用**零调用点**——RMS 没有审计页、也没有做数据来源标注；
//   2. 它们引用的 `iam.*` 译文键在 rms 的内置包里一个都没有（`grep -c "^  'iam\." = 0`）。
//      缺键兜底是 humanize，`iam.common.dash` 会被渲染成 **"Dash"**，不是 "—"。
//      也就是说 `fmtDateTime(null)` 在界面上显示的是 "Dash"——不报错，但明显不对。
//
// 所以这里不追求「与 IAM 保持一致」：只留真正被用的时间格式化，兜底用 rms 自己的
// `rms.common.dash`。跨应用共用的前提是两边都真的在用；只有一方用，上提只会把文案耦合搬到平台层。

import i18next from '@/lib/i18n';

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** 本地时间格式化：YYYY-MM-DD HH:mm:ss；空值显示 —。 */
export function fmtDateTime(s: string | null | undefined): string {
  if (!s) return i18next.t('rms.common.dash');
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
    d.getMinutes(),
  )}:${pad(d.getSeconds())}`;
}
