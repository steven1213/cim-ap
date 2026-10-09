import { useEffect, useMemo, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { applyTheme, persistTheme, readTheme, type Theme } from '@/lib/theme';
import { isIamAdmin } from '@/lib/permissions';
import { MENU, MENU_FLAT } from '@/lib/menu';
import { IconLogout, IconMoon, IconShield, IconSidebar, IconSun } from '@/components/Icons';

const SIDEBAR_KEY = 'iam-sidebar-collapsed';

/**
 * 控制台外壳：顶部 header + 可收缩左侧菜单 + 内容区 + 底部 footer。
 *
 * <p>菜单由 {@link MENU} 注册表驱动，按当前用户权限（{@link isIamAdmin}）过滤；
 * 侧栏收缩状态与主题选择都持久化到 localStorage；≤900px 时侧栏改为抽屉式。</p>
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

  const admin = isIamAdmin(user);

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

  // 按权限过滤菜单分组（全被过滤掉的分组不渲染）
  const groups = useMemo(
    () =>
      MENU.map((g) => ({ ...g, items: g.items.filter((i) => !i.admin || admin) })).filter(
        (g) => g.items.length > 0,
      ),
    [admin],
  );

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

  const entry = MENU_FLAT.find((m) => m.to === pathname);
  const title = entry?.label ?? 'IAM 控制台';
  const sub = entry?.sub ?? '';
  const crumbPath = pathname === '/' ? 'overview' : pathname.replace(/^\//, '');
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
          {groups.map((g, gi) => (
            <div className="side-group" key={g.cap || `g${gi}`}>
              {g.cap && <span className="side-cap">{g.cap}</span>}
              {g.items.map((m) => (
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
            </div>
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
            <h1>{title}</h1>
            <span className="path">IAM / {crumbPath}</span>
            {sub && <p>{sub}</p>}
          </div>

          <div className="topbar-right">
            {admin && <span className="role-chip" title="具备 IAM 管理面权限">ADMIN</span>}

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
