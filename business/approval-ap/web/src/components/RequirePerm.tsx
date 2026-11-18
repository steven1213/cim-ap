import type { ReactNode } from 'react';
import { RequirePermission } from '@cim/shell';
import { useHasPermission } from '@/lib/usePermission';
import ForbiddenPage from '@/pages/ForbiddenPage';

/**
 * 路由级权限守卫：无所需权限渲染 403 页（前端体验用，权威鉴权在后端 `@PreAuthorize`）。
 *
 * <p>判定与呈现分离：`RequirePermission` 在 `@cim/shell`（各 ap 共用），未授权呈现由本应用
 * 注入——approval 就地渲染 403 页（URL 不变，权限补上后刷新即达原页）。权限码读取走
 * {@link useHasPermission}：sso 通道精确匹配（无通配），dev 演示通道恒放行。</p>
 */
export default function RequirePerm({ code, children }: { code?: string | null; children: ReactNode }) {
  const allowed = useHasPermission(code);
  return (
    <RequirePermission allowed={allowed} fallback={<ForbiddenPage />}>
      {children}
    </RequirePermission>
  );
}
