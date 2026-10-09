import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import ProtectedRoute from '@/components/ProtectedRoute';
import RequireAdmin from '@/components/RequireAdmin';
import Layout from '@/components/Layout';
import LoginPage from '@/pages/LoginPage';
import DashboardPage from '@/pages/DashboardPage';
import ProfilePage from '@/pages/ProfilePage';
import AppMgmtPage from '@/pages/AppMgmtPage';
import UsersPage from '@/pages/UsersPage';
import OrgPage from '@/pages/OrgPage';
import OrgGrantsPage from '@/pages/OrgGrantsPage';
import LockoutsPage from '@/pages/LockoutsPage';
import AdmissionsPage from '@/pages/AdmissionsPage';
import RolesPage from '@/pages/RolesPage';
import SessionsPage from '@/pages/SessionsPage';
import AuditPage from '@/pages/AuditPage';
import SettingsPage from '@/pages/SettingsPage';

/** 管理页统一包裹：仅 IAM 管理员可访问。 */
function Admin({ children }: { children: React.ReactNode }) {
  return <RequireAdmin>{children}</RequireAdmin>;
}

export default function App() {
  const token = useAuthStore((s) => s.token);
  return (
    <Routes>
      <Route path="/login" element={token ? <Navigate to="/" replace /> : <LoginPage />} />
      <Route
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<DashboardPage />} />
        <Route path="/orgs" element={<Admin><OrgPage /></Admin>} />
        <Route path="/users" element={<Admin><UsersPage /></Admin>} />
        <Route path="/lockouts" element={<Admin><LockoutsPage /></Admin>} />
        <Route path="/apps" element={<Admin><AppMgmtPage /></Admin>} />
        <Route path="/admissions" element={<Admin><AdmissionsPage /></Admin>} />
        <Route path="/org-grants" element={<Admin><OrgGrantsPage /></Admin>} />
        <Route path="/roles" element={<Admin><RolesPage /></Admin>} />
        <Route path="/sessions" element={<Admin><SessionsPage /></Admin>} />
        <Route path="/audit" element={<Admin><AuditPage /></Admin>} />
        <Route path="/settings" element={<Admin><SettingsPage /></Admin>} />
        <Route path="/profile" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
