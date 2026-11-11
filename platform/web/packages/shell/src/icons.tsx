// 外壳自用的内联 SVG 图标（侧栏开关 / 主题 / 登出 / 盾牌 / chevron / 关闭）。
//
// 为什么不从各 ap 的 `Icons.tsx` 引：那三个文件是**业务图标集**（375/348/235 行，
// 各 ap 只用到自己那部分），外壳若依赖其中一个，共享包就挂上了某个 ap 的源码。
// 外壳只需要 7 个结构图标，路径逐字抄自各 ap 同名组件（视觉零差异），
// 代价是 7 段 path 重复——比让平台包依赖业务包便宜得多。
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

/** 侧栏开关 */
export function IconSidebar(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <rect x="3" y="4" width="18" height="16" rx="2.6" />
      <line x1="9.4" y1="4" x2="9.4" y2="20" />
    </svg>
  );
}

/** 暗色主题（月亮）*/
export function IconMoon(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M20 13.5A8 8 0 019.4 4.2 8.2 8.2 0 1020 13.5z" />
    </svg>
  );
}

/** 浅色主题（太阳）*/
export function IconSun(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2.5v2.2M12 19.3v2.2M4.2 4.2l1.6 1.6M18.2 18.2l1.6 1.6M2.5 12h2.2M19.3 12h2.2M4.2 19.8l1.6-1.6M18.2 5.8l1.6-1.6" />
    </svg>
  );
}

/** 登出 */
export function IconLogout(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M14.5 4.5H6.8A1.8 1.8 0 005 6.3v11.4a1.8 1.8 0 001.8 1.8h7.7" />
      <path d="M16 8.3L19.7 12 16 15.7M19.3 12H9.6" />
    </svg>
  );
}

/** 盾牌（侧栏底部安全提示）*/
export function IconShield(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M12 3l7 3v5.2c0 4.5-3 8-7 9.8-4-1.8-7-5.3-7-9.8V6l7-3z" />
    </svg>
  );
}

/** 箭头（分组折叠 / 页签滚动）*/
export function IconChevron(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M9.5 6.5l6 5.5-6 5.5" />
    </svg>
  );
}

/** 关闭（页签徽标）*/
export function IconX(p: IconProps) {
  return (
    <svg {...base} {...p}>
      <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />
    </svg>
  );
}
