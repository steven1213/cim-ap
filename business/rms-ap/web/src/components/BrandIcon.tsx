import type { SVGProps } from 'react';

const base: SVGProps<SVGSVGElement> = {
  width: 18,
  height: 18,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
};

/**
 * CIM RMS 品牌图形标——配方参数滑杆。
 *
 * <p>配方管理的核心动作是「按 PPID/腔体实例化并调校参数」：三条滑轨对应
 * 参数族，错位排布的实心旋钮表示配方值的设定点——比「烧瓶/文档」更贴近
 * 半导体配方调参的日常。</p>
 */
export default function BrandIcon(p: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...p}>
      <line x1="3.5" y1="6" x2="20.5" y2="6" />
      <line x1="3.5" y1="12" x2="20.5" y2="12" />
      <line x1="3.5" y1="18" x2="20.5" y2="18" />
      <circle cx="15" cy="6" r="2.4" fill="currentColor" stroke="none" />
      <circle cx="8" cy="12" r="2.4" fill="currentColor" stroke="none" />
      <circle cx="17" cy="18" r="2.4" fill="currentColor" stroke="none" />
    </svg>
  );
}
