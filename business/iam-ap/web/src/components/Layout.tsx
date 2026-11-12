import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, useShellTabs, type ShellLabels, type ShellNavGroup, type ShellNavItem } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';
import { useMenuStore } from '@/store/menuStore';
import { useTabStore } from '@/store/tabStore';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import { hasPermission, CONSOLE_ADMIN } from '@/lib/permissions';
import { resolveIcon } from '@/lib/iconRegistry';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import type { MenuTreeNode } from '@/types';

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
   * 后端菜单树 → 外壳的菜单模型。**递归**，与 `sys_menu` 的层级一一对应：
   * 顶级 DIR 成为分组标题（一级），其下 DIR 成为可展开的模块节点（二级），
   * MENU 成为页面（三级或二级，取决于所在深度）；顶级 MENU（概览 / 我的）自成一档。
   *
   * <p>以前这里只取 DIR 的<b>直接</b> MENU 子节点——后端明明支持任意层级，
   * 中间那层 DIR 一到前端就被静默丢弃（表现为「后端配了三层，界面上只有两层」）。
   * 现在 DIR 套 DIR 原样落地；空目录（子项全隐或全无权限）不渲染。</p>
   */
  const groups = useMemo<ShellNavGroup[]>(() => {
    const toItem = (node: MenuTreeNode): ShellNavItem | null => {
      if (node.menu.type === 'BUTTON' || !node.menu.visible) return null;
      const code = node.menu.i18nCode ?? '';
      const Icon = resolveIcon(node.menu.icon);

      const kids = (node.children ?? [])
        .map(toItem)
        .filter((x): x is ShellNavItem => x !== null);
      // 有可用子项 → 上级节点：只展开不导航，且不带副标题（副标题是「页面说明」的语义）
      if (kids.length > 0) {
        return { key: node.menu.id, label: t(code), Icon, children: kids };
      }
      if (node.menu.type === 'MENU' && node.menu.path) {
        return {
          key: node.menu.id,
          path: node.menu.path,
          label: t(code),
          // 副标题是 IAM 独有：菜单库给每个页面配一句说明，缺键时回退空串
          desc: t(`${code}.desc`, { defaultValue: '' }),
          Icon,
        };
      }
      return null;
    };

    const result: ShellNavGroup[] = [];
    for (const node of menuTree) {
      if (node.menu.type === 'BUTTON' || !node.menu.visible) continue;
      if (node.menu.type === 'DIR') {
        const items = (node.children ?? [])
          .map(toItem)
          .filter((x): x is ShellNavItem => x !== null);
        if (items.length > 0) {
          result.push({ key: node.menu.id, cap: t(node.menu.i18nCode ?? ''), items });
        }
      } else if (node.menu.type === 'MENU') {
        const item = toItem(node);
        if (item) result.push({ key: node.menu.id, items: [item] });
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
