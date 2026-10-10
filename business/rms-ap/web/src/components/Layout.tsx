import { useEffect, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/store/authStore';
import { hasPermission, RMS_ADMIN } from '@/lib/permissions';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import {
  IconGauge,
  IconSliders,
  IconApps,
  IconTree,
  IconMonitor,
  IconShield,
  IconRefresh,
  IconInbox,
  IconSidebar,
  IconMoon,
  IconSun,
  IconLogout,
} from '@/components/Icons';
import LanguageSwitcher from '@/components/LanguageSwitcher';

const SIDEBAR_KEY = 'rms-sidebar-collapsed';

type IconCmp = (p: { width?: number; height?: number }) => JSX.Element;

interface NavItem {
  to: string;
  i18n: string;
  Icon: IconCmp;
  perm?: string;
}
interface NavGroup {
  key: string;
  capI18n: string;
  items: NavItem[];
}

const GROUPS: NavGroup[] = [
  {
    key: 'overview',
    capI18n: '',
    items: [{ to: '/', i18n: 'rms.nav.dashboard', Icon: IconGauge }],
  },
  {
    key: 'recipe',
    capI18n: 'rms.nav.recipes',
    items: [{ to: '/recipes', i18n: 'rms.nav.recipes', Icon: IconSliders, perm: 'rms:recipe:view' }],
  },
  {
    key: 'device',
    capI18n: 'rms.nav.devices',
    items: [
      { to: '/device-types', i18n: 'rms.nav.deviceTypes', Icon: IconApps, perm: 'rms:device-type:view' },
      { to: '/device-areas', i18n: 'rms.nav.deviceAreas', Icon: IconTree, perm: 'rms:device-area:view' },
      { to: '/devices', i18n: 'rms.nav.devices', Icon: IconMonitor, perm: 'rms:device:view' },
    ],
  },
  {
    key: 'advanced',
    capI18n: 'rms.nav.signoff',
    items: [
      { to: '/signoff', i18n: 'rms.nav.signoff', Icon: IconShield },
      { to: '/compare', i18n: 'rms.nav.compare', Icon: IconRefresh },
      { to: '/audit', i18n: 'rms.nav.audit', Icon: IconInbox },
    ],
  },
];

/**
 * RMS 控制台外壳：顶部 header + 可收缩左侧菜单 + 内容区 + 底部 footer。
 * 视觉与交互对齐 IAM（四段式、设计令牌、深浅双主题）；菜单为前端静态配置
 * （RMS 自身页面固定，不依赖后端菜单下发）。
 */
export default function Layout() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const permissions = useAuthStore((s) => s.permissions);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(SIDEBAR_KEY) === '1');
  const [theme, setTheme] = useState<Theme>(() => readTheme());

  const admin = hasPermission(permissions, RMS_ADMIN) || permissions.includes('SUPER_ADMIN');
  const isProd = import.meta.env.MODE === 'production';

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

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
  async function onLogout() {
    await logout();
    navigate('/login');
  }

  const initial = (user?.username || '?').charAt(0).toUpperCase();

  return (
    <div className={'shell' + (collapsed ? ' collapsed' : '')}>
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="logo">RM</div>
          <div className="brand-text">
            <span className="brand-title">CIM RMS</span>
            <span className="brand-sub">{t('rms.shell.brandSub')}</span>
          </div>
        </div>

        <nav className="side-nav">
          {GROUPS.map((g, gi) => {
            const visible = g.items.filter((it) => !it.perm || hasPermission(permissions, it.perm));
            if (visible.length === 0) return null;
            return (
              <div className="side-group" key={g.key || `g${gi}`}>
                {g.capI18n ? <div className="side-cap static">{t(g.capI18n)}</div> : null}
                {visible.map((m) => (
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
            );
          })}
        </nav>

        <div className="sidebar-foot">
          <div className="side-note">
            <IconShield width={14} height={14} />
            <span>{t('rms.shell.crypto')}</span>
          </div>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button className="icon-btn" onClick={toggleSidebar} title={t('rms.shell.toggleNav')}
            aria-label={t('rms.shell.toggleNav')}>
            <IconSidebar width={18} height={18} />
          </button>

          <div className="topbar-right">
            {admin && (
              <span className="role-chip" title={t('rms.shell.adminHint')}>
                {t('rms.shell.adminBadge')}
              </span>
            )}
            <span className="env-chip" title={`build: ${import.meta.env.MODE}`}>
              <i className={'led ' + (isProd ? 'ok' : 'warn')} />
              {isProd ? 'PRODUCTION' : 'DEVELOPMENT'}
            </span>

            <LanguageSwitcher />

            <button
              className="icon-btn"
              onClick={toggleTheme}
              title={theme === 'light' ? t('rms.shell.theme.dark') : t('rms.shell.theme.light')}
              aria-label={t('rms.shell.toggleTheme')}
            >
              {theme === 'light' ? <IconMoon width={17} height={17} /> : <IconSun width={17} height={17} />}
            </button>

            <div className="user-chip" title={user?.username}>
              <span className="avatar">{initial}</span>
              <span className="user-meta">
                <b>{user?.username ?? t('rms.common.dash')}</b>
                <small>{user?.tenantId ? `tenant:${user.tenantId}` : 'local'}</small>
              </span>
            </div>

            <button className="icon-btn danger" onClick={onLogout} title={t('rms.shell.logout')}
              aria-label={t('rms.shell.logout')}>
              <IconLogout width={17} height={17} />
            </button>
          </div>
        </header>

        <main className="content">
          <Outlet />
        </main>

        <footer className="footer">
          <span>CIM-AP / RMS-AP</span>
          <span className="dot" />
          <span>{t('rms.shell.tagline')}</span>
          <span className="dot" />
          <span>{isProd ? 'production' : 'development'}</span>
        </footer>
      </div>
    </div>
  );
}
