import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/store/authStore';
import { useTabStore } from '@/store/tabStore';
import { hasPermission } from '@/lib/permissions';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import { GROUPS, NAV_FLAT } from '@/lib/navConfig';
import { IconShield, IconSidebar, IconMoon, IconSun, IconLogout } from '@/components/Icons';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import NotificationBell from '@/components/NotificationBell';
import TabBar from '@/components/TabBar';

const SIDEBAR_KEY = 'rms-sidebar-collapsed';

/**
 * RMS 控制台外壳：顶部 header + 可收缩左侧菜单 + 多页签栏 + 内容区 + 底部 footer。
 * 视觉与交互对齐 IAM（四段式、设计令牌、深浅双主题、多标签导航）；菜单为前端静态配置
 * （RMS 自身页面固定，不依赖后端菜单下发），定义在 {@link GROUPS}，与多页签 TabBar 共用。
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

  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(SIDEBAR_KEY) === '1');
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
    clearTabs(); // 下一个登录者不应看到上一个人的页签残留
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

            <NotificationBell />

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

        <TabBar />

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
