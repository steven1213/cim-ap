import type { ReactNode } from 'react';
import { RequirePermission } from '@cim/shell';
import { useHasPermission } from '@/lib/usePermission';
import ForbiddenPage from '@/pages/ForbiddenPage';

/**
 * 路由级权限守卫：无所需权限渲染 403 页（前端体验用，权威鉴权在后端 `@PreAuthorize`）。
 *
 * <p>判定与呈现分离：`RequirePermission` 在 `@cim/shell`（三 ap 共用），未授权呈现由本应用
 * 注入 —— RMS 就地渲染 403 页（URL 不变，权限补上后刷新即达原页），IAM 则跳转 {@code /403}。
 * 权限码读取与 IAM 统一走 {@code useHasPermission}，不再各自拼 store + 匹配函数。</p>
 */
export default function RequirePerm({ code, children }: { code?: string | null; children: ReactNode }) {
  const allowed = useHasPermission(code);
  return (
    <RequirePermission allowed={allowed} fallback={<ForbiddenPage />}>
      {children}
    </RequirePermission>
  );
}
