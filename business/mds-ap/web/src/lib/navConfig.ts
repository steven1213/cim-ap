import {
  IconApps,
  IconGauge,
  IconList,
  IconSliders,
  IconTree,
} from '@cim/ui';
import type { ShellIconCmp } from '@cim/shell';

export interface NavItem {
  /** 路由。二级模块节点（有 `children`）不传——它没有自己的页面。 */
  to?: string;
  i18n: string;
  Icon: ShellIconCmp;
  /** 子菜单：有值即渲染成可展开的模块节点（三级菜单的中间层）。 */
  children?: NavItem[];
}

export interface NavGroup {
  /** 分组标题 i18n key（侧边栏小节标题，折叠时隐藏）。 */
  i18n: string;
  items: NavItem[];
}

/**
 * 分级导航：概览 / 场地主数据。
 *
 * <p>MDS 的业务是按「主数据域」长的：场地（五层拓扑一页维护）→ 设备 → 产品 → 工艺路线 → 载具…
 * 每开一个域，在这里加一个分组，同时在 {@code MdsConsoleCatalog.menus()} 里加同批菜单
 * （后端那份用于授权与动态菜单，两份的 i18n 键必须一致）。</p>
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    i18n: 'mds.nav.dashboard',
    items: [{ to: '/', i18n: 'mds.nav.dashboard', Icon: IconGauge }],
  },
  {
    i18n: 'mds.nav.group.site',
    items: [
      // 2026-11-13 收敛：sites/fabs/areas 三页合一页「位置拓扑」（location-design.md §8.3）
      { to: '/topology', i18n: 'mds.nav.topology', Icon: IconTree },
    ],
  },
  {
    // 产品管理：Technology + Product（Route 预留，MdsConsoleCatalog grp.product 同构）
    i18n: 'mds.nav.group.product',
    items: [
      { to: '/technologies', i18n: 'mds.nav.technology', Icon: IconSliders },
      { to: '/products', i18n: 'mds.nav.products', Icon: IconApps },
    ],
  },
  {
    // 系统管理：平台级参照数据（数据字典等，grp.system 同构）
    i18n: 'mds.nav.group.system',
    items: [{ to: '/dicts', i18n: 'mds.nav.dicts', Icon: IconList }],
  },
];

/** 扁平导航（页签标题解析 / 路由匹配用）。递归：以后给某个分组加二级模块，这里不用改。 */
export const NAV_ITEMS: NavItem[] = NAV_GROUPS.flatMap((g) => flattenNav(g.items));

function flattenNav(items: NavItem[]): NavItem[] {
  return items.flatMap((it) => (it.children?.length ? flattenNav(it.children) : it.to ? [it] : []));
}
