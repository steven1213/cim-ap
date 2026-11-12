// 控制台外壳：顶部 header + 可收缩左侧菜单 + 多页签栏 + 内容区 + 底部 footer。
//
// <p><b>这一层存在的理由</b>：改造前三份 Layout（288/178/156 行）结构完全一样，
// 但每份都自己实现了一遍「侧栏收缩持久化、主题切换、页签联动、登出清理」，
// 于是同一处交互改三次，还漏改过（RMS 的主题存储键抄成了 IAM 的）。
// 现在这些只保留一份，各 ap 只剩「菜单从哪来、顶栏多放什么」。</p>
//
// <p><b>配置驱动 + 插槽兜底</b>（设计稿 §12-1 定稿）：菜单是数据（`ShellNavGroup[]`），
// 不是 `<Shell.Side>` 这类组合件——因为三个 ap 的菜单<b>来源</b>不同但<b>形态</b>相同，
// 组合件会让每个 ap 重写一遍分组/激活态/权限过滤；而顶栏确实各家不同
// （IAM 有超管徽标、approval 有身份切换、RMS 有通知铃），那部分用 `topbarExtra`
// 插槽交还各 ap。判据很简单：<b>结构性差异进模型，内容性差异进插槽</b>。</p>
import { useEffect, useState, type ReactNode } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { IconChevron, IconLogout, IconMoon, IconShield, IconSidebar, IconSun } from './icons';
import { TabStrip } from './TabStrip';
import type { ShellBrand, ShellEnv, ShellLabels, ShellNavItem, ShellNavGroup, ShellTabs, ShellUser } from './types';

/** 条目（含其后代）是否指向给定路由——用于「激活子项 → 上级自动展开/高亮」。 */
function containsPath(item: ShellNavItem, path: string): boolean {
  if (item.path === path) return true;
  return (item.children ?? []).some((c) => containsPath(c, path));
}

export interface ConsoleShellProps {
  /** localStorage 键前缀：`${storageKey}-sidebar-collapsed` / `${storageKey}-sidebar-groups`。 */
  storageKey: string;
  brand: ShellBrand;
  groups: ShellNavGroup[];
  labels: ShellLabels;
  /** 侧栏底部提示（图标 + 文案），如「密文存储 · 审计留痕」。 */
  sidebarNote?: ReactNode;
  /** 右上角用户块；`null` 表示无登录态。 */
  user?: ShellUser | null;
  /** 右上角权限徽标（超管），放在环境标识之前。 */
  roleBadge?: ReactNode;
  /** 环境标识（构建模式）；不传则不渲染。 */
  env?: ShellEnv;
  /**
   * 顶栏插槽：环境标识与主题按钮之间的区域。
   * 语言切换、通知铃、身份切换都放这里——它们在三个 ap 里的位置本来就在这段区间内。
   */
  topbarExtra?: ReactNode;
  footerApp: string;
  footerTagline: string;
  theme: 'light' | 'dark';
  onToggleTheme: () => void;
  onLogout: () => void;
  /** 分组头可折叠（IAM 有，RMS/approval 无）。 */
  collapsibleGroups?: boolean;
  /**
   * ≤ 该宽度时侧栏改为抽屉（配 `.scrim` 遮罩）。
   * 不传 = 窄屏沿用「收缩成图标栏」，不启用抽屉——RMS/approval 本地没有抽屉样式，
   * 贸然启用会出现「点了开关但侧栏不动」。
   */
  mobileBreakpoint?: number;
  /** 点击导航项后的副作用（IAM 用来请求页签滚回可视区）。 */
  onNavSelect?: (item: ShellNavItem) => void;
  /** 页签条；不传则不渲染页签区。 */
  tabs?: ShellTabs;
  children?: ReactNode;
}

