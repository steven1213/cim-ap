import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useIdentityStore } from '@/store/identityStore';
import { notificationsStreamUrl } from '@/lib/api';
import { toast } from '@/store/toastStore';
import { IconBell, IconInbox } from '@/components/Icons';
import type { NotificationEvent } from '@/types';

// SSE 通知铃：订阅 /api/v1/approval/notifications/stream?userId=<current>，新任务时弹 toast + 角标。
export default function NotificationBell() {
  const { t } = useTranslation();
  const userId = useIdentityStore((s) => s.userId);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<NotificationEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (esRef.current) esRef.current.close();
    setConnected(false);
    const es = new EventSource(notificationsStreamUrl(userId));
    esRef.current = es;
    es.onopen = () => setConnected(true);
    es.onerror = () => setConnected(false);
    es.onmessage = (ev) => {
      try {
        const data = JSON.parse(ev.data) as NotificationEvent;
        if (data.type === 'NEW_TASK') {
          setItems((prev) => [data, ...prev].slice(0, 12));
          toast(`${t('notify.newTask')}: ${data.name ?? data.taskId ?? ''}`, 'info');
        } else if (data.type === 'URGE') {
          setItems((prev) => [data, ...prev].slice(0, 12));
          toast(`${t('notify.urge')}: ${data.title ?? data.name ?? ''}`, 'warn');
        } else if (data.type === 'TASK_OVERDUE') {
          setItems((prev) => [data, ...prev].slice(0, 12));
          toast(`${t('notify.overdue')}: ${data.name ?? data.taskId ?? ''}`, 'warn');
        }
      } catch {
        /* ignore */
      }
    };
    return () => es.close();
  }, [userId, t]);

  return (
    <div style={{ position: 'relative' }}>
      <button
        className="icon-btn"
        onClick={() => setOpen((v) => !v)}
        title={connected ? t('notify.connected') : t('notify.disconnected')}
        aria-label={t('notify.title')}
      >
        <IconBell width={17} height={17} />
        {items.length > 0 && (
          <span
            style={{
              position: 'absolute',
              top: 2,
              right: 2,
              minWidth: 15,
              height: 15,
              padding: '0 3px',
              borderRadius: 8,
              background: 'var(--err)',
              color: '#fff',
              fontSize: 9,
              lineHeight: '15px',
              textAlign: 'center',
            }}
          >
            {items.length}
          </span>
        )}
      </button>

      {open && (
        <div className="tab-ctx" style={{ top: 40, right: 0, minWidth: 260 }}>
          <div style={{ padding: '8px 10px', fontSize: 11, color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: '0.06em', display: 'flex', alignItems: 'center', gap: 6 }}>
            <span className={'led ' + (connected ? 'ok' : 'err')} />
            {t('notify.title')}
          </div>
          {items.length === 0 ? (
            <div className="empty" style={{ padding: '14px 10px' }}>
              <IconInbox width={22} height={22} />
              <span>{t('notify.empty')}</span>
            </div>
          ) : (
            items.map((it, i) => (
              <div key={i} style={{ padding: '8px 10px', borderTop: '1px dashed var(--line)', fontSize: 12 }}>
                <div style={{ fontWeight: 600 }}>{it.name ?? t('notify.newTask')}</div>
                <div className="mono" style={{ color: 'var(--muted)', fontSize: 10.5 }}>
                  {it.taskId}
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
