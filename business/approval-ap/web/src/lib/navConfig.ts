import type { ComponentType, SVGProps } from 'react';
import {
  IconDashboard,
  IconFlow,
  IconInbox,
  IconCheck,
  IconOutbox,
  IconWebhook,
  IconShield,
  IconPencil,
} from '@/components/Icons';

export interface NavItem {
  to: string;
  i18n: string;
  Icon: ComponentType<SVGProps<SVGSVGElement> & { width?: number; height?: number }>;
}

export interface NavGroup {
  /** 分组标题 i18n key（侧边栏小节标题，折叠时隐藏） */
  i18n: string;
  items: NavItem[];
}

/**
 * 分级导航：工作台 / 审批中心 / 流程配置 / 合规治理。
 * 按用户动线组织——办事的人看「审批中心」，配置系统的人看「流程配置」，审计看「合规治理」。
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    i18n: 'nav.group.portal',
    items: [{ to: '/', i18n: 'nav.dashboard', Icon: IconDashboard }],
  },
  {
    i18n: 'nav.group.approval',
    items: [
      { to: '/todo', i18n: 'nav.todo', Icon: IconInbox },
      { to: '/done', i18n: 'nav.done', Icon: IconCheck },
      { to: '/initiated', i18n: 'nav.initiated', Icon: IconOutbox },
    ],
  },
  {
    i18n: 'nav.group.process',
    items: [
      { to: '/definitions', i18n: 'nav.definitions', Icon: IconFlow },
      { to: '/designer', i18n: 'nav.designer', Icon: IconPencil },
      { to: '/callbacks', i18n: 'nav.callbacks', Icon: IconWebhook },
    ],
  },
  {
    i18n: 'nav.group.governance',
    items: [{ to: '/audit', i18n: 'nav.audit', Icon: IconShield }],
  },
];

/** 扁平导航（TabBar 标题解析 / 路由匹配用）。 */
export const NAV_ITEMS: NavItem[] = NAV_GROUPS.flatMap((g) => g.items);

export const NAV_FLAT: NavItem[] = NAV_ITEMS;
