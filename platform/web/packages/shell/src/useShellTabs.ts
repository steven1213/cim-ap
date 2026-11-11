// 把页签 store 绑成外壳要的回调组。
//
// 这三个「关闭后跳哪」的规则以前写在三份 TabBar 里逐字重复（store 的 close 系列
// 返回「下一个激活路径」，由组件负责 navigate）。抽到这里以后，连导航语义都只有一份：
// 关闭激活签 → 落到相邻签、关闭全部 → 回固定签、点已激活签 → 只请求滚回可视区。
import { useLocation, useNavigate } from 'react-router-dom';
import type { StoreApi, UseBoundStore } from 'zustand';
import type { TabItem, TabState } from './createTabStore';
import type { ShellTabs } from './types';

export interface UseShellTabsOptions {
  /** 标题解析：菜单库/导航配置优先，回退开签时的文案码快照。 */
  titleOf: (path: string, i18nCode: string) => string;
  homePath?: string;
}

/**
 * @param useStore `createTabStore()` 产出的 store hook
 */
export function useShellTabs(
  useStore: UseBoundStore<StoreApi<TabState>>,
  { titleOf, homePath = '/' }: UseShellTabsOptions,
): ShellTabs {
  const navigate = useNavigate();
  const location = useLocation();

  const tabs = useStore((s) => s.tabs);
  const activePath = useStore((s) => s.activePath);
  const scrollTicket = useStore((s) => s.scrollTicket);
  const requestScroll = useStore((s) => s.requestScroll);
  const setActive = useStore((s) => s.setActive);
  const close = useStore((s) => s.close);
  const closeOthers = useStore((s) => s.closeOthers);
  const closeRight = useStore((s) => s.closeRight);
  const closeAll = useStore((s) => s.closeAll);

  return {
    tabs: tabs.map((tab: TabItem) => ({
      path: tab.path,
      label: titleOf(tab.path, tab.i18nCode),
      pinned: tab.path === homePath,
    })),
    activePath,
    // 路由键覆盖「同路径重新跳转」，票证覆盖「点已激活签」——两者都不变时靠语言切换重测宽度
    syncToken: `${location.key}:${scrollTicket}:${tabs.map((t) => t.path).join(',')}`,
    onSelect: (path) => {
      if (path === activePath) {
        requestScroll(); // 已激活：无状态变化，仍要滚回可视区
        return;
      }
      setActive(path);
      navigate(path);
    },
    onClose: (path) => {
      const next = close(path);
      if (next) navigate(next);
    },
    onCloseOthers: (path) => closeOthers(path),
    onCloseRight: (path) => {
      const next = closeRight(path);
      if (next) navigate(next);
    },
    onCloseAll: () => {
      const home = closeAll();
      if (activePath !== home) navigate(home);
    },
  };
}
