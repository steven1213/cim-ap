import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { useMenuStore } from '@/store/menuStore';
import ProtectedRoute from '@/components/ProtectedRoute';
import BootGate from '@/components/BootGate';
import RequirePerm from '@/components/RequirePerm';
import Layout from '@/components/Layout';
import LoginPage from '@/pages/LoginPage';
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
export default function App() {
  const token = useAuthStore((s) => s.token);
  const menus = useMenuStore((s) => s.flat);

  return (
    <Routes>
      <Route path="/login" element={token ? <Navigate to="/" replace /> : <LoginPage />} />
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
