import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useHasPermission } from '@/lib/usePermission';

/**
 * 路由权限守卫：无权限时跳 403 页。
 *
 * <p>与菜单可见性是<b>两套</b>授权（`sys_role_menu` / `sys_role_perm`），允许交叉：
 * 被授权了菜单但没权限码时，路由可达但页面应拒之——所以这里仍要拦一道。</p>
 *
 * <p>前置条件：调用方须在 {@code BootGate} 之内（权限码集已就绪），否则会把「尚未加载」
 * 误判为「无权限」。见 `components/BootGate.tsx`。</p>
 */
export default function RequirePerm({ code, children }: { code?: string | null; children: ReactNode }) {
  const allowed = useHasPermission(code);
  if (!allowed) return <Navigate to="/403" replace />;
  return <>{children}</>;
}
