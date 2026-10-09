import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';

/** 路由守卫：未登录跳转登录页。 */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}
