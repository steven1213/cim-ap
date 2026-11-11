// 按钮：权限门 + 悬停说明 + 文案键 三合一。
//
// 渲染目标说明（2026-11-12 变更，重要）：
// 曾经这里渲染 antd `Button`，而全站页面手写了 200 个 `<button className="btn ...">`。
// 结果是同一个应用里两套按钮视觉（iam/rms 全用 .btn，approval 全用 antd）。
// 抉择依据是数量：`.btn` 侧 171 处、antd 侧 45 处，让组件去迁就多数派只需改一处，
// 让 171 处迁就 antd 是一次全站改版。所以现在渲染 **原生 <button className="btn ...">**，
// 视觉来源是 ui.css 里的 `.btn` 段（`.btn` 主色 / `.btn-ghost` 次要 / `.btn-danger` 危险
// / `.btn-text` 纯文字 / `.btn-sm` 小号 / `.btn-block` 撑满）。
//
// ⚠️ `bare`：站内还有一类控件是 `<button>` 但不是「按钮」——`icon-btn`（顶栏图标）、
// `chip-x`（标签删除）、`tree-toggle`（树展开箭头）、`bell-item`、`node-del`。
// 它们各有自己的样式，**不能**被加上 `.btn` 基类（那会给它们主色底、14px 内边距、
// 600 字重）。传 `bare` 时组件只负责权限门/无障碍/事件，样式完全交给 `className`。
// 本轮批量替换时漏了这个区分，`icon-btn` 被强加 `type="primary"`，被渲染契约抓到。
//
// 对照参考项目的 GoatButton：它用 icon name 反推按钮语义色
// （IconRefresh→warning、IconDelete→danger、IconCirclePlus→success），
// 这是隐式约定 —— 换个图标名颜色就变了，且看代码看不出规则。这里不做，
// 层级一律由 `type` / `danger` 显式声明。
//
// 真正的增量是这三件各页面重复了上百次的事：
//   1. `perm`：无权限时直接不渲染（而不是渲染一个点了报 403 的按钮）
//   2. `tooltip` / `tooltipKey`：图标按钮必须能说明自己是什么
//   3. `labelKey`：文案走 i18n 键，切语言自动跟随
//
// 属性透传：`style` / `title` / `aria-*` / `data-*` / `onPointerDown` 等原生属性
// 一律原样透传（`...rest`），因为页面里手写按钮用过 `style`、`aria-expanded`、
// `aria-label`，组件漏一个就得回去改页面。

import type { ComponentPropsWithoutRef, MouseEventHandler, ReactNode } from 'react';
import { useUiLabel } from '../locale';
import { usePermissionContext } from '../perm';

/** 视觉层级。`danger` 优先级最高（覆盖 type）。 */
export type CimButtonVariant = 'primary' | 'default' | 'ghost' | 'text';

export interface CimButtonProps
  extends Omit<ComponentPropsWithoutRef<'button'>, 'type' | 'className' | 'children' | 'disabled'> {
  /** 按钮文案的 i18n 键；与 children 二选一。 */
  labelKey?: string;
  children?: ReactNode;
  /** 需要持有的权限码（三段式）。无权限时不渲染。 */
  perm?: string | string[];
  /** 无权限时的替代渲染（默认什么都不渲染）。 */
  permFallback?: ReactNode;

  /**
   * 视觉层级，沿用 antd 的命名以免调用点改写：
   * `primary` 主色实心 · `default` 次要（带边框）· `text`/`link` 纯文字。
   * `ghost` 是同义别名（`.btn-ghost` 的历史叫法）。
   */
  type?: CimButtonVariant | 'dashed' | 'link';
  /** 危险操作（删除、强制终止）。 */
  danger?: boolean;
  size?: 'small' | 'middle' | 'large';
  disabled?: boolean;
  loading?: boolean;
  block?: boolean;
  /**
   * 不作为「按钮」渲染：不套 `.btn` 基类，样式完全由 `className` 决定。
   * 用于 `icon-btn` / `chip-x` / `tree-toggle` 这类自主样式的控件。
   */
  bare?: boolean;
  /** 前置图标（等价于把图标写在 children 最前，但会自动跟随文案间距）。 */
  icon?: ReactNode;
  /** 只有图标：自动加标题/aria-label，并压成方形。 */
  iconOnly?: boolean;

  /** 悬停说明文本（已翻译）。 */
  tooltip?: string;
  /** 悬停说明的 i18n 键。 */
  tooltipKey?: string;

  /**
   * 与原生签名一致（`(e: MouseEvent) => void`）。不收窄成 `() => void`：
   * 页面里有 `onClick={(e) => { e.stopPropagation(); … }}`，窄化会让这些调用点报
   * 「Target signature provides too few arguments」（本轮实测踩到）。
   */
  onClick?: MouseEventHandler<HTMLButtonElement>;
  /** 原生 button 的 type，避免与上面的视觉层级 `type` 混淆。 */
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
  bare = false,
  icon,
  iconOnly = false,
  tooltip,
  tooltipKey,
  onClick,
  htmlType = 'button',
  className,
  ...rest
}: CimButtonProps) {
  const label = useUiLabel();
  const permCtx = usePermissionContext();

  // 权限未就绪时不隐藏，避免「渲染后又消失」的闪烁；就绪后按真实权限收敛
  if (perm && permCtx.ready) {
    const codes = Array.isArray(perm) ? perm : [perm];
    if (!codes.every((c) => permCtx.has(c))) return <>{permFallback}</>;
  }

  // `.btn` 基类本身就是主色，所以 primary 不额外加修饰类
  const variant = danger
    ? 'btn-danger'
    : type === 'primary'
      ? null
      : type === 'text' || type === 'link'
        ? 'btn-text'
        : 'btn-ghost';

  const classes = (
    bare
      ? [className]
      : ['btn', variant, size === 'small' && 'btn-sm', block && 'btn-block', iconOnly && 'btn-icon-only', className]
  )
    .filter(Boolean)
    .join(' ');

  const text = labelKey ? label(labelKey, labelKey) : children;
  const hint = tooltipKey ? label(tooltipKey, tooltipKey) : tooltip;
  // 只有图标的按钮必须有可读名称，否则屏幕阅读器读不出用途
  const ariaLabel = iconOnly && hint ? hint : undefined;

  return (
    <button
      {...rest}
      type={htmlType}
      className={classes}
      disabled={disabled || loading}
      onClick={onClick}
      title={hint ?? rest.title}
      aria-label={ariaLabel ?? rest['aria-label']}
      data-loading={loading ? 'true' : undefined}
    >
      {loading && <span className="btn-spinner" aria-hidden="true" />}
      {icon}
      {iconOnly ? null : text}
    </button>
  );
}
