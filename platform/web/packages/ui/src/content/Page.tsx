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

/**
 * 面板容器（等价于 `.panel`），用于包裹表格 / 详情块。
 *
 * 传 `title`（或 `titleKey`）时渲染标题栏 —— 现仓各 ap 都有一份自家的 `Panel`
 * 薄壳（`business/<ap>/web/src/components/Panel.tsx`），结构完全一样、只是无法复用。
 * 这里补上 `title / sub / actions / flush` 四个属性，业务侧那份薄壳即可删除。
 *
 * 不传 `title` 且不传 `actions` 时不渲染头部，保持「纯容器」行为不变。
 */
export function Panel({
  children,
  className,
  style,
  titleKey,
  title,
  subKey,
  sub,
  actions,
  flush = false,
}: {
  /** 不传时只渲染头部 —— 用于「标题 + 右侧动作」这类**一条**（如概览页的常用入口），
      此前只能塞一个空 `<Toolbar/>` 进去，于是多出一行 16px 内边距的空区块。 */
  children?: ReactNode;
  className?: string;
  style?: CSSProperties;
  titleKey?: string;
  title?: ReactNode;
  /** 标题下方的小号说明。 */
  subKey?: string;
  sub?: ReactNode;
  /** 头部右侧操作区。 */
  actions?: ReactNode;
  /** 内容区去掉内边距（表格贴边）。 */
  flush?: boolean;
}) {
  const t = useUiT();
  const head = titleKey ? t(titleKey) : title;
  const desc = subKey ? t(subKey) : sub;
  return (
    <section className={cx('panel', className)} style={style}>
      {head || actions ? (
        <header className="panel-head">
          <div className="panel-heading">
            {head ? <h2 className="panel-title">{head}</h2> : null}
            {desc ? <p className="panel-sub">{desc}</p> : null}
          </div>
          {actions ? <div className="panel-actions">{actions}</div> : null}
        </header>
      ) : null}
      {children ? (
        <div className={cx('panel-body', flush && 'flush')}>{children}</div>
      ) : null}
    </section>
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

/**
 * 空态：一句话说明 + 可选引导。不放置插画（产线屏幕不需要，也省体积）。
 *
 * <p>`icon` / `children` / `large` 是为**整页状态页**（403 / 404 / 占位页）加的：
 * 这类页面此前在三个应用里各写一套——iam 用 `.panel > .panel-body > .empty`、
 * rms 用 `.empty-state > .empty-icon`、approval 用 `.page > .empty-state`，
 * 三份的图标尺寸、标题字号、返回按钮间距都不一致。统一到这里后，
 * 状态页只需 `Page + Panel + EmptyState`。</p>
 */
export function EmptyState({
  titleKey,
  title,
  hintKey,
  hint,
  icon,
  children,
  large = false,
  className,
  style,
}: {
  titleKey?: string;
  title?: ReactNode;
  hintKey?: string;
  hint?: ReactNode;
  /** 顶部图标（整页状态页用；列表内空态通常不需要）。 */
  icon?: ReactNode;
  /** 底部动作区（返回首页 / 重试）。 */
  children?: ReactNode;
  /** 大号形态：整页状态页（图标更大、留白更多）。 */
  large?: boolean;
  /**
   * 额外类名（合并而非覆盖）。
   *
   * 面板内嵌的空态常要改外边距/内边距，此前这类页面只能手写
   * `<div className="empty" style={{ padding: '28px 0' }}>` 绕开组件。
   */
  className?: string;
  /** 内联样式，用途同 `className`（压 padding / margin）。 */
  style?: CSSProperties;
}) {
  const t = useUiT();
  const main = titleKey ? t(titleKey) : title;
  const sub = hintKey ? t(hintKey) : hint;
  return (
    <div className={cx('cim-empty', large && 'cim-empty-lg', className)} style={style}>
      {icon ? <div className="cim-empty-icon">{icon}</div> : null}
      {main ? <div className="cim-empty-title">{main}</div> : null}
      {sub ? <div className="cim-empty-hint">{sub}</div> : null}
      {children ? <div className="cim-empty-actions">{children}</div> : null}
    </div>
  );
}
