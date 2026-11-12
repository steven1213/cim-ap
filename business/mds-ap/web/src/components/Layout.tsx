import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, useShellTabs, type ShellLabels, type ShellNavGroup, type ShellNavItem } from '@cim/shell';
import { NAV_GROUPS, NAV_ITEMS, type NavItem } from '@/lib/navConfig';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import { useTabStore } from '@/store/tabStore';
import { useAuthStore } from '@/store/authStore';
import LanguageSwitcher from '@/components/LanguageSwitcher';

/**
 * MDS 控制台外壳。
 *
 * <p>框架来自 {@link ConsoleShell}（与 IAM / RMS / approval 同一份），本文件只保留本 ap 的差异：
 * 菜单来自 {@link NAV_GROUPS} 静态配置、顶栏放语言切换。加一个菜单要改两处——
 * 这里（前端渲染）与后端 {@code MdsConsoleCatalog.menus()}（授权与动态菜单）。</p>
 */
export default function Layout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const me = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const openTab = useTabStore((s) => s.open);
  const clearTabs = useTabStore((s) => s.clear);

  const [theme, setTheme] = useState<Theme>(() => readTheme());
  const isProd = import.meta.env.MODE === 'production';

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  // 路由切换 → 打开/激活对应页签（仅已知导航路径；404 等未知路径不开签）
  useEffect(() => {
    const m = NAV_ITEMS.find((x) => x.to === pathname);
    if (m?.to) openTab({ path: m.to, i18nCode: m.i18n });
  }, [pathname, openTab]);

  /**
   * 静态菜单 → 外壳模型。
   *
   * <p>递归而非一次 `map`：外壳支持多层级（不同系统有的两级、有的三级），
   * 这里必须能表达「有 children 的模块节点」——MDS 现在两级，将来场地主数据下
   * 长出「设备 / 产品 / 工艺路线」各域时，改 navConfig 加 children 就能用，本文件不动。</p>
   */
  const groups = useMemo<ShellNavGroup[]>(() => {
    const toShell = (items: NavItem[]): ShellNavItem[] => {
      const out: ShellNavItem[] = [];
      for (const it of items) {
        if (it.children?.length) {
          out.push({ key: it.i18n, label: t(it.i18n), Icon: it.Icon, children: toShell(it.children) });
        } else if (it.to) {
          out.push({ key: it.to, path: it.to, label: t(it.i18n), Icon: it.Icon });
        }
      }
      return out;
    };

    return NAV_GROUPS.map((g) => ({
      key: g.i18n,
      cap: t(g.i18n),
      items: toShell(g.items),
    }));
  }, [t]);

  const labels = useMemo<ShellLabels>(
    () => ({
      toggleNav: t('mds.shell.toggleNav'),
      toggleGroup: t('mds.shell.toggleGroup'),
      toggleTheme: t('mds.shell.toggleTheme'),
      themeToDark: t('mds.shell.theme.dark'),
      themeToLight: t('mds.shell.theme.light'),
      logout: t('mds.shell.logout'),
      tabs: {
        label: t('mds.shell.tabs.label'),
        scrollLeft: t('mds.shell.tabs.scrollLeft'),
        scrollRight: t('mds.shell.tabs.scrollRight'),
        close: t('mds.shell.tabs.close'),
        closeThis: t('mds.shell.tabs.closeThis'),
        closeOthers: t('mds.shell.tabs.closeOthers'),
        closeRight: t('mds.shell.tabs.closeRight'),
        closeAll: t('mds.shell.tabs.closeAll'),
      },
    }),
    [t],
  );

  // 标题解析优先取导航配置的文案码，回退到开签时快照
  const tabs = useShellTabs(useTabStore, {
    titleOf: (path, code) => {
      const i18n = NAV_ITEMS.find((x) => x.to === path)?.i18n ?? code;
      return i18n ? t(i18n) : path;
    },
  });

  return (
    <ConsoleShell
      storageKey="mds"
      brand={{ logo: 'MD', title: 'CIM MDS', subtitle: t('mds.shell.brandSub') }}
      groups={groups}
      labels={labels}
      collapsibleGroups
      env={{
        label: isProd ? 'PRODUCTION' : 'DEVELOPMENT',
        tone: isProd ? 'ok' : 'warn',
        title: `build: ${import.meta.env.MODE}`,
      }}
      topbarExtra={<LanguageSwitcher />}
      user={{ name: me?.username ?? me?.userId ?? '—', meta: me?.tenantId ? `tenant:${me.tenantId}` : 'local' }}
      footerApp="CIM-AP / MDS-AP"
      footerTagline={t('mds.shell.tagline')}
      theme={theme}
      onToggleTheme={() => {
        const next: Theme = theme === 'light' ? 'dark' : 'light';
        setTheme(next);
        persistTheme(next);
      }}
      onLogout={async () => {
        clearTabs();
        // 统一登录：先回 IdP 做服务端吊销（令牌入黑名单 + 清 SSO Cookie），再回登录中继页。
        await logout();
        navigate('/login', { replace: true });
      }}
      tabs={tabs}
    >
      <Outlet />
    </ConsoleShell>
  );
}
