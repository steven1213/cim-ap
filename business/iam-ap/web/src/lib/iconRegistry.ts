// 图标注册表：把后端 `sys_menu.icon`（字符串）映射为组件。
//
// <p>菜单入库后，前端不再硬编码 `ReactNode` 图标；后端只存图标**标识**，
// 由本表解析。契约：未知标识或为空时回退到 {@link IconFallback}（绝不渲染空白），
// 这样「管理端新增了一个前端还没有对应图标的菜单」不会让侧栏出现空洞。</p>
import type { ComponentType, SVGProps } from 'react';
import {
  IconApps,
  IconAlert,
  IconGauge,
  IconInbox,
  IconLink,
  IconList,
  IconLock,
  IconMonitor,
  IconOrgGrant,
  IconShield,
  IconSliders,
  IconTree,
  IconUser,
  IconUsers,
} from '@/components/Icons';

/** 图标组件签名（与 `components/Icons.tsx` 一致）。 */
export type IconComponent = ComponentType<SVGProps<SVGSVGElement>>;

/** 未知/缺失图标时的回退。 */
export const IconFallback: IconComponent = IconAlert;

/** 标识 → 组件。键与 `IamConsoleCatalog` 里 `MenuDef.icon` 取值一一对应。 */
const REGISTRY: Record<string, IconComponent> = {
  gauge: IconGauge,
  tree: IconTree,
  users: IconUsers,
  user: IconUser,
  lock: IconLock,
  apps: IconApps,
  link: IconLink,
  'org-grant': IconOrgGrant,
  shield: IconShield,
  monitor: IconMonitor,
  list: IconList,
  sliders: IconSliders,
  inbox: IconInbox,
};

/** 解析图标标识；未知返回回退图标。 */
export function resolveIcon(name: string | null | undefined): IconComponent {
  if (!name) return IconFallback;
  return REGISTRY[name] ?? IconFallback;
}

/** 注册表全部键（供菜单管理页做图标选择器）。 */
export function iconKeys(): string[] {
  return Object.keys(REGISTRY).sort();
}
