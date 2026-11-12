import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CimSelect, CimInputNumber, CimInput, CimField, CimButton, CimModal, useToast } from '@cim/ui';
import api from '@/lib/api';
import {
  KIND_LABEL,
  NODE_H,
  NODE_W,
  PALETTE,
  uid,
  validateModel,
  type FlowEdge,
  type FlowModel,
  type FlowNode,
  type NodeKind,
} from '@/lib/flow/types';
import { toBpmn } from '@/lib/flow/toBpmn';
import { fromBpmn } from '@/lib/flow/fromBpmn';
import { IconPencil, IconTrash, IconPlus, IconPlay, IconCopy, IconX } from '@/components/Icons';

/** 路由 state：从「流程模板」列表进入时携带 definitionId（readonly=历史版本只可查看）。 */
interface DesignerRouteState {
  definitionId?: string;
  readonly?: boolean;
}

const GRID = 20;
const CANVAS_W = 2600;
const CANVAS_H = 1700;

interface DragState {
  id: string;
  startX: number;
  startY: number;
  origX: number;
  origY: number;
  moved: boolean;
}

interface LinkDrag {
  sourceId: string;
  x: number;
  y: number;
}

function sampleModel(): FlowModel {
  const s = uid('s');
  const a = uid('a');
  const g = uid('g');
  const p = uid('p');
  const l = uid('l');
  const f = uid('f');
  const j = uid('j');
  const r = uid('r');
  const e = uid('e');
  return {
    nodes: [
      { id: s, kind: 'start', x: 60, y: 300, label: '开始' },
      { id: a, kind: 'approval', x: 300, y: 280, label: '主管审批', assigneeVar: 'assignee' },
      { id: g, kind: 'gateway', x: 560, y: 280, label: '金额判断', condition: '${amount > 1000}' },
      { id: p, kind: 'parallel', x: 840, y: 180, label: '并行复核' },
      { id: l, kind: 'approval', x: 1120, y: 100, label: '法务审批', assigneeVar: 'legal' },
      { id: f, kind: 'approval', x: 1120, y: 280, label: '财务审批', assigneeVar: 'finance' },
      { id: j, kind: 'join', x: 1400, y: 180, label: '汇聚' },
      { id: r, kind: 'approval', x: 840, y: 460, label: '主管复核', assigneeVar: 'reviewer' },
      { id: e, kind: 'end', x: 1660, y: 280, label: '结束' },
    ],
    edges: [
      { id: uid('e'), source: s, target: a },
      { id: uid('e'), source: a, target: g },
      { id: uid('e'), source: g, target: p, branch: 'yes' },
      { id: uid('e'), source: g, target: r, branch: 'no' },
      { id: uid('e'), source: p, target: l },
      { id: uid('e'), source: p, target: f },
      { id: uid('e'), source: l, target: j },
      { id: uid('e'), source: f, target: j },
      { id: uid('e'), source: j, target: e },
      { id: uid('e'), source: r, target: e },
    ],
  };
}

