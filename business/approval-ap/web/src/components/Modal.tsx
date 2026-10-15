import type { ReactNode } from 'react';
import { useEffect } from 'react';
import { IconClose } from '@/components/Icons';

interface ModalProps {
  title: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  /** 面板宽度（px），默认 420。 */
  width?: number;
}

// 通用模态：遮罩 + 面板，Esc 关闭。
export default function Modal({ title, onClose, children, footer, width = 420 }: ModalProps) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="scrim modal-scrim" onClick={onClose}>
      <div
        className="panel modal-panel"
        style={{ width, maxWidth: '94vw' }}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="panel-head">
          <div className="panel-heading">
            <h2 className="panel-title">{title}</h2>
          </div>
          <div className="panel-actions">
            <button className="icon-btn" onClick={onClose} aria-label="close">
              <IconClose width={16} height={16} />
            </button>
          </div>
        </header>
        <div className="panel-body">{children}</div>
        {footer && (
          <div className="panel-head" style={{ borderTop: '1px solid var(--line)', borderBottom: 'none' }}>
            <div className="panel-actions" style={{ marginLeft: 'auto' }}>
              {footer}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
