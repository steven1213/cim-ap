// 布局-only 表单网格：任意 children 的两列/三列排布。
//
// 存在的理由：`FormGrid` 是配置驱动（判别联合字段），装不下带级联逻辑的自定义控件——
// 页面只好手写 `style={{ display: 'grid', ... }}`，而 `.cim-field + .cim-field` 的
// 堆叠间距规则（ui.css）只豁免**带类名**的网格容器（`.cim-form-grid/.grid-2/.grid-3`），
// 内联样式网格第二列起全部吃到 11px 顶边距 → 整列下移、视觉底对齐
// （RMS ComparePage / BatchOpsModal 踩过：右列标签比左列低 11px）。
//
// 所以这里提供「挂 .cim-form-grid 类、列数可配」的薄壳：布局与豁免永远同生，
// 页面不再有手写表单网格的理由。
//
// ```tsx
// <FieldGrid columns={2}>
//   <CimField labelKey="...">...</CimField>
//   <CimField labelKey="...">...</CimField>
// </FieldGrid>
// ```
import type { CSSProperties, ReactNode } from 'react';

export interface FieldGridProps {
  /** 列数，默认 2。 */
  columns?: number;
  /** 格间距（px），默认取 `.cim-form-grid` 的 11。 */
  gap?: number;
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
}

export function FieldGrid({ columns = 2, gap, children, className, style }: FieldGridProps) {
  return (
    <div
      className={['cim-form-grid', className ?? ''].filter(Boolean).join(' ')}
      style={{
        gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
        ...(gap !== undefined ? { gap } : null),
        ...style,
      }}
    >
      {children}
    </div>
  );
}
