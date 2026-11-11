// 主题读写——实现已上提至 `@cim/theme`，本文件只负责绑定本 ap 的存储键。
//
// ⚠️ 存储键修正：本文件此前是从 IAM 整体拷贝的，键沿用 `iam-theme`。
// 各 ap 若部署在同一域名的不同路径下（如 /iam、/rms），localStorage 同源共享，
// 会导致两个应用的明暗偏好互相覆盖。改为 `rms-theme` 后前缀隔离。
import {
  applyTheme,
  persistTheme as persist,
  readTheme as read,
  type Theme,
} from '@cim/theme';

/** 本 ap 的偏好存储键（各 ap 前缀隔离，互不串味）。 */
const KEY = 'rms-theme';

export type { Theme };
export { applyTheme };

/** 读主题偏好（缺省 light）。 */
export const readTheme = (): Theme => read(KEY);

/** 落盘并立即生效。 */
export const persistTheme = (theme: Theme): void => persist(KEY, theme);
