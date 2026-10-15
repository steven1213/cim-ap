import { useEffect, useState, type ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import Layout from '@/components/Layout';
import ProtectedRoute from '@/components/ProtectedRoute';
import RequirePerm from '@/components/RequirePerm';
import LoginPage from '@/pages/LoginPage';
import SsoCallback from '@/pages/SsoCallback';
import DashboardPage from '@/pages/DashboardPage';
import RecipeLibraryPage from '@/pages/RecipeLibraryPage';
import DeviceRecipePage from '@/pages/DeviceRecipePage';
import DeviceTypePage from '@/pages/DeviceTypePage';
import DeviceAreaPage from '@/pages/DeviceAreaPage';
import DevicePage from '@/pages/DevicePage';
import SignoffPage from '@/pages/SignoffPage';
import ComparePage from '@/pages/ComparePage';
import AuditLogPage from '@/pages/AuditLogPage';
import SpecPage from '@/pages/SpecPage';
import SpecTemplatePage from '@/pages/SpecTemplatePage';
import EcSvPage from '@/pages/EcSvPage';
import DeviceAuthPage from '@/pages/DeviceAuthPage';
import BypassPage from '@/pages/BypassPage';
import QualificationPage from '@/pages/QualificationPage';
import GoldenPage from '@/pages/GoldenPage';
import IntegrationPage from '@/pages/IntegrationPage';
import BodyTemplatePage from '@/pages/BodyTemplatePage';
import ExperimentPage from '@/pages/ExperimentPage';
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
  const [bootFailed, setBootFailed] = useState(false);

  useEffect(() => {
    if (!token) {
      setReady(true);
      return;
    }
    let alive = true;
    Promise.all([loadMe(), loadPermissions()])
      .then(() => {
        if (alive) setReady(true);
      })
      .catch((err) => {
        // 401 已由 api.ts 拦截清态跳登录；此处处理 403（无本 ap 准入）
        // 或其它 boot 失败，避免无权限令牌卡死 Loading。
        console.error('[BootGate] boot failed', err);
        useAuthStore.getState().clear();
        if (alive) setBootFailed(true);
      });
    return () => {
      alive = false;
    };
  }, [token, loadMe, loadPermissions]);

  // 引导失败（典型：令牌 apps claim 不含本 ap 接入码 → 后端 403）。
  // 必须带 error=admission 落回 /login：否则 /login 会自动中继回 IAM，
  // 而 IAM 侧会话仍在、会拿同一枚令牌再次弹回 /sso，形成两端登录页无限往返。
  if (bootFailed) {
    return <Navigate to="/login?error=admission" replace />;
  }

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
            path="/device-recipes"
            element={
              <RequirePerm code="rms:recipe:view">
                <DeviceRecipePage />
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
          <Route
            path="/signoff"
            element={
              <RequirePerm code="rms:signoff:view">
                <SignoffPage />
              </RequirePerm>
            }
          />
          <Route
            path="/compare"
            element={
              <RequirePerm code="rms:compare:view">
                <ComparePage />
              </RequirePerm>
            }
          />
          <Route
            path="/audit"
            element={
              <RequirePerm code="rms:audit:view">
                <AuditLogPage />
              </RequirePerm>
            }
          />
          <Route
            path="/specs"
            element={
              <RequirePerm code="rms:spec:view">
                <SpecPage />
              </RequirePerm>
            }
          />
          <Route
            path="/spec-templates"
            element={
              <RequirePerm code="rms:spec:view">
                <SpecTemplatePage />
              </RequirePerm>
            }
          />
          <Route
            path="/ecsv"
            element={
              <RequirePerm code="rms:ecsv:view">
                <EcSvPage />
              </RequirePerm>
            }
          />
          <Route
            path="/device-auth"
            element={
              <RequirePerm code="rms:device-auth:view">
                <DeviceAuthPage />
              </RequirePerm>
            }
          />
          <Route
            path="/bypasses"
            element={
              <RequirePerm code="rms:bypass:view">
                <BypassPage />
              </RequirePerm>
            }
          />
          <Route
            path="/qualifications"
            element={
              <RequirePerm code="rms:qualification:view">
                <QualificationPage />
              </RequirePerm>
            }
          />
          <Route
            path="/golden"
            element={
              <RequirePerm code="rms:golden:view">
                <GoldenPage />
              </RequirePerm>
            }
          />
          <Route
            path="/integration"
            element={
              <RequirePerm code="rms:integration:view">
                <IntegrationPage />
              </RequirePerm>
            }
          />
          <Route
            path="/body-templates"
            element={
              <RequirePerm code="rms:btemplate:view">
                <BodyTemplatePage />
              </RequirePerm>
            }
          />
          <Route
            path="/experiments"
            element={
              <RequirePerm code="rms:experiment:view">
                <ExperimentPage />
              </RequirePerm>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
