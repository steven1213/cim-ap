import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useIdentityStore } from '@/store/identityStore';
import { notificationsStreamUrl } from '@/lib/api';
import { SseClient } from '@cim/notify-client';
import { NotificationBell as ShellBell } from '@cim/shell';
import { useToast } from '@cim/ui';
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
//
// 前端不持久化红点：刷新/重连必然丢消息。RMS 侧靠回拉 /unread-count 自愈，本 ap 目前
// **没有通知表**（后端 TODO：按各 AP 自管原则补），所以红点退化为会话内内存列表——
// 刷新即清零。界面与交互在 `@cim/shell`，这里只保留 SSE 建连与 toast 提示。
export default function NotificationBell() {
  const { t } = useTranslation();
  const toast = useToast();
  const userId = useIdentityStore((s) => s.userId);
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

  // 列表只有内存态，不需要「打开时拉取」，也不支持标记已读（没有通知表可写）。
  const notifyItems = useMemo(
    () =>
      items.map((it, i) => ({
        id: `${it.type ?? 'N'}-${it.taskId ?? ''}-${i}`,
        title: it.name ?? it.title ?? t('notify.newTask'),
        content: it.taskId ?? null,
        read: true,
      })),
    [items, t],
  );

  return (
    <ShellBell
      unread={items.length}
      items={notifyItems}
      labels={{
        title: t('notify.title'),
        empty: t('notify.empty'),
      }}
      connected={connected}
      connectedTitle={connected ? t('notify.connected') : t('notify.disconnected')}
    />
  );
}
