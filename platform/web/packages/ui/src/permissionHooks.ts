// 权限判定的 Hook 工厂——各 ap 的 `lib/usePermission.ts` 只在这里接线。
//
// 背景：`iam` / `rms` / `mds` 各写了一份 `usePermission.ts`（四个 hook，函数体逐行相同，
// 都是「读权限码集 → 转调 `hasPermission` 纯函数」）。iam/rms 那两份**逐字节相同**，
// mds 只差一行注释——这类「只差 import 路径」的副本最没意义：改匹配语义时三处都要动，
// 而漏掉一处的表现是「某个系统的按钮该隐藏没隐藏」。
//
// 为什么不直接把 hook 写死在 `@cim/ui` 里：权限码集在各 ap 的 zustand store 里，
// 平台包不认识它们（`ui` 的既有约束是「组件不读各 ap 的 store」，权限走
// `UiPermissionProvider` 注入）。所以这里只提供**工厂**，由应用把 `useCodes` 递进来。
//
// ⚠️ 与 `UiPermissionProvider` / `<Can>` 的关系：两条路读的是同一份权限码集，最终都调
// `hasPermission`。`<Can>` 用于组件树内声明式隐藏，本工厂用于「需要在 JS 里判一下」的场合
// （路由守卫、列定义、`onClick` 前判断）。

import { hasAllPermissions, hasAnyPermission, hasPermission } from './permissions';

export interface PermissionHooks {
  /** 当前用户权限码集。 */
  usePermissions(): string[];
  /** 是否具备某权限码（`code` 为空视为不需要权限）。 */
  useHasPermission(code: string | null | undefined): boolean;
  /** 是否具备任一权限码（空数组视为不需要权限）。 */
  useHasAnyPermission(codes: readonly string[]): boolean;
  /** 是否具备全部权限码（空数组视为不需要权限）。 */
  useHasAllPermissions(codes: readonly string[]): boolean;
}

/**
 * 生成一组权限 hook。
 *
 * <pre>{@code
 * // app/src/lib/usePermission.ts —— 应用侧只剩这一处接线
 * const useCodes = () => useAuthStore((s) => s.permissions);
 * export const { usePermissions, useHasPermission, useHasAnyPermission, useHasAllPermissions } =
 *   createPermissionHooks(useCodes);
 * }</pre>
 *
 * @param useCodes 读当前权限码集的 hook。**必须是稳定引用**（模块级函数或 store selector），
 *                 内联箭头会让下游 hook 每次渲染都拿到新函数——不影响正确性，但白丢一次 memo。
 */
export function createPermissionHooks(useCodes: () => string[]): PermissionHooks {
  function usePermissions(): string[] {
    return useCodes();
  }

  function useHasPermission(code: string | null | undefined): boolean {
    return hasPermission(useCodes(), code);
  }

  function useHasAnyPermission(codes: readonly string[]): boolean {
    return hasAnyPermission(useCodes(), codes);
  }

  function useHasAllPermissions(codes: readonly string[]): boolean {
    return hasAllPermissions(useCodes(), codes);
  }

  return { usePermissions, useHasPermission, useHasAnyPermission, useHasAllPermissions };
}
