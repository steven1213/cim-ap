/**
 * 缺键上报：去重 + 攒批 + 有界重试。
 *
 * <p>「缺键」= 四级兜底链全未命中（目标语言 → 默认语言 → 内置包 → humanize 之前），
 * 属**真缺口**，对应一张待翻译工单。三个约束决定了实现形态：</p>
 *
 * <ol>
 *   <li>一次页面渲染会连续缺十几个键 → 必须攒批，不能一键一请求；</li>
 *   <li>同一键在一次会话里只该报一次 → 会话级去重；</li>
 *   <li>端点缺失/404 时不能每 3 秒空打一枪 → 重试有上限，超限丢弃。</li>
 * </ol>
 */
export interface MissingReporterOptions {
  send: (keys: string[], lang: string) => Promise<unknown>;
  /** 取当前语言：flush 时才取值，避免切换语言后把旧语言带上去 */
  currentLang: () => string;
  debounceMs?: number;
  retryLimit?: number;
  dev?: boolean;
}

/**
 * @return 报告单个缺键的函数（可直接作为 i18next 的 `missingKeyHandler` 处理器）
 */
export function createMissingReporter(options: MissingReporterOptions): (key: string) => void {
  const { send, currentLang, debounceMs = 3000, retryLimit = 3, dev = false } = options;

  /** 本次会话已入队或已成功上报的键 */
  const seen = new Set<string>();
  /** 待上报 */
  const pending = new Set<string>();
  let timer: ReturnType<typeof setTimeout> | undefined;
  let failures = 0;

  function schedule(): void {
    if (timer !== undefined) return;
    timer = setTimeout(() => {
      void flush();
    }, debounceMs);
  }

  async function flush(): Promise<void> {
    timer = undefined;
    if (pending.size === 0) return;
    const keys = Array.from(pending);
    pending.clear();
    try {
      await send(keys, currentLang());
      failures = 0;
    } catch {
      failures += 1;
      if (failures > retryLimit) {
        // 端点大概率不存在（如尚未接入 cim-i18n-starter 的 ap）：停止重试，别刷控制台
        if (dev) {
          // eslint-disable-next-line no-console
          console.warn(`[i18n] 缺键上报连续失败 ${failures} 次，已放弃：${keys.join(', ')}`);
        }
        return;
      }
      keys.forEach((k) => pending.add(k));
      schedule();
    }
  }

  return function report(key: string): void {
    // 不带点的键不报：多半是 antd/业务把 data-* 当 key 透传进来的噪音
    if (!key || !key.includes('.')) return;
    if (seen.has(key)) return;
    seen.add(key);
    pending.add(key);
    if (dev) {
      // eslint-disable-next-line no-console
      console.warn(`[i18n] 缺失译文键：${key}`);
    }
    schedule();
  };
}
