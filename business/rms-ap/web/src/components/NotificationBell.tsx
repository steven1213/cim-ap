import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import * as api from '@/lib/rmsApi';
import { IconBell } from '@/components/Icons';
import type { AppNotification } from '@/types';

/**
 * 顶栏通知铃铛（W9 审批实时通知）：30s 轮询未读数 + 下拉通知面板。
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

  const poll = useCallback(async () => {
    try {
      setUnread(await api.unreadCount());
    } catch {
      // 401 由拦截器处理；轮询失败静默（不打扰用户）
    }
  }, []);

  useEffect(() => {
    poll();
    const timer = setInterval(poll, 30_000);
    return () => clearInterval(timer);
  }, [poll]);

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
      <button className="icon-btn" onClick={toggle} title={t('rms.bell.title')} aria-label={t('rms.bell.title')}>
        <IconBell width={17} height={17} />
        {unread > 0 && <span className="bell-dot">{unread > 99 ? '99+' : unread}</span>}
      </button>
      {open && (
        <div className="bell-panel">
          <div className="bell-head">
            <b>{t('rms.bell.title')}</b>
            <button className="btn btn-ghost btn-sm" onClick={readAll} disabled={unread === 0}>
              {t('rms.bell.readAll')}
            </button>
          </div>
          <div className="bell-list">
            {loading && <div className="bell-empty">{t('rms.common.loading')}</div>}
            {!loading && items.length === 0 && (
              <div className="bell-empty">{t('rms.bell.empty')}</div>
            )}
            {!loading &&
              items.map((n) => (
                <button
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
                </button>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}
