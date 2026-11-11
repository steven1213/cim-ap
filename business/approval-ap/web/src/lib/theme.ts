// 主题读写——实现已上提至 `@cim/theme`，本文件只负责绑定本 ap 的存储键。
import {
  applyTheme,
  persistTheme as persist,
  readTheme as read,
  type Theme,
} from '@cim/theme';

/** 本 ap 的偏好存储键（各 ap 前缀隔离，互不串味）。 */
const KEY = 'approval-theme';

export type { Theme };
export { applyTheme };

/** 读主题偏好（缺省 light）。 */
export const readTheme = (): Theme => read(KEY);

/** 落盘并立即生效。 */
export const persistTheme = (theme: Theme): void => persist(KEY, theme);
