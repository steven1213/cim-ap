import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, useShellTabs, type ShellLabels, type ShellNavGroup, type ShellNavItem } from '@cim/shell';
import { NAV_GROUPS, NAV_ITEMS, type NavItem } from '@/lib/navConfig';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import { useTabStore } from '@/store/tabStore';
import { useIdentityStore, SHARED_DOMAIN } from '@/store/identityStore';
import { useAuthStore } from '@/store/authStore';
import IdentitySwitcher from '@/components/IdentitySwitcher';
import NotificationBell from '@/components/NotificationBell';
import LanguageSwitcher from '@/components/LanguageSwitcher';

/**
 * 审批流平台外壳。
 *
 * <p>框架来自 {@link ConsoleShell}（与 IAM / RMS 同一份），本文件只保留本 ap 的差异：
 * 菜单来自 {@link NAV_GROUPS} 静态配置、顶栏放通知铃与语言切换。</p>
 *
 * <p>身份区按通道二选一：统一登录态显示用户块（姓名 + 租户，登出走 IdP 吊销）；
 * dev 态显示「身份切换」（演示多角色，身份以查询参数表达）。同一份产物两种部署通用。</p>
 */
export default function Layout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { clear: clearIdentity } = useIdentityStore();
  const mode = useAuthStore((s) => s.mode);
  const me = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const openTab = useTabStore((s) => s.open);
  const clearTabs = useTabStore((s) => s.clear);
  const isDevChannel = mode === 'dev';

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

    return NAV_GROUPS.map((g) => ({ key: g.i18n, cap: t(g.i18n), items: toShell(g.items) }));
  }, [t]);

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

  // 标题解析优先取导航配置的文案码，回退到开签时快照
  const tabs = useShellTabs(useTabStore, {
    titleOf: (path, code) => {
      const i18n = NAV_ITEMS.find((x) => x.to === path)?.i18n ?? code;
      return i18n ? t(i18n) : path;
    },
  });

  return (
    <>
      <ConsoleShell
        storageKey="approval"
        brand={{ logo: 'AP', title: 'CIM Approval', subtitle: t('shell.brandSub') }}
        groups={groups}
        labels={labels}
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
    </>
  );
}
