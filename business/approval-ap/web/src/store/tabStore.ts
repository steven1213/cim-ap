// 页签 store：多标签导航（tags-view）的打开/关闭/激活状态。
//
// <p>设计要点：
// <ul>
//   <li>「工作台」(`/`) 是<b>固定签</b>：不可关闭，关闭全部/关闭其他后始终保留；</li>
//   <li>标题只存 `i18nCode` 不存译文——切语言时由渲染层 `t()` 重新解析；</li>
//   <li>不落盘：页签是会话内的工作痕迹，刷新重置最符合直觉（与权限同理由）。</li>
// </ul></p>
import { create } from 'zustand';

/** 单个页签：路由路径 + 菜单文案码（译文渲染时解析）。 */
export interface TabItem {
  path: string;
  i18nCode: string;
}

/** 固定签路径（工作台），关闭全部后保留。 */
export const HOME_PATH = '/';

function homeTab(): TabItem[] {
  return [{ path: HOME_PATH, i18nCode: '' }];
}

interface TabState {
  tabs: TabItem[];
  activePath: string;
  scrollTicket: number;
  requestScroll: () => void;
  open: (tab: TabItem) => void;
  setActive: (path: string) => void;
  close: (path: string) => string | null;
  closeOthers: (path: string) => void;
  closeRight: (path: string) => string | null;
  closeAll: () => string;
  clear: () => void;
}

export const useTabStore = create<TabState>()((set, get) => ({
  tabs: homeTab(),
  activePath: HOME_PATH,
  scrollTicket: 0,

  requestScroll: () => set((s) => ({ scrollTicket: s.scrollTicket + 1 })),

  open: (tab) => {
    const { tabs } = get();
    const existing = tabs.find((x) => x.path === tab.path);
    if (existing) {
      set({
        activePath: tab.path,
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
    const nextActive = next[Math.min(idx, next.length - 1)]?.path ?? HOME_PATH;
    set({ tabs: next, activePath: nextActive });
    return nextActive;
  },

  closeOthers: (path) => {
    const { tabs } = get();
    const target = tabs.find((x) => x.path === path);
    if (!target) return;
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
