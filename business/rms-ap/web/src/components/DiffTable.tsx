import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { CimTag, DataTable, type DataTableColumn } from '@cim/ui';
import type { DiffItem } from '@/types';
import { fieldLabel, categoryLabel } from '@/lib/diff';

/**
 * 差异明细表：逐字段左/右值对照，变更行用语义色标出（`cim-row-warn`）。
 *
 * 迁移要点：原实现手写 `<table className="data">`，容器自带 `.table-wrap` 边框；
 * 现在用 `DataTable flat`（嵌在调用方的面板/抽屉里，不再画第二层框），
 * 变更行由 `rowClassName` 交回主题的 5 档语义色，不再用页面私有的 `.diff-changed`。
 */
export default function DiffTable({
  items,
  showCategory = true,
}: {
  items: DiffItem[];
  showCategory?: boolean;
}) {
  const { t } = useTranslation();

  const columns = useMemo<Array<DataTableColumn<DiffItem>>>(() => {
    const base: Array<DataTableColumn<DiffItem>> = [
      {
        key: 'field',
        titleKey: 'rms.compare.field',
        width: 180,
        mono: true,
        render: (it) => fieldLabel(t, it.field),
      },
    ];
    if (showCategory) {
      base.push({
        key: 'category',
        titleKey: 'rms.compare.category',
        width: 100,
        render: (it) => <CimTag size="small">{categoryLabel(t, it.category)}</CimTag>,
      });
    }
    base.push(
      {
        key: 'left',
        titleKey: 'rms.compare.leftValue',
        render: (it) => (
          <span className="cim-mono" style={{ wordBreak: 'break-all' }}>
            {it.left ?? t('rms.common.dash')}
          </span>
        ),
      },
      {
        key: 'right',
        titleKey: 'rms.compare.rightValue',
        render: (it) => (
          <span className="cim-mono" style={{ wordBreak: 'break-all' }}>
            {it.changed ? <b>{it.right ?? t('rms.common.dash')}</b> : (it.right ?? t('rms.common.dash'))}
          </span>
        ),
      },
    );
    return base;
  }, [t, showCategory]);

  return (
    <DataTable<DiffItem>
      flat
      columns={columns}
      rows={items}
      rowKey={(it) => it.field}
      rowClassName={(it) => (it.changed ? 'cim-row-warn' : '')}
      scrollX="max-content"
      showCount={false}
    />
  );
}
