import { useEffect, useState, type ReactNode } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import Layout from '@/components/Layout';
import ProtectedRoute from '@/components/ProtectedRoute';
import RequirePerm from '@/components/RequirePerm';
import LoginPage from '@/pages/LoginPage';
import SsoCallback from '@/pages/SsoCallback';
import DashboardPage from '@/pages/DashboardPage';
import RecipeLibraryPage from '@/pages/RecipeLibraryPage';
import DeviceTypePage from '@/pages/DeviceTypePage';
import DeviceAreaPage from '@/pages/DeviceAreaPage';
import DevicePage from '@/pages/DevicePage';
import ComingSoon from '@/pages/ComingSoon';
import NotFoundPage from '@/pages/NotFoundPage';
import ForbiddenPage from '@/pages/ForbiddenPage';

/**
 * 启动闸门：已登录时先拉取用户档案与权限码（内存态，不落盘），
 * 再渲染受保护路由。避免「首帧权限为空 → 误判 403」。
 */
function BootGate({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  const loadMe = useAuthStore((s) => s.loadMe);
  const loadPermissions = useAuthStore((s) => s.loadPermissions);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!token) {
      setReady(true);
      return;
    }
    let alive = true;
    Promise.all([loadMe(), loadPermissions()]).finally(() => {
      if (alive) setReady(true);
    });
    return () => {
      alive = false;
    };
  }, [token, loadMe, loadPermissions]);

  if (!ready) {
    return (
      <div className="login-screen">
        <div className="login-card">
          <div className="login-brand">
            <div className="logo">RM</div>
          </div>
          <p>…</p>
        </div>
      </div>
    );
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/sso" element={<SsoCallback />} />
        <Route path="/403" element={<ForbiddenPage />} />

        <Route
          element={
            <ProtectedRoute>
              <BootGate>
                <Layout />
              </BootGate>
            </ProtectedRoute>
          }
        >
          <Route path="/" element={<DashboardPage />} />
          <Route
            path="/recipes"
            element={
              <RequirePerm code="rms:recipe:view">
                <RecipeLibraryPage />
              </RequirePerm>
            }
          />
          <Route
            path="/device-types"
            element={
              <RequirePerm code="rms:device-type:view">
                <DeviceTypePage />
              </RequirePerm>
            }
          />
          <Route
            path="/device-areas"
            element={
              <RequirePerm code="rms:device-area:view">
                <DeviceAreaPage />
              </RequirePerm>
            }
          />
          <Route
            path="/devices"
            element={
              <RequirePerm code="rms:device:view">
                <DevicePage />
              </RequirePerm>
            }
          />
          <Route path="/signoff" element={<ComingSoon />} />
          <Route path="/compare" element={<ComingSoon />} />
          <Route path="/audit" element={<ComingSoon />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
