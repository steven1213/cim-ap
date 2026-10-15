import { useTranslation } from 'react-i18next';
import type { DiffItem } from '@/types';
import { fieldLabel, categoryLabel } from '@/lib/diff';

/** 差异明细表：逐字段左/右值对照，变更行高亮（对齐 IAM 表格范式）。 */
export default function DiffTable({ items, showCategory = true }: { items: DiffItem[]; showCategory?: boolean }) {
  const { t } = useTranslation();
  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            <th style={{ width: 180 }}>{t('rms.compare.field')}</th>
            {showCategory && <th style={{ width: 90 }}>{t('rms.compare.category')}</th>}
            <th>{t('rms.compare.leftValue')}</th>
            <th>{t('rms.compare.rightValue')}</th>
          </tr>
        </thead>
        <tbody>
          {items.map((it) => (
            <tr key={it.field} className={it.changed ? 'diff-changed' : undefined}>
              <td className="mono">{fieldLabel(t, it.field)}</td>
              {showCategory && <td><span className="tag">{categoryLabel(t, it.category)}</span></td>}
              <td className="mono" style={{ wordBreak: 'break-all' }}>{it.left ?? t('rms.common.dash')}</td>
              <td className="mono" style={{ wordBreak: 'break-all' }}>
                {it.changed ? <b>{it.right ?? t('rms.common.dash')}</b> : (it.right ?? t('rms.common.dash'))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
