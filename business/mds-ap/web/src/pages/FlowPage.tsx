import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Graph, type Edge as X6Edge, type KeyValue, type Node as X6Node } from '@antv/x6';
// X6 本体与插件样式必须引入：Selection 的 widget 容器缺 CSS 时无 pointer-events:none，
// 会以裸 div 铺满画布拦截全部真实鼠标事件（节点点不选/双击无效/「全是选中框」怪象的根因）
import '@antv/x6/es/index.css';
import '@antv/x6-plugin-selection/es/index.css';
import '@antv/x6-plugin-snapline/es/index.css';
import { Selection } from '@antv/x6-plugin-selection';
import { Keyboard } from '@antv/x6-plugin-keyboard';
import { Snapline } from '@antv/x6-plugin-snapline';
import {
  Can,
  CimButton,
  CimModal,
  DataTable,
  FormGrid,
  Page,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  type FormOption,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useHasPermission } from '@/lib/usePermission';
import type { FlowDetail, FlowView } from '@/types';

/**
 * 制造流程（Flow）——产品管理域 Route 口径页面：左列表右画布。
 *
 * <p><b>XML 单一事实源</b>：画布渲染与修改都以 flow.flowXml 为源（打开时前端
 * DOMParser 解析成 X6 节点/边，保存时整份序列化回传）；后端每次保存后重新解析
 * 重建解析产物表（步骤按 XML 原顺序）。</p>
 *
 * <p><b>画布引擎 = AntV X6 v2</b>（2026-11-16 由 React Flow 迁移）：连线自带
 * 箭头；hover 连线出 vertices/segments 工具（拖拐点调走线、单击加拐点、双击删
 * 拐点、拖 segments 调段），source/target-arrowhead 可拖拽改端点，button-remove
 * 删边。双击节点/连线编辑属性；连线语义按起点类型自动给（DECISION → PASS，
 * 其余 → MAIN）。</p>
 */

// ================================================================ 画布常量与工具

/** 业务节点类型（组件面板拖拽用；START/END 为结构性节点，SUBFLOW 为引用节点，SPLIT/MERGE 为分批/合批结构节点）。 */
const STEP_TYPES = ['START', 'PROCESS', 'INSPECT', 'REWORK', 'DECISION', 'SUBFLOW', 'SPLIT', 'MERGE', 'END'] as const;

/** 节点类型色（与边语义四色一致：主线蓝/PASS 绿/FAIL 琥珀/REWORK 红）。 */
const TYPE_COLORS: Record<string, string> = {
  START: '#2563eb',
  PROCESS: '#2563eb',
  INSPECT: '#7c3aed',
  REWORK: '#dc2626',
  DECISION: '#d97706',
  SUBFLOW: '#0891b2',
  SPLIT: '#db2777',
  MERGE: '#4f46e5',
  END: '#475569',
};

/** 批处理模式词表（V13；空 = 默认串行）。 */
const BATCH_MODES = ['SERIAL', 'PARALLEL', 'BATCH'] as const;

/** DECISION 节点的 bin 分档（词表；出边 condition 引用 code）。 */
interface BinDef {
  code: string;
  name?: string;
  type: 'PASS' | 'FAIL';
}

/** 边语义色（保存/回显一致；与 validate 语义守门对齐）。 */
const EDGE_COLORS: Record<string, string> = {
  MAIN: '#2563eb',
  PASS: '#16a34a',
  FAIL: '#d97706',
  REWORK: '#dc2626',
};

