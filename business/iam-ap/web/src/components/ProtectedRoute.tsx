import type { ReactNode } from 'react';
import { ProtectedRoute as ShellProtectedRoute } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';

/** 路由守卫：未登录跳转登录页。判定语义在 `@cim/shell`（与 RMS 共用），这里只供当前 token。 */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  return <ShellProtectedRoute authenticated={!!token}>{children}</ShellProtectedRoute>;
}
