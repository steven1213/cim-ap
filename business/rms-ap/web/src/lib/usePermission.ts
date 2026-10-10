// 权限判定的 React Hook 封装（配合 `<Perms>` 使用）。
import { useAuthStore } from '@/store/authStore';
import { hasAllPermissions, hasAnyPermission, hasPermission } from '@/lib/permissions';

/** 当前用户权限码集。 */
export function usePermissions(): string[] {
  return useAuthStore((s) => s.permissions);
}

/** 是否具备某权限码（`code` 为空视为不需要权限）。 */
export function useHasPermission(code: string | null | undefined): boolean {
  const perms = usePermissions();
  return hasPermission(perms, code);
}

/** 是否具备任一权限码。 */
export function useHasAnyPermission(codes: readonly string[]): boolean {
  const perms = usePermissions();
  return hasAnyPermission(perms, codes);
}

/** 是否具备全部权限码。 */
export function useHasAllPermissions(codes: readonly string[]): boolean {
  const perms = usePermissions();
  return hasAllPermissions(perms, codes);
}
