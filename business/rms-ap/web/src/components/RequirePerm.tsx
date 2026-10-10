import type { ReactNode } from 'react';
import { useAuthStore } from '@/store/authStore';
import { hasPermission } from '@/lib/permissions';
import ForbiddenPage from '@/pages/ForbiddenPage';

/** 路由级权限守卫：无所需权限渲染 403 页（前端体验用，权威鉴权在后端 @PreAuthorize）。 */
export default function RequirePerm({ code, children }: { code?: string | null; children: ReactNode }) {
  const permissions = useAuthStore((s) => s.permissions);
  if (code && !hasPermission(permissions, code)) return <ForbiddenPage />;
  return <>{children}</>;
}