/** XML 属性转义（& 先行；引号必转——属性全部双引号包裹）。 */
function esc(v: string | number | null | undefined): string {
  const s = v == null ? '' : String(v);
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/** 画布元素 id 生成（node/edge 通用，X6 cell id 原样落 XML）。 */
function genId(prefix: string): string {
  return `${prefix}${Date.now().toString(36)}${Math.floor(Math.random() * 1e4).toString(36)}`;
}

/** React 节点数据（业务属性全部进 XML；X6 以 node.getData() 存取）。 */
interface StepNodeData extends Record<string, unknown> {
  stepCode: string;
  stepName?: string;
  stepType: string;
  recipeRef?: string;
  equipType?: string;
  qtimeMin?: string;
  qtimeMax?: string;
  reworkLimit?: string;
  /** SUBFLOW 节点引用的目标流程编码。 */
  flowRef?: string;
  /** 批处理模式：SERIAL/PARALLEL/BATCH。 */
  batchMode?: string;
  /** 装载量（BATCH 模式）。 */
  batchSize?: string;
  /** Hold 点（关键站后暂停等释放）。 */
  holdPoint?: boolean;
  holdReason?: string;
  /** 检验计划引用（INSPECT 专用，V14）。 */
  inspectPlan?: string;
  /** 抽样率百分比（INSPECT 专用，V14；0-100）。 */
  samplingRate?: string;
  /** DECISION 节点的 bin 词表。 */
  bins?: BinDef[];
}

/** 跨站 Q-time 约束（V14；flow 级，序列化为 <canvas> 下 <qtime> 元素）。 */
interface QtimeDef {
  from: string;
  to: string;
  maxMinutes: string;
  action: 'HOLD' | 'SCRAP';
}

/** X6 边数据：语义 + 条件 + 拐点（vertices 持久化进 XML）。 */
interface StepEdgeData extends Record<string, unknown> {
  edgeType: string;
  condition?: string;
}

/** 解析产物节点（X6 addNode 输入）。 */
interface ParsedNode {
  id: string;
  x: number;
  y: number;
  data: StepNodeData;
}

/** 解析产物边（X6 addEdge 输入；from/to = XML 节点 id）。 */
interface ParsedEdge {
  id: string;
  from: string;
  to: string;
  sourceHandle?: string;
  targetHandle?: string;
  vertices?: Array<{ x: number; y: number }>;
  data: StepEdgeData;
}

/** 画布 XML 序列化（从 X6 Graph 读全量 + qtime 约束；坐标/拐点进 XML 不进解析产物表）。 */
function serializeCanvasXml(graph: Graph, qtimes: QtimeDef[]): string {
  const sb: string[] = ['<canvas version="1">', '  <nodes>'];
  for (const n of graph.getNodes()) {
    const d = n.getData() as StepNodeData;
    const pos = n.getPosition();
    const open = `    <node id="${esc(n.id)}" stepCode="${esc(d.stepCode)}" stepName="${esc(d.stepName)}"`
      + ` stepType="${esc(d.stepType)}" recipeRef="${esc(d.recipeRef)}" equipType="${esc(d.equipType)}"`
      + ` qtimeMin="${esc(d.qtimeMin)}" qtimeMax="${esc(d.qtimeMax)}" reworkLimit="${esc(d.reworkLimit)}"`
      + ` flowRef="${esc(d.flowRef)}" batchMode="${esc(d.batchMode)}" batchSize="${esc(d.batchSize)}"`
      + ` holdPoint="${d.holdPoint ? 'true' : 'false'}" holdReason="${esc(d.holdReason)}"`
      + ` inspectPlan="${esc(d.inspectPlan)}" samplingRate="${esc(d.samplingRate)}"`
      + ` x="${Math.round(pos.x)}" y="${Math.round(pos.y)}"`;
    const bins = d.bins ?? [];
    if (bins.length) {
      sb.push(`${open}>`);
      for (const b of bins) {
        sb.push(`      <bin code="${esc(b.code)}" name="${esc(b.name)}" type="${esc(b.type)}"/>`);
      }
      sb.push('    </node>');
    } else {
      sb.push(`${open}/>`);
    }
  }
  sb.push('  </nodes>', '  <edges>');
  for (const e of graph.getEdges()) {
    const d = (e.getData() ?? {}) as StepEdgeData;
    const src = e.getSource();
    const tgt = e.getTarget();
    const from = 'cell' in src ? (typeof src.cell === 'string' ? src.cell : (src.cell as X6Node).id) : '';
    const to = 'cell' in tgt ? (typeof tgt.cell === 'string' ? tgt.cell : (tgt.cell as X6Node).id) : '';
    const sPort = 'port' in src ? src.port : undefined;
    const tPort = 'port' in tgt ? tgt.port : undefined;
    const vs = e.getVertices().map((v) => `${Math.round(v.x)},${Math.round(v.y)}`).join(' ');
    sb.push(
      `    <edge id="${esc(e.id)}" from="${esc(from)}" to="${esc(to)}"`
        + ` edgeType="${esc(d.edgeType ?? 'MAIN')}"`
        + ` condition="${esc(d.condition)}"`
        + ` sourceHandle="${esc(sPort ?? 'r')}" targetHandle="${esc(tPort ?? 'l')}"`
        + ` vertices="${esc(vs || undefined)}"/>`,
    );
  }
  sb.push('  </edges>');
  for (const q of qtimes) {
    sb.push(`  <qtime from="${esc(q.from)}" to="${esc(q.to)}" maxMinutes="${esc(q.maxMinutes)}" action="${esc(q.action)}"/>`);
  }
  sb.push('</canvas>');
  return sb.join('\n');
}

/** 画布 XML → 解析产物（DOMParser；契约与后端 CanvasXmlSupport 一致）。 */
function parseCanvasXml(xml: string): { nodes: ParsedNode[]; edges: ParsedEdge[]; qtimes: QtimeDef[] } | null {
  if (!xml) return null;
  const doc = new DOMParser().parseFromString(xml, 'application/xml');
  if (doc.querySelector('parsererror') || doc.documentElement?.tagName !== 'canvas') return null;
  const nodes: ParsedNode[] = [];
  for (const el of Array.from(doc.getElementsByTagName('node'))) {
    const get = (k: string) => el.getAttribute(k) ?? undefined;
    const bins: BinDef[] = [];
    for (const binEl of Array.from(el.getElementsByTagName('bin'))) {
      const code = binEl.getAttribute('code') ?? '';
      const type = (binEl.getAttribute('type') ?? 'PASS') as BinDef['type'];
      if (code) bins.push({ code, name: binEl.getAttribute('name') ?? undefined, type });
    }
    nodes.push({
      id: get('id') ?? genId('n'),
      x: Number(get('x') ?? 0),
      y: Number(get('y') ?? 0),
      data: {
        stepCode: get('stepCode') ?? '',
        stepName: get('stepName'),
        stepType: get('stepType') ?? 'PROCESS',
        recipeRef: get('recipeRef'),
        equipType: get('equipType'),
        qtimeMin: get('qtimeMin'),
        qtimeMax: get('qtimeMax'),
        reworkLimit: get('reworkLimit'),
        flowRef: get('flowRef'),
        batchMode: get('batchMode'),
        batchSize: get('batchSize'),
        holdPoint: get('holdPoint') === 'true',
        holdReason: get('holdReason'),
        inspectPlan: get('inspectPlan'),
        samplingRate: get('samplingRate'),
        bins: bins.length ? bins : undefined,
      },
    });
  }
  const edges: ParsedEdge[] = [];
  for (const el of Array.from(doc.getElementsByTagName('edge'))) {
    const edgeType = el.getAttribute('edgeType') ?? 'MAIN';
    const vRaw = (el.getAttribute('vertices') ?? '').trim();
    const vertices = vRaw
      ? vRaw.split(/\s+/).map((pair) => {
          const [x, y] = pair.split(',').map(Number);
          return { x, y };
        })
      : undefined;
    edges.push({
      id: el.getAttribute('id') ?? genId('e'),
      from: el.getAttribute('from') ?? '',
      to: el.getAttribute('to') ?? '',
      sourceHandle: el.getAttribute('sourceHandle') || undefined,
      targetHandle: el.getAttribute('targetHandle') || undefined,
      vertices,
      data: { edgeType, condition: el.getAttribute('condition') ?? undefined },
    });
  }
  const qtimes: QtimeDef[] = [];
  for (const el of Array.from(doc.getElementsByTagName('qtime'))) {
    const from = el.getAttribute('from') ?? '';
    const to = el.getAttribute('to') ?? '';
    if (from && to) {
      qtimes.push({
        from,
        to,
        maxMinutes: el.getAttribute('maxMinutes') ?? '',
        action: (el.getAttribute('action') ?? 'HOLD') as QtimeDef['action'],
      });
    }
  }
  return { nodes, edges, qtimes };
}

// ================================================================ X6 形状注册

/** 四向连接桩（左右进/出 + 上下进/出；port id 与 XML sourceHandle/targetHandle 对齐）。 */
const FLOW_PORTS = {
  groups: {
    l: { position: 'left' as const, attrs: { circle: { r: 5, magnet: true, stroke: '#94a3b8', fill: '#fff', strokeWidth: 1 } } },
    r: { position: 'right' as const, attrs: { circle: { r: 5, magnet: true, stroke: '#94a3b8', fill: '#fff', strokeWidth: 1 } } },
    t: { position: 'top' as const, attrs: { circle: { r: 5, magnet: true, stroke: '#94a3b8', fill: '#fff', strokeWidth: 1 } } },
    b: { position: 'bottom' as const, attrs: { circle: { r: 5, magnet: true, stroke: '#94a3b8', fill: '#fff', strokeWidth: 1 } } },
  },
  items: [
    { id: 'l', group: 'l' },
    { id: 'r', group: 'r' },
    { id: 't', group: 't' },
    { id: 'b', group: 'b' },
  ],
};

let shapeRegistered = false;

/**
 * 注册流程节点形状。
 *
 * <p><b>不继承内置形状（inherit 省略）</b>：X6 Base 基类给选择器 `text` 配了
 * refX:0.5/refY:0.5/textVerticalAnchor:middle 默认值，未命中的选择器会走 CSS
 * 兜底 querySelectorAll('text')，把这套默认值打到全部 &lt;text&gt; 元素上（文字被
 * 平移节点宽高的一半、甩出节点外）；同理 'rect' 兜底会给 bar 竖条强加 #333 描边。
 * 因此 markup/attrs 全部显式声明，不依赖任何继承默认值。</p>
 */
function ensureShapeRegistered(): void {
  if (shapeRegistered) return;
  shapeRegistered = true;
  Graph.registerNode(
    'flow-step',
    {
      width: 168,
      height: 56,
      markup: [
        { tagName: 'title', selector: 'tip' },
        { tagName: 'rect', selector: 'body' },
        { tagName: 'rect', selector: 'bar' },
        { tagName: 'text', selector: 'typeLabel' },
        { tagName: 'text', selector: 'code' },
        { tagName: 'text', selector: 'name' },
        { tagName: 'circle', selector: 'holdDot' },
      ],
      attrs: {
        body: { fill: '#ffffff', stroke: '#dbe3ec', strokeWidth: 1, rx: 10, ry: 10, width: 168, height: 56, cursor: 'move' },
        bar: { x: 0, y: 8, width: 5, height: 40, rx: 2.5, ry: 2.5, fill: '#64748b' },
        typeLabel: { x: 18, y: 18, fontSize: 10, fontWeight: 600, fill: '#64748b', textAnchor: 'start' },
        code: { x: 18, y: 36, fontSize: 13, fontWeight: 600, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', fill: '#0f172a', textAnchor: 'start' },
        name: { x: 18, y: 50, fontSize: 10, fill: '#8a94a6', textAnchor: 'start' },
        holdDot: { r: 5, cx: 154, cy: 14, fill: '#f59e0b', stroke: '#ffffff', strokeWidth: 1.5, display: 'none' },
      },
      ports: { groups: FLOW_PORTS.groups },
    },
    true,
  );
}

/** 超长文本截断（SVG text 无自动省略，按像素宽粗估截断）。 */
function trunc(s: string | undefined, max: number): string {
  const v = (s ?? '').trim();
  if (!v) return '';
  // CJK 字符按 2 个单位宽估算
  let w = 0;
  for (let i = 0; i < v.length; i += 1) {
    w += v.charCodeAt(i) > 0x2e7f ? 2 : 1;
    if (w > max) return `${v.slice(0, i)}…`;
  }
  return v;
}

/** 节点实例 attrs（按数据 + i18n 文案生成；SUBFLOW 第二行显示引用流程编码）。 */
function nodeAttrs(d: StepNodeData, t: (k: string) => string): KeyValue {
  const color = TYPE_COLORS[d.stepType] ?? '#64748b';
  const secondLine = d.stepType === 'SUBFLOW' && d.flowRef ? d.flowRef : (d.stepName ?? '');
  const typeText = t(`mds.flow.stepType.${d.stepType}`);
  const tipParts = [`${typeText} ${d.stepCode ?? ''}`];
  if (secondLine) tipParts.push(secondLine);
  if (d.recipeRef) tipParts.push(`Recipe: ${d.recipeRef}`);
  if (d.equipType) tipParts.push(`Equip: ${d.equipType}`);
  if (d.inspectPlan) {
    tipParts.push(`EDC: ${d.inspectPlan}${d.samplingRate ? ` (${d.samplingRate}%)` : ''}`);
  }
  if (d.holdPoint) tipParts.push(`Hold${d.holdReason ? `: ${d.holdReason}` : ''}`);
  if (d.batchMode) tipParts.push(`Batch: ${d.batchMode}${d.batchSize ? ` × ${d.batchSize}` : ''}`);
  return {
    bar: { fill: color },
    typeLabel: { text: typeText, fill: color },
    code: { text: trunc(d.stepCode, 17) },
    name: { text: trunc(secondLine, 22) },
    body: { stroke: '#dbe3ec' },
    holdDot: { display: d.holdPoint ? 'block' : 'none' },
    tip: { text: tipParts.filter(Boolean).join('  ·  ') },
  };
}

/** 边装饰：语义色 + 箭头（MAIN 不显示文字标签，其余显示语义标签）。 */
function applyEdgeDecor(edge: X6Edge): void {
  const d = (edge.getData() ?? {}) as StepEdgeData;
  const type = d.edgeType ?? 'MAIN';
  const color = EDGE_COLORS[type] ?? '#64748b';
  edge.setAttrs({
    line: {
      stroke: color,
      strokeWidth: 2,
      strokeDasharray: type === 'REWORK' ? '6 4' : null,
      targetMarker: { name: 'classic', size: 8, fill: color },
    },
  });
  if (type === 'MAIN') {
    edge.setLabels([]);
    return;
  }
  edge.setLabels([
    {
      attrs: {
        label: { text: type, fill: color, fontSize: 11, fontWeight: 600 },
        body: { fill: '#fff', stroke: color, strokeWidth: 1, rx: 8, ry: 8 },
      },
      position: 0.5,
    },
  ]);
}

// ================================================================ 页面

export default function FlowPage() {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const toast = useToast();
  const canUpdate = useHasPermission(MDS_PERMS.flow.update);

  const [keyword, setKeyword] = useState('');
  const [flows, setFlows] = useState<FlowView[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<FlowDetail | null>(null);
  const [creating, setCreating] = useState(false);
  const [editingMaster, setEditingMaster] = useState(false);

  const refreshList = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setFlows(await api.listFlows());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  // 首次加载（useEffect 而非 render 阶段触发：refreshList 同步 setLoading，
  // render 里调用会报「state update on a component that hasn't mounted yet」）
  useEffect(() => {
    void refreshList();
  }, [refreshList]);

  const openDetail = useCallback(async (id: string) => {
    setSelectedId(id);
    try {
      setDetail(await api.getFlow(id));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const doDelete = async (row: FlowView) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      content: t('mds.flow.deleteConfirm', { code: row.flowCode }),
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteFlow(row.id);
      if (selectedId === row.id) {
        setSelectedId(null);
        setDetail(null);
      }
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    void refreshList();
  };

  const doCopy = async (row: FlowView) => {
    try {
      const copy = await api.copyFlow(row.id);
      toast.success(t('mds.flow.copyOk', { code: copy.flowCode }));
      await refreshList();
      void openDetail(copy.id);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const doDisable = async (row: FlowView) => {
    const ok = await confirm({
      title: t('mds.flow.disable'),
      content: t('mds.flow.disableConfirm', { code: row.flowCode }),
      danger: true,
      okKey: 'mds.flow.disable',
    });
    if (!ok) return;
    try {
      await api.disableFlow(row.id);
      await refreshList();
      if (selectedId === row.id) setDetail(await api.getFlow(row.id));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const rows = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return flows;
    return flows.filter(
      (r) =>
        r.flowCode.toLowerCase().includes(kw) ||
        r.flowName.toLowerCase().includes(kw) ||
        (r.techCode ?? '').toLowerCase().includes(kw),
    );
  }, [flows, keyword]);

  // 列宽合计 ≈ 容器宽（.flow-left 480px）：「版本/步骤」合并一列、「挂载制程技术」
  // 并入名称副行——上轮左列收窄给画布腾空间后，7 列（合计 786px）在窄容器里
  // 表头与状态徽章全部竖排换行，这里按容器实际宽度重新分配。
  const columns = useMemo<Array<DataTableColumn<FlowView>>>(
    () => [
      { key: 'flowCode', titleKey: 'mds.flow.field.flowCode', width: 100, mono: true, ellipsis: true },
      {
        key: 'flowName',
        titleKey: 'mds.flow.field.flowName',
        width: 132,
        ellipsis: true,
        render: (r) => (
          <span className="flow-name-cell" title={r.techCode ? `${r.flowName} · ${r.techCode}` : r.flowName}>
            <span className="flow-name-main">{r.flowName}</span>
            {r.techCode ? <span className="flow-name-sub">{r.techCode}</span> : null}
          </span>
        ),
      },
      {
        key: 'status',
        titleKey: 'mds.flow.field.status',
        width: 64,
        render: (r) => <span className={`flow-status flow-status-${r.status.toLowerCase()}`}>{t(`mds.flow.status.${r.status}`)}</span>,
      },
      {
        key: 'versionNo',
        titleKey: 'mds.flow.field.versionSteps',
        width: 78,
        mono: true,
        render: (r) => `V${r.versionNo ?? 1} · ${r.stepCount}`,
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 104,
        // 行点击会打开详情：动作按钮先拦掉冒泡，避免「点删除/复制顺手把详情也切了」
        render: (r) => (
          <span className="cell-actions" onClick={(e) => e.stopPropagation()}>
            {(r.status === 'FROZEN' || r.status === 'ACTIVE') && (
              <Can perm={MDS_PERMS.flow.update}>
                <CimButton size="small" onClick={() => void doDisable(r)}>
                  {t('mds.flow.disable')}
                </CimButton>
              </Can>
            )}
            <Can perm={MDS_PERMS.flow.create}>
              <CimButton size="small" onClick={() => void doCopy(r)}>
                {t('mds.flow.copy')}
              </CimButton>
            </Can>
            {(r.status === 'UNFROZEN' || r.status === 'DISABLED') && (
              <Can perm={MDS_PERMS.flow.delete}>
                <CimButton size="small" danger onClick={() => void doDelete(r)}>
                  {t('mds.common.delete')}
                </CimButton>
              </Can>
            )}
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, selectedId],
  );

  const editable = detail?.flow.status === 'UNFROZEN' && canUpdate;

  return (
    <Page className="flow-page">
      <div className="flow-layout">
        <div className="flow-left">
          <DataTable<FlowView>
            columns={columns}
            rows={rows}
            rowKey={(r) => r.id}
            loading={loading}
            error={error}
            onRetry={() => void refreshList()}
            onRowClick={(r) => void openDetail(r.id)}
            rowClassName={(r) => (r.id === selectedId ? 'is-selected' : '')}
            headActions={
              <Can perm={MDS_PERMS.flow.create}>
                <CimButton type="primary" size="small" onClick={() => setCreating(true)}>
                  {t('mds.flow.create')}
                </CimButton>
              </Can>
            }
            search={{ value: keyword, onChange: setKeyword, placeholderKey: 'mds.flow.searchPlaceholder' }}
            onRefresh={() => void refreshList()}
          />
        </div>
        <div className="flow-right">
          {detail ? (
            <CanvasPane
              key={detail.flow.id}
              detail={detail}
              editable={!!editable}
              canUpdate={canUpdate}
              onChanged={async (next) => {
                setDetail(next);
                await refreshList();
              }}
              onEditMaster={() => setEditingMaster(true)}
            />
          ) : (
            <div className="flow-canvas-empty">{t('mds.flow.canvasEmpty')}</div>
          )}
        </div>
      </div>

      {creating && (
        <FlowMasterModal
          initial={null}
          onClose={() => setCreating(false)}
          onDone={async (created) => {
            setCreating(false);
            await refreshList();
            void openDetail(created.id);
          }}
        />
      )}
      {editingMaster && detail && (
        <FlowMasterModal
          initial={detail.flow}
          onClose={() => setEditingMaster(false)}
          onDone={async () => {
            setEditingMaster(false);
            const id = detail.flow.id;
            await refreshList();
            void openDetail(id);
          }}
        />
      )}
    </Page>
  );
}

// ================================================================ 画布区

/** 画布区：工具栏 + 组件面板 + X6 画布（XML 序列化保存）。 */
function CanvasPane({
  detail,
  editable,
  canUpdate,
  onChanged,
  onEditMaster,
}: {
  detail: FlowDetail;
  editable: boolean;
  canUpdate: boolean;
  onChanged: (next: FlowDetail) => Promise<void> | void;
  onEditMaster: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const wrapper = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const editableRef = useRef(editable);
  const loadingRef = useRef(false);
  const toolDragRef = useRef(false);

  const [dirty, setDirty] = useState(false);
  const [nodeModal, setNodeModal] = useState<X6Node | null>(null);
  const [edgeModal, setEdgeModal] = useState<X6Edge | null>(null);
  const [qtimeModal, setQtimeModal] = useState(false);
  const [busy, setBusy] = useState(false);
  const [selCount, setSelCount] = useState(0);
  // 画布全屏（覆盖层式 fixed 铺满视口，Esc 退出；组件按 flow id 重挂载，切流程自动复位）
  const [maximized, setMaximized] = useState(false);

  useEffect(() => {
    if (!maximized) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMaximized(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [maximized]);

  // 进入/退出全屏容器尺寸剧变（autoResize 走 ResizeObserver 异步生效），稍候重新适配视野；
  // 覆盖按钮与 Esc 两条路径（挂载首跑跳过——首载 fit 由画布加载 effect 负责）
  const maximizedRef = useRef(false);
  useEffect(() => {
    if (maximizedRef.current === maximized) return;
    maximizedRef.current = maximized;
    const g = graphRef.current;
    if (!g || g.disposed) return;
    const t = window.setTimeout(() => {
      if (!g.disposed) g.zoomToFit({ padding: 24, maxScale: 1 });
    }, 240);
    return () => window.clearTimeout(t);
  }, [maximized]);

  // 跨站 Q-time（flow 级，不挂在节点上；存 state，保存时序列化进 XML）
  const [qtimes, setQtimes] = useState<QtimeDef[]>([]);
  const qtimesRef = useRef<QtimeDef[]>([]);
  qtimesRef.current = qtimes;

  const markDirty = useCallback(() => setDirty(true), []);

  // editable 状态镜像（X6 事件闭包里读 ref，避免重建图）
  useEffect(() => {
    editableRef.current = editable;
  }, [editable]);

  // 删除所选（节点连带关联边 + 选中边）；ref 供键盘事件闭包调用
  const deleteSelectedRef = useRef<() => void>(() => undefined);
  deleteSelectedRef.current = () => {
    const g = graphRef.current;
    if (!g || !editableRef.current) return;
    const cells = g.getSelectedCells();
    if (cells.length) g.removeCells(cells);
  };

  // ---- 创建 X6 画布（一次） + 事件绑定
  useEffect(() => {
    if (!wrapper.current || graphRef.current) return;
    ensureShapeRegistered();

    // 工具拖拽状态跟踪：拖拐点/端点时指针立刻离开连线本体，会触发 edge:mouseleave，
    // 若此时移除工具会把拖拽掐断（且中断后的拖拽控制器不清理，节点拖拽也跟着失灵）。
    // document 级捕获 mousedown：目标在 .x6-cell-tool 内 = 工具拖拽进行中。
    const onDocMouseDown = (e: MouseEvent) => {
      const target = e.target as Element | null;
      toolDragRef.current = !!(target?.closest?.('.x6-cell-tool'));
    };
    const onDocMouseUp = () => {
      toolDragRef.current = false;
    };
    document.addEventListener('mousedown', onDocMouseDown, true);
    document.addEventListener('mouseup', onDocMouseUp, true);

    const graph = new Graph({
      container: wrapper.current,
      autoResize: true,
      grid: { size: 10, visible: true, type: 'dot' },
      // 画布平移：空白处左键/右键拖拽；缩放：滚轮（不需修饰键，Figma 式直觉操作）
      // ——节点/边/连接线拖拽发生在节点上，与空白平移不冲突
      panning: { enabled: true, eventTypes: ['leftMouseDown', 'rightMouseDown'] },
      mousewheel: { enabled: true, modifiers: [], minScale: 0.2, maxScale: 3 },
      // 编辑能力整体随 editable 切换（函数式 interacting，免重建图）
      interacting: () => ({
        nodeMovable: editableRef.current,
        edgeMovable: editableRef.current,
        arrowheadMovable: editableRef.current,
        vertexMovable: editableRef.current,
        vertexAddable: editableRef.current,
        vertexDeletable: editableRef.current,
        magnetConnectable: editableRef.current,
      }),
      connecting: {
        snap: true,
        allowBlank: false,
        allowLoop: false,
        allowNode: false,
        allowEdge: false,
        allowMulti: 'withPort',
        connector: { name: 'rounded', args: { radius: 10 } },
        highlight: true,
      },
      highlighting: {
        magnetAdsorbed: { name: 'stroke', args: { attrs: { stroke: '#2563eb', strokeWidth: 2 } } },
      },
    });
    // 框选绑定 Shift：无修饰键的左键空白拖拽让给 panning（两插件抢左键时 panning 会因
    // selection.allowRubberband(strict) 判定让位——X6 源码口径：strict 判定「未按修饰键」为 true）
    graph.use(new Selection({ enabled: true, multiple: true, rubberband: true, rubberNode: true, movable: true, modifiers: ['shift'] }));
    graph.use(new Snapline({ enabled: true }));
    graph.use(new Keyboard({ enabled: true }));
    graph.bindKey(['backspace', 'delete'], () => deleteSelectedRef.current());
    graphRef.current = graph;

    // 点空白处：清掉所有连线的编辑工具（干净画布）
    graph.on('blank:click', () => {
      for (const e of graph.getEdges()) e.removeTools();
    });

    const mark = () => {
      if (!loadingRef.current) setDirty(true);
    };
    graph.on('node:moved', mark);
    graph.on('node:added', mark);
    graph.on('node:removed', mark);
    graph.on('edge:removed', mark);
    graph.on('edge:change:vertices', mark);
    graph.on('cell:change:vertices', mark);
    graph.on('edge:connected', ({ edge, isNew }: { edge: X6Edge; isNew?: boolean }) => {
      if (isNew) {
        const src = edge.getSourceNode();
        const st = (src?.getData() as StepNodeData | undefined)?.stepType;
        edge.setData({ edgeType: st === 'DECISION' ? 'PASS' : 'MAIN', condition: undefined }, { overwrite: true });
        applyEdgeDecor(edge);
      }
      mark();
    });
    graph.on('node:dblclick', ({ node }: { node: X6Node }) => {
      if (editableRef.current) setNodeModal(node);
      else toast.info(t('mds.flow.readonlyHint'));
    });
    graph.on('edge:dblclick', ({ edge }: { edge: X6Edge }) => {
      if (editableRef.current) setEdgeModal(edge);
      else toast.info(t('mds.flow.readonlyHint'));
    });
    // hover 连线出全套编辑工具（拐点/段拖拽/改端点/删边）；
    // 离开延迟移除且跳过工具拖拽中——见 toolDragRef 说明
    graph.on('edge:mouseenter', ({ cell }: { cell: X6Node | X6Edge }) => {
      if (!editableRef.current || !cell.isEdge()) return;
      cell.addTools(['vertices', 'segments', 'button-remove', 'source-arrowhead', 'target-arrowhead']);
    });
    graph.on('edge:mouseleave', ({ cell }: { cell: X6Node | X6Edge }) => {
      if (!cell.isEdge()) return;
      window.setTimeout(() => {
        if (!toolDragRef.current && !cell.disposed) cell.removeTools();
      }, 120);
    });
    // ⚠️ selection:changed 的 payload 是 {added,removed,options,selected}，没有 cells 字段——
    // 解构 cells 会在每次选中变化时抛 TypeError，异常中断 X6 mouseup 处理链（view 委托未恢复），
    // 导致第一次点击后所有左键交互（点选/双击/框选/删除）永久失效、右键平移却正常。
    graph.on('selection:changed', ({ selected }: { selected: Array<X6Node | X6Edge> }) =>
      setSelCount(selected?.length ?? 0));

    return () => {
      document.removeEventListener('mousedown', onDocMouseDown, true);
      document.removeEventListener('mouseup', onDocMouseUp, true);
      graph.dispose();
      graphRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ---- 加载画布内容（按流程 id；保存后 id 不变不重载）
  useEffect(() => {
    const g = graphRef.current;
    if (!g) return;
    loadingRef.current = true;
    g.clearCells();
    const parsed = parseCanvasXml(detail.flow.flowXml ?? '');
    if (parsed) {
      for (const n of parsed.nodes) {
        g.addNode({
          shape: 'flow-step',
          id: n.id,
          x: n.x,
          y: n.y,
          data: n.data,
          attrs: nodeAttrs(n.data, t),
          ports: { items: FLOW_PORTS.items },
        });
      }
      for (const e of parsed.edges) {
        const edge = g.addEdge({
          shape: 'edge',
          id: e.id,
          source: { cell: e.from, port: e.sourceHandle ?? 'r' },
          target: { cell: e.to, port: e.targetHandle ?? 'l' },
          vertices: e.vertices ?? [],
          data: e.data,
        });
        applyEdgeDecor(edge);
      }
      // 打开后自适应视野：内容大则缩到可视区内，小则 1:1 居中。等两帧再 fit——
      // 容器尺寸就绪有延迟（挂载早期读尺寸不准会把内容推出视野外）。
      requestAnimationFrame(() =>
        requestAnimationFrame(() => {
          if (!g.disposed) g.zoomToFit({ padding: 24, maxScale: 1 });
        }),
      );
    }
    setQtimes(parsed?.qtimes ?? []);
    loadingRef.current = false;
    setDirty(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detail.flow.id]);

  /** 拖入组件面板节点：屏幕坐标 → 画布坐标。 */
  const onDrop = (event: React.DragEvent) => {
    if (!editableRef.current) return;
    const stepType = event.dataTransfer.getData('application/cim-flow-step');
    if (!stepType || !STEP_TYPES.includes(stepType as (typeof STEP_TYPES)[number])) return;
    event.preventDefault();
    const g = graphRef.current;
    if (!g) return;
    const local = g.pageToLocal({ x: event.clientX, y: event.clientY });
    const d: StepNodeData = {
      stepCode: stepType === 'START' || stepType === 'END' ? stepType : '',
      stepType,
    };
    g.addNode({
      shape: 'flow-step',
      id: genId('n'),
      x: local.x - 84,
      y: local.y - 28,
      data: d,
      attrs: nodeAttrs(d, t),
      ports: { items: FLOW_PORTS.items },
    });
  };

  /** 保存：整份 XML 回传（单一事实源口径），后端解析重建解析产物表。 */
  const doSave = async () => {
    const g = graphRef.current;
    if (!g) return;
    setBusy(true);
    try {
      const xml = serializeCanvasXml(g, qtimesRef.current);
      const next = await api.saveFlowCanvas(detail.flow.id, { xml });
      setDirty(false);
      toast.success(t('mds.flow.saveOk', { nodes: g.getNodes().length, edges: g.getEdges().length }));
      await onChanged(next);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const doValidate = async () => {
    setBusy(true);
    try {
      const res = await api.validateFlow(detail.flow.id);
      if (res.valid) {
        toast.success(t('mds.flow.validateOk'));
      } else {
        toast.error(t('mds.flow.validateFailed', { count: res.problems.length }));
        await confirm({
          title: t('mds.flow.validate'),
          content: res.problems.join('\n'),
          cancelKey: 'mds.common.cancel',
        });
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  /** 生命周期流转（四态状态机：freeze/unfreeze/activate/disable，后端有状态守门）。 */
  const doTransition = async (
    action: 'freezeFlow' | 'unfreezeFlow' | 'activateFlow' | 'disableFlow',
    confirmKey: string,
  ) => {
    const ok = await confirm({
      title: t(`mds.flow.${action.replace('Flow', '')}`),
      content: t(confirmKey, { code: detail.flow.flowCode }),
      okKey: `mds.flow.${action.replace('Flow', '')}`,
    });
    if (!ok) return;
    try {
      await api[action](detail.flow.id);
      toast.success(t('mds.common.updated'));
      await onChanged(await api.getFlow(detail.flow.id));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const zoomBy = (delta: number) => {
    const g = graphRef.current;
    if (g) g.zoomTo(Math.min(3, Math.max(0.2, g.zoom() + delta)));
  };
  const fitContent = () => {
    const g = graphRef.current;
    if (g) {
      g.zoomTo(1);
      g.centerContent();
    }
  };

  const status = detail.flow.status;
  return (
    <div className={'flow-canvas-pane' + (maximized ? ' is-maximized' : '')}>
      <div className="flow-toolbar">
        <span className="flow-toolbar-title">
          {t('mds.flow.canvasTitle', { code: detail.flow.flowCode })}
          <span className={`flow-status flow-status-${status.toLowerCase()}`} style={{ marginLeft: 8 }}>
            {t(`mds.flow.status.${status}`)}
          </span>
          {dirty ? <em className="flow-dirty"> · {t('mds.flow.unsaved')}</em> : null}
        </span>
        {canUpdate && status === 'UNFROZEN' && (
          <CimButton size="small" onClick={onEditMaster}>
            {t('mds.common.edit')}
          </CimButton>
        )}
        {canUpdate && status === 'UNFROZEN' && (
          <>
            <CimButton size="small" type="primary" loading={busy} onClick={() => void doSave()}>
              {t('mds.flow.saveCanvas')}
            </CimButton>
            <CimButton size="small" loading={busy} onClick={() => void doValidate()}>
              {t('mds.flow.validate')}
            </CimButton>
            <CimButton size="small" onClick={() => setQtimeModal(true)}>
              {t('mds.flow.qtime.manage')}
            </CimButton>
            <CimButton size="small" onClick={() => deleteSelectedRef.current()} disabled={selCount === 0}>
              {t('mds.flow.deleteSelected')}
            </CimButton>
            <CimButton size="small" type="primary" onClick={() => void doTransition('freezeFlow', 'mds.flow.freezeConfirm')}>
              {t('mds.flow.freeze')}
            </CimButton>
          </>
        )}
        {canUpdate && status === 'FROZEN' && (
          <>
            <CimButton size="small" loading={busy} onClick={() => void doValidate()}>
              {t('mds.flow.validate')}
            </CimButton>
            <CimButton size="small" onClick={() => void doTransition('unfreezeFlow', 'mds.flow.unfreezeConfirm')}>
              {t('mds.flow.unfreeze')}
            </CimButton>
            <CimButton size="small" type="primary" onClick={() => void doTransition('activateFlow', 'mds.flow.activateConfirm')}>
              {t('mds.flow.activate')}
            </CimButton>
            <CimButton size="small" danger onClick={() => void doTransition('disableFlow', 'mds.flow.disableConfirm')}>
              {t('mds.flow.disable')}
            </CimButton>
          </>
        )}
        {canUpdate && status === 'ACTIVE' && (
          <CimButton size="small" danger onClick={() => void doTransition('disableFlow', 'mds.flow.disableConfirm')}>
            {t('mds.flow.disable')}
          </CimButton>
        )}
        {!editable && <span className="dim">{t('mds.flow.readonly')}</span>}
        <em className="flow-canvas-hint">{t('mds.flow.canvasHint')}</em>
      </div>
      <div className="flow-canvas-wrap">
        {editable && (
          <div
            className="flow-palette"
            onDragOver={(e) => e.preventDefault()}
          >
            <div className="flow-palette-title">{t('mds.flow.palette')}</div>
            {STEP_TYPES.map((st) => (
              <div
                key={st}
                className="flow-palette-item"
                draggable
                onDragStart={(e) => {
                  e.dataTransfer.setData('application/cim-flow-step', st);
                  e.dataTransfer.effectAllowed = 'move';
                }}
              >
                <i className="flow-palette-dot" style={{ background: TYPE_COLORS[st] }} />
                {t(`mds.flow.stepType.${st}`)}
              </div>
            ))}
            <div className="flow-palette-hint">{t('mds.flow.paletteHint')}</div>
          </div>
        )}
        {/* ⚠️ 缩放控件必须放在 X6 容器外：X6 init 会清空容器重建视图，
            容器内子元素（此前 .flow-zoom 就挂在里面）会被整个吞掉不渲染 */}
        <div className="flow-canvas-shell">
          <div
            className="flow-canvas"
            ref={wrapper}
            onDrop={onDrop}
            onDragOver={(e) => {
              if (editableRef.current) e.preventDefault();
            }}
          />
          <div className="flow-zoom">
            <button type="button" title="+" onClick={() => zoomBy(0.1)}>＋</button>
            <button type="button" title="−" onClick={() => zoomBy(-0.1)}>−</button>
            <button type="button" title="1:1" onClick={fitContent}>⌖</button>
            <button
              type="button"
              title={maximized ? t('mds.flow.fullscreenExit') : t('mds.flow.fullscreen')}
              onClick={() => setMaximized(!maximized)}
            >
              {/* 展开/收起角标图标（⛶ 字形字体覆盖不稳，用 SVG） */}
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true">
                {maximized ? (
                  // 收起：两角向内
                  <>
                    <path d="M4.5 1v3.5H1" stroke="currentColor" strokeWidth="1.4" />
                    <path d="M7.5 11V7.5H11" stroke="currentColor" strokeWidth="1.4" />
                  </>
                ) : (
                  // 展开：两角向外
                  <>
                    <path d="M1 4.5h3.5V1" stroke="currentColor" strokeWidth="1.4" />
                    <path d="M11 7.5H7.5V11" stroke="currentColor" strokeWidth="1.4" />
                  </>
                )}
              </svg>
            </button>
          </div>
        </div>
      </div>
      {qtimeModal && (
        <QtimeModal
          qtimes={qtimes}
          nodes={graphRef.current
            ? graphRef.current.getNodes().map((n) => {
                const d = n.getData() as StepNodeData;
                return { id: n.id, code: d.stepCode ?? '', name: d.stepName ?? '' };
              })
            : []}
          onClose={() => setQtimeModal(false)}
          onDone={(next) => {
            setQtimes(next);
            setQtimeModal(false);
            markDirty();
          }}
        />
      )}
      {nodeModal && (
        <NodeFormModal
          node={{ id: nodeModal.id, data: nodeModal.getData() as StepNodeData }}
          existingCodes={
            graphRef.current
              ? graphRef.current
                  .getNodes()
                  .filter((n) => n.id !== nodeModal.id)
                  .map((n) => ((n.getData() as StepNodeData).stepCode ?? ''))
              : []
          }
          excludeFlowId={detail.flow.id}
          onClose={() => setNodeModal(null)}
          onDone={(patch) => {
            const merged = { ...nodeModal.getData(), ...patch } as StepNodeData;
            nodeModal.setData(merged, { overwrite: true });
            nodeModal.setAttrs(nodeAttrs(merged, t));
            setNodeModal(null);
            markDirty();
          }}
        />
      )}
      {edgeModal && (
        <EdgeFormModal
          edge={{ id: edgeModal.id, data: (edgeModal.getData() ?? {}) as StepEdgeData }}
          binOptions={(() => {
            const srcId = edgeModal.getSourceCellId();
            const src = srcId ? graphRef.current?.getCellById(srcId) : null;
            const bins = src ? (src.getData() as StepNodeData | undefined)?.bins : undefined;
            return bins?.length ? bins.map((b) => ({ value: b.code, label: `${b.code}${b.name ? ' · ' + b.name : ''}` })) : undefined;
          })()}
          onClose={() => setEdgeModal(null)}
          onDone={(edgeType, condition) => {
            const merged = { ...(edgeModal.getData() ?? {}), edgeType, condition } as StepEdgeData;
            edgeModal.setData(merged, { overwrite: true });
            applyEdgeDecor(edgeModal);
            setEdgeModal(null);
            markDirty();
          }}
        />
      )}
    </div>
  );
}

// ================================================================ 弹窗

const FLOW_TITLE = {
  editKey: 'mds.flow.editTitle',
  createKey: 'mds.flow.createTitle',
} as const;

/** 流程主档新建/编辑弹窗（flowCode 走 NamingRule 占号，不在表单里）。 */
function FlowMasterModal({
  initial,
  onClose,
  onDone,
}: {
  initial: FlowView | null;
  onClose: () => void;
  onDone: (flow: FlowView) => void | Promise<void>;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [techs, setTechs] = useState<FormOption[]>([]);
  const [form, setForm] = useState({
    flowName: initial?.flowName ?? '',
    techCode: initial?.techCode ?? '',
    flowKind: (initial?.flowKind ?? 'MAIN') as 'MAIN' | 'SUB',
    remark: initial?.remark ?? '',
  });

  // 技术下拉（可空：流程可暂不挂技术）；取数放 useEffect，不在 render 阶段发起副作用
  useEffect(() => {
    let alive = true;
    void api
      .listTechnologies()
      .then((list) => {
        if (alive) setTechs(list.map((x) => ({ value: x.techCode, label: `${x.techCode} · ${x.techName}` })));
      })
      .catch(() => {
        if (alive) setTechs([]);
      });
    return () => {
      alive = false;
    };
  }, []);

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      { kind: 'text' as const, key: 'flowName' as const, labelKey: 'mds.flow.field.flowName', required: true, span: 2 },
      {
        kind: 'select' as const,
        key: 'techCode' as const,
        labelKey: 'mds.flow.field.techCode',
        options: techs,
        allowClear: true,
        showSearch: true,
      },
      {
        kind: 'select' as const,
        key: 'flowKind' as const,
        labelKey: 'mds.flow.field.flowKind',
        options: (['MAIN', 'SUB'] as const).map((k) => ({ value: k, labelKey: `mds.flow.flowKind.${k}` })),
      },
      { kind: 'text' as const, key: 'remark' as const, labelKey: 'mds.common.remark' },
    ],
    [techs],
  );

  async function submit() {
    if (!form.flowName.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const payload = {
        flowName: form.flowName.trim(),
        techCode: form.techCode || null,
        flowKind: form.flowKind,
        remark: form.remark || null,
      };
      const flow = initial
        ? await api.updateFlow(initial.id, payload)
        : await api.createFlow(payload);
      if (!initial) toast.success(flow.flowCode);
      await onDone(flow);
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey={initial ? FLOW_TITLE.editKey : FLOW_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={480}
    >
      <FormGrid fields={fields} value={form} onChange={(patch) => setForm((p) => ({ ...p, ...patch }))} columns={2} />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}

/** 分组小标题（节点弹窗内）。 */
function FieldGroup({ labelKey }: { labelKey: string }) {
  const { t } = useTranslation();
  return <div className="flow-field-group">{t(labelKey)}</div>;
}

/**
 * 节点编辑弹窗（FormGrid 分组：基本信息 / 加工属性 / 质量约束 / Hold 点 / 子流程 / bin 词表）。
 * START/END 编码锁死；DECISION 可编辑 bin 词表；SUBFLOW 必选引用已发布流程。
 */
function NodeFormModal({
  node,
  existingCodes,
  excludeFlowId,
  onClose,
  onDone,
}: {
  node: { id: string; data: StepNodeData };
  existingCodes: string[];
  excludeFlowId?: string;
  onClose: () => void;
  onDone: (patch: Partial<StepNodeData>) => void;
}) {
  const { t } = useTranslation();
  const [err, setErr] = useState('');
  const d = node.data;
  const structural = d.stepType === 'START' || d.stepType === 'END';
  const isDecision = d.stepType === 'DECISION';
  const isSubflow = d.stepType === 'SUBFLOW';
  const isInspect = d.stepType === 'INSPECT';
  const [form, setForm] = useState({
    stepCode: d.stepCode,
    stepName: d.stepName ?? '',
    stepType: d.stepType,
    recipeRef: d.recipeRef ?? '',
    equipType: d.equipType ?? '',
    batchMode: d.batchMode ?? '',
    batchSize: d.batchSize ?? '',
    qtimeMin: d.qtimeMin ?? '',
    qtimeMax: d.qtimeMax ?? '',
    reworkLimit: d.reworkLimit ?? '',
    holdPoint: d.holdPoint ?? false,
    holdReason: d.holdReason ?? '',
    flowRef: d.flowRef ?? '',
    inspectPlan: d.inspectPlan ?? '',
    samplingRate: d.samplingRate ?? '',
  });
  const [bins, setBins] = useState<BinDef[]>(d.bins ?? []);
  const [publishedFlows, setPublishedFlows] = useState<FormOption[]>([]);

  // 已发布流程下拉（SUBFLOW 引用候选；排除自身）；取数放 useEffect
  useEffect(() => {
    let alive = true;
    void api
      .listFlows()
      .then((list) => {
        if (alive)
          setPublishedFlows(
            list
              .filter((f) => f.status === 'ACTIVE' && f.id !== excludeFlowId)
              .map((f) => ({ value: f.flowCode, label: `${f.flowCode} · ${f.flowName}` })),
          );
      })
      .catch(() => {
        if (alive) setPublishedFlows([]);
      });
    return () => {
      alive = false;
    };
  }, [excludeFlowId]);

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      { kind: 'text' as const, key: 'stepCode' as const, labelKey: 'mds.flow.field.stepCode', required: true, mono: true, disabled: structural },
      { kind: 'text' as const, key: 'stepName' as const, labelKey: 'mds.flow.field.stepName' },
      {
        kind: 'select' as const,
        key: 'stepType' as const,
        labelKey: 'mds.flow.field.stepType',
        disabled: structural,
        options: STEP_TYPES.map((st) => ({ value: st, labelKey: `mds.flow.stepType.${st}` })),
      },
      // ---- 加工属性
      { kind: 'text' as const, key: 'recipeRef' as const, labelKey: 'mds.flow.field.recipeRef', mono: true },
      { kind: 'text' as const, key: 'equipType' as const, labelKey: 'mds.flow.field.equipType', mono: true },
      {
        kind: 'select' as const,
        key: 'batchMode' as const,
        labelKey: 'mds.flow.field.batchMode',
        options: BATCH_MODES.map((m) => ({ value: m, labelKey: `mds.flow.batchMode.${m}` })),
        allowClear: true,
      },
      { kind: 'number' as const, key: 'batchSize' as const, labelKey: 'mds.flow.field.batchSize', min: 1 },
      // ---- 质量约束
      { kind: 'number' as const, key: 'qtimeMin' as const, labelKey: 'mds.flow.field.qtimeMin', unit: 'min' },
      { kind: 'number' as const, key: 'qtimeMax' as const, labelKey: 'mds.flow.field.qtimeMax', unit: 'min' },
      { kind: 'number' as const, key: 'reworkLimit' as const, labelKey: 'mds.flow.field.reworkLimit', min: 0 },
      // ---- Hold 点
      { kind: 'switch' as const, key: 'holdPoint' as const, labelKey: 'mds.flow.field.holdPoint' },
      { kind: 'text' as const, key: 'holdReason' as const, labelKey: 'mds.flow.field.holdReason' },
      // ---- 检验配置（仅 INSPECT 显示）
      { kind: 'text' as const, key: 'inspectPlan' as const, labelKey: 'mds.flow.field.inspectPlan', mono: true, hidden: !isInspect },
      {
        kind: 'number' as const,
        key: 'samplingRate' as const,
        labelKey: 'mds.flow.field.samplingRate',
        unit: '%',
        min: 0,
        max: 100,
        hidden: !isInspect,
      },
      // ---- 子流程引用（仅 SUBFLOW 显示）
      {
        kind: 'select' as const,
        key: 'flowRef' as const,
        labelKey: 'mds.flow.field.flowRef',
        options: publishedFlows,
        showSearch: true,
        hidden: !isSubflow,
      },
    ],
    [structural, isSubflow, isInspect, publishedFlows],
  );

  /** bin 行更新。 */
  const patchBin = (idx: number, patch: Partial<BinDef>) => {
    setBins((bs) => bs.map((b, i) => (i === idx ? { ...b, ...patch } : b)));
  };

  function submit() {
    const code = form.stepCode.trim();
    if (!code) {
      setErr(t('mds.common.required'));
      return;
    }
    if (existingCodes.filter(Boolean).includes(code)) {
      setErr(t('mds.flow.field.stepCode') + ': ' + code);
      return;
    }
    if (isSubflow && !form.flowRef) {
      setErr(t('mds.flow.field.flowRef') + ': ' + t('mds.common.required'));
      return;
    }
    // bin 词表语法前端预检（后端仍有同口径守门）
    const seen = new Set<string>();
    for (const b of isDecision ? bins : []) {
      const c = b.code.trim();
      if (!c) {
        setErr(t('mds.flow.bin.codeRequired'));
        return;
      }
      if (seen.has(c)) {
        setErr(t('mds.flow.bin.codeDuplicate') + ': ' + c);
        return;
      }
      seen.add(c);
    }
    // 检验配置前端预检（后端仍有同口径守门）：抽样率 (0, 100]
    if (isInspect && form.samplingRate !== '') {
      const rate = Number(form.samplingRate);
      if (!Number.isFinite(rate) || rate <= 0 || rate > 100) {
        setErr(t('mds.flow.field.samplingRate') + ': (0, 100]');
        return;
      }
    }
    onDone({
      stepCode: code,
      stepName: form.stepName.trim() || undefined,
      stepType: structural ? d.stepType : form.stepType,
      recipeRef: form.recipeRef.trim() || undefined,
      equipType: form.equipType.trim() || undefined,
      batchMode: (form.batchMode || undefined) as StepNodeData['batchMode'],
      batchSize: form.batchSize === '' ? undefined : String(form.batchSize),
      qtimeMin: form.qtimeMin === '' ? undefined : String(form.qtimeMin),
      qtimeMax: form.qtimeMax === '' ? undefined : String(form.qtimeMax),
      reworkLimit: form.reworkLimit === '' ? undefined : String(form.reworkLimit),
      holdPoint: form.holdPoint || undefined,
      holdReason: form.holdPoint ? form.holdReason.trim() || undefined : undefined,
      inspectPlan: isInspect ? form.inspectPlan.trim() || undefined : undefined,
      samplingRate: isInspect && form.samplingRate !== '' ? String(form.samplingRate) : undefined,
      flowRef: isSubflow ? form.flowRef : undefined,
      bins: isDecision && bins.length ? bins : undefined,
    });
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="mds.flow.nodeTitle"
      onOk={submit}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      width={560}
    >
      <FormGrid fields={fields} value={form} onChange={(patch) => setForm((p) => ({ ...p, ...patch }))} columns={2} />
      {isDecision && (
        <>
          <FieldGroup labelKey="mds.flow.bin.group" />
          <div className="flow-bin-list">
            {bins.map((b, idx) => (
              <div className="flow-bin-row" key={idx}>
                <input
                  className="flow-bin-input"
                  placeholder="GOOD"
                  value={b.code}
                  onChange={(e) => patchBin(idx, { code: e.target.value })}
                />
                <input
                  className="flow-bin-input"
                  placeholder={t('mds.flow.bin.name')}
                  value={b.name ?? ''}
                  onChange={(e) => patchBin(idx, { name: e.target.value })}
                />
                <select
                  className="flow-bin-input"
                  value={b.type}
                  onChange={(e) => patchBin(idx, { type: e.target.value as BinDef['type'] })}
                >
                  <option value="PASS">PASS</option>
                  <option value="FAIL">FAIL</option>
                </select>
                <CimButton size="small" danger onClick={() => setBins((bs) => bs.filter((_, i) => i !== idx))}>
                  ×
                </CimButton>
              </div>
            ))}
            <CimButton size="small" onClick={() => setBins((bs) => [...bs, { code: '', type: 'PASS' }])}>
              {t('mds.flow.bin.add')}
            </CimButton>
            <span className="flow-bin-hint">{t('mds.flow.bin.hint')}</span>
          </div>
        </>
      )}
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}

/** 连线编辑弹窗（改语义 + 条件表达式；源 DECISION 定义了 bin 词表时条件改为下拉）。 */
function EdgeFormModal({
  edge,
  binOptions,
  onClose,
  onDone,
}: {
  edge: { id: string; data: StepEdgeData };
  binOptions?: FormOption[];
  onClose: () => void;
  onDone: (edgeType: string, condition?: string) => void;
}) {
  const [form, setForm] = useState({
    edgeType: edge.data?.edgeType ?? 'MAIN',
    condition: edge.data?.condition ?? '',
  });
  const hasBins = !!binOptions?.length;
  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      {
        kind: 'select' as const,
        key: 'edgeType' as const,
        labelKey: 'mds.flow.field.edgeType',
        options: (['MAIN', 'PASS', 'FAIL', 'REWORK'] as const).map((et) => ({
          value: et,
          labelKey: `mds.flow.edgeType.${et}`,
        })),
      },
      hasBins
        ? {
            kind: 'select' as const,
            key: 'condition' as const,
            labelKey: 'mds.flow.field.condition',
            options: binOptions!,
            allowClear: true,
          }
        : { kind: 'text' as const, key: 'condition' as const, labelKey: 'mds.flow.field.condition', mono: true, placeholder: "bin == 'GOOD'" },
    ],
    [hasBins, binOptions],
  );
  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="mds.flow.edgeTitle"
      onOk={() => onDone(form.edgeType, form.condition.trim() || undefined)}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      width={440}
    >
      <FormGrid fields={fields} value={form} onChange={(patch) => setForm((p) => ({ ...p, ...patch }))} columns={2} />
    </CimModal>
  );
}

/**
 * 跨站 Q-time 约束编辑器（V14；flow 级，序列化为画布 XML 的 <qtime> 元素）。
 * from/to 用节点下拉（stepCode 展示）；maxMinutes 分钟数；action HOLD/SCRAP。
 * 后端守门同口径：引用真实/非自站/maxMinutes>0/白名单/同对站唯一/from 主线可达 to。
 */
function QtimeModal({
  qtimes,
  nodes,
  onClose,
  onDone,
}: {
  qtimes: QtimeDef[];
  nodes: Array<{ id: string; code: string; name: string }>;
  onClose: () => void;
  onDone: (next: QtimeDef[]) => void;
}) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<QtimeDef[]>(qtimes.map((q) => ({ ...q })));
  const [err, setErr] = useState('');
  const nodeOptions = nodes.map((n) => ({ value: n.id, label: n.name ? `${n.code} · ${n.name}` : n.code }));
  const codeOf = (id: string) => nodes.find((n) => n.id === id)?.code ?? id;

  const patchRow = (idx: number, patch: Partial<QtimeDef>) => {
    setRows((rs) => rs.map((r, i) => (i === idx ? { ...r, ...patch } : r)));
  };

  function submit() {
    const seen = new Set<string>();
    for (const r of rows) {
      if (!r.from || !r.to) {
        setErr(t('mds.flow.qtime.nodesRequired'));
        return;
      }
      if (r.from === r.to) {
        setErr(t('mds.flow.qtime.sameNode'));
        return;
      }
      const minutes = Number(r.maxMinutes);
      if (!Number.isFinite(minutes) || minutes <= 0) {
        setErr(t('mds.flow.qtime.minutesInvalid'));
        return;
      }
      const pair = `${r.from}->${r.to}`;
      if (seen.has(pair)) {
        setErr(t('mds.flow.qtime.duplicate') + ': ' + `${codeOf(r.from)} → ${codeOf(r.to)}`);
        return;
      }
      seen.add(pair);
    }
    onDone(rows);
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="mds.flow.qtime.title"
      onOk={submit}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      width={640}
    >
      <div className="flow-qtime-list">
        {rows.map((r, idx) => (
          <div className="flow-qtime-row" key={idx}>
            <select
              className="flow-qtime-input"
              value={r.from}
              onChange={(e) => patchRow(idx, { from: e.target.value })}
            >
              <option value="">{t('mds.flow.qtime.from')}</option>
              {nodeOptions.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
            <span className="flow-qtime-arrow">→</span>
            <select
              className="flow-qtime-input"
              value={r.to}
              onChange={(e) => patchRow(idx, { to: e.target.value })}
            >
              <option value="">{t('mds.flow.qtime.to')}</option>
              {nodeOptions.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
            <input
              className="flow-qtime-input flow-qtime-minutes"
              type="number"
              min={1}
              placeholder="min"
              value={r.maxMinutes}
              onChange={(e) => patchRow(idx, { maxMinutes: e.target.value })}
            />
            <select
              className="flow-qtime-input flow-qtime-action"
              value={r.action}
              onChange={(e) => patchRow(idx, { action: e.target.value as QtimeDef['action'] })}
            >
              <option value="HOLD">HOLD</option>
              <option value="SCRAP">SCRAP</option>
            </select>
            <CimButton size="small" danger onClick={() => setRows((rs) => rs.filter((_, i) => i !== idx))}>
              ×
            </CimButton>
          </div>
        ))}
        <CimButton
          size="small"
          disabled={!nodes.length}
          onClick={() => setRows((rs) => [...rs, { from: '', to: '', maxMinutes: '', action: 'HOLD' }])}
        >
          {t('mds.flow.qtime.add')}
        </CimButton>
        <span className="flow-qtime-hint">{t('mds.flow.qtime.hint')}</span>
        {err ? <p className="cim-field-error form-err">{err}</p> : null}
      </div>
    </CimModal>
  );
}
