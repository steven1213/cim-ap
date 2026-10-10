import { useEffect, useMemo, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/store/authStore';
import { useMenuStore } from '@/store/menuStore';
import { useTabStore } from '@/store/tabStore';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import { hasPermission, CONSOLE_ADMIN } from '@/lib/permissions';
import { resolveIcon } from '@/lib/iconRegistry';
import { IconLogout, IconMoon, IconShield, IconSidebar, IconSun, IconChevron } from '@/components/Icons';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import TabBar from '@/components/TabBar';
import type { MenuTreeNode, SysMenuDto } from '@/types';

const SIDEBAR_KEY = 'iam-sidebar-collapsed';
const SIDEBAR_GROUPS_KEY = 'iam-sidebar-groups';

/** 侧栏分组（目录节点升格为分组标题；顶级菜单自成一档且不显示标题）。 */
interface NavGroup {
  key: string;
  cap: string;
  items: SysMenuDto[];
}

/**
 * 控制台外壳：顶部 header + 可收缩左侧菜单 + 内容区 + 底部 footer。
 *
 * <p><b>菜单完全由后端驱动</b>：结构来自 {@link useMenuStore}（`GET /sys/me/menus`，
 * 已按角色过滤），文案来自 i18n（`t(i18nCode)`），图标来自
 * {@link resolveIcon}。前端不再有任何「菜单清单」常量——改菜单/改文案/改图标
 * 都只需要动库（或管理端界面），无需发版。</p>
 *
 * <p>侧栏收缩状态与主题选择持久化到 localStorage；≤900px 时侧栏改为抽屉式。</p>
 */
export default function Layout() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const permissions = useAuthStore((s) => s.permissions);
  const logout = useAuthStore((s) => s.logout);
  const menuTree = useMenuStore((s) => s.tree);
  const flat = useMenuStore((s) => s.flat);
  const clearMenus = useMenuStore((s) => s.clear);
  const openTab = useTabStore((s) => s.open);
  const clearTabs = useTabStore((s) => s.clear);
  const requestScroll = useTabStore((s) => s.requestScroll);
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(SIDEBAR_KEY) === '1');
  const [mobileOpen, setMobileOpen] = useState(false);
  const [theme, setTheme] = useState<Theme>(() => readTheme());
  /** 收起的分组 key 集合（持久化；侧栏整体收起为图标态时不生效）。 */
  const [hiddenGroups, setHiddenGroups] = useState<Set<string>>(() => {
    try {
      const raw = localStorage.getItem(SIDEBAR_GROUPS_KEY);
      return new Set<string>(raw ? (JSON.parse(raw) as string[]) : []);
    } catch {
      return new Set<string>();
    }
  });

  const consoleAdmin = hasPermission(permissions, CONSOLE_ADMIN);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  // 路由切换时收起移动端抽屉
  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  // 路由切换 → 打开/激活对应页签（仅可路由的菜单项；404 等未知路径不开签）
  useEffect(() => {
    if (flat.length === 0) return;
    const m = flat.find((x) => x.path === pathname);
    if (m?.path) openTab({ path: m.path, i18nCode: m.i18nCode ?? '' });
  }, [pathname, flat, openTab]);

  // 后端树 → 侧栏分组：DIR 节点成为分组（cap），其子 MENU 为条目；
  // 顶级 MENU（如概览 / 我的）自成一档，不显示分组标题。
  const groups = useMemo<NavGroup[]>(() => {
    const visibleMenus = (node: MenuTreeNode): SysMenuDto[] =>
      (node.children ?? [])
        .filter((c) => c.menu.type === 'MENU' && c.menu.visible)
        .map((c) => c.menu);

    const result: NavGroup[] = [];
    for (const node of menuTree) {
      if (node.menu.type === 'BUTTON' || !node.menu.visible) continue;
      if (node.menu.type === 'DIR') {
        const items = visibleMenus(node);
        if (items.length > 0) {
          result.push({ key: node.menu.id, cap: t(node.menu.i18nCode ?? ''), items });
        }
      } else if (node.menu.type === 'MENU') {
        result.push({ key: node.menu.id, cap: '', items: [node.menu] });
      }
    }
    return result;
  }, [menuTree, t]);

  // 路由所在分组自动展开（折叠分组后点侧栏入口仍要能看到当前位置）
  useEffect(() => {
    const active = groups.find((g) => g.items.some((m) => m.path === pathname));
    if (!active) return;
    setHiddenGroups((prev) => {
      if (!prev.has(active.key)) return prev;
      const next = new Set(prev);
      next.delete(active.key);
      localStorage.setItem(SIDEBAR_GROUPS_KEY, JSON.stringify([...next]));
      return next;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname, groups]);

  function toggleSidebar() {
    if (window.matchMedia('(max-width: 900px)').matches) {
      setMobileOpen((v) => !v);
      return;
    }
    setCollapsed((v) => {
      const next = !v;
      localStorage.setItem(SIDEBAR_KEY, next ? '1' : '0');
      return next;
    });
  }

  function toggleTheme() {
    const next: Theme = theme === 'light' ? 'dark' : 'light';
    setTheme(next);
    persistTheme(next);
  }

  /** 收起/展开一个导航分组（持久化；图标态下不折叠）。 */
  function toggleGroup(key: string) {
    setHiddenGroups((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      localStorage.setItem(SIDEBAR_GROUPS_KEY, JSON.stringify([...next]));
      return next;
    });
  }

  async function onLogout() {
    await logout();
    clearMenus(); // 下一个登录者不应看到上一个人的菜单残留
    clearTabs(); // 页签同理
    navigate('/login');
  }

  const initial = (user?.username || '?').charAt(0).toUpperCase();
  const isProd = import.meta.env.MODE === 'production';

  return (
    <div className={'shell' + (collapsed ? ' collapsed' : '') + (mobileOpen ? ' mobile-open' : '')}>
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="logo">IA</div>
          <div className="brand-text">
            <span className="brand-title">CIM IAM</span>
            <span className="brand-sub">{t('iam.shell.brandSub')}</span>
          </div>
        </div>

        <nav className="side-nav">
          {groups.map((g, gi) => {
            const hide = !collapsed && !mobileOpen && hiddenGroups.has(g.key);
            return (
              <div className="side-group" key={g.key || `g${gi}`}>
                {g.cap ? (
                  <button
                    type="button"
                    className="side-cap nav-tog"
                    title={t('iam.shell.toggleGroup')}
                    aria-expanded={!hide}
                    onClick={() => toggleGroup(g.key)}
                  >
                    <span className="nav-tog-text">{g.cap}</span>
                    <span className={'tree-toggle' + (hide ? '' : ' open')}>
                      <IconChevron width={11} height={11} />
                    </span>
                  </button>
                ) : null}
                {!hide &&
                  g.items.map((m) => {
                    const Icon = resolveIcon(m.icon);
                    return (
                      <NavLink
                        key={m.id}
                        to={m.path as string}
                        end={m.path === '/'}
                        title={t(m.i18nCode ?? '')}
                        className={({ isActive }) => 'side-link' + (isActive ? ' active' : '')}
                        onClick={() => requestScroll()}
                      >
                        <span className="side-ico">
                          <Icon width={18} height={18} />
                        </span>
                        <span className="side-text">
                          <span className="side-label">{t(m.i18nCode ?? '')}</span>
                          <span className="side-desc">
                            {t(`${m.i18nCode ?? ''}.desc`, { defaultValue: '' })}
                          </span>
                        </span>
                      </NavLink>
                    );
                  })}
              </div>
            );
          })}
        </nav>

        <div className="sidebar-foot">
          <div className="side-note">
            <IconShield width={14} height={14} />
            <span>{t('iam.shell.crypto')}</span>
          </div>
        </div>
      </aside>

      {mobileOpen && <div className="scrim" onClick={() => setMobileOpen(false)} />}

      <div className="main">
        <header className="topbar">
          <button className="icon-btn" onClick={toggleSidebar} title={t('iam.shell.toggleNav')}
            aria-label={t('iam.shell.toggleNav')}>
            <IconSidebar width={18} height={18} />
          </button>

          {/* 当前页名已由页签栏承载，header 不再重复显示菜单/路径 */}

          <div className="topbar-right">
            {consoleAdmin && (
              <span className="role-chip" title={t('iam.shell.adminHint')}>
                {t('iam.shell.adminBadge')}
              </span>
            )}

            <span className="env-chip" title={`构建模式：${import.meta.env.MODE}`}>
              <i className={'led ' + (isProd ? 'ok' : 'warn')} />
              {isProd ? 'PRODUCTION' : 'DEVELOPMENT'}
            </span>

            <LanguageSwitcher />

            <button
              className="icon-btn"
              onClick={toggleTheme}
              title={theme === 'light' ? t('iam.shell.theme.dark') : t('iam.shell.theme.light')}
              aria-label={t('iam.shell.toggleTheme')}
            >
              {theme === 'light' ? <IconMoon width={17} height={17} /> : <IconSun width={17} height={17} />}
            </button>

            <div className="user-chip" title={user?.username}>
              <span className="avatar">{initial}</span>
              <span className="user-meta">
                <b>{user?.username ?? t('iam.common.notLoggedIn')}</b>
                <small>{user?.tenantId ? `tenant:${user.tenantId}` : 'local'}</small>
              </span>
            </div>

            <button className="icon-btn danger" onClick={onLogout} title={t('iam.shell.logout')}
              aria-label={t('iam.shell.logout')}>
              <IconLogout width={17} height={17} />
            </button>
          </div>
        </header>

        <TabBar />

        <main className="content">
          <Outlet />
        </main>

        <footer className="footer">
          <span>CIM-AP / IAM-AP</span>
          <span className="dot" />
          <span>{t('iam.shell.tagline')}</span>
          <span className="dot" />
          <span>{isProd ? 'production' : 'development'}</span>
        </footer>
      </div>
    </div>
  );
}
