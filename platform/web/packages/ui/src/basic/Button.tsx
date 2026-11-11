// 按钮：权限门 + 悬停说明 + 文案键 三合一。
//
// 对照参考项目的 GoatButton：它用 icon name 反推按钮语义色
// （IconRefresh→warning、IconDelete→danger、IconCirclePlus→success），
// 这是隐式约定 —— 换个图标名颜色就变了，且看代码看不出规则。这里不做，
// 颜色一律由 `danger` / `type` 显式声明。
//
// 真正的增量是这三件各页面重复了上百次的事：
//   1. `perm`：无权限时直接不渲染（而不是渲染一个点了报 403 的按钮）
//   2. `tooltip` / `tooltipKey`：图标按钮必须能说明自己是什么
//   3. `labelKey`：文案走 i18n 键，切语言自动跟随

import { Button } from 'antd';
import type { ReactNode } from 'react';
import { useUiLabel } from '../locale';
import { usePermissionContext } from '../perm';

export interface CimButtonProps {
  /** 按钮文案的 i18n 键；与 children 二选一。 */
  labelKey?: string;
  children?: ReactNode;
  /** 需要持有的权限码（三段式）。无权限时不渲染。 */
  perm?: string | string[];
  /** 无权限时的替代渲染（默认什么都不渲染）。 */
  permFallback?: ReactNode;

  type?: 'primary' | 'default' | 'dashed' | 'text' | 'link';
  /** 危险操作（删除、强制终止）。 */
  danger?: boolean;
  size?: 'small' | 'middle' | 'large';
  disabled?: boolean;
  loading?: boolean;
  block?: boolean;
  /** 前置图标。 */
  icon?: ReactNode;
  /** 只有图标：自动加 aria-label 与 tooltip（无障碍要求）。 */
  iconOnly?: boolean;

  /** 悬停说明文本（已翻译）。 */
  tooltip?: string;
  /** 悬停说明的 i18n 键。 */
  tooltipKey?: string;

  onClick?: () => void;
  htmlType?: 'button' | 'submit' | 'reset';
  className?: string;
}

export function CimButton({
  labelKey,
  children,
  perm,
  permFallback = null,
  type = 'default',
  danger = false,
  size = 'middle',
  disabled = false,
  loading = false,
  block = false,
  icon,
  iconOnly = false,
  tooltip,
  tooltipKey,
  onClick,
  htmlType = 'button',
  className,
}: CimButtonProps) {
  const label = useUiLabel();
  const permCtx = usePermissionContext();

  // 权限未就绪时不隐藏，避免「渲染后又消失」的闪烁；就绪后按真实权限收敛
  if (perm && permCtx.ready) {
    const codes = Array.isArray(perm) ? perm : [perm];
    if (!codes.every((c) => permCtx.has(c))) return <>{permFallback}</>;
  }

  const text = labelKey ? label(labelKey, labelKey) : children;
  const hint = tooltipKey ? label(tooltipKey, tooltipKey) : tooltip;
  // 只有图标的按钮必须有可读名称，否则屏幕阅读器读不出用途
  const ariaLabel = iconOnly && hint ? hint : undefined;

  return (
    <Button
      type={type}
      danger={danger}
      size={size}
      disabled={disabled}
      loading={loading}
      block={block}
      icon={icon}
      onClick={onClick}
      htmlType={htmlType}
      className={iconOnly ? `cim-btn-icon ${className ?? ''}` : className}
      title={hint}
      aria-label={ariaLabel}
    >
      {iconOnly ? null : text}
    </Button>
  );
}
