// 权限注入（不依赖各应用的 authStore / usePermission）。
//
// 背景：现仓 iam / rms / approval 各有一份 `lib/usePermission.ts`（md5 相同），
// 但它们读的是各自的 zustand store，所以无法直接上提到共用包。这里改成注入：
// 应用把「怎么判断有权限」这件事传进来，组件只问「能不能」。
//
// ⚠️ 前端权限只用于**隐藏/禁用入口**，不构成安全边界。后端每个接口仍必须校验，
//    否则改一下 localStorage 就能越权 —— 这不是组件层能解决的问题，但必须写明。

import { createContext, useContext, useMemo, type ReactNode } from 'react';

export interface UiPermissionValue {
  /** 传入完整权限码（三段式，如 rms:recipe:create），返回是否有权限。 */
  has: (code: string) => boolean;
  /**
   * 权限是否已就绪（登录后拉完权限码才为 true）。
   * 未就绪时不隐藏入口，避免「先渲染再消失」的闪烁；提交后由后端兜底。
   */
  ready: boolean;
}

/** 未注入权限系统时的兜底：一律放行。 */
const FALLBACK: UiPermissionValue = { has: () => true, ready: true };

const UiPermissionContext = createContext<UiPermissionValue>(FALLBACK);

export function UiPermissionProvider({
  has,
  ready = true,
  children,
}: {
  has: (code: string) => boolean;
  ready?: boolean;
  children: ReactNode;
}) {
  const value = useMemo<UiPermissionValue>(() => ({ has, ready }), [has, ready]);
  return <UiPermissionContext.Provider value={value}>{children}</UiPermissionContext.Provider>;
}

/** 取单个权限判断函数。 */
export function useCan(): (code: string) => boolean {
  return useContext(UiPermissionContext).has;
}

/** 取完整权限上下文（需要 ready 时用）。 */
export function usePermissionContext(): UiPermissionValue {
  return useContext(UiPermissionContext);
}

/**
 * 权限门：无权限时不渲染 children。
 *
 * ```tsx
 * <Can perm="rms:recipe:create"><CimButton ... /></Can>
 * ```
 */
export function Can({
  perm,
  fallback = null,
  children,
}: {
  perm: string | string[];
  fallback?: ReactNode;
  children: ReactNode;
}) {
  const ctx = useContext(UiPermissionContext);
  const codes = Array.isArray(perm) ? perm : [perm];
  // 未就绪：先按「有权限」渲染，避免闪烁；就绪后再按真实权限收敛
  const ok = !ctx.ready || codes.every((c) => ctx.has(c));
  return <>{ok ? children : fallback}</>;
}