export function ConsoleShell({
  storageKey,
  brand,
  groups,
  labels,
  sidebarNote,
  user,
  roleBadge,
  env,
  topbarExtra,
  footerApp,
  footerTagline,
  theme,
  onToggleTheme,
  onLogout,
  collapsibleGroups = false,
  mobileBreakpoint,
  onNavSelect,
  tabs,
  children,
}: ConsoleShellProps) {
  const COLLAPSED_KEY = `${storageKey}-sidebar-collapsed`;
  const GROUPS_KEY = `${storageKey}-sidebar-groups`;
  const OPEN_KEY = `${storageKey}-sidebar-open`;

  const { pathname } = useLocation();
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(COLLAPSED_KEY) === '1');
  const [mobileOpen, setMobileOpen] = useState(false);
  /** 收起的分组 key 集合（持久化；侧栏整体收起为图标态时不生效）。 */
  const [hiddenGroups, setHiddenGroups] = useState<Set<string>>(() => {
    try {
      const raw = localStorage.getItem(GROUPS_KEY);
      return new Set<string>(raw ? (JSON.parse(raw) as string[]) : []);
    } catch {
      return new Set<string>();
    }
  });
  /**
   * 展开的上级菜单 key（三级菜单的中间层）。
   *
   * <p>与 `hiddenGroups` 分开存：一个是「分组标题」的折叠，一个是「模块」的折叠，
   * 两者互不影响——用同一个集合会让「收起分组」顺带把模块状态也丢了。</p>
   */
  const [openNodes, setOpenNodes] = useState<Set<string>>(() => {
    try {
      const raw = localStorage.getItem(OPEN_KEY);
      return new Set<string>(raw ? (JSON.parse(raw) as string[]) : []);
    } catch {
      return new Set<string>();
    }
  });

  // 路由切换时收起移动端抽屉
  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  // 路由所在分组自动展开（折叠分组后点侧栏入口仍要能看到当前位置）
  useEffect(() => {
    if (!collapsibleGroups) return;
    const active = groups.find((g) => g.items.some((m) => containsPath(m, pathname)));
    if (!active) return;
    setHiddenGroups((prev) => {
      if (!prev.has(active.key)) return prev;
      const next = new Set(prev);
      next.delete(active.key);
      localStorage.setItem(GROUPS_KEY, JSON.stringify([...next]));
      return next;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname, groups]);

  // 路由所在模块链自动展开：直接点页签/深链进来时，侧栏不能停在「当前页看不见」的状态
  useEffect(() => {
    const keys: string[] = [];
    const walk = (items: ShellNavItem[]) => {
      for (const it of items) {
        if (!it.children?.length) continue;
        if (containsPath(it, pathname)) keys.push(it.key);
        walk(it.children);
      }
    };
    groups.forEach((g) => walk(g.items));
    if (keys.length === 0) return;
    setOpenNodes((prev) => {
      if (keys.every((k) => prev.has(k))) return prev;
      const next = new Set(prev);
      keys.forEach((k) => next.add(k));
      localStorage.setItem(OPEN_KEY, JSON.stringify([...next]));
      return next;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname, groups]);

  function toggleSidebar() {
    if (mobileBreakpoint && window.matchMedia(`(max-width: ${mobileBreakpoint}px)`).matches) {
      setMobileOpen((v) => !v);
      return;
    }
    setCollapsed((v) => {
      const next = !v;
      localStorage.setItem(COLLAPSED_KEY, next ? '1' : '0');
      return next;
    });
  }

  /** 收起/展开一个导航分组（持久化；图标态下不折叠）。 */
  function toggleGroup(key: string) {
    setHiddenGroups((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      localStorage.setItem(GROUPS_KEY, JSON.stringify([...next]));
      return next;
    });
  }

  /** 展开/收起一个上级菜单节点（三级菜单的中间层）。 */
  function toggleNode(key: string) {
    setOpenNodes((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      localStorage.setItem(OPEN_KEY, JSON.stringify([...next]));
      return next;
    });
  }

  /**
   * 点上级菜单：图标态下先展开侧栏再打开该项。
   *
   * <p>图标态只剩 18px 的图标，子项无处可放；此时若只切换 `openNodes`，
   * 用户会看到「点了图标什么都没发生」。所以先恢复侧栏宽度，再确保该项展开。</p>
   */
  function clickBranch(key: string) {
    if (collapsed) {
      setCollapsed(false);
      localStorage.setItem(COLLAPSED_KEY, '0');
      setOpenNodes((prev) => {
        if (prev.has(key)) return prev;
        const next = new Set(prev).add(key);
        localStorage.setItem(OPEN_KEY, JSON.stringify([...next]));
        return next;
      });
      return;
    }
    toggleNode(key);
  }

  /** 递归渲染条目：有 children 的是可展开节点，叶子是导航链接（depth ≥ 1 缩进、不带图标）。 */
  function renderItems(items: ShellNavItem[], depth: number): ReactNode {
    return items.map((m) => {
      const kids = m.children ?? [];
      if (kids.length > 0) {
        const open = openNodes.has(m.key);
        const branchActive = kids.some((k) => containsPath(k, pathname));
        return (
          <div className="side-branch" key={m.key}>
            <button
              type="button"
              className={'side-node' + (branchActive ? ' active' : '')}
              title={m.label}
              aria-expanded={open}
              onClick={() => clickBranch(m.key)}
            >
              <span className="side-ico">
                <m.Icon width={18} height={18} />
              </span>
              <span className="side-text">
                <span className="side-label">{m.label}</span>
              </span>
              <span className={'tree-toggle' + (open ? ' open' : '')}>
                <IconChevron width={11} height={11} />
              </span>
            </button>
            {open && !collapsed && <div className="side-sub">{renderItems(kids, depth + 1)}</div>}
          </div>
        );
      }

      // 叶子必须有路由：缺 path 是配置错误（比如说好的目录写成了 MENU 却没给路径）。
      // 此前的类型是 `path: string`，改成可选后这一支才可能出现，所以要显式兜住。
      if (!m.path) {
        if (import.meta.env.DEV) {
          console.warn('[ConsoleShell] 菜单项缺少 path，已跳过：', m.key, m.label);
        }
        return null;
      }
      return (
        <NavLink
          key={m.key}
          to={m.path}
          end={m.path === '/'}
          title={m.desc ? `${m.label} · ${m.desc}` : m.label}
          className={({ isActive }) =>
            'side-link' + (depth > 0 ? ' sub' : '') + (isActive ? ' active' : '')
          }
          onClick={() => {
            setMobileOpen(false);
            onNavSelect?.(m);
          }}
        >
          {depth === 0 ? (
            <span className="side-ico">
              <m.Icon width={18} height={18} />
            </span>
          ) : null}
          <span className="side-text">
            <span className="side-label">{m.label}</span>
            {/* 用 undefined 判断而非真假值：IAM 的副标题键可能缺翻译（回退空串），
                那里原本就渲染一个空的 .side-desc 占位，不能因为空就少一个节点。
                三级行不显示副标题：每行都是两行高的话，层级反而不易辨认（完整文案进 title）。 */}
            {depth === 0 && m.desc !== undefined ? <span className="side-desc">{m.desc}</span> : null}
          </span>
        </NavLink>
      );
    });
  }

  const isDark = theme === 'dark';
  const initial = (user?.name || '?').charAt(0).toUpperCase();

  return (
    <div className={'shell' + (collapsed ? ' collapsed' : '') + (mobileOpen ? ' mobile-open' : '')}>
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="logo">{brand.logo}</div>
          <div className="brand-text">
            <span className="brand-title">{brand.title}</span>
            <span className="brand-sub">{brand.subtitle}</span>
          </div>
        </div>

        <nav className="side-nav">
          {groups.map((g, gi) => {
            const hide = collapsibleGroups && !collapsed && !mobileOpen && hiddenGroups.has(g.key);
            return (
              <div className="side-group" key={g.key || `g${gi}`}>
                {g.cap ? (
                  collapsibleGroups ? (
                    <button
                      type="button"
                      className="side-cap nav-tog"
                      title={labels.toggleGroup}
                      aria-expanded={!hide}
                      onClick={() => toggleGroup(g.key)}
                    >
                      <span className="nav-tog-text">{g.cap}</span>
                      <span className={'tree-toggle' + (hide ? '' : ' open')}>
                        <IconChevron width={11} height={11} />
                      </span>
                    </button>
                  ) : (
                    <div className="side-cap static">{g.cap}</div>
                  )
                ) : null}
                {!hide && renderItems(g.items, 0)}
              </div>
            );
          })}
        </nav>

        {/* 没有说明文字时整块不渲染：否则只剩一个 14px 的盾牌图标悬在左下角，
            看起来像漏了内容的空按钮（MDS 就没传 sidebarNote）。 */}
        {sidebarNote ? (
          <div className="sidebar-foot">
            <div className="side-note">
              <IconShield width={14} height={14} />
              <span>{sidebarNote}</span>
            </div>
          </div>
        ) : null}
      </aside>

      {mobileOpen && <div className="scrim" onClick={() => setMobileOpen(false)} />}

      <div className="main">
        <header className="topbar">
          <button
            className="icon-btn"
            onClick={toggleSidebar}
            title={labels.toggleNav}
            aria-label={labels.toggleNav}
          >
            <IconSidebar width={18} height={18} />
          </button>

          <div className="topbar-right">
            {roleBadge}

            {env && (
              <span className="env-chip" title={env.title}>
                <i className={'led ' + env.tone} />
                {env.label}
              </span>
            )}

            {topbarExtra}

            <button
              className="icon-btn"
              onClick={onToggleTheme}
              title={isDark ? labels.themeToLight : labels.themeToDark}
              aria-label={labels.toggleTheme}
            >
              {isDark ? <IconSun width={17} height={17} /> : <IconMoon width={17} height={17} />}
            </button>

            {user && (
              <div className="user-chip" title={user.name}>
                <span className="avatar">{initial}</span>
                <span className="user-meta">
                  <b>{user.name}</b>
                  <small>{user.meta}</small>
                </span>
              </div>
            )}

            <button
              className="icon-btn danger"
              onClick={onLogout}
              title={labels.logout}
              aria-label={labels.logout}
            >
              <IconLogout width={17} height={17} />
            </button>
          </div>
        </header>

        {tabs && (
          <TabStrip
            tabs={tabs.tabs}
            activePath={tabs.activePath}
            syncToken={tabs.syncToken}
            labels={labels.tabs}
            onSelect={tabs.onSelect}
            onClose={tabs.onClose}
            onCloseOthers={tabs.onCloseOthers}
            onCloseRight={tabs.onCloseRight}
            onCloseAll={tabs.onCloseAll}
          />
        )}

        <main className="content">{children}</main>

        <footer className="footer">
          <span>{footerApp}</span>
          <span className="dot" />
          <span>{footerTagline}</span>
          <span className="dot" />
          {/* footer 末位原本各 ap 自己写 `isProd ? 'production' : 'development'`，
              直接复用 env 标识的小写形式，避免再传一个只差大小写的 prop。 */}
          <span>{env?.label.toLowerCase()}</span>
        </footer>
      </div>
    </div>
  );
}
