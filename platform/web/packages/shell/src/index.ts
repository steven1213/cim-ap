// @cim/shell —— 控制台外壳（五个 ap 共用）。
//
// 依赖方向：`@cim/ui`（样式）→ `@cim/shell` ← 应用。外壳不依赖 @cim/data / @cim/i18n：
// 菜单是数据模型、文案是注入字符串，切换语言由应用重渲染驱动（见 types.ts 说明）。
export { ConsoleShell, type ConsoleShellProps } from './ConsoleShell';
export { TabStrip, type TabStripProps } from './TabStrip';
export { createTabStore, type TabItem, type TabState } from './createTabStore';
export { useShellTabs, type UseShellTabsOptions } from './useShellTabs';
export {
  LocaleSwitcher,
  useLocaleSwitcher,
  type LocaleSwitcherProps,
  type ShellLocale,
  type ShellLocaleRuntime,
  type UseLocaleSwitcherResult,
} from './LocaleSwitcher';
export {
  NotificationBell,
  type NotificationBellLabels,
  type NotificationBellProps,
  type ShellNotifyItem,
} from './NotificationBell';

export {
  IconChevron,
  IconLogout,
  IconMoon,
  IconShield,
  IconSidebar,
  IconSun,
  IconX,
} from './icons';
export type {
  ShellBrand,
  ShellEnv,
  ShellIconCmp,
  ShellLabels,
  ShellNavGroup,
  ShellNavItem,
  ShellTab,
  ShellTabLabels,
  ShellTabs,
  ShellUser,
} from './types';
