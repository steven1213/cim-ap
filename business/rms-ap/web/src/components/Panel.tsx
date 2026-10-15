import type { ReactNode } from 'react';

interface PanelProps {
  title?: ReactNode;
  /** 标题下方的小号补充说明。 */
  sub?: string;
  /** 头部右侧操作区（按钮、状态徽标、统计 tag 等）。 */
  actions?: ReactNode;
  children: ReactNode;
  /** 去掉内边距（用于表格贴边）。 */
  flush?: boolean;
  className?: string;
}

/**
 * 工程化面板：统一标题栏 + 内容区，承载表格/表单/信息块（对齐 IAM 的 Panel 范式）。
 * 依赖 styles.css 的 .panel / .panel-head / .panel-title / .panel-body(.flush) 类。
 */
export default function Panel({ title, sub, actions, children, flush, className }: PanelProps) {
  return (
    <section className={'panel' + (className ? ' ' + className : '')}>
      {(title || actions) && (
        <header className="panel-head">
          <div className="panel-heading">
            {title && <h2 className="panel-title">{title}</h2>}
            {sub && <p className="panel-sub">{sub}</p>}
          </div>
          {actions && <div className="panel-actions">{actions}</div>}
        </header>
      )}
      <div className={'panel-body' + (flush ? ' flush' : '')}>{children}</div>
    </section>
  );
}
