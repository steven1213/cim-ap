import type { MeDto } from '@/types';

/** IAM 管理面所需的准入与角色组（与 server 端 @PreAuthorize 一致）。 */
export const IAM_APP = 'iam-ap';
export const IAM_ADMIN = 'ADMIN';

/**
 * 当前用户是否具备 IAM 管理面权限。
 *
 * <p>依据「准入 apps 含 iam-ap」且「iam-ap 下角色组含 ADMIN」判定，
 * 与后端 {@code hasAuthority('iam-ap:ADMIN')} 的语义对齐（前端仅用于菜单可见性与路由守卫，
 * 真正的鉴权始终在后端）。</p>
 */
export function isIamAdmin(user: MeDto | null | undefined): boolean {
  if (!user) return false;
  const apps = user.apps ?? [];
  const roles = user.roles?.[IAM_APP] ?? [];
  return apps.includes(IAM_APP) && roles.includes(IAM_ADMIN);
}
