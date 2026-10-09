import { useEffect } from 'react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';

/** 控制台外壳：顶部导航 + 用户信息 + 退出。 */
export default function Layout() {
  const user = useAuthStore((s) => s.user);
  const token = useAuthStore((s) => s.token);
  const loadMe = useAuthStore((s) => s.loadMe);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  useEffect(() => {
    if (token && !user) {
      loadMe().catch(() => {});
    }
  }, [token, user, loadMe]);

  async function onLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <div className="app">
      <header className="app-header">
        <div className="brand">CIM · IAM 控制台</div>
        <nav className="nav">
          <Link to="/">概览</Link>
          <Link to="/apps">应用与准入</Link>
          <Link to="/token-version">令牌踢人</Link>
          <Link to="/profile">我的</Link>
        </nav>
        <div className="user">
          {user ? `当前用户：${user.username}` : '未登录'}
          <button onClick={onLogout}>退出</button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
