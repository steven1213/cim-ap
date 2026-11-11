import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, useShellTabs, type ShellLabels, type ShellNavGroup } from '@cim/shell';
import { NAV_GROUPS, NAV_ITEMS } from '@/lib/navConfig';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import { useTabStore } from '@/store/tabStore';
import { useIdentityStore } from '@/store/identityStore';
import IdentitySwitcher from '@/components/IdentitySwitcher';
import NotificationBell from '@/components/NotificationBell';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import ToastHost from '@/components/ToastHost';

/**
 * 审批流平台外壳。
 *
 * <p>框架来自 {@link ConsoleShell}（与 IAM / RMS 同一份），本文件只保留本 ap 的差异：
 * 菜单来自 {@link NAV_GROUPS} 静态配置、顶栏放身份切换（dev 态模拟操作人）+ 通知铃，
 * 且<b>没有用户块</b>——审批平台当前不持有登录态，接入 IAM 后再补 `user`。</p>
 */
export default function Layout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { clear: clearIdentity } = useIdentityStore();
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
    if (m) openTab({ path: m.to, i18nCode: m.i18n });
  }, [pathname, openTab]);

  const groups = useMemo<ShellNavGroup[]>(
    () =>
      NAV_GROUPS.map((g) => ({
        key: g.i18n,
        cap: t(g.i18n),
        items: g.items.map((it) => ({ key: it.to, path: it.to, label: t(it.i18n), Icon: it.Icon })),
      })),
    [t],
  );

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
            <IdentitySwitcher />
            <NotificationBell />
            <LanguageSwitcher />
          </>
        }
        footerApp="CIM-AP / Approval-AP"
        footerTagline={t('shell.tagline')}
        theme={theme}
        onToggleTheme={() => {
          const next: Theme = theme === 'light' ? 'dark' : 'light';
          setTheme(next);
          persistTheme(next);
        }}
        onLogout={() => {
          // dev 态无真实登出：清身份与页签残留，回到工作台。
          clearIdentity();
          clearTabs();
          navigate('/');
        }}
        tabs={tabs}
      >
        <Outlet />
      </ConsoleShell>
      <ToastHost />
    </>
  );
}
