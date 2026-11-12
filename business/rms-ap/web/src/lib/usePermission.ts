// 权限判定的 Hook —— 实现是 `@cim/ui` 的 `createPermissionHooks`，本文件只接线。
//
// 四个函数的函数体此前与 IAM / MDS 的副本逐行相同（只有 import 路径不同），
// 改匹配语义时要动三处、漏一处就表现为「某个系统的按钮该隐藏没隐藏」。
import { createPermissionHooks } from '@cim/ui';
import { useAuthStore } from '@/store/authStore';

/** 当前用户权限码集（store 里的内存态，不落盘）。 */
const useCodes = () => useAuthStore((s) => s.permissions);

export const { usePermissions, useHasPermission, useHasAnyPermission, useHasAllPermissions } =
  createPermissionHooks(useCodes);
