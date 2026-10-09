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
