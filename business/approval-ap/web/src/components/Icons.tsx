// 图标出口（薄壳 + 审批领域图标）。
//
// 通用图标（用户/铃铛/盾牌/新增/搜索/刷新/垃圾桶/主题/侧栏/登出/chevron…）的实现
// 在 `@cim/ui`（`platform/web/packages/ui/src/icons.tsx`）——IAM 与 RMS 早已迁过去，
// 本文件是最后一份未并的副本：13 个同名图标与平台版本**同名不同路径**（同一语义两套画法），
// 同屏出现时观感不一致。现统一 re-export 平台版本，本地只保留审批领域图标。
//
// 新加图标前先判断：通用语义 → 加到 `@cim/ui`；只有审批懂的（流程图/回退/退回/企微…）
// → 才在本文件本地实现。
export {
  IconInbox,
  IconShield,
  IconBell,
  IconUser,
  IconPlus,
  IconSearch,
  IconSun,
  IconMoon,
  IconSidebar,
  IconLogout,
  IconRefresh,
  IconChevron,
  IconTrash,
  IconX,
} from '@cim/ui';

import type { ReactNode, SVGProps } from 'react';

type P = SVGProps<SVGSVGElement> & { width?: number; height?: number };

/** 与 `@cim/ui` 保持一致的描边基准（本地图标沿用，避免两套视觉）。 */
function Svg({ width = 20, height = 20, children, ...rest }: P & { children: ReactNode }) {
  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      {...rest}
    >
      {children}
    </svg>
  );
}

export const IconDashboard = (p: P) => (
  <Svg {...p}>
    <rect x="3" y="3" width="7" height="9" rx="1.5" />
    <rect x="14" y="3" width="7" height="5" rx="1.5" />
    <rect x="14" y="12" width="7" height="9" rx="1.5" />
    <rect x="3" y="16" width="7" height="5" rx="1.5" />
  </Svg>
);

export const IconFlow = (p: P) => (
  <Svg {...p}>
    <rect x="3" y="4" width="7" height="5" rx="1.5" />
    <rect x="14" y="15" width="7" height="5" rx="1.5" />
    <path d="M10 6.5h4a3 3 0 0 1 3 3v5.5" />
  </Svg>
);

export const IconCheck = (p: P) => (
  <Svg {...p}>
    <path d="M20 6 9 17l-5-5" />
  </Svg>
);

export const IconOutbox = (p: P) => (
  <Svg {...p}>
    <path d="M3 13h13l-3-3M16 13V6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2" />
    <path d="M16 3h5v5" />
  </Svg>
);

export const IconWebhook = (p: P) => (
  <Svg {...p}>
    <path d="M9 7a3 3 0 1 0 3 3h1" />
    <path d="M12 10a3 3 0 1 0 3 3v1a3 3 0 0 0 3-3" />
    <path d="M9 17a3 3 0 1 1-3-3" />
  </Svg>
);

export const IconUndo = (p: P) => (
  <Svg {...p}>
    <path d="M8 5 3 10l5 5" />
    <path d="M3 10h11a6 6 0 0 1 0 12h-4" />
  </Svg>
);

export const IconReturn = (p: P) => (
  <Svg {...p}>
    <path d="M9 14 4 9l5-5" />
    <path d="M4 9h11a5 5 0 0 1 0 10h-3" />
  </Svg>
);

export const IconAddUser = (p: P) => (
  <Svg {...p}>
    <circle cx="9" cy="8" r="4" />
    <path d="M3 21a6 6 0 0 1 12 0" />
    <path d="M19 8v6M16 11h6" />
  </Svg>
);

export const IconSwap = (p: P) => (
  <Svg {...p}>
    <path d="M7 7h12l-3-3M17 17H5l3 3" />
  </Svg>
);

export const IconPlay = (p: P) => (
  <Svg {...p}>
    <path d="M7 5l12 7-12 7z" />
  </Svg>
);

export const IconSend = (p: P) => (
  <Svg {...p}>
    <path d="M4 12l16-8-6 16-3-6z" />
  </Svg>
);

export const IconCopy = (p: P) => (
  <Svg {...p}>
    <rect x="9" y="9" width="11" height="11" rx="2" />
    <path d="M5 15V5a2 2 0 0 1 2-2h10" />
  </Svg>
);

export const IconGlobe = (p: P) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M3 12h18M12 3c2.5 2.5 2.5 15 0 18M12 3c-2.5 2.5-2.5 15 0 18" />
  </Svg>
);

export const IconPencil = (p: P) => (
  <Svg {...p}>
    <path d="M4 20h4L18.5 9.5a2.1 2.1 0 0 0-3-3L5 17v3z" />
    <path d="M13.5 6.5l3 3" />
  </Svg>
);

export const IconDownload = (p: P) => (
  <Svg {...p}>
    <path d="M12 4v10m0 0l-4-4m4 4l4-4" />
    <path d="M4 17v2a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-2" />
  </Svg>
);

export const IconClip = (p: P) => (
  <Svg {...p}>
    <path d="M20 11l-7.6 7.6a4.6 4.6 0 0 1-6.5-6.5L13.5 4.6a3 3 0 0 1 4.3 4.3l-7.6 7.6a1.5 1.5 0 0 1-2.1-2.1L15 7.5" />
  </Svg>
);

export const IconWeCom = (p: P) => (
  <Svg {...p}>
    <path d="M4 5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H9l-4 4v-4H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z" />
    <path d="M8 9h8M8 12h5" />
  </Svg>
);
