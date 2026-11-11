// 信息表：两列（键 / 值）的竖向表格，用于「详情抽屉里一长串字段」。
//
// 对照参考项目 GoatViewInfoTable：它要先用 `setInfoData(row, columnList)` 把行数据
// 拆成 [{key, value}]，还要 deepCopy，并且从表格的 columnList 反推字段名 —— 一层数据
// 要转三次手。这里直接吃 items，字段名就是字段名。
//
// 另有 `columns` 变体：字段多时按多列并排（比 Descriptions 更适合长字段文本）。

import { Fragment, type ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface InfoItem<T> {
  key: string;
  labelKey?: string;
  label?: string;
  dataIndex?: keyof T & string;
  render?: (record: T) => ReactNode;
  value?: ReactNode;
  /** 值区是否用等宽字体（编码、哈希、时间戳）。 */
  mono?: boolean;
  /** 值超长时折行显示（默认省略）。 */
  wrap?: boolean;
}

export interface CimInfoTableProps<T> {
  record?: T;
  items: Array<InfoItem<T>>;
  /** 并排几组键值（默认 1 组 = 两列）。 */
  columns?: number;
  size?: 'default' | 'small';
  /** 键列宽度，默认 34%。 */
  labelWidth?: string;
  className?: string;
}

function display(v: unknown): ReactNode {
  if (v === null || v === undefined || v === '') return '—';
  return typeof v === 'string' || typeof v === 'number' ? v : (v as ReactNode);
}

export function CimInfoTable<T = Record<string, unknown>>({
  record,
  items,
  columns = 1,
  size = 'small',
  labelWidth = '34%',
  className,
}: CimInfoTableProps<T>) {
  const label = useUiLabel();

  // 按 columns 切分成若干组，每组渲染一对 td（键 + 值）
  const groups: Array<Array<InfoItem<T>>> = [];
  const perGroup = Math.max(1, Math.ceil(items.length / columns));
  for (let i = 0; i < items.length; i += perGroup) {
    groups.push(items.slice(i, i + perGroup));
  }

  // 每组按列数横向排：先把每组补足行数，再转置成行
  const rows: Array<Array<InfoItem<T> | undefined>> = [];
  const maxLen = Math.max(...groups.map((g) => g.length), 0);
  for (let r = 0; r < maxLen; r++) {
    rows.push(groups.map((g) => g[r]));
  }

  return (
    <table
      className={`cim-infotable${className ? ` ${className}` : ''}`}
      style={{ width: '100%', borderCollapse: 'collapse', fontSize: size === 'small' ? 12.5 : 13 }}
    >
      <tbody>
        {rows.map((row, ri) => (
          <tr key={ri}>
            {row.map((item, ci) => {
              if (!item) {
                // 该组已无字段：补两个空格，保持表格结构不塌
                return (
                  <td key={`e${ci}`} colSpan={2} style={{ border: 'none', padding: 0 }} />
                );
              }
              let content: ReactNode;
              if (item.render && record !== undefined) content = item.render(record);
              else if (item.dataIndex && record !== undefined)
                content = display((record as Record<string, unknown>)[item.dataIndex]);
              else content = display(item.value);

              return (
                <Fragment key={item.key}>
                  <td
                    style={{
                      width: labelWidth,
                      background: 'var(--panel-2)',
                      color: 'var(--text-2)',
                      border: '1px solid var(--line)',
                      padding: '5px 8px',
                      fontWeight: 600,
                      verticalAlign: 'top',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {item.labelKey ? label(item.labelKey, item.labelKey) : item.label}
                  </td>
                  <td
                    className={item.mono ? 'cim-mono' : undefined}
                    style={{
                      border: '1px solid var(--line)',
                      padding: '5px 8px',
                      color: 'var(--text)',
                      verticalAlign: 'top',
                      ...(item.wrap
                        ? { whiteSpace: 'pre-wrap', wordBreak: 'break-word' }
                        : { whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }),
                    }}
                  >
                    {content}
                  </td>
                </Fragment>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
