// RMS 侧栏导航配置（前端静态），供外壳 Layout 与多页签 TabBar 共用。
//
// <p>说明：RMS 当前采用<b>前端静态菜单</b>（页面集合固定，不依赖后端菜单下发），
// 后续若接入平台 `/sys/me/menus`，只需把本文件的 GROUPS/NAV_FLAT 替换为 menuStore
// 的派生结果，Layout/TabBar 的消费面无需改动。</p>
//
// <p>⚠️ 后端 {@code RmsConsoleCatalog} 里那份菜单树是<b>平面示意</b>（4 组：recipe / device /
// governance / advanced），与本文件的 3 组 7 模块<b>并不等价</b>。要接后端驱动菜单，
// 必须先把两边对齐（哪边为准：以本文件的信息架构为准），否则切过去菜单会整体变形。</p>
//
// <p><b>层级</b>：分组标题（一级）→ module 节点（二级，只有 children 没有 to）→ 页面（三级）。
// 分三级不是为了好看：治理与合规 6 项、基础数据 7 项平铺时，一眼看不出「哪些是日常动作、
// 哪些是配置」。二级节点按动作语义收拢（放行 / 审计 / 规格 / 授权），每组 2–3 项。
// 概览这类单页不套二级，直接挂在无标题分组下。</p>
import {
  IconGauge,
  IconSliders,
  IconList,
  IconApps,
  IconTree,
  IconMonitor,
  IconAlert,
  IconShield,
  IconBan,
  IconLink,
  IconCheckCircle,
  IconKey,
  IconRefresh,
  IconInbox,
  IconSync,
  IconFlask,
  IconFile,
} from '@/components/Icons';

/** 图标组件签名：接受尺寸，渲染内联 SVG。 */
export type IconCmp = (p: { width?: number; height?: number }) => JSX.Element;

/**
 * 单个导航项。
 *
 * <p>`to` 与 `children` 二选一：有 `children` 的是二级模块节点（点击只展开，不导航），
 * 叶子才有 `to`。两者都缺是配置错误——`NAV_FLAT` 会把它丢掉，侧栏也不渲染。</p>
 */
export interface NavItem {
  to?: string;
  i18n: string;
  Icon: IconCmp;
  perm?: string;
  children?: NavItem[];
}

/** 一个侧栏分组：目录文案码（空串表示顶级菜单，不显示分组标题）+ 条目。 */
export interface NavGroup {
  key: string;
  capI18n: string;
  items: NavItem[];
}

/** 侧栏分组定义（顺序即渲染顺序；按使用频率分层——高频在上，低频基础数据收尾）。 */
export const GROUPS: NavGroup[] = [
  {
    key: 'overview',
    capI18n: '',
    items: [{ to: '/', i18n: 'rms.nav.dashboard', Icon: IconGauge }],
  },
  {
    // 高频日常：占日常操作 80%（任务中心直达 + 枢纽页）
    key: 'daily',
    capI18n: 'rms.nav.group.daily',
    items: [
      {
        // 「配方」与「设备侧动作」是两条动线：一条管配方本身，一条管配方在设备上的落地与签核
        i18n: 'rms.nav.module.recipe',
        Icon: IconSliders,
        children: [
          { to: '/recipes', i18n: 'rms.nav.recipes', Icon: IconSliders, perm: 'rms:recipe:view' },
          { to: '/device-recipes', i18n: 'rms.nav.deviceRecipes', Icon: IconTree, perm: 'rms:recipe:view' },
        ],
      },
      {
        i18n: 'rms.nav.module.deviceOps',
        Icon: IconMonitor,
        children: [
          { to: '/devices', i18n: 'rms.nav.devices', Icon: IconMonitor, perm: 'rms:device:view' },
          { to: '/signoff', i18n: 'rms.nav.signoff', Icon: IconShield, perm: 'rms:signoff:view' },
          { to: '/compare', i18n: 'rms.nav.compare', Icon: IconRefresh, perm: 'rms:compare:view' },
        ],
      },
    ],
  },
  {
    // 治理与合规：审批域配置与安全管控（次高频）
    key: 'governance',
    capI18n: 'rms.nav.group.governance',
    items: [
      {
        // 「谁能动、动到什么程度」：资质、金批、旁路放行——都是对生产放行的管控
        i18n: 'rms.nav.module.release',
        Icon: IconShield,
        children: [
          { to: '/qualifications', i18n: 'rms.nav.qualifications', Icon: IconLink, perm: 'rms:qualification:view' },
          { to: '/golden', i18n: 'rms.nav.golden', Icon: IconCheckCircle, perm: 'rms:golden:view' },
          { to: '/bypasses', i18n: 'rms.nav.bypasses', Icon: IconBan, perm: 'rms:bypass:view' },
        ],
      },
      {
        // 事后可追溯 + 对外协同：审计、集成、试验
        i18n: 'rms.nav.module.observe',
        Icon: IconInbox,
        children: [
          { to: '/audit', i18n: 'rms.nav.audit', Icon: IconInbox, perm: 'rms:audit:view' },
          { to: '/integration', i18n: 'rms.nav.integration', Icon: IconSync, perm: 'rms:integration:view' },
          { to: '/experiments', i18n: 'rms.nav.experiments', Icon: IconFlask, perm: 'rms:experiment:view' },
        ],
      },
    ],
  },
  {
    // 基础数据：低频维护（建好之后很少动），归拢一处降低视觉噪音
    key: 'master',
    capI18n: 'rms.nav.group.master',
    items: [
      {
        i18n: 'rms.nav.module.deviceModel',
        Icon: IconApps,
        children: [
          { to: '/device-types', i18n: 'rms.nav.deviceTypes', Icon: IconApps, perm: 'rms:device-type:view' },
          { to: '/device-areas', i18n: 'rms.nav.deviceAreas', Icon: IconTree, perm: 'rms:device-area:view' },
        ],
      },
      {
        i18n: 'rms.nav.module.spec',
        Icon: IconList,
        children: [
          { to: '/specs', i18n: 'rms.nav.specs', Icon: IconList, perm: 'rms:spec:view' },
          { to: '/spec-templates', i18n: 'rms.nav.specTemplates', Icon: IconSliders, perm: 'rms:spec:view' },
          { to: '/body-templates', i18n: 'rms.nav.bodyTemplates', Icon: IconFile, perm: 'rms:btemplate:view' },
        ],
      },
      {
        i18n: 'rms.nav.module.auth',
        Icon: IconKey,
        children: [
          { to: '/device-auth', i18n: 'rms.nav.deviceAuth', Icon: IconKey, perm: 'rms:device-auth:view' },
          { to: '/ecsv', i18n: 'rms.nav.ecsv', Icon: IconAlert, perm: 'rms:ecsv:view' },
        ],
      },
    ],
  },
];

/** 递归扁平化：只收有 `to` 的叶子（二级模块节点没有可路由的页面）。 */
function flatten(items: NavItem[]): NavItem[] {
  return items.flatMap((it) => (it.children?.length ? flatten(it.children) : [it]));
}

/** 扁平化导航（路径 → 文案码），供多页签标题解析。 */
export const NAV_FLAT: ReadonlyArray<{ path: string; i18n: string }> = GROUPS.flatMap((g) =>
  flatten(g.items)
    .filter((it): it is NavItem & { to: string } => !!it.to)
    .map((it) => ({ path: it.to, i18n: it.i18n })),
);
