// 描述列表：详情页顶部的「键 → 值」栅格。
//
// 现仓每个详情页都手写一遍 `<div className="form-grid"><div className="field"><span
// className="field-label">…</span>…`，共 30+ 处。这里收成一个声明式列表。
//
// 与参考项目 GoatDescriptions 的差别：
//   - 它的 item 可以塞任意表单组件（`item.name` + `item.options`），于是这个组件
//     反向依赖了 8 个表单组件；查看态的字段又变成可编辑，语义混乱。
//     这里只做**只读展示**；需要边看边改请用 FormGrid 或 Drawer + FormGrid。
//   - 文案一律 `labelKey`，切语言自动跟随。

import { Descriptions } from 'antd';
import type { ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface DescItem<T> {
  /** 唯一键。 */
  key: string;
  labelKey?: string;
  label?: string;
  /** 从 record 取值的字段名。 */
  dataIndex?: keyof T & string;
  /** 自定义渲染（优先级高于 dataIndex / value）。 */
  render?: (record: T) => ReactNode;
  /** 直接给值（与 dataIndex 二选一）。 */
  value?: ReactNode;
  /** 占几列。 */
  span?: number;
}

export interface CimDescriptionsProps<T> {
  /** 数据源；配合 item.dataIndex / item.render 使用。只读展示也可以不传。 */
  record?: T;
  items: Array<DescItem<T>>;
  /** 每行列数，默认 3。 */
  column?: number;
  /** 带边框（密集表格观感，产线详情页建议开）。 */
  bordered?: boolean;
  size?: 'default' | 'middle' | 'small';
  titleKey?: string;
  title?: ReactNode;
  /** 右上角动作区。 */
  extra?: ReactNode;
}

/** 空值统一渲染为 —，避免出现空白格让人怀疑数据没加载。 */
function display(v: unknown): ReactNode {
  if (v === null || v === undefined || v === '') return '—';
  if (typeof v === 'string' || typeof v === 'number') return v;
  return v as ReactNode;
}

export function CimDescriptions<T = Record<string, unknown>>({
  record,
  items,
  column = 3,
  bordered = true,
  size = 'small',
  titleKey,
  title,
  extra,
}: CimDescriptionsProps<T>) {
  const label = useUiLabel();

  const antdItems = items.map((item) => {
    let content: ReactNode;
    if (item.render && record !== undefined) {
      content = item.render(record);
    } else if (item.dataIndex && record !== undefined) {
      content = display((record as Record<string, unknown>)[item.dataIndex]);
    } else {
      content = display(item.value);
    }
    return {
      key: item.key,
      label: item.labelKey ? label(item.labelKey, item.labelKey) : item.label,
      children: content,
      span: item.span,
    };
  });

  return (
    <Descriptions
      className="cim-desc"
      title={titleKey ? label(titleKey, titleKey) : title}
      extra={extra}
      items={antdItems}
      column={column}
      bordered={bordered}
      size={size}
    />
  );
}
