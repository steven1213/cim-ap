// Spin：加载指示。纯 CSS 实现，不引 antd Spin —— 它要包住被加载内容才能显示，
// 而路由切换时我们只有一块空区域，用不上那层包裹。
//
// 使用场景：
//   · 路由级 code-split 的 Suspense 兜底（`block` + `height` 保持内容区高度，避免跳动）；
//   · 页面内局部区块加载（不带 block，跟文字并排）。

import { useUiLabel } from '../locale';

export interface CimSpinProps {
  size?: 'sm' | 'md';
  /** 文案键；默认 `ui.common.loading`。 */
  labelKey?: string;
  /** 撑满父容器并垂直居中（路由兜底用）。 */
  block?: boolean;
  /** 仅 `block` 时生效：最小高度，避免内容区高度塌陷导致页面抖动。 */
  height?: number | string;
}

export function CimSpin({ size = 'md', labelKey, block = false, height }: CimSpinProps) {
  const label = useUiLabel();
  const style =
    block && height !== undefined
      ? { minHeight: typeof height === 'number' ? `${height}px` : height }
      : undefined;

  return (
    <div
      className={`cim-spin cim-spin-${size}${block ? ' cim-spin-block' : ''}`}
      role="status"
      aria-live="polite"
      style={style}
    >
      <span className="cim-spin-ring" aria-hidden="true" />
      <span className="cim-spin-label">{label(labelKey ?? 'ui.common.loading', '加载中…')}</span>
    </div>
  );
}
