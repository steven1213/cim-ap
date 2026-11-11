// 前端权限判定（**仅用于体验**：菜单显隐、按钮挂载、路由守卫）。
//
// 三个匹配函数已上提到 `@cim/ui`（`packages/ui/src/permissions.ts`）——此前 IAM 与 RMS
// 各存一份、逐字节相同。本文件只保留 RMS 专属常量并转出函数，页面引用面零改动。
//
// ⚠️ 前端权限只用于隐藏/禁用入口，不构成安全边界：权威鉴权始终在后端（`@PreAuthorize`），
//    否则改一下 localStorage 就能越权。
// ⚠️ **不做通配**：后端 `hasAuthority(...)` 是精确匹配；超管之所以拥有全部权限，是
//    `cim-system` 把超管角色**展开为全部启用权限码**的结果，不是某个 `*`——所以这里绝不能
//    把 `SUPER_ADMIN` 当成通配符放通，否则会出现「前端显示按钮、后端拒绝」的假阳性。

/** 超管信号权限码（由后端 DbLocalAuthorityLoader 在解析超管角色时展开进 authorities）。 */
export const SUPER_ADMIN = 'SUPER_ADMIN';

export { hasPermission, hasAnyPermission, hasAllPermissions } from '@cim/ui';
