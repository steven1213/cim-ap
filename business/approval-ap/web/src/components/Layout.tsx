import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, useShellTabs, type CimMenuNode, type ShellIconCmp, type ShellLabels, type ShellNavGroup, type ShellNavItem } from '@cim/shell';
import { resolveIcon } from '@cim/admin';
import { useAuthStore } from '@/store/authStore';
import { useTabStore } from '@/store/tabStore';
import { useIdentityStore, SHARED_DOMAIN } from '@/store/identityStore';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import IdentitySwitcher from '@/components/IdentitySwitcher';
import NotificationBell from '@/components/NotificationBell';
import LanguageSwitcher from '@/components/LanguageSwitcher';

/**
 * 审批流平台外壳。
 *
 * <p>框架来自 {@link ConsoleShell}（与 IAM / MDS / RMS 同一份）。侧栏数据源是
 * <b>后端菜单树</b>（`/me/menus`：sso 通道按角色过滤，dev 通道为全量树）——菜单与按钮
 * 全部在 {@code sys_menu} 里在线维护（菜单管理页 `/menus`），前端不再持有导航配置
 * （原 {@code navConfig.ts} 已退场）。图标经共享包 {@link resolveIcon} 按 {@code sys_menu.icon}
 * 键映射；译文键 {@code i18n_code} 命中远端译文包时随库更新。</p>
 *
 * <p>身份区按通道二选一：统一登录态显示用户块（姓名 + 租户，登出走 IdP 吊销）；
 * dev 态显示「身份切换」（演示多角色，身份以查询参数表达）。同一份产物两种部署通用。</p>
 */
function iconOf(key: string | null | undefined): ShellIconCmp {
  return resolveIcon(key) as ShellIconCmp;
}

export default function Layout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { clear: clearIdentity } = useIdentityStore();
  const mode = useAuthStore((s) => s.mode);
  const me = useAuthStore((s) => s.user);
  const menus = useAuthStore((s) => s.menus);
  const logout = useAuthStore((s) => s.logout);
  const openTab = useTabStore((s) => s.open);
  const clearTabs = useTabStore((s) => s.clear);
  const isDevChannel = mode === 'dev';

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
   * 模块 DIR → 可展开条目；顶层 MENU（工作台）→ 单项分组。空组不渲染。
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
      toggleNav: t('shell.toggleNav'),
      toggleGroup: t('shell.toggleGroup'),
      toggleTheme: t('shell.toggleTheme'),
      themeToDark: t('shell.theme.dark'),
      themeToLight: t('shell.theme.light'),
      logout: t('shell.logout'),
      tabs: {
        label: t('shell.tabs.label'),
        scrollLeft: t('shell.tabs.scrollLeft'),
        scrollRight: t('shell.tabs.scrollRight'),
        close: t('shell.tabs.close'),
        closeThis: t('shell.tabs.closeThis'),
        closeOthers: t('shell.tabs.closeOthers'),
        closeRight: t('shell.tabs.closeRight'),
        closeAll: t('shell.tabs.closeAll'),
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
      storageKey="approval"
      brand={{ logo: 'AP', title: 'CIM Approval', subtitle: t('shell.brandSub') }}
      groups={groups}
      labels={labels}
      collapsibleGroups
      sidebarNote={t('shell.crypto')}
      env={{
        label: isProd ? 'PRODUCTION' : 'DEVELOPMENT',
        tone: isProd ? 'ok' : 'warn',
        title: `build: ${import.meta.env.MODE}`,
      }}
      topbarExtra={
        <>
          {isDevChannel && <IdentitySwitcher />}
          <NotificationBell />
          <LanguageSwitcher />
        </>
      }
      user={
        isDevChannel
          ? null
          : { name: me?.username ?? me?.userId ?? '—', meta: `domain:${me?.tenantId || SHARED_DOMAIN}` }
      }
      footerApp="CIM-AP / Approval-AP"
      footerTagline={t('shell.tagline')}
      theme={theme}
      onToggleTheme={() => {
        const next: Theme = theme === 'light' ? 'dark' : 'light';
        setTheme(next);
        persistTheme(next);
      }}
      onLogout={async () => {
        clearTabs();
        if (isDevChannel) {
          // dev 态无真实登出：清身份与页签残留，回到工作台。
          clearIdentity();
          navigate('/');
          return;
        }
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
