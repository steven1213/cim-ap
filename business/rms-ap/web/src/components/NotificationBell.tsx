import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import * as api from '@/lib/rmsApi';
import { SseClient } from '@cim/notify-client';
import { useAuthStore } from '@/store/authStore';
import { NotificationBell as ShellBell } from '@cim/shell';
import type { AppNotification } from '@/types';

/**
 * 顶栏通知铃铛（W9 审批实时通知）。
 *
 * <p>界面（按钮/角标/弹层/列表/点外关闭）在 `@cim/shell`，本文件只管 RMS 的数据：
 * 轮询未读数（红点真相在持久表 `rms_notification`）+ SSE 实时刷新（`cim-notify-starter`）。
 * SSE 仅做在线即时提醒；刷新/重连必然丢消息，故 `onOpen`（重连）与收到消息都回拉
 * `/unread-count` 自愈。点击通知 → 按引用类型直达对应页并标记已读；全部已读一键兜底。</p>
 */
export default function NotificationBell() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(false);
  /** 面板是否展开（开合状态由 shell 的 NotificationBell 管，这里只记一份给 SSE 回调判断）。 */
  const openRef = useRef(false);

  const poll = useCallback(async () => {
    try {
      setUnread(await api.unreadCount());
    } catch {
      // 401 由拦截器处理；轮询失败静默（不打扰用户）
    }
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const page = await api.listNotifications(false);
      setItems(page.items);
      setUnread(page.unread);
    } catch {
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // 轮询兜底（红点真相在持久表）
  useEffect(() => {
    poll();
    const timer = setInterval(poll, 30_000);
    return () => clearInterval(timer);
  }, [poll]);

  // 实时通道：订阅本 ap /sys/notify/stream；到消息即时刷新；重连自愈。
  useEffect(() => {
    const { token, user } = useAuthStore.getState();
    let url: string;
    let getToken: () => string | null;
    if (token) {
      url = '/sys/notify/stream';
      getToken = () => useAuthStore.getState().token;
    } else {
      // dev 直连（cim.auth.enabled=false + dev-accept-query-identity=true）
      const uid = user?.userId ?? 'anonymous';
      const tid = user?.tenantId ?? 'default';
      url = `/sys/notify/stream?userId=${encodeURIComponent(uid)}&tenantId=${encodeURIComponent(tid)}`;
      getToken = () => null;
    }
    const client = new SseClient({
      url,
      getToken,
      mode: 'event-source',
      onMessage: () => {
        poll();
        if (openRef.current) refresh();
      },
      onOpen: () => {
        poll(); // 重连自愈：回拉未读真相
      },
      onError: () => {
        // 静默，轮询仍兜底红点
      },
    });
    client.connect();
    return () => client.close();
  }, [poll, refresh]);

  // 点击外部关闭 / 打开时刷新都交给 shell 的 NotificationBell；
  // 这里只需要知道「面板当前开着吗」，供 SSE 回调决定是否顺手刷新列表。
  function destFor(n: AppNotification): string {
    if (n.refType === 'SIGNOFF') return '/signoff';
    if (n.refType === 'HOLD' || n.refType === 'DOWNLOAD') return '/integration';
    return '/';
  }

  async function openItem(n: AppNotification) {
    if (!n.read) {
      try {
        await api.markNotificationRead(n.id);
        setUnread((u) => Math.max(0, u - 1));
        setItems((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
      } catch {
        // 忽略
      }
    }
    navigate(destFor(n));
  }

  async function readAll() {
    try {
      await api.markAllNotificationsRead();
      setUnread(0);
      setItems((list) => list.map((x) => ({ ...x, read: true })));
    } catch {
      // 忽略
    }
  }

  return (
    <ShellBell
      unread={unread}
      loading={loading}
      items={items.map((n) => ({
        id: n.id,
        title: n.title,
        content: n.content,
        time: n.occurredAt,
        read: n.read,
      }))}
      labels={{
        title: t('rms.bell.title'),
        empty: t('rms.bell.empty'),
        loading: t('rms.common.loading'),
        readAll: t('rms.bell.readAll'),
      }}
      onRefresh={refresh}
      onOpenChange={(next) => {
        openRef.current = next;
      }}
      onOpenItem={(it) => {
        const n = items.find((x) => x.id === it.id);
        if (n) void openItem(n);
      }}
      onReadAll={() => void readAll()}
    />
  );
}
