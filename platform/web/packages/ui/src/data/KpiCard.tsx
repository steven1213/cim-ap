// KPI 指标卡：图标 + 标签 + 数值 +（可选）副标题，左侧色条表示状态。
//
// 为什么值得做成组件：`.kpi` 这套结构手写了几十处，IAM 甚至在自己页面里带了个
// 同款局部组件 `Kpi`。样式 `.kpi / .kpi-grid / .kpi-ico / .kpi-main / .kpi-label /
// .kpi-val / .kpi-sub` 早已在 `ui.css` 里，缺的只是把它们组装成组件的这一步。
//
// ⚠️ 数字口径：说「171 处」是错的——那是把 `kpi-main` / `kpi-label` / `kpi-val`
// 这些**子元素**也按 `className="kpi` 前缀数进去了。真正的 `.kpi` 容器
// 全站 40 个（2026-11-12 精确复核），见 docs/platform/web/component-adoption-audit.md。
//
// 不依赖路由：需要点击跳转时由调用方包 `<Link>`（组件包不引入 react-router）。
// 这类「整卡是链接」的场景用 `bare`：卡片外壳与跳转交给调用方，本组件只出内容。
//
// ```tsx
// <Link to="/device" className="kpi ok">
//   <KpiCard bare icon={<IconChip />} label={t('rms.kpi.devices')} value={12} />
// </Link>
// ```
import type { CSSProperties, ReactNode } from 'react';

/** 左侧色条语义。`err` 是「超标/异常」，与 `warn` 的区别是它要求立即处理。 */
export type KpiTone = 'ok' | 'warn' | 'err' | 'info';

export interface KpiCardProps {
  /** 左侧图标（通常 16px 的通用图标）。 */
  icon?: ReactNode;
  /** 指标名（已翻译）。 */
  label: ReactNode;
  /** 指标值；可以是 `12 / 30` 这类组合串。 */
  value: ReactNode;
  /** 补充说明（如「其中 3 个已停用」）。 */
  sub?: ReactNode;
  /** 左侧色条语义；不传则用主题主色。 */
  tone?: KpiTone;
  /**
   * 只渲染卡片内容（图标 + 文本区），不渲染 `.kpi` 外壳。
   * 用于「整张卡是一个 `<Link>`」的导航型指标卡。
   */
  bare?: boolean;
}

export function KpiCard({ icon, label, value, sub, tone, bare = false }: KpiCardProps) {
  const body = (
    <>
      {icon ? <div className="kpi-ico">{icon}</div> : null}
      <div className="kpi-main">
        <div className="kpi-label">{label}</div>
        <div className="kpi-val">{value}</div>
        {sub ? <div className="kpi-sub">{sub}</div> : null}
      </div>
    </>
  );
  if (bare) return body;
  return <div className={tone ? `kpi ${tone}` : 'kpi'}>{body}</div>;
}

/** KPI 卡片容器：等宽自适应网格（`repeat(auto-fit, minmax(168px, 1fr))`）。 */
export interface KpiGridProps {
  children: ReactNode;
  /** 追加类名（与 `kpi-grid` 合并，不覆盖）。 */
  className?: string;
  /**
   * 追加行内样式。
   * 面板内嵌场景要压掉容器外边距（`margin: '10px 14px 0'`），页面此前因此绕开组件手写 `<div>`。
   */
  style?: CSSProperties;
}

export function KpiGrid({ children, className, style }: KpiGridProps) {
  return (
    <div className={className ? `kpi-grid ${className}` : 'kpi-grid'} style={style}>
      {children}
    </div>
  );
}
