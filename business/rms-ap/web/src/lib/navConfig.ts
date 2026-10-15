// RMS 侧栏导航配置（前端静态），供外壳 Layout 与多页签 TabBar 共用。
//
// <p>说明：RMS 当前采用<b>前端静态菜单</b>（页面集合固定，不依赖后端菜单下发）；
// 后端 {@code RmsConsoleCatalog} 已种入等价菜单树（供平台侧/未来后端驱动菜单复用），
// 后续若接入平台 `/sys/me/menus`，只需把本文件的 GROUPS/NAV_FLAT 替换为 menuStore
// 的派生结果，Layout/TabBar 的消费面无需改动。</p>
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

/** 单个导航项：路由 + 文案码 + 图标 + 可选权限码（无权限则不渲染）。 */
export interface NavItem {
  to: string;
  i18n: string;
  Icon: IconCmp;
  perm?: string;
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
      { to: '/recipes', i18n: 'rms.nav.recipes', Icon: IconSliders, perm: 'rms:recipe:view' },
      { to: '/device-recipes', i18n: 'rms.nav.deviceRecipes', Icon: IconTree, perm: 'rms:recipe:view' },
      { to: '/devices', i18n: 'rms.nav.devices', Icon: IconMonitor, perm: 'rms:device:view' },
      { to: '/signoff', i18n: 'rms.nav.signoff', Icon: IconShield, perm: 'rms:signoff:view' },
      { to: '/compare', i18n: 'rms.nav.compare', Icon: IconRefresh, perm: 'rms:compare:view' },
    ],
  },
  {
    // 治理与合规：审批域配置与安全管控（次高频）
    key: 'governance',
    capI18n: 'rms.nav.group.governance',
    items: [
      { to: '/qualifications', i18n: 'rms.nav.qualifications', Icon: IconLink, perm: 'rms:qualification:view' },
      { to: '/golden', i18n: 'rms.nav.golden', Icon: IconCheckCircle, perm: 'rms:golden:view' },
      { to: '/bypasses', i18n: 'rms.nav.bypasses', Icon: IconBan, perm: 'rms:bypass:view' },
      { to: '/audit', i18n: 'rms.nav.audit', Icon: IconInbox, perm: 'rms:audit:view' },
      { to: '/integration', i18n: 'rms.nav.integration', Icon: IconSync, perm: 'rms:integration:view' },
      { to: '/experiments', i18n: 'rms.nav.experiments', Icon: IconFlask, perm: 'rms:experiment:view' },
    ],
  },
  {
    // 基础数据：低频维护（建好之后很少动），归拢一处降低视觉噪音
    key: 'master',
    capI18n: 'rms.nav.group.master',
    items: [
      { to: '/device-types', i18n: 'rms.nav.deviceTypes', Icon: IconApps, perm: 'rms:device-type:view' },
      { to: '/device-areas', i18n: 'rms.nav.deviceAreas', Icon: IconTree, perm: 'rms:device-area:view' },
      { to: '/specs', i18n: 'rms.nav.specs', Icon: IconList, perm: 'rms:spec:view' },
      { to: '/spec-templates', i18n: 'rms.nav.specTemplates', Icon: IconSliders, perm: 'rms:spec:view' },
      { to: '/body-templates', i18n: 'rms.nav.bodyTemplates', Icon: IconFile, perm: 'rms:btemplate:view' },
      { to: '/ecsv', i18n: 'rms.nav.ecsv', Icon: IconAlert, perm: 'rms:ecsv:view' },
      { to: '/device-auth', i18n: 'rms.nav.deviceAuth', Icon: IconKey, perm: 'rms:device-auth:view' },
    ],
  },
];

/** 扁平化导航（路径 → 文案码），供多页签标题解析。 */
export const NAV_FLAT: ReadonlyArray<{ path: string; i18n: string }> = GROUPS.flatMap((g) =>
  g.items.map((it) => ({ path: it.to, i18n: it.i18n })),
);
