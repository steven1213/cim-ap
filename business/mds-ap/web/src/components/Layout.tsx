import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, useShellTabs, type CimMenuNode, type ShellIconCmp, type ShellLabels, type ShellNavGroup, type ShellNavItem } from '@cim/shell';
import { resolveIcon } from '@/lib/iconRegistry';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import { useTabStore } from '@/store/tabStore';
import { useAuthStore } from '@/store/authStore';
import LanguageSwitcher from '@/components/LanguageSwitcher';

/**
 * MDS 控制台外壳。
 *
 * <p>框架来自 {@link ConsoleShell}（与 IAM / RMS / approval 同一份）。侧栏数据源是
 * <b>后端菜单树</b>（`/mds/me/menus`，authStore 启动时拉取、按角色过滤）——菜单与按钮
 * 全部在 {@code sys_menu} 里在线维护（菜单管理页 `/menus`），前端不再持有导航配置
 * （原 {@code navConfig.ts} 已退场）。图标经 {@link resolveIcon} 按 {@code sys_menu.icon}
 * 键映射；译文键 {@code i18n_code} 命中远端译文包时随库更新。</p>
 */

function iconOf(key: string | null | undefined): ShellIconCmp {
  return resolveIcon(key) as ShellIconCmp;
}

export default function Layout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const me = useAuthStore((s) => s.user);
  const menus = useAuthStore((s) => s.menus);
  const logout = useAuthStore((s) => s.logout);
  const openTab = useTabStore((s) => s.open);
  const clearTabs = useTabStore((s) => s.clear);

  const [theme, setTheme] = useState<Theme>(() => readTheme());
  const isProd = import.meta.env.MODE === 'production';

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

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
    const m = flatMenus.find((x) => x.path === pathname);
    if (m) openTab({ path: m.path!, i18nCode: m.i18nCode });
  }, [pathname, flatMenus, openTab]);

  /**
   * 后端菜单树 → 外壳模型。
   *
   * <p>顶层 DIR → 分组（cap 取其 i18nCode）；顶层 MENU（无分组归属，如概览）→
   * 单项分组。MENU 的 children 只在维护页配出多级时出现（MDS 现为两级）。</p>
   */
  const groups = useMemo<ShellNavGroup[]>(() => {
    const toItem = (m: CimMenuNode): ShellNavItem => {
      const Icon = iconOf(m.icon);
      if (m.children?.some((c) => c.type === 'MENU' && c.visible && c.status === 'ENABLED')) {
        return {
          key: m.id,
          label: t(m.i18nCode),
          Icon,
          children: m.children
            .filter((c) => c.type === 'MENU' && c.visible && c.status === 'ENABLED' && c.path)
            .map((c) => ({ key: c.id, path: c.path!, label: t(c.i18nCode), Icon: iconOf(c.icon) })),
        };
      }
      return { key: m.id, path: m.path!, label: t(m.i18nCode), Icon };
    };

    const out: ShellNavGroup[] = [];
    for (const m of menus) {
      if (m.status !== 'ENABLED' || !m.visible) continue;
      if (m.type === 'DIR') {
        const items = m.children
          .filter((c) => c.type === 'MENU' && c.visible && c.status === 'ENABLED' && c.path)
          .map(toItem);
        if (items.length) out.push({ key: m.id, cap: t(m.i18nCode), items });
      } else if (m.type === 'MENU' && m.path) {
        out.push({ key: m.id, cap: t(m.i18nCode), items: [toItem(m)] });
      }
    }
    return out;
  }, [menus, t]);

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

  // 标题解析优先取菜单的文案码，回退到开签时快照
  const tabs = useShellTabs(useTabStore, {
    titleOf: (path, code) => {
      const i18n = flatMenus.find((x) => x.path === path)?.i18nCode ?? code;
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
