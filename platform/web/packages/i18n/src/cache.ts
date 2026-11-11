/**
 * 译文整包的本地缓存。
 *
 * <p>⚠️ 必须缓存**整包**而不是只缓存版本号。端点 `GET /api/i18n/messages` 的约定是
 * 「`v` 与当前版本一致则只回版本、消息体为空」；若前端只记版本号，刷新页面后就会拿到
 * 空包 → 界面全空。整包落 `localStorage` 才能让「先渲染缓存、再比对版本」成立。</p>
 */
export interface BundleCache {
  read(lang: string): Record<string, string> | null;
  write(lang: string, messages: Record<string, string>): void;
  readVersion(): string | undefined;
  writeVersion(version: string): void;
}

/**
 * @param app 命名空间前缀，见 `CreateI18nOptions.app`
 */
export function createBundleCache(app: string): BundleCache {
  const versionKey = `${app}-i18n-version`;
  const bundlePrefix = `${app}-i18n-bundle:`;

  return {
    read(lang: string): Record<string, string> | null {
      try {
        const raw = localStorage.getItem(bundlePrefix + lang);
        if (!raw) return null;
        const parsed = JSON.parse(raw) as unknown;
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null;
        const map = parsed as Record<string, string>;
        return Object.keys(map).length > 0 ? map : null;
      } catch {
        // 隐私模式禁写、或历史脏数据：当没有缓存
        return null;
      }
    },

    write(lang: string, messages: Record<string, string>): void {
      // 空包不落盘：写进去等于把上一份好缓存擦掉，下次启动就只剩兜底文案
      if (!messages || Object.keys(messages).length === 0) return;
      try {
        localStorage.setItem(bundlePrefix + lang, JSON.stringify(messages));
      } catch {
        // 配额超限：本次会话内存里已有，不影响渲染
      }
    },

    readVersion(): string | undefined {
      try {
        return localStorage.getItem(versionKey) ?? undefined;
      } catch {
        return undefined;
      }
    },

    writeVersion(version: string): void {
      try {
        localStorage.setItem(versionKey, version);
      } catch {
        /* 忽略 */
      }
    },
  };
}

/** 语言偏好（独立于 `BundleCache`，两个 API 的用途完全不同）。 */
export function createLangPreference(app: string, defaultLng: string) {
  const key = `${app}-lang`;
  return {
    read(): string {
      try {
        return localStorage.getItem(key) || defaultLng;
      } catch {
        return defaultLng;
      }
    },
    write(lang: string): void {
      try {
        localStorage.setItem(key, lang);
      } catch {
        /* 忽略：只影响下次启动的初值 */
      }
    },
  };
}
