import { Suspense, lazy, useEffect, useState, type ComponentType, type ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { CimSpin } from '@cim/ui';
import { useAuthStore } from '@/store/authStore';
import Layout from '@/components/Layout';
import ProtectedRoute from '@/components/ProtectedRoute';
import RequirePerm from '@/components/RequirePerm';

// 登录/回调/403/404 保持同步加载：未登录用户首屏就落在这几条上，
// 再拆一个 chunk 只是把「白屏」换成「多一次往返」。
import LoginPage from '@/pages/LoginPage';
import SsoCallback from '@/pages/SsoCallback';
import NotFoundPage from '@/pages/NotFoundPage';
import ForbiddenPage from '@/pages/ForbiddenPage';

/**
 * 业务页面一律按路由拆包。
 *
 * <p>不拆的话整个应用（含 antd、22 个页面、xlsx 之类的工具）打成一个 1.47 MB 的 chunk，
 * 用户打开「设备区域」也要先下载「配方比对」的代码。拆开后首屏只拉布局与登录态，
 * 页面各自成 chunk，按需加载。</p>
 *
 * <p>{@link lazyRoute} 把「Suspense + 权限守卫 + 页面」包成一个路由 element：
 * Suspense 层级在 Layout 的 `<Outlet/>` **内部**，切页时外壳不卸载，
 * 只有内容区显示加载态（放在 Routes 外层写会连侧边栏一起闪掉）。</p>
 */
function lazyRoute(Page: ComponentType, permCode?: string) {
  return (
    <Suspense fallback={<CimSpin block height={320} />}>
      {permCode ? (
        <RequirePerm code={permCode}>
          <Page />
        </RequirePerm>
      ) : (
        <Page />
      )}
    </Suspense>
  );
}

const DashboardPage = lazy(() => import('@/pages/DashboardPage'));
const RecipeLibraryPage = lazy(() => import('@/pages/RecipeLibraryPage'));
const DeviceRecipePage = lazy(() => import('@/pages/DeviceRecipePage'));
const DeviceTypePage = lazy(() => import('@/pages/DeviceTypePage'));
const DeviceAreaPage = lazy(() => import('@/pages/DeviceAreaPage'));
const DevicePage = lazy(() => import('@/pages/DevicePage'));
const SignoffPage = lazy(() => import('@/pages/SignoffPage'));
const ComparePage = lazy(() => import('@/pages/ComparePage'));
const AuditLogPage = lazy(() => import('@/pages/AuditLogPage'));
const SpecPage = lazy(() => import('@/pages/SpecPage'));
const SpecTemplatePage = lazy(() => import('@/pages/SpecTemplatePage'));
const EcSvPage = lazy(() => import('@/pages/EcSvPage'));
const DeviceAuthPage = lazy(() => import('@/pages/DeviceAuthPage'));
const BypassPage = lazy(() => import('@/pages/BypassPage'));
const QualificationPage = lazy(() => import('@/pages/QualificationPage'));
const GoldenPage = lazy(() => import('@/pages/GoldenPage'));
const IntegrationPage = lazy(() => import('@/pages/IntegrationPage'));
const BodyTemplatePage = lazy(() => import('@/pages/BodyTemplatePage'));
const ExperimentPage = lazy(() => import('@/pages/ExperimentPage'));

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
          <Route path="/" element={lazyRoute(DashboardPage)} />
          <Route path="/recipes" element={lazyRoute(RecipeLibraryPage, 'rms:recipe:view')} />
          <Route path="/device-types" element={lazyRoute(DeviceTypePage, 'rms:device-type:view')} />
          <Route path="/device-areas" element={lazyRoute(DeviceAreaPage, 'rms:device-area:view')} />
          <Route path="/device-recipes" element={lazyRoute(DeviceRecipePage, 'rms:recipe:view')} />
          <Route path="/devices" element={lazyRoute(DevicePage, 'rms:device:view')} />
          <Route path="/signoff" element={lazyRoute(SignoffPage, 'rms:signoff:view')} />
          <Route path="/compare" element={lazyRoute(ComparePage, 'rms:compare:view')} />
          <Route path="/audit" element={lazyRoute(AuditLogPage, 'rms:audit:view')} />
          <Route path="/specs" element={lazyRoute(SpecPage, 'rms:spec:view')} />
          <Route path="/spec-templates" element={lazyRoute(SpecTemplatePage, 'rms:spec:view')} />
          <Route path="/ecsv" element={lazyRoute(EcSvPage, 'rms:ecsv:view')} />
          <Route path="/device-auth" element={lazyRoute(DeviceAuthPage, 'rms:device-auth:view')} />
          <Route path="/bypasses" element={lazyRoute(BypassPage, 'rms:bypass:view')} />
          <Route path="/qualifications" element={lazyRoute(QualificationPage, 'rms:qualification:view')} />
          <Route path="/golden" element={lazyRoute(GoldenPage, 'rms:golden:view')} />
          <Route path="/integration" element={lazyRoute(IntegrationPage, 'rms:integration:view')} />
          <Route path="/body-templates" element={lazyRoute(BodyTemplatePage, 'rms:btemplate:view')} />
          <Route path="/experiments" element={lazyRoute(ExperimentPage, 'rms:experiment:view')} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
