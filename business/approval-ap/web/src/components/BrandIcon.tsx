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
 * CIM Approval 品牌图形标——单据与勾。
 *
 * <p>审批平台的业务单元是「一张单据走完一条流程」：折角单据是待审对象，
 * 打通到右下的大对勾是结论本身——图形重心落在勾上，审批的产出就是它。</p>
 */
export default function BrandIcon(p: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...p}>
      <path d="M14 3.5H7.4A1.9 1.9 0 005.5 5.4v13.2a1.9 1.9 0 001.9 1.9h9.2a1.9 1.9 0 001.9-1.9V8Z" />
      <path d="M14 3.5V8h4.5" />
      <path d="M8.9 14.7l2.2 2.2 4-4.4" />
    </svg>
  );
}
