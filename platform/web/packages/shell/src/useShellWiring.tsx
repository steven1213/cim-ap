// 控制台外壳接线统一 Hook——四份 Layout 里逐字重复的「theme / 菜单模型 / labels /
// 页签 / 开签」收敛为一份实现；ap 只注入差异：菜单树、图标解析、tabStore、
// i18n 键空间、主题三件套。
//
// 边界哲学与 ConsoleShell 一致：渲染形态全平台一份，「怎么得到数据」留在各 ap。
// Layout 收敛后只剩品牌/用户/插槽/登出这类真正的 ap 差异，约 60~80 行。
import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { StoreApi, UseBoundStore } from 'zustand';
import type { CimMenuNode } from './createAuthStore';
import type { TabState } from './createTabStore';
import { useShellTabs } from './useShellTabs';
import type {
  ShellEnv,
  ShellIconCmp,
  ShellLabels,
  ShellNavGroup,
  ShellNavItem,
  ShellTabs,
  ShellTheme,
} from './types';

/** 平台超管通配码（cim-auth 按令牌 is_super 发放，cim-system 据此短路放通）。 */
const SUPER_ADMIN = 'SUPER_ADMIN';

/** 主题读写三件套（ap 的 lib/theme.ts 薄绑定；shell 刻意不依赖 @cim/theme）。 */
export interface ShellThemeIo {
  read: () => ShellTheme;
  persist: (t: ShellTheme) => void;
  apply: (t: ShellTheme) => void;
}

export interface UseShellWiringOptions {
  /** 应用码：theme Io 已在 ap 侧绑定存储键，这里用于文档与将来的键推导。 */
  app: string;
  /**
   * i18n 键空间前缀：'mds' → `mds.shell.*`；approval 的键无应用前缀（`shell.*`）→
   * 传 ''。缺省取 app。
   */
  labelNs?: string;
  /** 归一菜单树（CimMenuNode）。信封形数据（IAM）先过 normalizeMenuTree。 */
  menus: CimMenuNode[];
  /** 图标解析器（ap 的 iconRegistry / @cim/admin resolveIcon）。 */
  resolveIcon: (key: string | null | undefined) => ShellIconCmp;
  /** 页签 store（createTabStore 产物）。 */
  useTabStore: UseBoundStore<StoreApi<TabState>>;
  /** 主题读写三件套。 */
  themeIo: ShellThemeIo;
  /** 页面条目副标题 `{i18nCode}.desc`（IAM 菜单库为每页配说明，其余 ap 无此约定）。 */
  itemDesc?: boolean;
  /** 顶级 MENU 自成一档时是否带分组标题（IAM 不带，其余 ap 带）。 */
  topMenuCap?: boolean;
  /**
   * 当前用户权限码集（内存态）。含平台超管通配码 `SUPER_ADMIN` 时，顶栏渲染
   * 「管理员」徽标（文案取 `{ns}.shell.adminBadge` / `.adminHint`，未配置键则不渲染）
   * ——超管标识是平台统一语义，不再由各 Layout 自行判定。
   */
  permissions?: string[];
}

export interface ShellWiring {
  theme: ShellTheme;
  onThemeChange: (t: ShellTheme) => void;
  groups: ShellNavGroup[];
  labels: ShellLabels;
  tabs: ShellTabs;
  env: ShellEnv;
  /** 平台超管徽标（非超管或未配置 `shell.adminBadge` 键时为 undefined）。 */
  roleBadge?: ReactNode;
  /** 登出时清页签残留（下一个登录者不应看到上一个人的页签）。 */
  clearTabs: () => void;
}

export interface NavBuildOptions {
  t: (key: string, opts?: { defaultValue?: string }) => string;
  resolveIcon: (key: string | null | undefined) => ShellIconCmp;
  /** 页面条目副标题 `{i18nCode}.desc`。 */
  itemDesc?: boolean;
  /** 顶级 MENU 自成一档时是否带分组标题。 */
  topMenuCap?: boolean;
}

/**
 * 归一菜单树 → 外壳侧栏模型。递归，与 `sys_menu` 层级一一对应：
 * 顶级 DIR → 分组标题（cap），其下 DIR/MENU 套 MENU → 可展开节点，MENU 叶子 → 页面；
 * 顶级 MENU（概览/工作台）自成一档。空组（子项全隐/全禁用/无权限）不渲染。
 */
export function buildNavGroups(menus: CimMenuNode[], opts: NavBuildOptions): ShellNavGroup[] {
  const { t, resolveIcon, itemDesc = false, topMenuCap = true } = opts;

  const toItem = (m: CimMenuNode): ShellNavItem | null => {
    if (m.type === 'BUTTON' || !m.visible || m.status !== 'ENABLED') return null;
    const kids = (m.children ?? [])
      .map(toItem)
      .filter((x): x is ShellNavItem => x !== null);
    // 有可用子项 → 上级节点：只展开不导航
    if (kids.length > 0) {
      return { key: m.id, label: t(m.i18nCode), Icon: resolveIcon(m.icon), children: kids };
    }
    if (m.type === 'MENU' && m.path) {
      return {
        key: m.id,
        path: m.path,
        label: t(m.i18nCode),
        // 副标题是「页面说明」语义（IAM 菜单库逐页配置），缺键回退空串
        ...(itemDesc ? { desc: t(`${m.i18nCode}.desc`, { defaultValue: '' }) } : {}),
        Icon: resolveIcon(m.icon),
      };
    }
    return null;
  };

  const out: ShellNavGroup[] = [];
  for (const m of menus) {
    if (m.type === 'BUTTON' || !m.visible || m.status !== 'ENABLED') continue;
    if (m.type === 'DIR') {
      const items = (m.children ?? [])
        .map(toItem)
        .filter((x): x is ShellNavItem => x !== null);
      if (items.length) out.push({ key: m.id, cap: t(m.i18nCode), items });
    } else if (m.type === 'MENU' && m.path) {
      const item = toItem(m);
      if (item) {
        out.push({ key: m.id, ...(topMenuCap ? { cap: t(m.i18nCode) } : {}), items: [item] });
      }
    }
  }
  return out;
}

