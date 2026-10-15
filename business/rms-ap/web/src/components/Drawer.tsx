import type { ReactNode } from 'react';

interface DrawerProps {
  title: ReactNode;
  onClose: () => void;
  children: ReactNode;
  /** 底部操作区（可选）。 */
  foot?: ReactNode;
}

/**
 * 右侧抽屉：用于详情/明细展示（对齐 IAM 的 drawer 范式）。
 * 依赖 styles.css 的 .drawer-backdrop / .drawer / .drawer-head / .drawer-body。
 */
export default function Drawer({ title, onClose, children, foot }: DrawerProps) {
  return (
    <>
      <div className="drawer-backdrop" onClick={onClose} />
      <aside className="drawer" role="dialog" aria-modal="true">
        <div className="drawer-head">
          <h3>{title}</h3>
          <button className="icon-btn" onClick={onClose} aria-label="close">✕</button>
        </div>
        <div className="drawer-body">{children}</div>
        {foot && <div className="modal-foot">{foot}</div>}
      </aside>
    </>
  );
}
