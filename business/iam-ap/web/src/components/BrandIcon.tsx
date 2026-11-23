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
 * CIM IAM 品牌图形标——盾牌与钥匙孔。
 *
 * <p>统一身份认证与跨业务准入：盾牌轮廓是「准入守门」，实心钥匙孔是
 * 「一个身份开所有门」——盾而不锁死，孔即入口。</p>
 */
export default function BrandIcon(p: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...p}>
      <path d="M12 3 20 6.1v5.2c0 4.6-3.1 8.1-8 9.7-4.9-1.6-8-5.1-8-9.7V6.1Z" />
      <circle cx="12" cy="10.4" r="2.1" fill="currentColor" stroke="none" />
      <path d="M12 12.5v2.7" />
    </svg>
  );
}
