// 图标出口（薄壳 + RMS 领域图标）。
//
// 34 个通用图标的实现已上提到 `@cim/ui`（原与 IAM 的同名文件逐字节相同）；
// 本文件只保留 RMS 独有的三个领域图标。页面里的 `from '@/components/Icons'` 无需改动。
export {
  IconGauge,
  IconApps,
  IconKick,
  IconUser,
  IconSidebar,
  IconSun,
  IconMoon,
  IconLogout,
  IconShield,
  IconKey,
  IconAlert,
  IconCheckCircle,
  IconInbox,
  IconBell,
  IconRefresh,
  IconEye,
  IconEyeOff,
  IconSearch,
  IconUsers,
  IconLock,
  IconLink,
  IconMonitor,
  IconList,
  IconSliders,
  IconPlus,
  IconTrash,
  IconBan,
  IconPower,
  IconTree,
  IconOrgGrant,
  IconChevron,
  IconX,
  IconEdit,
  IconSync,
  IconSave,
} from '@cim/ui';

import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement>;

/** 与 `@cim/ui` 保持一致的描边基准（本地图标沿用，避免两套视觉）。 */
const base: IconProps = {
  width: 20,
  height: 20,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
};

/** 配方（锥形瓶） */
export function IconFlask(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M9.5 3.5h5M10.5 3.5v5.2L5.4 17a2.4 2.4 0 0 0 2.1 3.5h9a2.4 2.4 0 0 0 2.1-3.5l-5.1-8.3V3.5" />
      <path d="M7.6 14.5h8.8" />
    </svg>
  );
}

/** 文件 */
export function IconFile(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M6 3.5h8l4.5 4.5v12.5H6z" />
      <path d="M14 3.5V8h4.5M9 12h6M9 15.5h6" />
    </svg>
  );
}
