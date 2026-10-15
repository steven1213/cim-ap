import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Layout from '@/components/Layout';
import DashboardPage from '@/pages/DashboardPage';
import DefinitionsPage from '@/pages/DefinitionsPage';
import TodoPage from '@/pages/TodoPage';
import DonePage from '@/pages/DonePage';
import InitiatedPage from '@/pages/InitiatedPage';
import CallbacksPage from '@/pages/CallbacksPage';
import AuditPage from '@/pages/AuditPage';
import DesignerPage from '@/pages/DesignerPage';
import NotFoundPage from '@/pages/NotFoundPage';

/**
 * 审批流平台（approval-ap）前端路由。
 *
 * <p>后端当前为 dev 态（cim.auth.enabled=false），以 userId / tenantId 查询参数标识身份，
 * 无需 IAM 登录；外壳顶栏内置「身份切换」用于在演示/多角色间切换。
 * 生产接入 IAM 后，此处应加 ProtectedRoute + BootGate（参考 rms-ap 的 App.tsx）。</p>
 */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/definitions" element={<DefinitionsPage />} />
          <Route path="/designer" element={<DesignerPage />} />
          <Route path="/todo" element={<TodoPage />} />
          <Route path="/done" element={<DonePage />} />
          <Route path="/initiated" element={<InitiatedPage />} />
          <Route path="/callbacks" element={<CallbacksPage />} />
          <Route path="/audit" element={<AuditPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
