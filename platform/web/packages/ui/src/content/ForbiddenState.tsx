// 403 状态页（跨 ap 统一形态）。
//
// 此前 IAM 与 RMS 各写一份，形态差得远：IAM 是「盾形图标 + 标题 + 说明 + 返回首页按钮」，
// RMS 只有一行「403 / 无访问权限」，没有图标也没有出口——同一个语义在相邻两个应用里
// 长得不像同一个产品。判定逻辑（`RequirePermission`）早在 `@cim/shell` 共用了，
// 呈现层却在各自漂移，所以这里把「长什么样」也收上来。
//
// 文案仍由应用注入（`@cim/ui` 不碰文案，见 `locale.tsx` 的铁律）；出口动作由调用方以
// children 传入——`@cim/ui` 不依赖 react-router，`Link` 由应用自己给。
import type { ReactNode } from 'react';

import { IconBan } from '../icons';
import { EmptyState, Panel } from './Page';

export interface ForbiddenStateProps {
  /** 标题文案键（如「无访问权限」）。 */
  titleKey?: string;
  /** 直接给标题节点（与 `titleKey` 二选一）。 */
  title?: ReactNode;
  /** 说明文案键（为什么被拦、该找谁）。 */
  hintKey?: string;
  /** 直接给说明节点。 */
  hint?: ReactNode;
  /** 顶部图标，缺省为禁止符号。 */
  icon?: ReactNode;
  /** 出口动作（返回首页 / 切换账号…）。不给则只显示说明。 */
  children?: ReactNode;
}

export function ForbiddenState({
  titleKey,
  title,
  hintKey,
  hint,
  icon,
  children,
}: ForbiddenStateProps) {
  return (
    <Panel>
      <EmptyState
        large
        icon={icon ?? <IconBan width={28} height={28} />}
        titleKey={titleKey}
        title={title}
        hintKey={hintKey}
        hint={hint}
      >
        {children}
      </EmptyState>
    </Panel>
  );
}
