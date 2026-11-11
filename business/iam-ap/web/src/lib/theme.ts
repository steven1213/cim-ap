// 主题读写——实现已上提至 `@cim/theme`，本文件只负责绑定本 ap 的存储键。
// index.html 内联脚本会在首屏渲染前抢先应用该键，避免主题闪烁（FOUC）——
// 改键名时必须同步 index.html，否则首屏与运行时会读取不同键。
import {
  applyTheme,
  persistTheme as persist,
  readTheme as read,
  type Theme,
} from '@cim/theme';

/** 本 ap 的偏好存储键（各 ap 前缀隔离，互不串味）。 */
const KEY = 'iam-theme';

export type { Theme };
export { applyTheme };

/** 读主题偏好（缺省 light）。 */
export const readTheme = (): Theme => read(KEY);

/** 落盘并立即生效。 */
export const persistTheme = (theme: Theme): void => persist(KEY, theme);
