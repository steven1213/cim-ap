import type { ReactNode } from 'react';
import { useHasAllPermissions, useHasPermission } from '@/lib/usePermission';

/**
 * 按钮级权限包裹器：无权限时**不挂载**子树（而非 `display:none`）。
 *
 * <p>`display:none` 只是「看不见」——DOM 里仍有元素、处理器仍可被脚本/键盘触发，
 * 也容易被误判为「已隐藏」而放松后端校验。不挂载则连事件处理器都不存在，语义更严格。
 * 真正的鉴权仍在后端 `@PreAuthorize`，本组件只负责「不给无权限的人看到入口」。</p>
 *
 * <p><b>双闸门</b>：`codes` 为**全部满足（AND）**语义，用于「操作同时需要控制台码与平台码」的场景——
 * 例如菜单/权限/角色配置页的写操作，前端码决定入口、平台码决定接口；只有两者都具备时才渲染，
 * 从而不会出现「按钮可见但接口 403」。</p>
 *
 * @param code     单个所需权限码（为空/不传视为不需要权限）
 * @param codes    多个所需权限码（AND，全部具备才渲染）
 * @param children 有权限时渲染的内容
 * @param fallback 无权限时的替代内容（默认不渲染任何东西）
 */
export default function Perms({ code, codes, children, fallback = null }: {
  code?: string | null;
  codes?: readonly string[];
  children: ReactNode;
  fallback?: ReactNode;
}) {
  const single = useHasPermission(code);
  const multiple = useHasAllPermissions(codes ?? []);
  if (!single || !multiple) return <>{fallback}</>;
  return <>{children}</>;
}
