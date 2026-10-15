import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { NAV_GROUPS, NAV_ITEMS } from '@/lib/navConfig';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import { useTabStore } from '@/store/tabStore';
import IdentitySwitcher from '@/components/IdentitySwitcher';
import NotificationBell from '@/components/NotificationBell';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import TabBar from '@/components/TabBar';
import ToastHost from '@/components/ToastHost';
import { IconSidebar, IconMoon, IconSun, IconShield, IconLogout } from '@/components/Icons';
import { useIdentityStore } from '@/store/identityStore';

const SIDEBAR_KEY = 'approval-sidebar-collapsed';

/**
 * 审批流平台外壳：顶部 header + 可收缩左侧菜单 + 多页签栏 + 内容区 + 底部 footer。
 * 视觉与交互对齐 IAM / RMS（四段式、设计令牌、深浅双主题、多标签导航）。
 * dev 态以顶栏「身份切换」标识当前操作人；生产接入 IAM 后此处置换为登录态。
 */
export default function Layout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { clear: clearIdentity } = useIdentityStore();
  const openTab = useTabStore((s) => s.open);
  const clearTabs = useTabStore((s) => s.clear);

  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(SIDEBAR_KEY) === '1');
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

  function toggleSidebar() {
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
  function onLogout() {
    // dev 态无真实登出：清身份与页签残留，回到工作台。
    clearIdentity();
    clearTabs();
    navigate('/');
  }

  return (
    <div className={'shell' + (collapsed ? ' collapsed' : '')}>
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="logo">AP</div>
          <div className="brand-text">
            <span className="brand-title">CIM Approval</span>
            <span className="brand-sub">{t('shell.brandSub')}</span>
          </div>
        </div>

        <nav className="side-nav">
          {NAV_GROUPS.map((group) => (
            <div className="side-group" key={group.i18n}>
              <div className="side-cap">{t(group.i18n)}</div>
              {group.items.map((m) => (
                <NavLink
                  key={m.to}
                  to={m.to}
                  end={m.to === '/'}
                  title={t(m.i18n)}
                  className={({ isActive }) => 'side-link' + (isActive ? ' active' : '')}
                >
                  <span className="side-ico">
                    <m.Icon width={18} height={18} />
                  </span>
                  <span className="side-text">
                    <span className="side-label">{t(m.i18n)}</span>
                  </span>
                </NavLink>
              ))}
            </div>
          ))}
        </nav>

        <div className="sidebar-foot">
          <div className="side-note">
            <IconShield width={14} height={14} />
            <span>{t('shell.crypto')}</span>
          </div>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button className="icon-btn" onClick={toggleSidebar} title={t('shell.toggleNav')} aria-label={t('shell.toggleNav')}>
            <IconSidebar width={18} height={18} />
          </button>

          <div className="topbar-right">
            <span className="env-chip" title={`build: ${import.meta.env.MODE}`}>
              <i className={'led ' + (isProd ? 'ok' : 'warn')} />
              {isProd ? 'PRODUCTION' : 'DEVELOPMENT'}
            </span>

            <IdentitySwitcher />
            <NotificationBell />

            <LanguageSwitcher />

            <button
              className="icon-btn"
              onClick={toggleTheme}
              title={theme === 'light' ? t('shell.theme.dark') : t('shell.theme.light')}
              aria-label={t('shell.toggleTheme')}
            >
              {theme === 'light' ? <IconMoon width={17} height={17} /> : <IconSun width={17} height={17} />}
            </button>

            <button className="icon-btn danger" onClick={onLogout} title={t('shell.logout')} aria-label={t('shell.logout')}>
              <IconLogout width={17} height={17} />
            </button>
          </div>
        </header>

        <TabBar />

        <main className="content">
          <Outlet />
        </main>

        <footer className="footer">
          <span>CIM-AP / Approval-AP</span>
          <span className="dot" />
          <span>{t('shell.tagline')}</span>
          <span className="dot" />
          <span>{isProd ? 'production' : 'development'}</span>
        </footer>
      </div>

      <ToastHost />
    </div>
  );
}
