import { useEffect, useRef, useState } from 'react';
import { SseClient, type SseClientOptions, type SseMessage, type SseError } from './SseClient';

export type SseStatus = 'idle' | 'open' | 'reconnecting' | 'closed';

export interface UseSseStreamResult {
  status: SseStatus;
}

/**
 * React Hook：挂载即建立 SSE 连接，卸载自动清理。
 *
 * 用法：
 * ```tsx
 * useSseStream({
 *   url: `${BASE}/sys/notify/stream`,
 *   getToken: () => useAuthStore.getState().accessToken,
 *   mode: 'event-source',          // 默认；高安全场景用 'fetch'
 *   onMessage: (m) => bumpBadge(m.data),
 *   onOpen: () => refreshUnread(), // 重连成功 → 回拉 /unread-count 自愈红点
 *   onError: async (e) => {
 *     if (e.type === 'unauthorized') { await ensureRefreshed(); /* 下次重连用新令牌 *\/ }
 *   },
 * });
 * ```
 */
export function useSseStream(options: SseClientOptions): UseSseStreamResult {
  const optionsRef = useRef(options);
  optionsRef.current = options;
  const [status, setStatus] = useState<SseStatus>('idle');

  useEffect(() => {
    const client = new SseClient({
      ...optionsRef.current,
      onOpen: () => {
        setStatus('open');
        optionsRef.current.onOpen?.();
      },
      onError: (err: SseError) => {
        if (err.type === 'unauthorized') setStatus('reconnecting');
        optionsRef.current.onError?.(err);
      },
      onMessage: (msg: SseMessage) => optionsRef.current.onMessage?.(msg),
    });
    setStatus('open');
    client.connect();
    return () => {
      client.close();
      setStatus('closed');
    };
    // 仅在挂载时建立一次连接；动态参数请通过稳定的 getToken / 回调闭包传入
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { status };
}