export default function DesignerPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const toast = useToast();
  const location = useLocation();
  const routeState = (location.state || {}) as DesignerRouteState;

  const [model, setModel] = useState<FlowModel>(() => sampleModel());
  const [processKey, setProcessKey] = useState('customApproval');
  const [processName, setProcessName] = useState('自定义审批');
  const [selectedNode, setSelectedNode] = useState<string | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<string | null>(null);
  const [linkDrag, setLinkDrag] = useState<LinkDrag | null>(null);
  const [showXml, setShowXml] = useState(false);
  const [busy, setBusy] = useState(false);
  /** 只读模式（查看历史版本）：禁编辑、禁部署 */
  const [readonly, setReadonly] = useState(!!routeState.readonly);
  /** 已加载的 definitionId（防 StrictMode 双执行重复加载） */
  const loadedDefRef = useRef<string | null>(null);

  useEffect(() => {
    const defId = routeState.definitionId;
    if (!defId || loadedDefRef.current === defId) return;
    loadedDefRef.current = defId;
    api.getDefinitionXml(defId).then((def) => {
      setProcessKey(def.key);
      setProcessName(def.name || def.key);
      try {
        setModel(fromBpmn(def.bpmnXml));
        setReadonly(!!routeState.readonly);
      } catch (e) {
        toast.error(t('designer.parseFailed') + '：' + (e as Error).message);
      }
    }).catch((e) => {
      toast.error((e as Error).message);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [routeState.definitionId, routeState.readonly]);

  const canvasRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<DragState | null>(null);
  const linkHandleRef = useRef<HTMLSpanElement | null>(null);

  const nodeMap = new Map(model.nodes.map((n) => [n.id, n]));

  // 连线锚点索引：同一节点的多条出/入边沿节点边缘均匀分布，避免所有连线从同一点发出而互相覆盖。
  const outCount = new Map<string, number>();
  const inCount = new Map<string, number>();
  for (const e of model.edges) {
    outCount.set(e.source, (outCount.get(e.source) ?? 0) + 1);
    inCount.set(e.target, (inCount.get(e.target) ?? 0) + 1);
  }
  const outSeen = new Map<string, number>();
  const inSeen = new Map<string, number>();
  const outIdx = new Map<string, number>();
  const inIdx = new Map<string, number>();
  for (const e of model.edges) {
    const oi = outSeen.get(e.source) ?? 0;
    outIdx.set(e.id, oi); outSeen.set(e.source, oi + 1);
    const ii = inSeen.get(e.target) ?? 0;
    inIdx.set(e.id, ii); inSeen.set(e.target, ii + 1);
  }
  const anchorY = (n: FlowNode, idx: number, total: number): number =>
    total <= 1 ? n.y + NODE_H / 2 : n.y + 10 + (idx + 0.5) * (NODE_H - 20) / total;
  const sourcePt = (n: FlowNode, eid: string) => ({ x: n.x + NODE_W, y: anchorY(n, outIdx.get(eid) ?? 0, outCount.get(n.id) ?? 1) });
  const targetPt = (n: FlowNode, eid: string) => ({ x: n.x, y: anchorY(n, inIdx.get(eid) ?? 0, inCount.get(n.id) ?? 1) });

  // 节点防重叠：放置/移动后若与其它节点重叠，自动下移到空闲单元格（避免节点互相覆盖）。
  const overlaps = (a: FlowNode, x: number, y: number): boolean =>
    x < a.x + NODE_W && x + NODE_W > a.x && y < a.y + NODE_H && y + NODE_H > a.y;
  const freeSpot = (x: number, y: number): { x: number; y: number } => {
    let cx = x, cy = y, guard = 0;
    while (model.nodes.some((n) => overlaps(n, cx, cy)) && guard < 300) { cy += NODE_H + GRID; guard++; }
    return { x: cx, y: cy };
  };
  const resolveOverlap = (id: string) => {
    const self = nodeMap.get(id);
    if (!self) return;
    let { x, y } = self; let guard = 0;
    while (model.nodes.some((n) => n.id !== id && overlaps(n, x, y)) && guard < 300) { y += NODE_H + GRID; guard++; }
    if (y !== self.y) updateNode(id, { x, y });
  };

  // ---------- 节点操作 ----------
  function addNode(kind: NodeKind, x: number, y: number) {
    if (readonly) return;
    const snap = (v: number) => Math.round(v / GRID) * GRID;
    const id = uid(kind);
    const base: FlowNode = {
      id,
      kind,
      x: snap(x - NODE_W / 2),
      y: snap(y - NODE_H / 2),
      label: KIND_LABEL[kind],
    };
    if (kind === 'approval') base.assigneeVar = 'assignee';
    if (kind === 'anyone') { base.approversVar = 'approvers'; base.threshold = 'one'; }
    if (kind === 'countersign') { base.approversVar = 'approvers'; base.threshold = 'half'; }
    if (kind === 'gateway') base.condition = '${amount > 1000}';
    setModel((m) => ({ ...m, nodes: [...m.nodes, base] }));
    setSelectedNode(id);
    setSelectedEdge(null);
  }

  function updateNode(id: string, patch: Partial<FlowNode>) {
    setModel((m) => ({ ...m, nodes: m.nodes.map((n) => (n.id === id ? { ...n, ...patch } : n)) }));
  }

  function updateEdge(id: string, patch: Partial<FlowEdge>) {
    setModel((m) => ({ ...m, edges: m.edges.map((e) => (e.id === id ? { ...e, ...patch } : e)) }));
  }

  function createEdge(source: string, target: string) {
    if (source === target) return;
    const exists = model.edges.some((e) => e.source === source && e.target === target);
    if (exists) return;
    // 条件分支出边自动分配「满足 / 不满足」语义：先补缺口（无 yes 先 yes，再补 no）
    const sn = nodeMap.get(source);
    let branch: FlowEdge['branch'];
    if (sn?.kind === 'gateway') {
      const outs = model.edges.filter((e) => e.source === source);
      const hasYes = outs.some((e) => e.branch === 'yes');
      const hasNo = outs.some((e) => e.branch === 'no');
      branch = !hasYes ? 'yes' : !hasNo ? 'no' : 'yes';
    }
    setModel((m) => ({
      ...m,
      edges: [...m.edges, { id: uid('e'), source, target, ...(branch ? { branch } : {}) }],
    }));
  }

  function deleteSelected() {
    if (readonly) return;
    if (selectedNode) {
      setModel((m) => ({
        nodes: m.nodes.filter((n) => n.id !== selectedNode),
        edges: m.edges.filter((e) => e.source !== selectedNode && e.target !== selectedNode),
      }));
      setSelectedNode(null);
    } else if (selectedEdge) {
      setModel((m) => ({ ...m, edges: m.edges.filter((e) => e.id !== selectedEdge) }));
      setSelectedEdge(null);
    }
  }

  // ---------- 拖拽：palette → canvas ----------
  function onPaletteDragStart(e: React.DragEvent, kind: NodeKind) {
    e.dataTransfer.setData('text/flow-kind', kind);
    e.dataTransfer.effectAllowed = 'copy';
  }

  function onCanvasDrop(e: React.DragEvent) {
    e.preventDefault();
    const kind = e.dataTransfer.getData('text/flow-kind') as NodeKind;
    if (!kind) return;
    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = e.clientX - rect.left + (canvasRef.current?.scrollLeft ?? 0);
    const y = e.clientY - rect.top + (canvasRef.current?.scrollTop ?? 0);
    const spot = freeSpot(x - NODE_W / 2, y - NODE_H / 2);
    addNode(kind, spot.x + NODE_W / 2, spot.y + NODE_H / 2);
  }

  // ---------- 节点移动 / 点击 ----------
  function onNodePointerDown(e: React.PointerEvent, n: FlowNode) {
    if ((e.target as HTMLElement).closest('.link-handle, .node-del')) return;
    e.stopPropagation();
    dragRef.current = { id: n.id, startX: e.clientX, startY: e.clientY, origX: n.x, origY: n.y, moved: false };
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  }

  function onNodePointerMove(e: React.PointerEvent, n: FlowNode) {
    if (readonly) return;
    const d = dragRef.current;
    if (!d || d.id !== n.id) return;
    const dx = e.clientX - d.startX;
    const dy = e.clientY - d.startY;
    if (!d.moved && Math.abs(dx) <= 3 && Math.abs(dy) <= 3) return;
    d.moved = true;
    updateNode(n.id, { x: d.origX + dx, y: d.origY + dy });
  }

  function onNodePointerUp(n: FlowNode) {
    const d = dragRef.current;
    if (d && d.id === n.id) {
      if (!d.moved) onNodeActivate(n.id);
      else resolveOverlap(n.id);
      dragRef.current = null;
    }
  }

  function onNodeActivate(id: string) {
    setSelectedNode(id);
    setSelectedEdge(null);
  }

  // ---------- 拖拽连线 ----------
  /** 落点命中容差：节点矩形外扩像素数，降低「差一点没连上」的挫败感 */
  const HIT_PAD = 24;

  function toCanvasPoint(clientX: number, clientY: number) {
    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return { x: 0, y: 0 };
    return {
      x: clientX - rect.left + (canvasRef.current?.scrollLeft ?? 0),
      y: clientY - rect.top + (canvasRef.current?.scrollTop ?? 0),
    };
  }

  function hitNode(p: { x: number; y: number }, excludeId?: string): FlowNode | null {
    for (const n of model.nodes) {
      if (n.id === excludeId) continue;
      if (p.x >= n.x - HIT_PAD && p.x <= n.x + NODE_W + HIT_PAD
        && p.y >= n.y - HIT_PAD && p.y <= n.y + NODE_H + HIT_PAD) return n;
    }
    return null;
  }

  function findNodeAt(cx: number, cy: number, excludeId?: string) {
    return hitNode(toCanvasPoint(cx, cy), excludeId);
  }

  function onLinkHandlePointerDown(e: React.PointerEvent, n: FlowNode) {
    if (readonly) return;
    e.stopPropagation();
    e.preventDefault();
    const handle = e.currentTarget as HTMLSpanElement;
    handle.setPointerCapture(e.pointerId);
    linkHandleRef.current = handle;
    const p = toCanvasPoint(e.clientX, e.clientY);
    setLinkDrag({ sourceId: n.id, x: p.x, y: p.y });
    setSelectedNode(null);
    setSelectedEdge(null);
  }

  function onLinkHandlePointerMove(e: React.PointerEvent) {
    if (!linkDrag) return;
    const p = toCanvasPoint(e.clientX, e.clientY);
    setLinkDrag({ ...linkDrag, x: p.x, y: p.y });
  }

  function onLinkHandlePointerUp(e: React.PointerEvent) {
    const handle = linkHandleRef.current;
    if (handle) {
      try { handle.releasePointerCapture(e.pointerId); } catch { /* ignore */ }
      linkHandleRef.current = null;
    }
    if (!linkDrag) return;
    const source = nodeMap.get(linkDrag.sourceId);
    const target = findNodeAt(e.clientX, e.clientY, linkDrag.sourceId);
    if (source && target) createEdge(source.id, target.id);
    setLinkDrag(null);
  }

  // ---------- 连线控制点 ----------
  function endpoints(n: FlowNode) {
    return { rx: n.x + NODE_W, ry: n.y + NODE_H / 2, lx: n.x, ly: n.y + NODE_H / 2 };
  }

  function edgePath(e: FlowEdge): string {
    const s = nodeMap.get(e.source);
    const tg = nodeMap.get(e.target);
    if (!s || !tg) return '';
    const sp = sourcePt(s, e.id);
    const tp = targetPt(tg, e.id);
    return bezierPath(sp.x, sp.y, tp.x, tp.y);
  }

  function bezierPath(x1: number, y1: number, x2: number, y2: number) {
    const dist = Math.min(260, Math.max(60, Math.abs(x2 - x1) * 0.5));
    return `M ${x1} ${y1} C ${x1 + dist} ${y1}, ${x2 - dist} ${y2}, ${x2} ${y2}`;
  }

  // ---------- 校验 + 部署 + 导出 ----------
  function errors(): string[] {
    const keyOk = /^[a-zA-Z][a-zA-Z0-9_]*$/.test(processKey);
    const errs = validateModel(model);
    if (!keyOk) errs.unshift('流程 Key 须以字母开头，仅含字母数字下划线');
    if (!processName.trim()) errs.unshift('流程名称不能为空');
    return errs;
  }

  function buildXml(): string {
    return toBpmn(model, processKey.trim(), processName.trim());
  }

  async function onDeploy() {
    if (readonly) return;
    const errs = errors();
    if (errs.length) {
      toast.error(errs[0]);
      return;
    }
    const xml = buildXml();
    setBusy(true);
    try {
      const def = await api.deployProcess({ processKey: processKey.trim(), processName: processName.trim(), bpmnXml: xml });
      toast.success(`${t('designer.deploy')} ✓ ${def.name} (v${def.version})`);
      navigate('/definitions');
    } catch (e) {
      toast.error((e as Error).message || t('designer.deploy'));
    } finally {
      setBusy(false);
    }
  }

  const errs = errors();
  const sel = selectedNode ? nodeMap.get(selectedNode) : null;
  const selEdge = selectedEdge ? model.edges.find((e) => e.id === selectedEdge) : null;
  const selEdgeSource = selEdge ? nodeMap.get(selEdge.source) : null;
  const selEdgeTarget = selEdge ? nodeMap.get(selEdge.target) : null;

  /**
   * 复制 XML。
   *
   * <p>不能「先 toast 再写」：`navigator.clipboard` 在非安全上下文或未授权时直接拒绝，
   * 先提示成功会让用户以为剪贴板里已经有内容（headless 里 100% 复现）。</p>
   */
  async function copyXml() {
    try {
      if (!navigator.clipboard) throw new Error('clipboard unavailable');
      await navigator.clipboard.writeText(buildXml());
      toast.success(t('common.copied'));
    } catch {
      toast.error(t('common.copy') + ' ✗');
    }
  }

  return (
    <div className="designer">
            {/* 设计器工具条：标题/说明已由页签承载，这里只保留功能性控件 */}
      <div className="designer-meta">
        <CimField compact label={t('def.colKey')}>
          <CimInput width={180} value={processKey} onChange={setProcessKey} placeholder="customApproval" disabled={readonly} />
        </CimField>
        <CimField compact label={t('def.colName')}>
          <CimInput width={200} value={processName} onChange={setProcessName} placeholder="自定义审批" disabled={readonly} />
        </CimField>
        <div className="designer-actions">
          {!readonly && (
            <>
              <CimButton size="small" onClick={() => { setModel(sampleModel()); setSelectedNode(null); setSelectedEdge(null); }}>
                {t('designer.loadSample')}
              </CimButton>
              <CimButton size="small" onClick={() => { setModel({ nodes: [], edges: [] }); setSelectedNode(null); setSelectedEdge(null); }}>
                {t('common.clear')}
              </CimButton>
            </>
          )}
          <CimButton size="small" icon={<IconCopy width={14} height={14} />} onClick={() => setShowXml(true)}>
            {t('designer.exportXml')}
          </CimButton>
          {!readonly && (
            <CimButton
              size="small"
              type="primary"
              icon={<IconPlay width={14} height={14} />}
              disabled={busy || errs.length > 0}
              onClick={onDeploy}
            >
              {t('designer.deploy')}
            </CimButton>
          )}
        </div>
      </div>

      {readonly && (
        <div className="designer-hint" style={{ borderColor: 'var(--warn)', color: 'var(--warn)' }}>
          {t('designer.readonlyBanner')}
        </div>
      )}

      {linkDrag && (
        <div className="designer-hint">
          {t('designer.linking')} {t('designer.dragRelease')}
        </div>
      )}
      {errs.length > 0 && (
        <div className="designer-errors">
          <IconX width={14} height={14} /> {errs[0]}
        </div>
      )}

      <div className="designer-body">
        <aside className="palette">
          <div className="palette-title">{t('designer.palette')}</div>
          {PALETTE.map((p) => (
            <div
              key={p.kind}
              className="palette-item"
              draggable
              onDragStart={(e) => onPaletteDragStart(e, p.kind)}
              onDoubleClick={() => addNode(p.kind, 400, 300)}
              title={p.desc}
            >
              <span className="palette-dot" style={{ background: p.color }} />
              <span className="palette-text">
                <b>{p.label}</b>
                <small>{p.desc}</small>
              </span>
            </div>
          ))}
          <div className="palette-foot">{t('designer.paletteHint')}</div>
        </aside>

        <div
          className="canvas"
          ref={canvasRef}
          onDragOver={(e) => e.preventDefault()}
          onDrop={onCanvasDrop}
          onPointerDown={(e) => {
            if (e.target === canvasRef.current) {
              setSelectedNode(null);
              setSelectedEdge(null);
            }
          }}
        >
          <svg className="edge-layer" width={CANVAS_W} height={CANVAS_H}>
            <defs>
              {/* 注意：SVG presentation attribute 不支持 var()，颜色必须写在 style 上 */}
              <marker id="arrow" markerUnits="userSpaceOnUse" markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto">
                <path d="M0,0 L12,6 L0,12 z" style={{ fill: 'var(--primary)' }} />
              </marker>
              <marker id="arrow-sel" markerUnits="userSpaceOnUse" markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto">
                <path d="M0,0 L12,6 L0,12 z" style={{ fill: 'var(--err)' }} />
              </marker>
            </defs>
            {model.edges.map((e) => {
              const d = edgePath(e);
              if (!d) return null;
              const isSel = selectedEdge === e.id;
              const sn = nodeMap.get(e.source);
              const isGwOut = sn?.kind === 'gateway' && !!e.branch;
              return (
                <g key={e.id}>
                  <path d={d} className="edge-hit" onClick={() => { setSelectedEdge(e.id); setSelectedNode(null); }} />
                  <path
                    d={d}
                    className="edge-line"
                    style={{ stroke: isSel ? 'var(--err)' : 'var(--primary)' }}
                    markerEnd={isSel ? 'url(#arrow-sel)' : 'url(#arrow)'}
                  />
                  {isGwOut && sn && (() => {
                    const sp = sourcePt(sn, e.id);
                    const tg = nodeMap.get(e.target);
                    if (!tg) return null;
                    const tp = targetPt(tg, e.id);
                    const dist = Math.min(260, Math.max(60, Math.abs(tp.x - sp.x) * 0.5));
                    const t = 0.42;
                    const mt = 1 - t;
                    const lx = mt * mt * mt * sp.x + 3 * mt * mt * t * (sp.x + dist) + 3 * mt * t * t * (tp.x - dist) + t * t * t * tp.x;
                    const ly = mt * mt * mt * sp.y + 3 * mt * mt * t * sp.y + 3 * mt * t * t * tp.y + t * t * t * tp.y;
                    // 满足分支标签在曲线上方、不满足在下方；锚点已分离，双目标同高也不会叠线
                    const dy = e.branch === 'yes' ? -12 : 18;
                    return (
                      <text
                        className="edge-label"
                        x={lx}
                        y={ly + dy}
                        textAnchor="middle"
                        style={{ fill: e.branch === 'yes' ? 'var(--ok)' : 'var(--err)' }}
                      >
                        {e.branch === 'yes' ? '✓ 满足' : '✗ 不满足'}
                      </text>
                    );
                  })()}
                </g>
              );
            })}
            {linkDrag && (() => {
              const s = nodeMap.get(linkDrag.sourceId);
              if (!s) return null;
              const sp = endpoints(s);
              const d = bezierPath(sp.rx, sp.ry, linkDrag.x, linkDrag.y);
              return (
                <path
                  key="link-drag"
                  d={d}
                  className="edge-line temp-edge"
                  style={{ stroke: 'var(--warn)' }}
                  strokeDasharray="6 4"
                />
              );
            })()}
          </svg>

          {model.nodes.map((n) => {
            const isSel = selectedNode === n.id;
            const isLinkTarget = linkDrag && linkDrag.sourceId !== n.id
              && hitNode(linkDrag, linkDrag.sourceId)?.id === n.id;
            return (
              <div
                key={n.id}
                className={`flow-node ${n.kind}${isSel ? ' selected' : ''}${isLinkTarget ? ' link-target' : ''}`}
                style={{ left: n.x, top: n.y, width: NODE_W, height: NODE_H }}
                onPointerDown={(e) => onNodePointerDown(e, n)}
                onPointerMove={(e) => onNodePointerMove(e, n)}
                onPointerUp={() => onNodePointerUp(n)}
              >
                {n.kind !== 'start' && <span className="in-port" />}
                <span className="node-kind">{KIND_LABEL[n.kind]}</span>
                <span className="node-label">{n.label}</span>
                {n.kind !== 'start' && n.kind !== 'end' && (
                  <CimButton
                    bare
                    className="node-del"
                    title={t('common.delete')}
                    onPointerDown={(e) => e.stopPropagation()}
                    onClick={(e) => { e.stopPropagation(); setSelectedNode(n.id); deleteSelected(); }}
                  >
                    <IconTrash width={12} height={12} />
                  </CimButton>
                )}
                {n.kind !== 'end' && (
                  <span
                    className="link-handle"
                    title={t('designer.linkHint')}
                    onPointerDown={(e) => onLinkHandlePointerDown(e, n)}
                    onPointerMove={(e) => onLinkHandlePointerMove(e)}
                    onPointerUp={(e) => onLinkHandlePointerUp(e)}
                  >
                    <IconPlus width={12} height={12} />
                  </span>
                )}
              </div>
            );
          })}
        </div>

        <aside className="props-panel">
          {sel ? (
            <NodeProps
              node={sel}
              onChange={(patch) => updateNode(sel.id, patch)}
              onDelete={() => deleteSelected()}
            />
          ) : selEdge ? (
            <EdgeProps
              edge={selEdge}
              sourceNode={selEdgeSource ?? undefined}
              targetNode={selEdgeTarget ?? undefined}
              onEditSource={selEdgeSource ? () => { setSelectedNode(selEdgeSource.id); setSelectedEdge(null); } : undefined}
              onChange={(patch) => updateEdge(selEdge.id, patch)}
              onDelete={() => deleteSelected()}
            />
          ) : (
            <div className="props-empty">
              <IconPencil width={22} height={22} />
              <p>{t('designer.propsHint')}</p>
            </div>
          )}
        </aside>
      </div>

      {showXml && (
        <CimModal
          open
          onClose={() => setShowXml(false)}
          title={`${t('designer.exportXml')} · BPMN 2.0`}
          width={900}
          footer={
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
              <CimButton
                icon={<IconCopy width={13} height={13} />}
                onClick={() => void copyXml()}
              >
                {t('common.copy')}
              </CimButton>
              <CimButton type="primary" onClick={() => setShowXml(false)}>
                {t('common.close')}
              </CimButton>
            </div>
          }
        >
          <pre className="xml-view">{buildXml()}</pre>
        </CimModal>
      )}
    </div>
  );
}

function NodeProps({ node, onChange, onDelete }: {
  node: FlowNode;
  onChange: (patch: Partial<FlowNode>) => void;
  onDelete: () => void;
}) {
  const { t } = useTranslation();
  const isMulti = node.kind === 'countersign' || node.kind === 'anyone';
  const isApprovalKind = node.kind === 'approval' || node.kind === 'anyone' || node.kind === 'countersign';

  function thresholdOption(v: string) {
    if (v === 'one' || v === 'half' || v === 'all') return v;
    const n = Number(v);
    return Number.isNaN(n) ? 'half' : n;
  }

  function thresholdSelectVal(): string {
    const v = node.threshold;
    if (v === 'one' || v === 'half' || v === 'all') return v;
    if (typeof v === 'number') return '__num__';
    return 'half';
  }

  /** 推导当前节点的选人方式（G6 / D8）：显式 candidateType 优先，否则按已填字段回退。 */
  function candidateTypeVal(): 'assignee' | 'user' | 'role' {
    if (node.candidateType === 'assignee' || node.candidateType === 'user' || node.candidateType === 'role') {
      return node.candidateType;
    }
    if (node.candidateGroups?.trim()) return 'role';
    if (node.candidateUsers?.trim()) return 'user';
    return 'assignee';
  }

  return (
    <div className="props-form">
      <div className="props-title">{KIND_LABEL[node.kind]}</div>
      <CimField label={t('designer.field.label')}>
        <CimInput value={node.label} onChange={(v) => onChange({ label: v })} />
      </CimField>

      {node.kind === 'gateway' && (
        <>
          <CimField label={t('designer.field.condition')}>
            <CimInput value={node.condition || ''} onChange={(v) => onChange({ condition: v })} placeholder="${amount > 1000}" />
            <small>{t('designer.field.conditionHint')}</small>
          </CimField>
          <p className="props-note">{t('designer.gateway.hint')}</p>
        </>
      )}

      {node.kind === 'parallel' && <p className="props-note">{t('designer.parallel.hint')}</p>}
      {node.kind === 'join' && <p className="props-note">{t('designer.join.hint')}</p>}

      {node.kind === 'end' && (
        <CimField label={t('designer.field.endResult')}>
          <CimSelect
            value={node.result === 'REJECTED' ? 'REJECTED' : 'APPROVED'}
            onChange={(v) => onChange({ result: (v === 'REJECTED' ? 'REJECTED' : 'APPROVED') as 'APPROVED' | 'REJECTED' })}
            options={[
              { value: 'APPROVED', label: t('designer.endResult.approved') },
              { value: 'REJECTED', label: t('designer.endResult.rejected') },
            ]}
          />
          <small>{t('designer.field.endResultHint')}</small>
        </CimField>
      )}

      {node.kind === 'approval' && (
        <CimField label={t('designer.field.candidateType')}>
          <CimSelect
            value={candidateTypeVal()}
            onChange={(raw) => {
              const v = (raw ?? 'assignee') as 'assignee' | 'user' | 'role';
              onChange({
                candidateType: v,
                candidateUsers: v === 'role' ? '' : node.candidateUsers,
                candidateGroups: v === 'user' ? '' : node.candidateGroups,
                assigneeVar: v === 'assignee' ? (node.assigneeVar || 'assignee') : '',
              });
            }}
            options={[
              { value: 'assignee', label: t('designer.candidateType.assignee') },
              { value: 'user', label: t('designer.candidateType.user') },
              { value: 'role', label: t('designer.candidateType.role') },
            ]}
          />
          <small>{t('designer.field.candidateTypeHint')}</small>
        </CimField>
      )}

      {node.kind === 'approval' && candidateTypeVal() === 'assignee' && (
        <CimField label={t('designer.field.assigneeVar')}>
          <CimInput value={node.assigneeVar || ''} onChange={(v) => onChange({ assigneeVar: v })} placeholder="assignee" />
          <small>{t('designer.field.assigneeVarHint')}</small>
        </CimField>
      )}

      {node.kind === 'approval' && candidateTypeVal() === 'user' && (
        <CimField label={t('designer.field.candidateUsers')}>
          <CimInput value={node.candidateUsers || ''} onChange={(v) => onChange({ candidateUsers: v })} placeholder="alice, bob" />
          <small>{t('designer.field.candidateUsersHint')}</small>
        </CimField>
      )}

      {node.kind === 'approval' && candidateTypeVal() === 'role' && (
        <CimField label={t('designer.field.candidateGroups')}>
          <CimInput value={node.candidateGroups || ''} onChange={(v) => onChange({ candidateGroups: v })} placeholder="manager, finance" />
          <small>{t('designer.field.candidateGroupsHint')}</small>
        </CimField>
      )}

      {isMulti && (
        <>
          <CimField label={t('designer.field.approversVar')}>
            <CimInput value={node.approversVar || ''} onChange={(v) => onChange({ approversVar: v })} placeholder="approvers" />
            <small>{t('designer.field.approversVarHint')}</small>
          </CimField>
          <CimField label={t('designer.field.threshold')}>
            <div className="field-row">
              <CimSelect
                value={thresholdSelectVal()}
                disabled={node.kind === 'anyone'}
                onChange={(raw) => {
                  const v = String(raw ?? 'one');
                  onChange({ threshold: v === '__num__' ? (typeof node.threshold === 'number' ? node.threshold : 1) : (v as 'one' | 'half' | 'all') });
                }}
                options={[
                  { value: 'one', label: t('designer.threshold.one') },
                  { value: 'half', label: t('designer.threshold.half') },
                  { value: 'all', label: t('designer.threshold.all') },
                  { value: '__num__', label: t('designer.threshold.num') },
                ]}
              />
              {thresholdSelectVal() === '__num__' && (
                <CimInputNumber
                  min={1}
                  width={80}
                  value={typeof node.threshold === 'number' ? node.threshold : 1}
                  onChange={(v) => onChange({ threshold: thresholdOption(String(v ?? 1)) })}
                />
              )}
            </div>
            <small>{t('designer.field.thresholdHint')}</small>
          </CimField>
        </>
      )}

      {isApprovalKind && (
        <div className="field-row">
          <CimField label={t('designer.field.dueDate')} style={{ flex: 1 }}>
            <CimInput value={node.dueDate || ''} onChange={(v) => onChange({ dueDate: v })} placeholder="PT2H" />
          </CimField>
          <CimField label={t('designer.field.priority')} style={{ width: 90 }}>
            <CimInputNumber
              min={0}
              max={100}
              value={node.priority ?? null}
              onChange={(v) => onChange({ priority: v ?? undefined })}
            />
          </CimField>
        </div>
      )}

      {node.kind !== 'start' && node.kind !== 'end' && (
        <CimButton danger size="small" block onClick={onDelete}>
          <IconTrash width={14} height={14} /> {t('common.delete')}
        </CimButton>
      )}
    </div>
  );
}

function EdgeProps({ edge, sourceNode, targetNode, onEditSource, onChange, onDelete }: {
  edge: FlowEdge;
  sourceNode?: FlowNode;
  targetNode?: FlowNode;
  onEditSource?: () => void;
  onChange: (patch: Partial<FlowEdge>) => void;
  onDelete: () => void;
}) {
  const { t } = useTranslation();
  const sourceKind = sourceNode?.kind;
  return (
    <div className="props-form">
      <div className="props-title">{t('designer.edge.title')}</div>
      <div className="props-row edge-route">
        <span className="tag">{sourceNode?.label ?? '?'}</span>
        <span className="edge-route-arrow">→</span>
        <span className="tag">{targetNode?.label ?? t('designer.edge.toTarget')}</span>
      </div>
      {sourceKind === 'gateway' ? (
        <>
          <CimField label={t('designer.edge.branch')}>
            <label className={`branch-option branch-yes${edge.branch === 'no' ? '' : ' active'}`}>
              <input
                type="radio"
                name={`branch-${edge.id}`}
                checked={edge.branch !== 'no'}
                onChange={() => onChange({ branch: 'yes' })}
              />
              <span className="branch-icon">✓</span>
              <span className="branch-text">
                <b>{t('designer.branch.yesTitle')}</b>
                <small>{t('designer.branch.yesDesc')}</small>
              </span>
            </label>
            <label className={`branch-option branch-no${edge.branch === 'no' ? ' active' : ''}`}>
              <input
                type="radio"
                name={`branch-${edge.id}`}
                checked={edge.branch === 'no'}
                onChange={() => onChange({ branch: 'no' })}
              />
              <span className="branch-icon">✗</span>
              <span className="branch-text">
                <b>{t('designer.branch.noTitle')}</b>
                <small>{t('designer.branch.noDesc')}</small>
              </span>
            </label>
          </CimField>
          <p className="props-note">{t('designer.edge.conditionOnNode')}</p>
          {onEditSource && (
            <CimButton type="primary" size="small" block onClick={onEditSource}>
              <IconPencil width={13} height={13} /> {t('designer.edge.editCondition')}
            </CimButton>
          )}
        </>
      ) : sourceKind === 'parallel' ? (
        <p className="props-note">{t('designer.edge.parallelNoCond')}</p>
      ) : sourceKind === 'join' ? (
        <p className="props-note">{t('designer.edge.joinHint')}</p>
      ) : (
        <p className="props-note">{t('designer.edge.autoApprove')}</p>
      )}
      <CimButton danger size="small" block onClick={onDelete}>
        <IconTrash width={14} height={14} /> {t('common.delete')}
      </CimButton>
    </div>
  );
}
