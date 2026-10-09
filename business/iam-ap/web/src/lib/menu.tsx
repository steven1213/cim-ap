import type { ReactNode } from 'react';
import {
  IconApps,
  IconGauge,
  IconLink,
  IconList,
  IconLock,
  IconMonitor,
  IconShield,
  IconSliders,
  IconUser,
  IconUsers,
} from '@/components/Icons';

/**
 * 控制台菜单注册表（信息架构的唯一来源）。
 *
 * <p>按「用户使用习惯」分组：概览 → 身份管理 → 接入管理 → 安全与会话 → 我的。
 * 每个条目声明 {@code admin} 是否需要 IAM 管理面权限，侧栏按当前用户权限过滤，
 * 不再在组件里硬编码固定的几项。</p>
 */
export interface MenuEntry {
  to: string;
  label: string;
  /** 侧栏副标题（展开态显示）。 */
  desc: string;
  icon: ReactNode;
  /** 面包屑/页头副标题。 */
  sub: string;
  end?: boolean;
  /** 仅 IAM 管理员可见。 */
  admin?: boolean;
}

export interface MenuGroup {
  /** 分组标题（空串表示不显示标题）。 */
  cap: string;
  items: MenuEntry[];
}

export const MENU: MenuGroup[] = [
  {
    cap: '',
    items: [
      { to: '/', label: '概览', desc: '平台态势总览', sub: '身份、接入与会话的整体态势', icon: <IconGauge width={18} height={18} />, end: true },
    ],
  },
  {
    cap: '身份管理',
    items: [
      { to: '/users', label: '用户账号', desc: '账号与状态', sub: '本地账号的创建、启停、重置口令与删除', icon: <IconUsers width={18} height={18} />, admin: true },
      { to: '/lockouts', label: '登录锁定', desc: '锁定与解锁', sub: '暴力破解防护触发的账号锁定，可手动解锁', icon: <IconLock width={18} height={18} />, admin: true },
    ],
  },
  {
    cap: '接入管理',
    items: [
      { to: '/apps', label: '应用注册', desc: '业务接入码', sub: '注册业务 ap 接入码，维护状态与排序', icon: <IconApps width={18} height={18} />, admin: true },
      { to: '/admissions', label: '准入授权', desc: '用户 × 应用', sub: '授予 / 撤销用户进入某应用的准入与角色组', icon: <IconLink width={18} height={18} />, admin: true },
      { to: '/roles', label: '角色组', desc: '角色分布', sub: '各接入码内实际使用的粗角色组与人数分布', icon: <IconShield width={18} height={18} />, admin: true },
    ],
  },
  {
    cap: '安全与会话',
    items: [
      { to: '/sessions', label: '在线会话', desc: '会话与下线', sub: '活跃会话列表与强制下线（bump 令牌版本）', icon: <IconMonitor width={18} height={18} />, admin: true },
      { to: '/audit', label: '审计日志', desc: '动作流水', sub: '登录、授权、改密等关键动作的追溯流水', icon: <IconList width={18} height={18} />, admin: true },
      { to: '/settings', label: '系统设置', desc: '策略与参数', sub: '认证源、令牌与锁定策略等运行参数（只读）', icon: <IconSliders width={18} height={18} />, admin: true },
    ],
  },
  {
    cap: '',
    items: [
      { to: '/profile', label: '我的', desc: '账户与口令', sub: '账户信息、我的准入与口令修改', icon: <IconUser width={18} height={18} /> },
    ],
  },
];

/** 扁平化菜单（路由与标题映射用）。 */
export const MENU_FLAT: MenuEntry[] = MENU.flatMap((g) => g.items);
