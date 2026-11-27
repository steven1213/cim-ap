import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

/**
 * 操作动线步骤索引（GuideBar），多链注册表。
 *
 * 各一级菜单若有「先后依赖」的配置动线，在这里登记一条链；
 * 侧栏菜单 order 与链一致（辅助页置尾，不进主链）。
 * 页面内 <GuideBar /> 无参调用，按当前路径自动匹配所在链。
 *
 * ⚠️ 同一路径可属多条链（如 /param-defs：产品链第 3 步 + 数采链第 1 步）。
 * 链上下文用查询参数 `?chain=<id>` 携带：引导条内点击始终带上当前链 id，
 * 无参数进入时取注册序第一条（产品链优先）——侧栏直达 = 原动线，EDC 页跳转 = 数采动线。
 *
 * 产品管理 7 步：工艺 → 产品 → 参数定义 → 工序 → 流程 → 节点参数 → 产品绑定；
 * 设备管理 5 步：机型模板 → 机台 → 机台组 → 机台能力 → 存储区；
 * 班次管理 4 步：班次定义 → 排班 → 换班 → 交接班日志；
 * 数采管理 5 步：参数定义(产品管理组) → 参数集 → 报告模板 → 设备接口映射 → EDC 方案；
 * 配方链 2 步：工序（可变参数）→ 配方解算。
 */
const CHAINS: readonly { id: string; steps: readonly { path: string | readonly string[]; key: string }[] }[] = [
  {
    id: 'product',
    steps: [
      { path: '/technologies', key: 'technology' },
      { path: '/products', key: 'products' },
      { path: '/param-defs', key: 'paramDefs' },
      { path: '/operations', key: 'operations' },
      { path: '/flows', key: 'flows' },
      { path: '/step-params', key: 'stepParams' },
      { path: '/product-bindings', key: 'productFlows' },
    ],
  },
  {
    id: 'equipment',
    steps: [
      { path: '/equipment-classes', key: 'equipmentClasses' },
      { path: ['/equipments', '/transport-equipments'], key: 'equipments' },
      { path: '/tool-groups', key: 'toolGroups' },
      { path: '/capabilities', key: 'capabilities' },
      { path: '/banks', key: 'banks' },
    ],
  },
  {
    id: 'shift',
    steps: [
      { path: '/shift-defs', key: 'shiftDefs' },
      { path: '/schedule', key: 'schedule' },
      { path: '/shift-swaps', key: 'shiftSwaps' },
      { path: '/shift-logs', key: 'shiftLogs' },
    ],
  },
  {
    id: 'edc',
    steps: [
      { path: '/param-defs', key: 'paramDefs' },
      { path: '/edc/param-sets', key: 'edcParamSets' },
      { path: '/edc/report-tpls', key: 'edcReportTpls' },
      { path: '/edc/equip-maps', key: 'edcEquipMaps' },
      { path: '/edc/plans', key: 'edcPlans' },
    ],
  },
  {
    id: 'recipe',
    steps: [
      { path: '/operations', key: 'operations' },
      { path: '/recipe-resolve', key: 'recipeResolve' },
    ],
  },
];

export default function GuideBar() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  /** 步骤命中判定：path 支持 string 或别名数组（同组件多视图薄 wrapper，如搬运设备=机台视图）。 */
  const hit = (s: { path: string | readonly string[] }) =>
    typeof s.path === 'string' ? location.pathname === s.path : s.path.includes(location.pathname);
  // 命中的所有链里优先取 ?chain= 指定的链；否则取注册序第一条（缺省动线）。
  const candidates = CHAINS.filter((c) => c.steps.some(hit));
  if (!candidates.length) return null;
  const chainHint = new URLSearchParams(location.search).get('chain');
  const chain = candidates.find((c) => c.id === chainHint) ?? candidates[0];
  const idx = chain.steps.findIndex(hit);
  const firstPath = (s: { path: string | readonly string[] }) =>
    typeof s.path === 'string' ? s.path : s.path[0];
  return (
    <div className="guide-bar">
      {chain.steps.map((s, i) => (
        <button
          key={s.key}
          type="button"
          className={
            'guide-step'
            + (i === idx ? ' is-current' : '')
            + (idx >= 0 && i < idx ? ' is-done' : '')
          }
          title={t(`mds.guide.hint.${s.key}`)}
          onClick={() => i !== idx && navigate(`${firstPath(s)}?chain=${chain.id}`)}
        >
          <span className="guide-no">{i + 1}</span>
          <span className="guide-label">{t(`mds.guide.step.${s.key}`)}</span>
        </button>
      ))}
    </div>
  );
}
