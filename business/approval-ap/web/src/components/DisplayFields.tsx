import type { DisplayField } from '@/types';

const MAX_VISIBLE = 4;
const MAX_LEN = 24;

function truncate(s?: string | null): string {
  if (!s) return '';
  return s.length > MAX_LEN ? s.slice(0, MAX_LEN) + '…' : s;
}

/** 业务摘要渲染（G5）：卡片/列表/详情一眼可见的关键业务项，前 4 个，超长截断。 */
export function DisplayFields({ fields }: { fields?: DisplayField[] | null }) {
  if (!fields || fields.length === 0) return null;
  const visible = fields.slice(0, MAX_VISIBLE);
  const rest = fields.length - visible.length;
  return (
    <div className="df-block">
      {visible.map((f) => (
        <span key={f.key} className="df-item" title={f.value ?? ''}>
          {f.label ? `${f.label}：` : ''}
          {truncate(f.value)}
        </span>
      ))}
      {rest > 0 && <span className="df-more">+{rest}</span>}
    </div>
  );
}
