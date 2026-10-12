import { Navigate, Route, Routes, useSearchParams } from 'react-router-dom';
import { useEffect } from 'react';
import { useAuthStore } from '@/store/authStore';
import { useMenuStore } from '@/store/menuStore';
import ProtectedRoute from '@/components/ProtectedRoute';
import BootGate from '@/components/BootGate';
import RequirePerm from '@/components/RequirePerm';
import Layout from '@/components/Layout';
import SsoCallback from '@/pages/SsoCallback';
import ForbiddenPage from '@/pages/ForbiddenPage';
import NotFoundPage from '@/pages/NotFoundPage';
import { resolvePage } from '@/pages/pageRegistry';
import type { SysMenuDto } from '@/types';

/**
 * 单个菜单对应的页面：`component` 标识 → 组件，再按 `permCode` 加一道路由守卫。
 *
 * <p>「菜单可见性」与「接口/按钮权限」是两套授权（`sys_role_menu` / `sys_role_perm`），
 * 允许交叉配置，故此处两层都要过。</p>
 */
function RoutePage({ menu }: { menu: SysMenuDto }) {
  const Page = resolvePage(menu.component);
  return (
    <RequirePerm code={menu.permCode}>
      <Page />
    </RequirePerm>
  );
}

/**
 * 应用路由。
 *
 * <p><b>路由由后端菜单派生</b>：`GET /sys/me/menus` 的 `path` + `component` 决定「有哪些页面」，
 * 前端不再维护路由清单（原 `lib/menu.tsx` 已删除）。未授权/未知地址落到 404，
 * 已登录但无权限落到 403。</p>
 */
/**
 * 登录入口：统一中继到 IAM 后端（8081）的 /login（与 RMS 等接入 ap 同一页，单一登录页）。
 *
 * <p>不再在控制台 SPA 内渲染登录表单——登录 UI 由后端直出，登录入口与控制台解耦。
 * 已登录且有 SSO 回跳（罕见：外部 ap 误把用户指到 5171/login）则直接带 token 跳回；
 * 否则整页跳到 8081 登录页，成功后回跳本控制台的 /sso-callback 收口 token。</p>
 */
function LoginEntry() {
  const token = useAuthStore((s) => s.token);
  const refreshToken = useAuthStore((s) => s.refreshToken);
  const [params] = useSearchParams();
  const redirect = params.get('redirect');

  useEffect(() => {
    if (token) {
      if (redirect) {
        const sep = redirect.includes('?') ? '&' : '?';
        window.location.assign(
          `${redirect}${sep}token=${encodeURIComponent(token)}&refreshToken=${encodeURIComponent(refreshToken || token)}`,
        );
      }
      return;
    }
    const backend = import.meta.env.VITE_IAM_BACKEND_URL || 'http://localhost:8081';
    const ownCallback = window.location.origin + '/sso-callback';
    const target = redirect && redirect.indexOf('http') === 0 ? redirect : ownCallback;
    window.location.assign(
      `${backend}/login?app=IAM&redirect=${encodeURIComponent(target)}`,
    );
  }, [token, redirect, refreshToken]);

  if (token) return redirect ? null : <Navigate to="/" replace />;
  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ color: 'var(--muted, #7d8899)', fontSize: 13 }}>正在跳转至统一登录…</div>
    </div>
  );
}

export default function App() {
  const menus = useMenuStore((s) => s.flat);

  return (
    <Routes>
      <Route path="/login" element={<LoginEntry />} />
      <Route path="/sso-callback" element={<SsoCallback />} />
      <Route
        element={
          <ProtectedRoute>
            <BootGate>
              <Layout />
            </BootGate>
          </ProtectedRoute>
        }
      >
        <Route path="/403" element={<ForbiddenPage />} />
        {menus.map((m) => (
          <Route key={m.id} path={m.path as string} element={<RoutePage menu={m} />} />
        ))}
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
