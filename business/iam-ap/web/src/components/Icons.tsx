// 内联 SVG 图标：不引入图标库依赖，统一 currentColor 描边，尺寸随字号。
import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement>;

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

/** 概览 */
export function IconGauge(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <rect x="3" y="3" width="7.5" height="7.5" rx="2" />
      <rect x="13.5" y="3" width="7.5" height="7.5" rx="2" />
      <rect x="3" y="13.5" width="7.5" height="7.5" rx="2" />
      <rect x="13.5" y="13.5" width="7.5" height="7.5" rx="2" />
    </svg>
  );
}

/** 应用与准入（盾牌 + 勾） */
export function IconApps(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M12 3l7 3v5.2c0 4.5-3 8-7 9.8-4-1.8-7-5.3-7-9.8V6l7-3z" />
      <path d="M9 12l2.2 2.2L15.5 10" />
    </svg>
  );
}

/** 令牌踢人（闪电） */
export function IconKick(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M13 2.5L5 13.2h5.6L9.9 21.5 18 10.8h-5.5L13 2.5z" />
    </svg>
  );
}

/** 我的 */
export function IconUser(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <circle cx="12" cy="8.2" r="3.6" />
      <path d="M4.8 20c.9-3.4 3.8-5.4 7.2-5.4s6.3 2 7.2 5.4" />
    </svg>
  );
}

/** 侧栏收缩（面板 + 箭头） */
export function IconSidebar(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <rect x="3" y="4" width="18" height="16" rx="2.6" />
      <line x1="9.4" y1="4" x2="9.4" y2="20" />
    </svg>
  );
}

export function IconSun(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2.5v2.2M12 19.3v2.2M4.2 4.2l1.6 1.6M18.2 18.2l1.6 1.6M2.5 12h2.2M19.3 12h2.2M4.2 19.8l1.6-1.6M18.2 5.8l1.6-1.6" />
    </svg>
  );
}

export function IconMoon(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M20 13.5A8 8 0 019.4 4.2 8.2 8.2 0 1020 13.5z" />
    </svg>
  );
}

export function IconLogout(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M14.5 4.5H6.8A1.8 1.8 0 005 6.3v11.4a1.8 1.8 0 001.8 1.8h7.7" />
      <path d="M16 8.3L19.7 12 16 15.7M19.3 12H9.6" />
    </svg>
  );
}

export function IconShield(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M12 3l7 3v5.2c0 4.5-3 8-7 9.8-4-1.8-7-5.3-7-9.8V6l7-3z" />
    </svg>
  );
}

export function IconKey(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <circle cx="8" cy="12" r="3.4" />
      <path d="M11.4 12H21M17.5 12v3M20 12v2.4" />
    </svg>
  );
}

export function IconAlert(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7.5v5.2M12 16.4h.01" />
    </svg>
  );
}

export function IconCheckCircle(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <circle cx="12" cy="12" r="9" />
      <path d="M8.4 12.2l2.4 2.4 4.8-5" />
    </svg>
  );
}

export function IconInbox(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M3 13.5h4.2l1.6 2.6h6.4l1.6-2.6H21" />
      <path d="M5.4 5.5h13.2l2.4 8v3.5a1.8 1.8 0 01-1.8 1.8H4.8A1.8 1.8 0 013 17V13.5l2.4-8z" />
    </svg>
  );
}

export function IconRefresh(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M20.5 12a8.5 8.5 0 11-2.6-6.1" />
      <path d="M20.5 3.6v5.1h-5.1" />
    </svg>
  );
}

export function IconEye(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M2.5 12S6 5.8 12 5.8 21.5 12 21.5 12 18 18.2 12 18.2 2.5 12 2.5 12z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

export function IconEyeOff(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M9.9 5.9A9.6 9.6 0 0112 5.8c6 0 9.5 6.2 9.5 6.2a17 17 0 01-2.6 3.3M6.5 7.4A17 17 0 002.5 12S6 18.2 12 18.2c1.4 0 2.7-.3 3.8-.8" />
      <path d="M10 10a2.8 2.8 0 004 4M3 3l18 18" />
    </svg>
  );
}

export function IconSearch(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <circle cx="11" cy="11" r="6.2" />
      <path d="M15.6 15.6L20 20" />
    </svg>
  );
}

/** 用户账号（多用户） */
export function IconUsers(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <circle cx="9" cy="8.4" r="3.2" />
      <path d="M3.6 19.4c.7-2.9 2.9-4.6 5.4-4.6s4.7 1.7 5.4 4.6" />
      <path d="M16 5.6a3 3 0 010 5.8M17.4 19.4c-.2-1.5-.6-2.7-1.3-3.7 2 .1 3.6 1.6 4.1 3.7" />
    </svg>
  );
}

