import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, useShellTabs, type CimMenuNode, type ShellIconCmp, type ShellLabels, type ShellNavGroup, type ShellNavItem } from '@cim/shell';
import { resolveIcon } from '@cim/admin';
import { useAuthStore } from '@/store/authStore';
import { useTabStore } from '@/store/tabStore';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import NotificationBell from '@/components/NotificationBell';

/**
 * RMS 控制台外壳。
 *
 * <p>框架来自 {@link ConsoleShell}（与 IAM / MDS / approval 同一份）。侧栏数据源是
 * <b>后端菜单树</b>（`/rms/me/menus`，authStore 启动时拉取、按角色过滤）——菜单与按钮
 * 全部在 {@code sys_menu} 里在线维护（菜单管理页 `/menus`），前端不再持有导航配置
 * （原 {@code navConfig.ts} 已退场）。图标经共享包 {@link resolveIcon} 按 {@code sys_menu.icon}
 * 键映射；译文键 {@code i18n_code} 命中远端译文包时随库更新。</p>
 *
 * <p>三级结构：分组 DIR → 模块 DIR → 页面 MENU；顶层 MENU（概览）→ 单项分组。</p>
 */

function iconOf(key: string | null | undefined): ShellIconCmp {
  return resolveIcon(key) as ShellIconCmp;
}

export default function Layout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const user = useAuthStore((s) => s.user);
  const permissions = useAuthStore((s) => s.permissions);
  const menus = useAuthStore((s) => s.menus);
  const logout = useAuthStore((s) => s.logout);
  const openTab = useTabStore((s) => s.open);
  const clearTabs = useTabStore((s) => s.clear);

  const [theme, setTheme] = useState<Theme>(() => readTheme());
  const isProd = import.meta.env.MODE === 'production';

  // 超管判定：后端 DbLocalAuthorityLoader 在解析超管角色时会把 SUPER_ADMIN 展开进 authorities
  // （与 cim-system 的精确字符串匹配约定一致，见 lib/permissions.ts 注释），故以 SUPER_ADMIN 为准。
  const admin = permissions.includes('SUPER_ADMIN');

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

  /** 可见且启用的 MENU 子节点 → 外壳条目（有孩子的模块节点渲染成可展开行）。 */
  const toItem = (m: CimMenuNode): ShellNavItem | null => {
    const menuKids = (m.children ?? []).filter(
      (c) => c.type === 'MENU' && c.visible && c.status === 'ENABLED' && c.path,
    );
    if (menuKids.length) {
      return {
        key: m.id,
        label: t(m.i18nCode),
        Icon: iconOf(m.icon),
        children: menuKids.map((c) => ({ key: c.id, path: c.path!, label: t(c.i18nCode), Icon: iconOf(c.icon) })),
      };
    }
    if (m.type !== 'MENU' || !m.path) return null;
    return { key: m.id, path: m.path, label: t(m.i18nCode), Icon: iconOf(m.icon) };
  };

  /**
   * 后端菜单树 → 外壳模型（三级）：顶层 DIR → 分组（cap 取其 i18nCode），分组内的
   * 模块 DIR → 可展开条目；顶层 MENU（概览）→ 单项分组。空组不渲染。
   */
  const groups = useMemo<ShellNavGroup[]>(() => {
    const out: ShellNavGroup[] = [];
    for (const m of menus) {
      if (m.status !== 'ENABLED' || !m.visible) continue;
      if (m.type === 'DIR') {
        const items = (m.children ?? [])
          .filter((c) => c.status === 'ENABLED' && c.visible)
          .map(toItem)
          .filter((x): x is ShellNavItem => x !== null);
        if (items.length) out.push({ key: m.id, cap: t(m.i18nCode), items });
      } else if (m.type === 'MENU' && m.path) {
        const item = toItem(m);
        if (item) out.push({ key: m.id, cap: t(m.i18nCode), items: [item] });
      }
    }
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [menus, t]);

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

  // 标题解析优先取菜单的文案码，回退到开签时快照（只存码不存译文，切语言即时生效）
  const tabs = useShellTabs(useTabStore, {
    titleOf: (path, code) => {
      const i18n = flatMenus.find((x) => x.path === path)?.i18nCode ?? code;
      return i18n ? t(i18n) : path;
    },
  });

  return (
    <ConsoleShell
      storageKey="rms"
      brand={{ logo: 'RM', title: 'CIM RMS', subtitle: t('rms.shell.brandSub') }}
      groups={groups}
      labels={labels}
      collapsibleGroups
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
