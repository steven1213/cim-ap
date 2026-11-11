// 页签 store：多标签导航（tags-view）的打开/关闭/激活状态。
//
// <p>实现已上移到 `@cim/shell` 的 `createTabStore`（三份原本逐字相同）。
// 本文件只保留「本 ap 的固定签是谁」和对外导出，行为改动请改共享包。</p>
//
// <p>约定不变：「工作台」(`/`) 是固定签，不可关闭；标题只存 `i18nCode` 不存译文；
// 不落盘（页签是会话内的工作痕迹，刷新重置）。</p>
import { createTabStore, type TabItem } from '@cim/shell';

/** 固定签路径（工作台），关闭全部后保留。 */
export const HOME_PATH = '/';

export const useTabStore = createTabStore(HOME_PATH);

export type { TabItem };