/** 登录锁定（挂锁） */
export function IconLock(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <rect x="4.5" y="10" width="15" height="10" rx="2" />
      <path d="M8 10V7.4a4 4 0 018 0V10M12 14v2.4" />
    </svg>
  );
}

/** 准入授权（链环） */
export function IconLink(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M9.6 14.4l4.8-4.8" />
      <path d="M8.4 11.2l-1.6 1.6a3.4 3.4 0 004.8 4.8l1.6-1.6" />
      <path d="M15.6 12.8l1.6-1.6a3.4 3.4 0 00-4.8-4.8l-1.6 1.6" />
    </svg>
  );
}

/** 在线会话（显示器） */
export function IconMonitor(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <rect x="3" y="4.4" width="18" height="12" rx="2" />
      <path d="M8.4 20h7.2M12 16.4V20" />
    </svg>
  );
}

/** 审计日志（列表） */
export function IconList(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M8 6.5h12M8 12h12M8 17.5h12" />
      <path d="M4 6.5h.01M4 12h.01M4 17.5h.01" />
    </svg>
  );
}

/** 系统设置（滑杆） */
export function IconSliders(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M6 3.6v6.4M6 14.4v6M12 3.6v3.2M12 10.8v9.6M18 3.6v10M18 17.6v2.8" />
      <circle cx="6" cy="12" r="2" />
      <circle cx="12" cy="9" r="2" />
      <circle cx="18" cy="15.6" r="2" />
    </svg>
  );
}

/** 新增 */
export function IconPlus(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M12 5.5v13M5.5 12h13" />
    </svg>
  );
}

/** 删除 / 移除 */
export function IconTrash(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M4.5 6.5h15M9.5 6.5V4.8h5v1.7M6.4 6.5l.9 12.2a1.6 1.6 0 001.6 1.5h6.2a1.6 1.6 0 001.6-1.5l.9-12.2" />
      <path d="M10.2 10.4v6M13.8 10.4v6" />
    </svg>
  );
}

/** 禁用（禁止） */
export function IconBan(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <circle cx="12" cy="12" r="8.4" />
      <path d="M6.1 6.1l11.8 11.8" />
    </svg>
  );
}

/** 电源（启用/停用） */
export function IconPower(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M12 3.6v8" />
      <path d="M17.2 6.6a7 7 0 11-10.4 0" />
    </svg>
  );
}

/** 组织架构（层级树） */
export function IconTree(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <rect x="9" y="3" width="6" height="4.4" rx="1.2" />
      <rect x="2.5" y="16.6" width="6" height="4.4" rx="1.2" />
      <rect x="15.5" y="16.6" width="6" height="4.4" rx="1.2" />
      <path d="M12 7.4V13M5.5 13h13M5.5 13v3.6M18.5 13v3.6" />
    </svg>
  );
}

/** 组织授权（盾牌 + 授予） */
export function IconOrgGrant(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M12 3l7 3v5c0 4.3-2.9 7.7-7 9.4-4.1-1.7-7-5.1-7-9.4V6l7-3z" />
      <path d="M9.4 12h5.2M12 9.4v5.2" />
    </svg>
  );
}

/** 展开/折叠箭头（默认指向右，展开时旋转 90°） */
export function IconChevron(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M9.5 6.5l6 5.5-6 5.5" />
    </svg>
  );
}

/** 关闭（×） */
export function IconX(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />
    </svg>
  );
}

/** 编辑（铅笔） */
export function IconEdit(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M4.5 19.5h4l10-10a2 2 0 00-2.8-2.8l-10 10v2.8z" />
      <path d="M14.5 8.5l1.8 1.8" />
    </svg>
  );
}

/** 同步（双向箭头） */
export function IconSync(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M4.5 9.4A7.6 7.6 0 0117 6.6l2.4 2.4" />
      <path d="M19.5 5.2v3.8h-3.8" />
      <path d="M19.5 14.6A7.6 7.6 0 017 17.4l-2.4-2.4" />
      <path d="M4.5 18.8v-3.8h3.8" />
    </svg>
  );
}

/** 保存（磁盘） */
export function IconSave(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M5.4 4.5h10l3.1 3.1v11.9H5.4z" />
      <path d="M8.4 4.5v5h6.2v-5M8.6 19.5v-5.4h6.8v5.4" />
    </svg>
  );
}

