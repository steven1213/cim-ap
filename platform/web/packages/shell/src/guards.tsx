// 路由守卫：登录态 + 权限码。
//
// 收在这里的原因：`iam` 与 `rms` 的 `ProtectedRoute.tsx` md5 相同；`RequirePerm.tsx` 原本
// 也是同一概念的两种写法（各自读自己的 store）。守卫的**判定结果**必须由应用注入——token 与
// 权限码都在各自 zustand store 里，包内不碰状态库（与 LocaleSwitcher / NotificationBell 同口径）。
//
// ⚠️ `RequirePermission` 的未授权呈现走 `fallback` 而非内置：IAM 跳 `/403`（URL 变），
//    RMS 就地渲染 403 页（URL 不变）。这个差异会改变「刷新/后退/分享」的行为，属产品决策，
//    组件层不该替应用统一。
import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';

export interface ProtectedRouteProps {
  /** 是否已登录（应用从自己的 auth store 取，包内不判断）。 */
  authenticated: boolean;
  /** 未登录时跳转的登录地址。 */
  loginPath?: string;
  children: ReactNode;
}

/** 未登录 → 跳登录页；已登录 → 渲染 children。 */
export function ProtectedRoute({ authenticated, loginPath = '/login', children }: ProtectedRouteProps) {
  if (!authenticated) return <Navigate to={loginPath} replace />;
  return <>{children}</>;
}

export interface RequirePermissionProps {
  /** 是否已具备所需权限（应用用 `useHasPermission(code)` 判定后传入）。 */
  allowed: boolean;
  /** 未授权时的呈现（跳 403 页或就地渲染 403 页，由应用决定）。 */
  fallback: ReactNode;
  children: ReactNode;
}

/**
 * 路由级权限门：与 `@cim/ui` 的 `<Can>` 同语义，区别是 `<Can>` 从 `UiPermissionProvider`
 * 取判定函数、用于元素级；本组件收已算好的布尔值、用于路由级（`element`/`RoutePage`）。
 */
export function RequirePermission({ allowed, fallback, children }: RequirePermissionProps) {
  return <>{allowed ? children : fallback}</>;
}
