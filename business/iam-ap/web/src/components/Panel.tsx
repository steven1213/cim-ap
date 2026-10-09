import type { ReactNode } from 'react';

interface PanelProps {
  title?: string;
  /** 标题右侧的小号补充说明（支持等宽代码片段用 <code>）。 */
  sub?: string;
  /** 头部右侧操作区（按钮、状态徽标等）。 */
  actions?: ReactNode;
  children: ReactNode;
  /** 去掉内边距（用于表格贴边）。 */
  flush?: boolean;
  className?: string;
}

/** 工程化面板：统一的标题栏 + 内容区，用于承载表格、表单与信息块。 */
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
