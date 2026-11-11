// 页签 store 工厂：三份 `tabStore.ts` 逐字相同（仅注释里的首页叫法不同），
// 唯一差异是 `HOME_PATH`——三个 ap 的固定签都是 `/`。
//
// <p>为什么用工厂而不是共享单例：页签是**会话内状态**，每个 ap 独立部署、
// 各自登录，共享一个 store 会让 A 应用的页签漏到 B 应用里。工厂产出各自独立的
// store 实例，行为（关闭时优先右邻、固定签不可关）只有一份实现。</p>
import { create } from 'zustand';

/** 单个页签：路由路径 + 文案码（译文渲染时解析，切语言即时生效）。 */
export interface TabItem {
  path: string;
  i18nCode: string;
}

export interface TabState {
  tabs: TabItem[];
  activePath: string;
  /**
   * 滚动票证：每次递增，请求页签条把激活签滚入可视区。
   * 导航会变 `location.key`，但「点击已激活路由的菜单」时激活签与列表都不变，
   * 必须靠显式票证兜底。
   */
  scrollTicket: number;
  requestScroll: () => void;
  /** 打开（或激活）一个页签；已存在则仅激活并刷新文案码。 */
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

/**
 * 建一个页签 store。
 *
 * @param homePath 固定签路径（三个 ap 都是 `/`），不可关闭、关闭全部后保留
 */
export function createTabStore(homePath = '/') {
  const homeTab = (): TabItem[] => [{ path: homePath, i18nCode: '' }];

  return create<TabState>()((set, get) => ({
    tabs: homeTab(),
    activePath: homePath,
    scrollTicket: 0,

    requestScroll: () => set((s) => ({ scrollTicket: s.scrollTicket + 1 })),

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
      if (idx < 0 || path === homePath) return null;
      const next = tabs.filter((x) => x.path !== path);
      if (path !== activePath) {
        set({ tabs: next });
        return null;
      }
      // 优先右邻，否则左邻；删空（不可能，固定签在）兜底回固定签
      const nextActive = next[Math.min(idx, next.length - 1)]?.path ?? homePath;
      set({ tabs: next, activePath: nextActive });
      return nextActive;
    },

    closeOthers: (path) => {
      const { tabs } = get();
      const target = tabs.find((x) => x.path === path);
      if (!target) return;
      set({
        tabs: target.path === homePath ? [target] : [...homeTab(), target],
        activePath: path,
      });
    },

    closeAll: () => {
      set({ tabs: homeTab(), activePath: homePath });
      return homePath;
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

    clear: () => set({ tabs: homeTab(), activePath: homePath }),
  }));
}
