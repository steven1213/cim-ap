// 整页状态页（403 / 404 等跨 ap 统一形态）。
//
// 背景：这类页面每个应用都写过一遍，形态漂得很远——同是 404，IAM 有图标有说明有返回首页，
// RMS 只有一行「404 / 页面不存在」，没有出口；approval 用字面量标题还把「返回」配了个盾牌图标
// （盾牌是权限语义，与 404 无关）。403 也一样（见下）。判定逻辑早就共用，漂的是呈现层。
//
// 所以这里只做一件事：**一个布局，两个语义预设**。
// - `StatePage`：Panel + 大号 EmptyState + 可插槽的图标与出口动作；
// - `ForbiddenState` / `NotFoundState`：只差默认图标，调用处按语义取名。
//
// 文案仍由应用注入（`@cim/ui` 不碰文案，见 `locale.tsx` 的铁律）；出口动作由调用方以
// children 传入——`@cim/ui` 不依赖 react-router，`Link` 由应用自己给。
import type { ReactNode } from 'react';

import { IconBan, IconSearch } from '../icons';
import { EmptyState, Panel } from './Page';

export interface StatePageProps {
  /** 标题文案键（如「页面不存在」）。 */
  titleKey?: string;
  /** 直接给标题节点（与 `titleKey` 二选一；服务端返回的原始文案走这里）。 */
  title?: ReactNode;
  /** 说明文案键（为什么会被拦 / 这个地址是什么情况）。 */
  hintKey?: string;
  /** 直接给说明节点。 */
  hint?: ReactNode;
  /** 顶部图标，缺省由各预设给定。 */
  icon?: ReactNode;
  /** 出口动作（返回首页 / 切换账号…）。不给则只显示说明。 */
  children?: ReactNode;
}

/** 状态页骨架：不带语义，不带默认图标。 */
export function StatePage({ titleKey, title, hintKey, hint, icon, children }: StatePageProps) {
  return (
    <Panel>
      <EmptyState large icon={icon} titleKey={titleKey} title={title} hintKey={hintKey} hint={hint}>
        {children}
      </EmptyState>
    </Panel>
  );
}

export type ForbiddenStateProps = StatePageProps;

/** 403：无权限。默认图标为禁止符号。 */
export function ForbiddenState({ icon, ...rest }: ForbiddenStateProps) {
  return <StatePage {...rest} icon={icon ?? <IconBan width={28} height={28} />} />;
}

export type NotFoundStateProps = StatePageProps;

/** 404：地址不可达。默认图标为放大镜（「找过了，没有」），不是禁止符号。 */
export function NotFoundState({ icon, ...rest }: NotFoundStateProps) {
  return <StatePage {...rest} icon={icon ?? <IconSearch width={28} height={28} />} />;
}
