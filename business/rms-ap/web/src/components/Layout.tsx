import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, useShellTabs, type ShellLabels, type ShellNavGroup, type ShellNavItem } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';
import { useTabStore } from '@/store/tabStore';
import { hasPermission } from '@/lib/permissions';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import { GROUPS, NAV_FLAT, type NavItem } from '@/lib/navConfig';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import NotificationBell from '@/components/NotificationBell';

/**
 * RMS 控制台外壳。
 *
 * <p>框架（侧栏/顶栏/页签/内容/footer）全部来自 {@link ConsoleShell}，
 * 本文件只保留 RMS 自己的两件事：<b>菜单从哪来</b>（{@link GROUPS} 静态配置 + 权限过滤）、
 * <b>顶栏多放什么</b>（通知铃 + 语言切换）。收缩态、主题、登出清理、页签导航语义
 * 都在外壳里，三个 ap 共用一份。</p>
 */
export default function Layout() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const permissions = useAuthStore((s) => s.permissions);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const openTab = useTabStore((s) => s.open);
  const clearTabs = useTabStore((s) => s.clear);

  const [theme, setTheme] = useState<Theme>(() => readTheme());

  // 超管判定：后端 DbLocalAuthorityLoader 在解析超管角色时会把 SUPER_ADMIN 展开进 authorities
  // （与 cim-system 的精确字符串匹配约定一致，见 lib/permissions.ts 注释），故以 SUPER_ADMIN 为准。
  const admin = permissions.includes('SUPER_ADMIN');
  const isProd = import.meta.env.MODE === 'production';

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  // 路由切换 → 打开/激活对应页签（仅已知导航路径；404 等未知路径不开签）
  useEffect(() => {
    const m = NAV_FLAT.find((x) => x.path === pathname);
    if (m) openTab({ path: m.path, i18nCode: m.i18n });
  }, [pathname, openTab]);

  /**
   * 静态菜单 → 外壳的菜单模型。权限过滤在映射前做（无权限的条目根本不进模型），
   * 而不是在外壳里传一个 perm 字段——外壳不该认识各 ap 的权限码格式。
   *
   * <p>递归处理三级：有 `children` 的是模块节点（`to` 为空，外壳渲染成可展开行）；
   * 子项全被权限过滤掉时连模块节点一起不渲染（留下一个点开空白的壳最糟）。</p>
   */
  const groups = useMemo<ShellNavGroup[]>(() => {
    const toShell = (it: NavItem): ShellNavItem | null => {
      if (it.children?.length) {
        const kids = it.children
          .map(toShell)
          .filter((x): x is ShellNavItem => x !== null);
        return kids.length > 0 ? { key: it.i18n, label: t(it.i18n), Icon: it.Icon, children: kids } : null;
      }
      if (it.perm && !hasPermission(permissions, it.perm)) return null;
      if (!it.to) return null;
      return { key: it.to, path: it.to, label: t(it.i18n), Icon: it.Icon };
    };

    return GROUPS.map((g) => ({
      key: g.key,
      cap: g.capI18n ? t(g.capI18n) : undefined,
      items: g.items.map(toShell).filter((x): x is ShellNavItem => x !== null),
    }));
  }, [t, permissions]);

  const labels = useMemo<ShellLabels>(
    () => ({
      toggleNav: t('rms.shell.toggleNav'),
      toggleGroup: t('rms.shell.toggleGroup'),
      toggleTheme: t('rms.shell.toggleTheme'),
      themeToDark: t('rms.shell.theme.dark'),
      themeToLight: t('rms.shell.theme.light'),
      logout: t('rms.shell.logout'),
      tabs: {
        label: t('rms.shell.tabs.label'),
        scrollLeft: t('rms.shell.tabs.scrollLeft'),
        scrollRight: t('rms.shell.tabs.scrollRight'),
        close: t('rms.shell.tabs.close'),
        closeThis: t('rms.shell.tabs.closeThis'),
        closeOthers: t('rms.shell.tabs.closeOthers'),
        closeRight: t('rms.shell.tabs.closeRight'),
        closeAll: t('rms.shell.tabs.closeAll'),
      },
    }),
    [t],
  );

  // 标题解析：导航配置优先（码最新），回退开签时的快照（只存码不存译文，切语言即时生效）
  const tabs = useShellTabs(useTabStore, {
    titleOf: (path, code) => {
      const i18n = NAV_FLAT.find((x) => x.path === path)?.i18n ?? code;
      return i18n ? t(i18n) : path;
    },
  });

  return (
    <ConsoleShell
      storageKey="rms"
      brand={{ logo: 'RM', title: 'CIM RMS', subtitle: t('rms.shell.brandSub') }}
      groups={groups}
      labels={labels}
      sidebarNote={t('rms.shell.crypto')}
      user={{ name: user?.username ?? t('rms.common.dash'), meta: user?.tenantId ? `tenant:${user.tenantId}` : 'local' }}
      roleBadge={
        admin ? (
          <span className="role-chip" title={t('rms.shell.adminHint')}>
            {t('rms.shell.adminBadge')}
          </span>
        ) : undefined
      }
      env={{
        label: isProd ? 'PRODUCTION' : 'DEVELOPMENT',
        tone: isProd ? 'ok' : 'warn',
        title: `build: ${import.meta.env.MODE}`,
      }}
      topbarExtra={
        <>
          <NotificationBell />
          <LanguageSwitcher />
        </>
      }
      footerApp="CIM-AP / RMS-AP"
      footerTagline={t('rms.shell.tagline')}
      theme={theme}
      onToggleTheme={() => {
        const next: Theme = theme === 'light' ? 'dark' : 'light';
        setTheme(next);
        persistTheme(next);
      }}
      onLogout={async () => {
        await logout();
        clearTabs(); // 下一个登录者不应看到上一个人的页签残留
        navigate('/login');
      }}
      tabs={tabs}
    >
      <Outlet />
    </ConsoleShell>
  );
}
