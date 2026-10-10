// 页签 store：多标签导航（tags-view）的打开/关闭/激活状态。
//
// <p>设计要点：
// <ul>
//   <li>「概览」(`/`) 是<b>固定签</b>：不可关闭，关闭全部/关闭其他后始终保留；</li>
//   <li>标题只存 `i18nCode` 不存译文——切语言时由渲染层 `t()` 重新解析；</li>
//   <li>不落盘：页签是会话内的工作痕迹，刷新重置最符合直觉（与权限同理由）。</li>
// </ul></p>
import { create } from 'zustand';

/** 单个页签：路由路径 + 菜单文案码（译文渲染时解析）。 */
export interface TabItem {
  path: string;
  i18nCode: string;
}

/** 固定签路径（概览），关闭全部后保留。 */
export const HOME_PATH = '/';

function homeTab(): TabItem[] {
  return [{ path: HOME_PATH, i18nCode: '' }];
}

interface TabState {
  tabs: TabItem[];
  activePath: string;
  /** 打开（或激活）一个页签；已存在则仅激活并更新文案码。 */
  open: (tab: TabItem) => void;
  /** 仅切换激活签（点击既有签时，导航由调用方完成）。 */
  setActive: (path: string) => void;
  /**
   * 关闭一个签；固定签拒绝关闭（返回 null）。
   * 若关闭的是激活签，自动选中相邻签并返回其路径（调用方负责导航）；否则返回 null。
   */
  close: (path: string) => string | null;
  /** 关闭其他：保留固定签与指定签，激活指定签。 */
  closeOthers: (path: string) => void;
  /**
   * 关闭右侧：保留指定签及其左侧（含固定签）。
   * 若激活签被关掉，激活指定签并返回其路径（调用方负责导航）；否则返回 null。
   */
  closeRight: (path: string) => string | null;
  /** 关闭全部：重置为只剩固定签，返回固定签路径（调用方负责导航）。 */
  closeAll: () => string;
  /** 登出清空：重置为初始态。 */
  clear: () => void;
}

export const useTabStore = create<TabState>()((set, get) => ({
  tabs: homeTab(),
  activePath: HOME_PATH,

  open: (tab) => {
    const { tabs } = get();
    const existing = tabs.find((x) => x.path === tab.path);
    if (existing) {
      set({
        activePath: tab.path,
        // 文案码可能随后端菜单更新：顺手刷新
        tabs: existing.i18nCode === tab.i18nCode ? tabs : tabs.map((x) => (x.path === tab.path ? tab : x)),
      });
      return;
    }
    set({ tabs: [...tabs, tab], activePath: tab.path });
  },

  setActive: (path) => set({ activePath: path }),

  close: (path) => {
    const { tabs, activePath } = get();
    const idx = tabs.findIndex((x) => x.path === path);
    if (idx < 0 || path === HOME_PATH) return null;
    const next = tabs.filter((x) => x.path !== path);
    if (path !== activePath) {
      set({ tabs: next });
      return null;
    }
    // 优先右邻，否则左邻；删空（不可能，固定签在）兜底回固定签
    const nextActive = next[Math.min(idx, next.length - 1)]?.path ?? HOME_PATH;
    set({ tabs: next, activePath: nextActive });
    return nextActive;
  },

  closeOthers: (path) => {
    const { tabs } = get();
    const target = tabs.find((x) => x.path === path);
    if (!target) return;
    // 固定签 + 指定签（指定签即固定签时不重复）
    set({
      tabs: target.path === HOME_PATH ? [target] : [...homeTab(), target],
      activePath: path,
    });
  },

  closeAll: () => {
    set({ tabs: homeTab(), activePath: HOME_PATH });
    return HOME_PATH;
  },

  closeRight: (path) => {
    const { tabs, activePath } = get();
    const idx = tabs.findIndex((x) => x.path === path);
    if (idx < 0) return null;
    const next = tabs.slice(0, idx + 1);
    if (next.some((x) => x.path === activePath)) {
      set({ tabs: next });
      return null;
    }
    set({ tabs: next, activePath: path });
    return path;
  },

  clear: () => set({ tabs: homeTab(), activePath: HOME_PATH }),
}));
