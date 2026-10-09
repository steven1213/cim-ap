import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import ProtectedRoute from '@/components/ProtectedRoute';
import Layout from '@/components/Layout';
import LoginPage from '@/pages/LoginPage';
import DashboardPage from '@/pages/DashboardPage';
import ProfilePage from '@/pages/ProfilePage';
import AppMgmtPage from '@/pages/AppMgmtPage';
import TokenVersionPage from '@/pages/TokenVersionPage';

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
        <Route path="/apps" element={<AppMgmtPage />} />
        <Route path="/token-version" element={<TokenVersionPage />} />
        <Route path="/profile" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