/**
 * @param opts 见 {@link UseShellWiringOptions}
 */
export function useShellWiring(opts: UseShellWiringOptions): ShellWiring {
  const {
    app,
    labelNs = app,
    menus,
    resolveIcon,
    useTabStore,
    themeIo,
    itemDesc = false,
    topMenuCap = true,
    permissions,
  } = opts;

  const { t } = useTranslation();
  const { pathname } = useLocation();

  const [theme, setTheme] = useState<ShellTheme>(() => themeIo.read());
  useEffect(() => {
    themeIo.apply(theme);
  }, [theme, themeIo]);
  const onThemeChange = (next: ShellTheme) => {
    setTheme(next);
    themeIo.persist(next);
  };

  const openTab = useTabStore((s) => s.open);
  const clearTabs = useTabStore((s) => s.clear);

  /** 菜单树展平（页签标题解析 / 路由匹配用；只看有 path 的 MENU 节点）。 */
  const flatMenus = useMemo<CimMenuNode[]>(() => {
    const out: CimMenuNode[] = [];
    const walk = (nodes: CimMenuNode[]) => {
      for (const n of nodes) {
        if (n.path) out.push(n);
        walk(n.children);
      }
    };
    walk(menus);
    return out;
  }, [menus]);

  // 路由切换 → 打开/激活对应页签（仅已知导航路径；404 等未知路径不开签）
  useEffect(() => {
    if (flatMenus.length === 0) return;
    const m = flatMenus.find((x) => x.path === pathname);
    if (m?.path) openTab({ path: m.path, i18nCode: m.i18nCode });
  }, [pathname, flatMenus, openTab]);

  const groups = useMemo<ShellNavGroup[]>(
    () => buildNavGroups(menus, { t, resolveIcon, itemDesc, topMenuCap }),
    [menus, t, resolveIcon, itemDesc, topMenuCap],
  );

  const k = (key: string) => (labelNs ? `${labelNs}.${key}` : key);
  const labels = useMemo<ShellLabels>(
    () => ({
      toggleNav: t(k('shell.toggleNav')),
      toggleGroup: t(k('shell.toggleGroup')),
      themePicker: t(k('shell.themePicker')),
      themeNames: {
        light: t(k('shell.theme.light')),
        dark: t(k('shell.theme.dark')),
        cockpit: t(k('shell.theme.cockpit')),
        amber: t(k('shell.theme.amber')),
      },
      logout: t(k('shell.logout')),
      tabs: {
        label: t(k('shell.tabs.label')),
        scrollLeft: t(k('shell.tabs.scrollLeft')),
        scrollRight: t(k('shell.tabs.scrollRight')),
        close: t(k('shell.tabs.close')),
        closeThis: t(k('shell.tabs.closeThis')),
        closeOthers: t(k('shell.tabs.closeOthers')),
        closeRight: t(k('shell.tabs.closeRight')),
        closeAll: t(k('shell.tabs.closeAll')),
      },
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, labelNs],
  );

  // 环境标识四 ap 同构（构建模式徽标），文案统一为 `build: <mode>`
  const env = useMemo<ShellEnv>(() => {
    const isProd = import.meta.env.MODE === 'production';
    return {
      label: isProd ? 'PRODUCTION' : 'DEVELOPMENT',
      tone: isProd ? 'ok' : 'warn',
      title: `build: ${import.meta.env.MODE}`,
    };
  }, []);

  // 平台超管徽标：判定与文案都收敛在壳层（原 IAM/RMS 各写一份、MDS/approval 缺失）
  const roleBadge = useMemo<ReactNode | undefined>(() => {
    if (!permissions?.includes(SUPER_ADMIN)) return undefined;
    const badge = t(k('shell.adminBadge'), { defaultValue: '' });
    if (!badge) return undefined;
    const hint = t(k('shell.adminHint'), { defaultValue: '' });
    return (
      <span className="role-chip" title={hint || undefined}>
        {badge}
      </span>
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [permissions, t, labelNs]);

  // 标题解析优先取菜单的文案码（随授权即时更新），回退到开签时快照（只存码不存译文）
  const tabs = useShellTabs(useTabStore, {
    titleOf: (path, code) => {
      const i18n = flatMenus.find((x) => x.path === path)?.i18nCode ?? code;
      return i18n ? t(i18n) : path;
    },
  });

  return { theme, onThemeChange, groups, labels, tabs, env, roleBadge, clearTabs };
}
