// 通用格式化工具。

export function fmtDate(iso?: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '—';
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function shortHash(h?: string | null, len = 10): string {
  if (!h) return '—';
  return h.length > len ? h.slice(0, len) + '…' : h;
}

/** 超时时长人性化：<1h 显示分钟，<48h 显示小时，否则显示天数。 */
export function fmtOverdue(minutes: number): string {
  if (minutes < 60) return `${Math.max(1, Math.round(minutes))} min`;
  if (minutes < 60 * 48) return `${(minutes / 60).toFixed(1)} h`;
  return `${(minutes / 60 / 24).toFixed(1)} d`;
}

// 业务变量 JSON 解析（发起表单用），失败返回 null。
export function parseVariables(text: string): Record<string, unknown> | null {
  const v = text.trim();
  if (!v) return null;
  try {
    const obj = JSON.parse(v);
    return obj && typeof obj === 'object' ? obj : null;
  } catch {
    return null;
  }
}
