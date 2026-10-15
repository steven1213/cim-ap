import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '@/lib/api';
import { toast } from '@/store/toastStore';
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
        toast(t('designer.parseFailed') + '：' + (e as Error).message, 'err');
      }
    }).catch((e) => {
      toast((e as Error).message, 'err');
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [routeState.definitionId, routeState.readonly]);

  const canvasRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<DragState | null>(null);
  const linkHandleRef = useRef<HTMLSpanElement | null>(null);

  const nodeMap = new Map(model.nodes.map((n) => [n.id, n]));

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
    addNode(kind, x, y);
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
    return bezierPath(endpoints(s).rx, endpoints(s).ry, endpoints(tg).lx, endpoints(tg).ly);
  }

  function bezierPath(x1: number, y1: number, x2: number, y2: number) {
    const dist = Math.max(60, Math.abs(x2 - x1) * 0.5);
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
      toast(errs[0], 'err');
      return;
    }
    const xml = buildXml();
    setBusy(true);
    try {
      const def = await api.deployProcess({ processKey: processKey.trim(), processName: processName.trim(), bpmnXml: xml });
      toast(`已部署：${def.name} (v${def.version})`, 'ok');
      navigate('/definitions');
    } catch (e) {
      toast((e as Error).message || '部署失败', 'err');
    } finally {
      setBusy(false);
    }
  }

  const errs = errors();
  const sel = selectedNode ? nodeMap.get(selectedNode) : null;
  const selEdge = selectedEdge ? model.edges.find((e) => e.id === selectedEdge) : null;
  const selEdgeSource = selEdge ? nodeMap.get(selEdge.source) : null;
  const selEdgeTarget = selEdge ? nodeMap.get(selEdge.target) : null;

  return (
    <div className="designer">
      <div className="page-head">
        <div>
          <h1>{t('designer.title')}</h1>
          <p className="page-sub">{t('designer.sub')}</p>
        </div>
        <div className="designer-meta">
          <label className="field field-inline">
            <span className="field-label">{t('def.colKey')}</span>
            <input
              className="text-input"
              style={{ width: 180 }}
              value={processKey}
              onChange={(e) => setProcessKey(e.target.value)}
              placeholder="customApproval"
              disabled={readonly}
            />
          </label>
          <label className="field field-inline">
            <span className="field-label">{t('def.colName')}</span>
            <input
              className="text-input"
              style={{ width: 200 }}
              value={processName}
              onChange={(e) => setProcessName(e.target.value)}
              placeholder="自定义审批"
              disabled={readonly}
            />
          </label>
          <div className="designer-actions">
            {!readonly && (
              <>
                <button className="btn btn-sm" onClick={() => { setModel(sampleModel()); setSelectedNode(null); setSelectedEdge(null); }}>
                  {t('designer.loadSample')}
                </button>
                <button className="btn btn-sm" onClick={() => { setModel({ nodes: [], edges: [] }); setSelectedNode(null); setSelectedEdge(null); }}>
                  {t('common.clear')}
                </button>
              </>
            )}
            <button className="btn btn-sm" onClick={() => setShowXml(true)}>
              <IconCopy width={14} height={14} /> {t('designer.exportXml')}
            </button>
            {!readonly && (
              <button className="btn btn-primary btn-sm" disabled={busy || errs.length > 0} onClick={onDeploy}>
                <IconPlay width={14} height={14} /> {t('designer.deploy')}
              </button>
            )}
          </div>
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
              const sp = sn ? endpoints(sn) : null;
              return (
                <g key={e.id}>
                  <path d={d} className="edge-hit" onClick={() => { setSelectedEdge(e.id); setSelectedNode(null); }} />
                  <path
                    d={d}
                    className="edge-line"
                    style={{ stroke: isSel ? 'var(--err)' : 'var(--primary)' }}
                    markerEnd={isSel ? 'url(#arrow-sel)' : 'url(#arrow)'}
                  />
                  {isGwOut && sp && (() => {
                    const tg = nodeMap.get(e.target);
                    if (!tg) return null;
                    const tp = endpoints(tg);
                    // 与 bezierPath 同参数取曲线上的点（t≈0.42），两条出边起点相同、此处已分离
                    const dist = Math.max(60, Math.abs(tp.lx - sp.rx) * 0.5);
                    const t = 0.42;
                    const mt = 1 - t;
                    const lx = mt * mt * mt * sp.rx + 3 * mt * mt * t * (sp.rx + dist) + 3 * mt * t * t * (tp.lx - dist) + t * t * t * tp.lx;
                    const ly = mt * mt * mt * sp.ry + 3 * mt * mt * t * sp.ry + 3 * mt * t * t * tp.ly + t * t * t * tp.ly;
                    // 满足分支标在曲线上方、不满足标在下方，即使两目标同高也不会叠
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
                  <button
                    className="node-del"
                    title={t('common.delete')}
                    onPointerDown={(e) => e.stopPropagation()}
                    onClick={(e) => { e.stopPropagation(); setSelectedNode(n.id); deleteSelected(); }}
                  >
                    <IconTrash width={12} height={12} />
                  </button>
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
        <div className="modal-backdrop" onClick={() => setShowXml(false)}>
          <div className="modal modal-lg" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h3>{t('designer.exportXml')} · BPMN 2.0</h3>
              <button className="icon-btn" onClick={() => setShowXml(false)}><IconX width={16} height={16} /></button>
            </div>
            <pre className="xml-view">{buildXml()}</pre>
            <div className="modal-foot">
              <button className="btn" onClick={() => navigator.clipboard?.writeText(buildXml())}>{t('common.copy')}</button>
              <button className="btn btn-primary" onClick={() => setShowXml(false)}>{t('common.close')}</button>
            </div>
          </div>
        </div>
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

  return (
    <div className="props-form">
      <div className="props-title">{KIND_LABEL[node.kind]}</div>
      <div className="field">
        <label className="field-label">{t('designer.field.label')}</label>
        <input value={node.label} onChange={(e) => onChange({ label: e.target.value })} />
      </div>

      {node.kind === 'gateway' && (
        <>
          <div className="field">
            <label className="field-label">{t('designer.field.condition')}</label>
            <input value={node.condition || ''} onChange={(e) => onChange({ condition: e.target.value })} placeholder="${amount > 1000}" />
            <small>{t('designer.field.conditionHint')}</small>
          </div>
          <p className="props-note">{t('designer.gateway.hint')}</p>
        </>
      )}

      {node.kind === 'parallel' && <p className="props-note">{t('designer.parallel.hint')}</p>}
      {node.kind === 'join' && <p className="props-note">{t('designer.join.hint')}</p>}

      {node.kind === 'approval' && (
        <div className="field">
          <label className="field-label">{t('designer.field.assigneeVar')}</label>
          <input value={node.assigneeVar || ''} onChange={(e) => onChange({ assigneeVar: e.target.value })} placeholder="assignee" />
          <small>{t('designer.field.assigneeVarHint')}</small>
        </div>
      )}

      {isMulti && (
        <>
          <div className="field">
            <label className="field-label">{t('designer.field.approversVar')}</label>
            <input value={node.approversVar || ''} onChange={(e) => onChange({ approversVar: e.target.value })} placeholder="approvers" />
            <small>{t('designer.field.approversVarHint')}</small>
          </div>
          <div className="field">
            <label className="field-label">{t('designer.field.threshold')}</label>
            <div className="field-row">
              <select
                value={thresholdSelectVal()}
                disabled={node.kind === 'anyone'}
                onChange={(e) => {
                  const v = e.target.value;
                  onChange({ threshold: v === '__num__' ? (typeof node.threshold === 'number' ? node.threshold : 1) : (v as 'one' | 'half' | 'all') });
                }}
              >
                <option value="one">{t('designer.threshold.one')}</option>
                <option value="half">{t('designer.threshold.half')}</option>
                <option value="all">{t('designer.threshold.all')}</option>
                <option value="__num__">{t('designer.threshold.num')}</option>
              </select>
              {thresholdSelectVal() === '__num__' && (
                <input
                  type="number"
                  min={1}
                  value={typeof node.threshold === 'number' ? node.threshold : 1}
                  onChange={(e) => onChange({ threshold: thresholdOption(e.target.value) })}
                  style={{ width: 80 }}
                />
              )}
            </div>
            <small>{t('designer.field.thresholdHint')}</small>
          </div>
        </>
      )}

      {isApprovalKind && (
        <>
          <div className="field">
            <label className="field-label">{t('designer.field.candidateUsers')}</label>
            <input value={node.candidateUsers || ''} onChange={(e) => onChange({ candidateUsers: e.target.value })} placeholder="alice, bob" />
            <small>{t('designer.field.candidateUsersHint')}</small>
          </div>
          <div className="field">
            <label className="field-label">{t('designer.field.candidateGroups')}</label>
            <input value={node.candidateGroups || ''} onChange={(e) => onChange({ candidateGroups: e.target.value })} placeholder="manager, finance" />
            <small>{t('designer.field.candidateGroupsHint')}</small>
          </div>
          <div className="field-row">
            <div className="field" style={{ flex: 1 }}>
              <label className="field-label">{t('designer.field.dueDate')}</label>
              <input value={node.dueDate || ''} onChange={(e) => onChange({ dueDate: e.target.value })} placeholder="PT2H" />
            </div>
            <div className="field" style={{ width: 90 }}>
              <label className="field-label">{t('designer.field.priority')}</label>
              <input
                type="number"
                min={0}
                max={100}
                value={node.priority ?? ''}
                onChange={(e) => onChange({ priority: e.target.value === '' ? undefined : Number(e.target.value) })}
              />
            </div>
          </div>
        </>
      )}

      {node.kind !== 'start' && node.kind !== 'end' && (
        <button className="btn btn-danger btn-block btn-sm" onClick={onDelete}>
          <IconTrash width={14} height={14} /> {t('common.delete')}
        </button>
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
          <div className="field">
            <label className="field-label">{t('designer.edge.branch')}</label>
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
          </div>
          <p className="props-note">{t('designer.edge.conditionOnNode')}</p>
          {onEditSource && (
            <button className="btn btn-block btn-sm" onClick={onEditSource}>
              <IconPencil width={13} height={13} /> {t('designer.edge.editCondition')}
            </button>
          )}
        </>
      ) : sourceKind === 'parallel' ? (
        <p className="props-note">{t('designer.edge.parallelNoCond')}</p>
      ) : sourceKind === 'join' ? (
        <p className="props-note">{t('designer.edge.joinHint')}</p>
      ) : (
        <p className="props-note">{t('designer.edge.autoApprove')}</p>
      )}
      <button className="btn btn-danger btn-block btn-sm" onClick={onDelete}>
        <IconTrash width={14} height={14} /> {t('common.delete')}
      </button>
    </div>
  );
}
