// 主题与密度偏好的唯一读写入口。
//
// 契约：
// 1. localStorage 键由调用方传入（各 ap 用自己的 appCode 作前缀，互不干扰）；
// 2. 主题作用在 `<html data-theme>`（四选一），密度作用在 `<html data-cim-density>`；
// 3. 首屏防闪白由各应用 index.html 内联脚本负责（本模块不参与，避免同步阻塞渲染）。
//
// ⚠️ 令牌（token）不可持久化，语言/密度/主题这三类**非敏感偏好**才可落盘。

export type Theme = 'light' | 'dark' | 'cockpit' | 'amber';
export type Density = 'compact' | 'normal' | 'touch';

export const THEME_ATTR = 'data-theme';
export const DENSITY_ATTR = 'data-cim-density';

/** 全部可选主题（顺序即选择菜单展示顺序）。 */
export const THEMES: readonly Theme[] = ['light', 'dark', 'cockpit', 'amber'] as const;

export const DEFAULT_THEME: Theme = 'light';
export const DEFAULT_DENSITY: Density = 'normal';

/** 深色类主题（antd ConfigProvider 需要切 dark 算法；CSS 侧由 tokens.css 自行区分）。 */
export const DARK_THEMES: readonly Theme[] = ['dark', 'cockpit'] as const;

export function isDarkTheme(theme: Theme): boolean {
  return DARK_THEMES.includes(theme);
}

function readKey(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeKey(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* 隐私模式/配额超限时静默降级为「本次会话有效」 */
  }
}

/** 读主题偏好；不在 THEMES 列表内的值（含旧数据）回退 `fallback`。 */
export function readTheme(storageKey: string, fallback: Theme = DEFAULT_THEME): Theme {
  const v = readKey(storageKey);
  return v !== null && (THEMES as readonly string[]).includes(v) ? (v as Theme) : fallback;
}

/** 把主题写到 `<html>`，不落盘。 */
export function applyTheme(theme: Theme): void {
  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute(THEME_ATTR, theme);
  }
}

/** 落盘并立即生效。 */
export function persistTheme(storageKey: string, theme: Theme): void {
  writeKey(storageKey, theme);
  applyTheme(theme);
}

/** 读密度档；非法值回退 `fallback`（默认 normal）。 */
export function readDensity(storageKey: string, fallback: Density = DEFAULT_DENSITY): Density {
  const v = readKey(storageKey);
  return v === 'compact' || v === 'touch' ? v : fallback;
}

/** 把密度档写到 `<html>`；`normal` 时移除属性（回落到 :root 默认值）。 */
export function applyDensity(density: Density): void {
  if (typeof document === 'undefined') return;
  if (density === 'normal') {
    document.documentElement.removeAttribute(DENSITY_ATTR);
  } else {
    document.documentElement.setAttribute(DENSITY_ATTR, density);
  }
}

/** 落盘并立即生效。 */
export function persistDensity(storageKey: string, density: Density): void {
  writeKey(storageKey, density);
  applyDensity(density);
}
