import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import * as api from '@/lib/rmsApi';
import { SseClient } from '@cim/notify-client';
import { useAuthStore } from '@/store/authStore';
import { IconBell } from '@/components/Icons';
import type { AppNotification } from '@/types';

import { CimButton } from '@cim/ui';
/**
 * 顶栏通知铃铛（W9 审批实时通知）：轮询未读数（红点真相在持久表 rms_notification）+ SSE 实时刷新（cim-notify-starter）。
 * SSE 仅做在线即时提醒；刷新/重连必然丢消息，故 onOpen（重连）与收到消息都回拉 /unread-count 自愈。
 * 点击通知 → 按引用类型直达对应页并标记已读；全部已读一键兜底。
 */
export default function NotificationBell() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);
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

  useEffect(() => {
    openRef.current = open;
  }, [open]);

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

  // 点击外部关闭
  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  async function toggle() {
    const next = !open;
    setOpen(next);
    if (next) {
      await refresh();
    }
  }

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
    setOpen(false);
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
    <div className="bell-wrap" ref={wrapRef}>
      <CimButton bare className="icon-btn" onClick={toggle} title={t('rms.bell.title')} aria-label={t('rms.bell.title')}>
        <IconBell width={17} height={17} />
        {unread > 0 && <span className="bell-dot">{unread > 99 ? '99+' : unread}</span>}
      </CimButton>
      {open && (
        <div className="bell-panel">
          <div className="bell-head">
            <b>{t('rms.bell.title')}</b>
            <CimButton size="small" onClick={readAll} disabled={unread === 0}>
              {t('rms.bell.readAll')}
            </CimButton>
          </div>
          <div className="bell-list">
            {loading && <div className="bell-empty">{t('rms.common.loading')}</div>}
            {!loading && items.length === 0 && (
              <div className="bell-empty">{t('rms.bell.empty')}</div>
            )}
            {!loading &&
              items.map((n) => (
                <CimButton
                  type="primary"
                  key={n.id}
                  className={'bell-item' + (n.read ? '' : ' unread')}
                  onClick={() => openItem(n)}
                >
                  <span className="bell-item-title">
                    {!n.read && <i className="bell-unread-led" />}
                    {n.title}
                  </span>
                  {n.content && <span className="bell-item-sub">{n.content}</span>}
                  <span className="bell-item-time">{n.occurredAt}</span>
                </CimButton>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}
