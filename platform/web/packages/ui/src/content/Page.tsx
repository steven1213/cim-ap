// 页面骨架原语：Page / PageHead / Panel / Toolbar / EmptyState。
//
// 这四个是「每个页面都要写一遍」的结构（现仓 20 处 page-head、22 处 toolbar），
// 收进组件后页面代码只剩业务表达。
// 样式复用 @cim/ui 已有的 .page / .panel，页头与工具栏用 .cim-* 自带样式。

import type { CSSProperties, ReactNode } from 'react';
import { useUiT } from '../locale';

function cx(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(' ');
}

/** 页面容器：flex 纵向、撑满内容区（等价于现有 `<div className="page">`）。 */
export function Page({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cx('page', className)}>{children}</div>;
}

/**
 * 页头：标题 + 说明 + 右侧动作。
 * 传 `titleKey`（配 i18n）或 `title`（已翻译文本）二选一。
 */
export function PageHead({
  titleKey,
  title,
  subtitleKey,
  subtitle,
  actions,
}: {
  titleKey?: string;
  title?: ReactNode;
  subtitleKey?: string;
  subtitle?: ReactNode;
  actions?: ReactNode;
}) {
  const t = useUiT();
  const heading = titleKey ? t(titleKey) : title;
  const sub = subtitleKey ? t(subtitleKey) : subtitle;
  return (
    <div className="cim-page-head">
      <div className="cim-page-head-main">
        <h1>{heading}</h1>
        {sub ? <p className="cim-page-head-sub">{sub}</p> : null}
      </div>
      {actions ? <div className="cim-page-head-actions">{actions}</div> : null}
    </div>
  );
}

/** 面板容器（等价于 `.panel`），用于包裹表格 / 详情块。 */
export function Panel({
  children,
  className,
  style,
}: {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
}) {
  return (
    <div className={cx('panel', className)} style={style}>
      {children}
    </div>
  );
}

/** 工具条：左区（动作/搜索）· 右区（计数/设置）。 */
export function Toolbar({
  left,
  right,
  children,
}: {
  left?: ReactNode;
  right?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div className="cim-toolbar">
      <div className="cim-toolbar-left">{left ?? children}</div>
      <div className="cim-toolbar-spacer" />
      {right ? <div className="cim-toolbar-right">{right}</div> : null}
    </div>
  );
}

/** 空态：一句话说明 + 可选引导。不放置插画（产线屏幕不需要，也省体积）。 */
export function EmptyState({
  titleKey,
  title,
  hintKey,
  hint,
}: {
  titleKey?: string;
  title?: ReactNode;
  hintKey?: string;
  hint?: ReactNode;
}) {
  const t = useUiT();
  const main = titleKey ? t(titleKey) : title;
  const sub = hintKey ? t(hintKey) : hint;
  return (
    <div className="cim-empty">
      {main ? <div className="cim-empty-title">{main}</div> : null}
      {sub ? <div className="cim-empty-hint">{sub}</div> : null}
    </div>
  );
}
