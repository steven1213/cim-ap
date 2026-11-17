import {
  IconApps,
  IconCheckCircle,
  IconGauge,
  IconInbox,
  IconKey,
  IconLink,
  IconList,
  IconMonitor,
  IconOrgGrant,
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
    // 设备管理：搬运设备 + 设备共用一张表的过滤视图；类型/能力是「类」与「能力字典」（grp.equipment 同构）
    i18n: 'mds.nav.group.equipment',
    items: [
      { to: '/transport-equipments', i18n: 'mds.nav.transportEquipments', Icon: IconLink },
      { to: '/equipments', i18n: 'mds.nav.equipments', Icon: IconMonitor },
      { to: '/equipment-types', i18n: 'mds.nav.equipmentTypes', Icon: IconOrgGrant },
      { to: '/capabilities', i18n: 'mds.nav.capabilities', Icon: IconCheckCircle },
      { to: '/banks', i18n: 'mds.nav.banks', Icon: IconInbox },
    ],
  },
  {
    // 产品管理：Technology + Product + Flow（Route 口径：XML 画布拖拽，MdsConsoleCatalog grp.product 同构）
    i18n: 'mds.nav.group.product',
    items: [
      { to: '/technologies', i18n: 'mds.nav.technology', Icon: IconSliders },
      { to: '/products', i18n: 'mds.nav.products', Icon: IconApps },
      { to: '/flows', i18n: 'mds.nav.flows', Icon: IconTree },
      // V15 独立规则层：锚定流程版本族，evaluate 契约供 MES/MCS（grp.product 同构）
      { to: '/flow-rules', i18n: 'mds.nav.flowRules', Icon: IconList },
    ],
  },
  {
    // 系统管理：平台级参照数据（数据字典）+ 状态模型（词表转换规则）+ 编码规则（Key Generation）
    i18n: 'mds.nav.group.system',
    items: [
      { to: '/dicts', i18n: 'mds.nav.dicts', Icon: IconList },
      { to: '/state-models', i18n: 'mds.nav.stateModels', Icon: IconCheckCircle },
      { to: '/naming-rules', i18n: 'mds.nav.namingRules', Icon: IconKey },
    ],
  },
];

/** 扁平导航（页签标题解析 / 路由匹配用）。递归：以后给某个分组加二级模块，这里不用改。 */
export const NAV_ITEMS: NavItem[] = NAV_GROUPS.flatMap((g) => flattenNav(g.items));

function flattenNav(items: NavItem[]): NavItem[] {
  return items.flatMap((it) => (it.children?.length ? flattenNav(it.children) : it.to ? [it] : []));
}
