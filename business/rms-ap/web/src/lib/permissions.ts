// 前端权限判定（**仅用于体验**：菜单显隐、按钮挂载、路由守卫）。
//
// 权威鉴权始终在后端（`@PreAuthorize`）。前端权限集由 `GET /sys/me/permissions` 提供，
// 该端点**直接回显已注入 SecurityContext 的 authorities、不重查库**，因此「前端看到的」
// 与「后端鉴权用的」严格同源，不会出现「有按钮却 403」的错配（见 cim-system `SysMeController`）。
//
// ⚠️ **不做通配**：后端 `hasAuthority(...)` 是**精确字符串匹配**（`PermissionEvaluator` 不参与），
// 故前端也精确匹配。超管之所以拥有全部权限，是因为 `cim-system` 在解析时已把超管角色
// **展开为全部启用权限码**，而不是靠某个通配符——所以这里绝不能把 `SUPER_ADMIN` 当成 `*` 放通，
// 否则会出现「前端显示按钮、后端拒绝」的假阳性。

/** RMS 管理员兜底权限码（定义见 `lib/permCodes.ts`，此处转出便于统一引用）。 */
export { RMS_ADMIN } from '@/lib/permCodes';

/**
 * 是否具备某权限码。
 *
 * @param permissions 当前用户权限码集（来自 `/sys/me/permissions`）
 * @param code        权限码；为空表示「不需要权限」
 */
export function hasPermission(permissions: readonly string[] | undefined | null,
                             code: string | null | undefined): boolean {
  if (!code) return true;
  if (!permissions || permissions.length === 0) return false;
  return permissions.includes(code);
}

/** 是否具备任意一个权限码。 */
export function hasAnyPermission(permissions: readonly string[] | undefined | null,
                                codes: readonly string[]): boolean {
  if (codes.length === 0) return true;
  return codes.some((c) => hasPermission(permissions, c));
}

/** 是否具备全部权限码。 */
export function hasAllPermissions(permissions: readonly string[] | undefined | null,
                                 codes: readonly string[]): boolean {
  return codes.every((c) => hasPermission(permissions, c));
}
