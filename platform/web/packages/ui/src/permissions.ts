// 权限码匹配（纯函数，无状态、无框架依赖）。
//
// 背景：`iam` / `rms` 各有一份 `lib/permissions.ts`，三个函数逐字节相同，只有文件末尾
// 导出的应用专属常量不同（IAM 是 `CONSOLE_ADMIN`、RMS 是 `SUPER_ADMIN`）。这里收掉纯逻辑，
// 应用侧文件保留为「re-export + 本地常量」，引用面零改动。
//
// ⚠️ **不做通配**：后端 `hasAuthority(...)` 是**精确字符串匹配**，故前端也精确匹配。
// 超管之所以拥有全部权限，是因为 `cim-system` 在解析时已把超管角色**展开为全部启用权限码**，
// 而不是靠某个通配符——所以这里绝不能把 `SUPER_ADMIN` 当成 `*` 放通，
// 否则会出现「前端显示按钮、后端拒绝」的假阳性。
//
// ⚠️ 前端权限只用于**隐藏/禁用入口**，不构成安全边界；后端每个接口仍须校验。

/**
 * 是否具备某权限码。
 *
 * @param permissions 当前用户权限码集（来自 `/sys/me/permissions`）
 * @param code        权限码；为空表示「不需要权限」
 */
export function hasPermission(
  permissions: readonly string[] | undefined | null,
  code: string | null | undefined,
): boolean {
  if (!code) return true;
  if (!permissions || permissions.length === 0) return false;
  return permissions.includes(code);
}

/** 是否具备任意一个权限码（空数组视为不需要权限）。 */
export function hasAnyPermission(
  permissions: readonly string[] | undefined | null,
  codes: readonly string[],
): boolean {
  if (codes.length === 0) return true;
  return codes.some((c) => hasPermission(permissions, c));
}

/** 是否具备全部权限码（空数组视为不需要权限）。 */
export function hasAllPermissions(
  permissions: readonly string[] | undefined | null,
  codes: readonly string[],
): boolean {
  return codes.every((c) => hasPermission(permissions, c));
}
