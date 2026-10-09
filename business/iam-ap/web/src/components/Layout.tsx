import { useEffect, useState, type ReactNode } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import {
  IconApps,
  IconGauge,
  IconKick,
  IconLogout,
  IconMoon,
  IconShield,
  IconSidebar,
  IconSun,
  IconUser,
} from '@/components/Icons';

interface MenuItem {
  to: string;
  label: string;
  desc: string;
  icon: ReactNode;
  end?: boolean;
}

const MENU: MenuItem[] = [
  { to: '/', label: '概览', desc: '身份与准入总览', icon: <IconGauge width={18} height={18} />, end: true },
  { to: '/apps', label: '应用与准入', desc: '注册应用 · 分配准入', icon: <IconApps width={18} height={18} /> },
  { to: '/token-version', label: '令牌踢人', desc: '强制用户下线', icon: <IconKick width={18} height={18} /> },
  { to: '/profile', label: '我的', desc: '账户与口令', icon: <IconUser width={18} height={18} /> },
];

const TITLES: Record<string, { title: string; sub: string }> = {
  '/': { title: '概览', sub: '当前登录身份与可进入的应用' },
  '/apps': { title: '应用与准入', sub: '注册业务应用，管理用户准入与角色' },
  '/token-version': { title: '令牌踢人', sub: 'bump 令牌版本，使存量令牌即时失效' },
  '/profile': { title: '我的', sub: '账户信息与口令修改' },
};

const SIDEBAR_KEY = 'iam-sidebar-collapsed';

/**
 * 控制台外壳：顶部 header + 可收缩左侧菜单 + 内容区 + 底部 footer。
 * 侧栏收缩状态与主题选择都持久化到 localStorage；≤900px 时侧栏改为抽屉式。
 */
export default function Layout() {
  const user = useAuthStore((s) => s.user);
  const token = useAuthStore((s) => s.token);
  const loadMe = useAuthStore((s) => s.loadMe);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(SIDEBAR_KEY) === '1');
  const [mobileOpen, setMobileOpen] = useState(false);
  const [theme, setTheme] = useState<Theme>(() => readTheme());

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  useEffect(() => {
    if (token && !user) {
      loadMe().catch(() => {});
    }
  }, [token, user, loadMe]);

  // 路由切换时收起移动端抽屉
  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  function toggleSidebar() {
    if (window.matchMedia('(max-width: 900px)').matches) {
      setMobileOpen((v) => !v);
      return;
    }
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

  const meta = TITLES[pathname] ?? { title: 'IAM 控制台', sub: '' };
  const initial = (user?.username || '?').charAt(0).toUpperCase();
  const isProd = import.meta.env.MODE === 'production';

  return (
    <div className={'shell' + (collapsed ? ' collapsed' : '') + (mobileOpen ? ' mobile-open' : '')}>
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="logo">IA</div>
          <div className="brand-text">
            <span className="brand-title">CIM IAM</span>
            <span className="brand-sub">IAM-AP / 统一身份准入</span>
          </div>
        </div>

        <nav className="side-nav">
          <span className="side-cap">模块</span>
          {MENU.map((m) => (
            <NavLink
              key={m.to}
              to={m.to}
              end={m.end}
              title={m.label}
              className={({ isActive }) => 'side-link' + (isActive ? ' active' : '')}
            >
              <span className="side-ico">{m.icon}</span>
              <span className="side-text">
                <span className="side-label">{m.label}</span>
                <span className="side-desc">{m.desc}</span>
              </span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-foot">
          <div className="side-note">
            <IconShield width={14} height={14} />
            <span>RS256 / JWKS 本地验签</span>
          </div>
        </div>
      </aside>

      {mobileOpen && <div className="scrim" onClick={() => setMobileOpen(false)} />}

      <div className="main">
        <header className="topbar">
          <button className="icon-btn" onClick={toggleSidebar} title="收起 / 展开菜单" aria-label="切换菜单">
            <IconSidebar width={18} height={18} />
          </button>

          <div className="crumb">
            <h1>{meta.title}</h1>
            <span className="path">IAM / {pathname === '/' ? 'overview' : pathname.replace(/^\//, '')}</span>
            {meta.sub && <p>{meta.sub}</p>}
          </div>

          <div className="topbar-right">
            <span className="env-chip" title={`构建模式：${import.meta.env.MODE}`}>
              <i className={'led ' + (isProd ? 'ok' : 'warn')} />
              {isProd ? 'PRODUCTION' : 'DEVELOPMENT'}
            </span>

            <button
              className="icon-btn"
              onClick={toggleTheme}
              title={theme === 'light' ? '切换到深色主题' : '切换到浅色主题'}
              aria-label="切换主题"
            >
              {theme === 'light' ? <IconMoon width={17} height={17} /> : <IconSun width={17} height={17} />}
            </button>

            <div className="user-chip" title={user?.username}>
              <span className="avatar">{initial}</span>
              <span className="user-meta">
                <b>{user?.username ?? '未登录'}</b>
                <small>{user?.tenantId ? `tenant:${user.tenantId}` : 'local'}</small>
              </span>
            </div>

            <button className="icon-btn danger" onClick={onLogout} title="退出登录" aria-label="退出登录">
              <IconLogout width={17} height={17} />
            </button>
          </div>
        </header>

        <main className="content">
          <Outlet />
        </main>

        <footer className="footer">
          <span>CIM-AP / IAM-AP</span>
          <span className="dot" />
          <span>统一登录与准入控制</span>
          <span className="dot" />
          <span>{isProd ? 'production' : 'development'}</span>
        </footer>
      </div>
    </div>
  );
}
