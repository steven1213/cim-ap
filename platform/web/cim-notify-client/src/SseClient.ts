/**
 * CIM 实时推送前端客户端（SSE）。
 *
 * 设计要点（与后端 cim-notify-starter 对齐）：
 * - 两种传输：EventSource（默认，简单、自带重连）/ fetch-stream（可带 Authorization 头，避免 URL 暴露令牌）。
 * - 指数退避重连（base 1s ×2，上限 30s）；连接成功即重置退避计数。
 * - 死连接探测：超过 heartbeatTimeoutMs 未收到任何帧（含心跳注释）则强制重连，防止反向代理静默掐断。
 * - 401 处理：fetch 模式可读取响应状态码；onError({type:'unauthorized'}) 由调用方借现有令牌刷新逻辑换发后重连。
 * - 重连成功后触发 onOpen（调用方应在 onOpen 中回拉各 ap 的 /unread-count 自愈红点，因为 SSE 必然丢消息）。
 */

export type SseTransportMode = 'event-source' | 'fetch';

export interface SseMessage {
  id?: string;
  event?: string;
  /** 已 JSON.parse 的 data 体（解析失败则为原始字符串）。 */
  data: unknown;
  /** 原始 data 文本。 */
  raw: string;
}

export interface SseError {
  type: 'error' | 'unauthorized';
  status?: number;
  error?: unknown;
}

export interface SseClientOptions {
  /** 订阅地址，如 `${baseURL}/sys/notify/stream`。 */
  url: string;
  /** 取令牌（内存态）；EventSource 模式会拼到 ?token=，fetch 模式写入 Authorization 头。 */
  getToken?: () => string | null | undefined;
  mode?: SseTransportMode;
  onMessage?: (msg: SseMessage) => void;
  onOpen?: () => void;
  onError?: (err: SseError) => void;
  /** 重连基线毫秒（默认 1000）。 */
  reconnectBaseMs?: number;
  /** 重连上限毫秒（默认 30000）。 */
  reconnectMaxMs?: number;
  /** 死连接探测阈值毫秒（默认 70000，须 > 服务端心跳 25s + 余量）。 */
  heartbeatTimeoutMs?: number;
}

const HB_COMMENT_PREFIX = ':';

export class SseClient {
  private readonly opts: Required<Pick<SseClientOptions, 'url' | 'reconnectBaseMs' | 'reconnectMaxMs' | 'heartbeatTimeoutMs'>> &
    SseClientOptions;
  private es?: EventSource;
  private controller?: AbortController;
  private reader?: ReadableStreamDefaultReader<Uint8Array>;
  private closed = false;
  private retries = 0;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private hbTimer?: ReturnType<typeof setInterval>;
  private lastActivityAt = 0;

  constructor(options: SseClientOptions) {
    this.opts = {
      reconnectBaseMs: 1000,
      reconnectMaxMs: 30000,
      heartbeatTimeoutMs: 70000,
      mode: 'event-source',
      ...options,
    };
  }

  connect(): void {
    this.closed = false;
    if (this.opts.mode === 'fetch') {
      this.connectFetch();
    } else {
      this.connectEventSource();
    }
  }

