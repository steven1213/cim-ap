import { Suspense, lazy, type ComponentType } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { CimSpin } from '@cim/ui';
import Layout from '@/components/Layout';
import NotFoundPage from '@/pages/NotFoundPage';

/**
 * 审批流平台（approval-ap）前端路由。
 *
 * <p>后端当前为 dev 态（cim.auth.enabled=false），以 userId / tenantId 查询参数标识身份，
 * 无需 IAM 登录；外壳顶栏内置「身份切换」用于在演示/多角色间切换。
 * 生产接入 IAM 后，此处应加 ProtectedRoute + BootGate（参考 rms-ap 的 App.tsx）。</p>
 *
 * <p>页面按路由拆包：设计器（DesignerPage）自带画布与 SVG 逻辑，体积远大于列表页，
 * 不该压在看「待我审批」的人身上。404 同步加载 —— 它是随手访问错地址时的兜底，
 * 不值得为它多一次往返。</p>
 */

const DashboardPage = lazy(() => import('@/pages/DashboardPage'));
const DefinitionsPage = lazy(() => import('@/pages/DefinitionsPage'));
const DesignerPage = lazy(() => import('@/pages/DesignerPage'));
const TodoPage = lazy(() => import('@/pages/TodoPage'));
const DonePage = lazy(() => import('@/pages/DonePage'));
const InitiatedPage = lazy(() => import('@/pages/InitiatedPage'));
const CallbacksPage = lazy(() => import('@/pages/CallbacksPage'));
const AuditPage = lazy(() => import('@/pages/AuditPage'));
const WeComCallbackPage = lazy(() => import('@/pages/WeComCallbackPage'));

/** Suspense 在 Layout 的 `<Outlet/>` 内部，切页时外壳不闪。 */
function page(Page: ComponentType) {
  return (
    <Suspense fallback={<CimSpin block height={320} />}>
      <Page />
    </Suspense>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={page(DashboardPage)} />
          <Route path="/definitions" element={page(DefinitionsPage)} />
          <Route path="/designer" element={page(DesignerPage)} />
          <Route path="/todo" element={page(TodoPage)} />
          <Route path="/done" element={page(DonePage)} />
          <Route path="/initiated" element={page(InitiatedPage)} />
          <Route path="/callbacks" element={page(CallbacksPage)} />
          <Route path="/audit" element={page(AuditPage)} />
          <Route path="/wecom/callback" element={page(WeComCallbackPage)} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
