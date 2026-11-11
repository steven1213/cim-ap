// KPI 指标卡：图标 + 标签 + 数值 +（可选）副标题，左侧色条表示状态。
//
// 为什么值得做成组件：`.kpi` 这套结构在三个应用里手写了 178 处（RMS 131 / approval 40 / IAM 7），
// IAM 甚至在自己的 DashboardPage 里带了个同款局部组件 `Kpi`（11 处调用）。
// 样式 `.kpi / .kpi-grid / .kpi-ico / .kpi-main / .kpi-label / .kpi-val / .kpi-sub`
// 早已在 `ui.css` 里，缺的只是把它们组装成组件的这一步。
//
// 不依赖路由：需要点击跳转时由调用方包一层 `<Link>`/`<a>`（组件包不引入 react-router）。
import type { ReactNode } from 'react';

export type KpiTone = 'ok' | 'warn' | 'info';

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
}

export function KpiCard({ icon, label, value, sub, tone }: KpiCardProps) {
  return (
    <div className={tone ? `kpi ${tone}` : 'kpi'}>
      {icon ? <div className="kpi-ico">{icon}</div> : null}
      <div className="kpi-main">
        <div className="kpi-label">{label}</div>
        <div className="kpi-val">{value}</div>
        {sub ? <div className="kpi-sub">{sub}</div> : null}
      </div>
    </div>
  );
}

/** KPI 卡片容器：等宽自适应网格（`repeat(auto-fit, minmax(168px, 1fr))`）。 */
export function KpiGrid({ children }: { children: ReactNode }) {
  return <div className="kpi-grid">{children}</div>;
}
