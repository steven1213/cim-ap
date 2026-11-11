import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useIdentityStore } from '@/store/identityStore';
import { notificationsStreamUrl } from '@/lib/api';
import { SseClient } from '@cim/notify-client';
import { CimButton, useToast } from '@cim/ui';
import { IconBell, IconInbox } from '@/components/Icons';
import type { NotificationEvent } from '@/types';

// SSE 推送信封（与后端 cim-notify-starter 对齐）：payload 为业务 JSON 字符串（双编码）。
interface NotifyEnvelope {
  id?: string;
  ts?: number;
  appCode?: string;
  type?: string;
  payload?: string;
}

// SSE 通知铃：订阅本 ap 的 /sys/notify/stream（统一 cim-notify-starter），新任务时弹 toast + 角标。
// 前端不持久化红点：刷新/重连必然丢消息，故 onOpen（重连）也应回拉各 ap 的 /unread-count 自愈
// （approval-ap 当前未持久化通知，红点以本会话内存角标表示；后续可按各 AP 自管原则补通知表）。
export default function NotificationBell() {
  const { t } = useTranslation();
  const toast = useToast();
  const userId = useIdentityStore((s) => s.userId);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<NotificationEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<SseClient | null>(null);

  useEffect(() => {
    if (clientRef.current) clientRef.current.close();
    setConnected(false);
    const client = new SseClient({
      url: notificationsStreamUrl(), // dev 直连 ?userId=&tenantId=；生产应改用 getToken 注入 ?token=
      getToken: () => null,
      mode: 'event-source',
      onOpen: () => setConnected(true),
      onError: () => setConnected(false),
      onMessage: (m) => {
        try {
          const env = (m.data ?? {}) as NotifyEnvelope;
          const biz = (typeof env.payload === 'string'
            ? JSON.parse(env.payload)
            : env) as NotificationEvent;
          if (!biz || !biz.type) return;
          if (biz.type === 'NEW_TASK') {
            setItems((prev) => [biz, ...prev].slice(0, 12));
            toast.info(`${t('notify.newTask')}: ${biz.name ?? biz.taskId ?? ''}`);
          } else if (biz.type === 'URGE') {
            setItems((prev) => [biz, ...prev].slice(0, 12));
            toast.warning(`${t('notify.urge')}: ${biz.title ?? biz.name ?? ''}`);
          } else if (biz.type === 'TASK_OVERDUE') {
            setItems((prev) => [biz, ...prev].slice(0, 12));
            toast.warning(`${t('notify.overdue')}: ${biz.name ?? biz.taskId ?? ''}`);
          }
        } catch {
          /* ignore */
        }
      },
    });
    clientRef.current = client;
    client.connect();
    return () => client.close();
  }, [userId, t]);

  return (
    <div style={{ position: 'relative' }}>
      <CimButton
        bare
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
      </CimButton>

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
