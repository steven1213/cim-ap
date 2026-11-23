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
 * CIM MDS 品牌图形标——主数据分层。
 *
 * <p>三层堆叠的菱形切面：主数据服务的核心语义是「定义向下、执行向上」——
 * Site→Fab→Area→Bay→Stage 五层位置拓扑与产品/技术/流程定义层层向下奠基，
 * 顶层的实心切面代表「唯一定义源」。</p>
 */
export default function BrandIcon(p: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...p}>
      <path d="M12 3 21 7.7 12 12.4 3 7.7Z" fill="currentColor" strokeWidth={1.6} />
      <path d="M3 12.2 12 16.9l9-4.7" />
      <path d="M3 16.6 12 21.3l9-4.7" />
    </svg>
  );
}
