import { useToastStore } from '@/store/toastStore';
import { IconCheck, IconClose } from '@/components/Icons';

// 全局轻提示：固定在右下角，自动消失。
export default function ToastHost() {
  const { toasts, remove } = useToastStore();
  return (
    <div
      style={{
        position: 'fixed',
        right: 18,
        bottom: 18,
        zIndex: 90,
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        maxWidth: 340,
      }}
    >
      {toasts.map((t) => (
        <div key={t.id} className={`alert ${t.kind === 'info' ? 'warn' : t.kind}`}>
          {t.kind === 'ok' && <IconCheck width={15} height={15} />}
          <span style={{ flex: 1 }}>{t.msg}</span>
          <button className="tab-x" style={{ position: 'static' }} onClick={() => remove(t.id)} aria-label="close">
            <IconClose width={11} height={11} />
          </button>
        </div>
      ))}
    </div>
  );
}
