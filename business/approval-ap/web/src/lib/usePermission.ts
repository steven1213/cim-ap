// 权限判定 —— 与 RMS 同构（`@cim/ui` 纯函数、精确匹配、无通配：后端 hasAuthority
// 是精确字符串匹配，前端通配会出现「按钮可见、后端拒绝」的假阳性）。
//
// 与另外三端唯一的不同是 approval 的 **dev 演示通道**（`cim.auth.enabled=false`）：
// 无 IAM、无登录身份，权限码无从解析——该通道一律放行（前端权限只控制入口显隐，
// 不构成安全边界；dev 态后端本就无鉴权）。sso 通道行为与 RMS 完全一致。
import { hasPermission } from '@cim/ui';
import { useAuthStore } from '@/store/authStore';

/** 是否具备某权限码（同步版：事件回调 / 列定义里判）。dev 演示通道恒放行。 */
export function hasPerm(code: string | null | undefined): boolean {
  if (useAuthStore.getState().mode === 'dev') return true;
  return hasPermission(useAuthStore.getState().user?.permissions, code);
}

/** 当前用户权限码集（sso 通道的内存态；dev 通道为空——入口判定走 {@link hasPerm}）。 */
export function usePermissions(): string[] {
  const perms = useAuthStore((s) => s.user?.permissions);
  return perms ?? [];
}

/** 是否具备某权限码（响应式 hook：路由守卫 / 组件内判）。dev 演示通道恒放行。 */
export function useHasPermission(code: string | null | undefined): boolean {
  const mode = useAuthStore((s) => s.mode);
  const perms = useAuthStore((s) => s.user?.permissions);
  if (mode === 'dev') return true;
  return hasPermission(perms, code);
}
