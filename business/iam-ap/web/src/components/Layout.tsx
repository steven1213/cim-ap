import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, useShellTabs, type ShellLabels, type ShellNavGroup } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';
import { useMenuStore } from '@/store/menuStore';
import { useTabStore } from '@/store/tabStore';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import { hasPermission, CONSOLE_ADMIN } from '@/lib/permissions';
import { resolveIcon } from '@/lib/iconRegistry';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import type { MenuTreeNode, SysMenuDto } from '@/types';

/**
 * 控制台外壳。
 *
 * <p>框架来自 {@link ConsoleShell}，本文件只保留 IAM 自己的两件事：
 * <b>菜单从哪来</b>、<b>顶栏多放什么</b>。</p>
 *
 * <p><b>菜单完全由后端驱动</b>：结构来自 {@link useMenuStore}（`GET /sys/me/menus`，
 * 已按角色过滤），文案来自 i18n（`t(i18nCode)`），图标来自 {@link resolveIcon}。
 * 前端没有任何「菜单清单」常量——改菜单/改文案/改图标都只需要动库（或管理端界面）。</p>
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

  const [theme, setTheme] = useState<Theme>(() => readTheme());
  const consoleAdmin = hasPermission(permissions, CONSOLE_ADMIN);
  const isProd = import.meta.env.MODE === 'production';

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  // 路由切换 → 打开/激活对应页签（仅可路由的菜单项；404 等未知路径不开签）
  useEffect(() => {
    if (flat.length === 0) return;
    const m = flat.find((x) => x.path === pathname);
    if (m?.path) openTab({ path: m.path, i18nCode: m.i18nCode ?? '' });
  }, [pathname, flat, openTab]);

  /**
   * 后端树 → 外壳的菜单模型：DIR 节点成为分组标题，其子 MENU 为条目；
   * 顶级 MENU（如概览 / 我的）自成一档，不显示分组标题。
   */
  const groups = useMemo<ShellNavGroup[]>(() => {
    const visibleMenus = (node: MenuTreeNode): SysMenuDto[] =>
      (node.children ?? [])
        .filter((c) => c.menu.type === 'MENU' && c.menu.visible)
        .map((c) => c.menu);

    const result: ShellNavGroup[] = [];
    for (const node of menuTree) {
      if (node.menu.type === 'BUTTON' || !node.menu.visible) continue;
      if (node.menu.type === 'DIR') {
        const items = visibleMenus(node);
        if (items.length > 0) {
          result.push({
            key: node.menu.id,
            cap: t(node.menu.i18nCode ?? ''),
            items: items.map((m) => {
              const Icon = resolveIcon(m.icon);
              const code = m.i18nCode ?? '';
              return {
                key: m.id,
                path: m.path as string,
                label: t(code),
                // 副标题是 IAM 独有：菜单库给每个页面配一句说明，缺键时回退空串
                desc: t(`${code}.desc`, { defaultValue: '' }),
                Icon,
              };
            }),
          });
        }
      } else if (node.menu.type === 'MENU') {
        const code = node.menu.i18nCode ?? '';
        const Icon = resolveIcon(node.menu.icon);
        result.push({
          key: node.menu.id,
          items: [
            {
              key: node.menu.id,
              path: node.menu.path as string,
              label: t(code),
              desc: t(`${code}.desc`, { defaultValue: '' }),
              Icon,
            },
          ],
        });
      }
    }
    return result;
  }, [menuTree, t]);

  const labels = useMemo<ShellLabels>(
    () => ({
      toggleNav: t('iam.shell.toggleNav'),
      toggleGroup: t('iam.shell.toggleGroup'),
      toggleTheme: t('iam.shell.toggleTheme'),
      themeToDark: t('iam.shell.theme.dark'),
      themeToLight: t('iam.shell.theme.light'),
      logout: t('iam.shell.logout'),
      tabs: {
        label: t('iam.shell.tabs.label'),
        scrollLeft: t('iam.shell.tabs.scrollLeft'),
        scrollRight: t('iam.shell.tabs.scrollRight'),
        close: t('iam.shell.tabs.close'),
        closeThis: t('iam.shell.tabs.closeThis'),
        closeOthers: t('iam.shell.tabs.closeOthers'),
        closeRight: t('iam.shell.tabs.closeRight'),
        closeAll: t('iam.shell.tabs.closeAll'),
      },
    }),
    [t],
  );

  // 标题解析优先取菜单库的 i18nCode（随授权即时更新），回退到开签时快照
  const tabs = useShellTabs(useTabStore, {
    titleOf: (path, code) => {
      const i18n = flat.find((m) => m.path === path)?.i18nCode ?? code;
      return i18n ? t(i18n) : path;
    },
  });

  return (
    <ConsoleShell
      storageKey="iam"
      brand={{ logo: 'IA', title: 'CIM IAM', subtitle: t('iam.shell.brandSub') }}
      groups={groups}
      labels={labels}
      sidebarNote={t('iam.shell.crypto')}
      user={{
        name: user?.username ?? t('iam.common.notLoggedIn'),
        meta: user?.tenantId ? `tenant:${user.tenantId}` : 'local',
      }}
      roleBadge={
        consoleAdmin ? (
          <span className="role-chip" title={t('iam.shell.adminHint')}>
            {t('iam.shell.adminBadge')}
          </span>
        ) : undefined
      }
      env={{
        label: isProd ? 'PRODUCTION' : 'DEVELOPMENT',
        tone: isProd ? 'ok' : 'warn',
        title: `构建模式：${import.meta.env.MODE}`,
      }}
      topbarExtra={<LanguageSwitcher />}
      footerApp="CIM-AP / IAM-AP"
      footerTagline={t('iam.shell.tagline')}
      theme={theme}
      onToggleTheme={() => {
        const next: Theme = theme === 'light' ? 'dark' : 'light';
        setTheme(next);
        persistTheme(next);
      }}
      onLogout={async () => {
        await logout();
        clearMenus(); // 下一个登录者不应看到上一个人的菜单残留
        clearTabs(); // 页签同理
        navigate('/login');
      }}
      collapsibleGroups
      mobileBreakpoint={900}
      onNavSelect={() => requestScroll()}
      tabs={tabs}
    >
      <Outlet />
    </ConsoleShell>
  );
}
