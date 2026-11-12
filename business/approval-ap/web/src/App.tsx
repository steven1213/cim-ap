import { Suspense, lazy, useEffect, useState, type ComponentType, type ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CimSpin } from '@cim/ui';
import { ProtectedRoute, SsoRelay } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';
import Layout from '@/components/Layout';

// 登录中继/回调保持同步加载：未登录用户首屏就落在这两条上，再拆 chunk 只是多一次往返。
import LoginPage from '@/pages/LoginPage';
import SsoCallback from '@/pages/SsoCallback';
import NotFoundPage from '@/pages/NotFoundPage';

/**
 * 审批流平台（approval-ap）前端路由。
 *
 * <p>身份有两条通道，由后端 `GET /api/v1/approval/me` 的 `authEnabled` 决定（`main.tsx`
 * 挂载前已探明，写入 `authStore.mode`）：</p>
 * <ul>
 *   <li><b>统一登录</b>（`cim.auth.enabled=true`）：`ProtectedRoute` 无令牌即跳 `/login`，
 *       由该页整页中继到 IAM（8081）登录页；回跳走 `/sso`。</li>
 *   <li><b>dev 态</b>（`enabled=false`）：不设登录门，顶栏保留「身份切换」，
 *       身份以 userId/tenantId 查询参数表达。</li>
 * </ul>
 *
 * <p>页面按路由拆包：设计器（DesignerPage）自带画布与 SVG 逻辑，体积远大于列表页，
 * 不该压在看「待我审批」的人身上。404 同步加载 —— 它是随手访问错地址时的兜底，
 * 不值得为它多一次往返。</p>
 */
function page(Page: ComponentType) {
  // Suspense 在 Layout 的 `<Outlet/>` 内部，切页时外壳不闪。
  return (
    <Suspense fallback={<CimSpin block height={320} />}>
      <Page />
    </Suspense>
  );
}

const DashboardPage = lazy(() => import('@/pages/DashboardPage'));
const DefinitionsPage = lazy(() => import('@/pages/DefinitionsPage'));
const DesignerPage = lazy(() => import('@/pages/DesignerPage'));
const TodoPage = lazy(() => import('@/pages/TodoPage'));
const DonePage = lazy(() => import('@/pages/DonePage'));
const InitiatedPage = lazy(() => import('@/pages/InitiatedPage'));
const CallbacksPage = lazy(() => import('@/pages/CallbacksPage'));
const AuditPage = lazy(() => import('@/pages/AuditPage'));
const WeComCallbackPage = lazy(() => import('@/pages/WeComCallbackPage'));

/**
 * 启动闸门：统一登录态下先把 `/me` 的档案灌进 `identityStore`，再渲染外壳。
 *
 * <p>不灌就会出现「已登录但待办列表空」：列表页用 store 里的 userId 查询，
 * 首帧拿到空值即发出空查询。dev 态无需等待（身份来自持久化的切换器）。</p>
 */
function BootGate({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const mode = useAuthStore((s) => s.mode);
  const user = useAuthStore((s) => s.user);
  const loadMe = useAuthStore((s) => s.loadMe);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (mode !== 'sso' || user) return;
    loadMe().catch((err) => {
      // 403 典型是令牌 apps claim 不含 approval-ap（准入未开通）。
      // 必须带 error=admission 落回 /login：否则该页会自动中继回 IAM，
      // 而 IAM 侧会话仍在、会拿同一枚令牌再次弹回 /sso，形成两端无限往返。
      console.error('[BootGate] /me 失败', err);
      useAuthStore.getState().clear();
      setFailed(true);
    });
  }, [mode, user, loadMe]);

  if (failed) return <Navigate to="/login?error=admission" replace />;
  if (mode === 'sso' && !user) {
    return <SsoRelay mark="AP" brand="CIM Approval" label={t('common.loading')} />;
  }
  return <>{children}</>;
}

export default function App() {
  const mode = useAuthStore((s) => s.mode);
  const token = useAuthStore((s) => s.token);
  // dev 通道不设登录门；统一登录要求令牌（档案由 BootGate 补齐）
  const authenticated = mode === 'dev' || Boolean(token);

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/sso" element={<SsoCallback />} />

        <Route
          element={
            <ProtectedRoute authenticated={authenticated}>
              <BootGate>
                <Layout />
              </BootGate>
            </ProtectedRoute>
          }
        >
          <Route path="/" element={page(DashboardPage)} />
          <Route path="/definitions" element={page(DefinitionsPage)} />
          <Route path="/designer" element={page(DesignerPage)} />
          <Route path="/todo" element={page(TodoPage)} />
          <Route path="/done" element={page(DonePage)} />
          <Route path="/initiated" element={page(InitiatedPage)} />
          <Route path="/callbacks" element={page(CallbacksPage)} />
          <Route path="/audit" element={page(AuditPage)} />
          {/* 企微免登回调：链接由企微内置浏览器打开、本身不带 JWT。认证态下后端 `/api/**` 统一要求
              令牌，该端点尚未放行，故这条链路目前只在 dev 通道可用（见 AUDIT §36.5「已知边界」）。 */}
          <Route path="/wecom/callback" element={page(WeComCallbackPage)} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