  close(): void {
    this.closed = true;
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.hbTimer) clearInterval(this.hbTimer);
    this.es?.close();
    this.controller?.abort();
    try {
      this.reader?.cancel();
    } catch {
      /* ignore */
    }
  }

  private startHeartbeatWatch(): void {
    this.lastActivityAt = Date.now();
    if (this.hbTimer) clearInterval(this.hbTimer);
    this.hbTimer = setInterval(() => {
      if (Date.now() - this.lastActivityAt > this.opts.heartbeatTimeoutMs) {
        // 死连接：强制重连
        this.close();
        this.scheduleReconnect();
      }
    }, Math.min(10000, this.opts.heartbeatTimeoutMs / 2));
  }

  private scheduleReconnect(): void {
    if (this.closed) return;
    const delay = Math.min(
      this.opts.reconnectBaseMs * 2 ** this.retries,
      this.opts.reconnectMaxMs,
    );
    this.retries += 1;
    this.reconnectTimer = setTimeout(() => {
      if (this.closed) return;
      this.connect();
    }, delay);
  }

  private buildUrlWithToken(): string {
    const token = this.opts.getToken?.();
    if (!token) return this.opts.url;
    const sep = this.opts.url.includes('?') ? '&' : '?';
    return `${this.opts.url}${sep}token=${encodeURIComponent(token)}`;
  }

  private connectEventSource(): void {
    const url = this.buildUrlWithToken();
    const es = new EventSource(url);
    this.es = es;
    es.onopen = () => {
      this.retries = 0;
      this.startHeartbeatWatch();
      this.opts.onOpen?.();
    };
    es.onmessage = (ev: MessageEvent<string>) => {
      this.lastActivityAt = Date.now();
      this.dispatchData(ev.data);
    };
    es.onerror = (ev: Event) => {
      // EventSource 不暴露 HTTP 状态码；未成功打开即报错多半为鉴权/网络失败，按 unauthorized 兜底交由调用方处理
      const unauthorized = es.readyState === EventSource.CONNECTING;
      if (!this.closed) {
        this.opts.onError?.({
          type: unauthorized ? 'unauthorized' : 'error',
          error: ev,
        });
        es.close();
        this.scheduleReconnect();
      }
    };
  }

  private async connectFetch(): Promise<void> {
    const token = this.opts.getToken?.();
    const headers: Record<string, string> = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const controller = new AbortController();
    this.controller = controller;
    let resp: Response;
    try {
      resp = await fetch(this.opts.url, { headers, signal: controller.signal });
    } catch (e) {
      if (!this.closed) {
        this.opts.onError?.({ type: 'error', error: e });
        this.scheduleReconnect();
      }
      return;
    }
    if (resp.status === 401) {
      this.opts.onError?.({ type: 'unauthorized', status: 401 });
      this.scheduleReconnect();
      return;
    }
    if (!resp.ok || !resp.body) {
      if (!this.closed) {
        this.opts.onError?.({ type: 'error', status: resp.status });
        this.scheduleReconnect();
      }
      return;
    }
    this.retries = 0;
    this.startHeartbeatWatch();
    this.opts.onOpen?.();
    this.readStream(resp.body.getReader());
  }

  private async readStream(reader: ReadableStreamDefaultReader<Uint8Array>): Promise<void> {
    this.reader = reader;
    const decoder = new TextDecoder();
    let buffer = '';
    try {
      while (!this.closed) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const frames = buffer.split('\n\n');
        buffer = frames.pop() ?? '';
        for (const rawFrame of frames) {
          const parsed = this.parseFrame(rawFrame);
          if (parsed) {
            this.lastActivityAt = Date.now();
            this.dispatchData(parsed.data, parsed.id, parsed.event);
          } else if (rawFrame.trim().startsWith(HB_COMMENT_PREFIX)) {
            this.lastActivityAt = Date.now();
          }
        }
      }
    } catch (e) {
      if (!this.closed) {
        this.opts.onError?.({ type: 'error', error: e });
        this.scheduleReconnect();
      }
    }
  }

  private parseFrame(frame: string): { id?: string; event?: string; data: string } | null {
    let id: string | undefined;
    let event: string | undefined;
    const dataLines: string[] = [];
    for (const line of frame.split('\n')) {
      if (!line) continue;
      if (line.startsWith(HB_COMMENT_PREFIX)) continue;
      const idx = line.indexOf(':');
      const field = idx === -1 ? line : line.slice(0, idx);
      const val = idx === -1 ? '' : line.slice(idx + 1).replace(/^ /, '');
      if (field === 'data') dataLines.push(val);
      else if (field === 'id') id = val;
      else if (field === 'event') event = val;
    }
    if (dataLines.length === 0) return null;
    return { id, event, data: dataLines.join('\n') };
  }

  private dispatchData(raw: string, id?: string, event?: string): void {
    let data: unknown = raw;
    try {
      data = JSON.parse(raw);
    } catch {
      /* 保持原始字符串 */
    }
    this.opts.onMessage?.({ id, event, data, raw });
  }
}
