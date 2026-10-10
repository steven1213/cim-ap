// 主题（浅色 / 深色）读写：落 localStorage，作用在 <html data-theme>。
// index.html 内联脚本会在首屏渲染前抢先应用，避免主题闪烁（FOUC）。
export type Theme = 'light' | 'dark';

const KEY = 'iam-theme';

export function readTheme(): Theme {
  return localStorage.getItem(KEY) === 'dark' ? 'dark' : 'light';
}

export function applyTheme(theme: Theme): void {
  document.documentElement.setAttribute('data-theme', theme);
}

export function persistTheme(theme: Theme): void {
  localStorage.setItem(KEY, theme);
  applyTheme(theme);
}
