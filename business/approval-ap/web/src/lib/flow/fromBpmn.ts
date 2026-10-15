// BPMN 2.0 XML → 流程设计器模型（fromBpmn）。
//
// <p>解析规则与 {@link toBpmn} 的导出规则互逆：
// <ul>
//   <li>自动生成的「审批结果」网关（gw_*）与驳回终点（reject_*）不还原为节点，
//       其「通过」出边还原为审批节点的直接出边；</li>
//   <li>排他网关：出边 conditionExpression 还原为节点 condition（yes），default 出边为 no；</li>
//   <li>userTask：multiInstance + countersignListener → 会签/或签（completionCondition 反解阈值），
//       否则单人审批（flowable:assignee 反解变量名）；</li>
//   <li>parallelGateway：出度 ≥2 → 并行分支，入度 ≥2 → 并行汇聚；</li>
//   <li>布局：从开始节点 BFS 分层，简单网格排布。</li>
// </ul></p>
import type { FlowEdge, FlowNode, NodeKind } from './types';
import { uid } from './types';

interface Parsed {
  nodes: FlowNode[];
  edges: FlowEdge[];
}

function thresholdOf(cc: string): 'one' | 'half' | 'all' | number {
  const m = cc.match(/approvedCount\s*>=\s*(.+?)(?:\s*&&|$)/);
  const expr = m?.[1]?.trim() ?? '';
  if (expr === '1') return 'one';
  if (expr === 'approvers.size()') return 'all';
  if (expr === 'approvalThreshold') return 'half';
  const num = Number(expr);
  return Number.isFinite(num) && expr !== '' ? num : 'half';
}

/** varName 从 "${xxx}" 表达式中提取变量名。 */
function varOf(expr: string | null): string | undefined {
  const m = expr?.match(/^\$\{(.+)\}$/);
  return m?.[1]?.trim() || undefined;
}

export function fromBpmn(xml: string): Parsed {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  if (doc.querySelector('parsererror')) {
    throw new Error('BPMN XML 解析失败');
  }

  // ---------- 收集 BPMN 元素 ----------
  const el = (name: string) => Array.from(doc.getElementsByTagNameNS('*', name));
  const starts = el('startEvent');
  const ends = el('endEvent');
  const tasks = el('userTask');
  const exclGateways = el('exclusiveGateway');
  const parGateways = el('parallelGateway');
  const flows = el('sequenceFlow');

  // BPMN id → 模型节点 id
  const idMap = new Map<string, string>();
  const nodes: FlowNode[] = [];
  const edges: FlowEdge[] = [];

  const register = (bpmnId: string, make: (modelId: string) => FlowNode): string => {
    const existing = idMap.get(bpmnId);
    if (existing) return existing;
    const mid = uid('n');
    idMap.set(bpmnId, mid);
    nodes.push(make(mid));
    return mid;
  };

  // 开始节点
  const startEl = starts[0];
  if (startEl) register(startEl.getAttribute('id') || 'start', (mid) => ({
    id: mid, kind: 'start', x: 0, y: 0, label: startEl.getAttribute('name') || '开始',
  }));

  // 结束节点：跳过自动驳回终点（id=reject_* 或 REJECT listener）
  for (const end of ends) {
    const eid = end.getAttribute('id') || '';
    if (eid.startsWith('reject_')) continue;
    const isReject = Array.from(end.getElementsByTagNameNS('*', 'executionListener'))
      .some((l) => (l.getAttribute('expression') || '').includes("'REJECT'"));
    if (isReject) continue;
    register(eid || 'end', (mid) => ({
      id: mid, kind: 'end', x: 0, y: 0, label: end.getAttribute('name') || '结束',
    }));
  }

  // userTask：会签/或签（multiInstance）或单人审批
  for (const t of tasks) {
    const tid = t.getAttribute('id') || uid('b');
    const label = t.getAttribute('name') || '审批';
    const candidateUsers = t.getAttribute('flowable:candidateUsers') || undefined;
    const candidateGroups = t.getAttribute('flowable:candidateGroups') || undefined;
    const dueDate = t.getAttribute('flowable:dueDate') || undefined;
    const prio = t.getAttribute('flowable:priority');
    const priority = prio != null && prio !== '' ? Number(prio) : undefined;

    const multi = t.getElementsByTagNameNS('*', 'multiInstanceLoopCharacteristics')[0];
    let kind: NodeKind = 'approval';
    let extra: Partial<FlowNode> = {};
    if (multi) {
      kind = 'countersign';
      const collection = varOf(multi.getAttribute('flowable:collection'));
      const cc = multi.getElementsByTagNameNS('*', 'completionCondition')[0]?.textContent || '';
      extra = { approversVar: collection || 'approvers', threshold: thresholdOf(cc) };
    } else {
      extra = { assigneeVar: varOf(t.getAttribute('flowable:assignee')) || 'assignee' };
    }
    if (candidateUsers) extra.candidateUsers = candidateUsers;
    if (candidateGroups) extra.candidateGroups = candidateGroups;
    if (dueDate) extra.dueDate = dueDate;
    if (priority != null && !Number.isNaN(priority)) extra.priority = priority;

    register(tid, (mid) => ({ id: mid, kind, x: 0, y: 0, label, ...extra }));
  }

  // 排他网关：跳过自动审批结果网关 gw_*；其余为条件分支（条件在节点上）
  const gwDefault = new Map<string, string>(); // bpmnId → default flow id
  for (const g of exclGateways) {
    const gid = g.getAttribute('id') || '';
    if (gid.startsWith('gw_')) continue;
    const def = g.getAttribute('default');
    if (def) gwDefault.set(gid, def);
    register(gid, (mid) => ({
      id: mid, kind: 'gateway', x: 0, y: 0, label: g.getAttribute('name') || '条件分支',
    }));
  }

  // 并行网关：出度≥2 → 分支，入度≥2 → 汇聚
  const outDegree = new Map<string, number>();
  const inDegree = new Map<string, number>();
  for (const f of flows) {
    const s = f.getAttribute('sourceRef') || '';
    const t = f.getAttribute('targetRef') || '';
    outDegree.set(s, (outDegree.get(s) || 0) + 1);
    inDegree.set(t, (inDegree.get(t) || 0) + 1);
  }
  for (const g of parGateways) {
    const gid = g.getAttribute('id') || '';
    const kind: NodeKind = (outDegree.get(gid) || 0) >= 2 ? 'parallel' : 'join';
    register(gid, (mid) => ({
      id: mid, kind, x: 0, y: 0, label: g.getAttribute('name') || (kind === 'parallel' ? '并行分支' : '并行汇聚'),
    }));
  }

  // ---------- 连线 ----------
  // 网关 yes 出边条件 → 回填节点 condition
  const conditionOfGw = new Map<string, string>();
  for (const f of flows) {
    const s = f.getAttribute('sourceRef') || '';
    const condEl = f.getElementsByTagNameNS('*', 'conditionExpression')[0];
    if (condEl && idMap.has(s) && nodes.find((n) => n.id === idMap.get(s))?.kind === 'gateway') {
      conditionOfGw.set(s, (condEl.textContent || '').trim());
    }
  }
  for (const [gid, cond] of conditionOfGw) {
    const node = nodes.find((n) => n.id === idMap.get(gid));
    if (node) node.condition = cond;
  }

  const defaultFlowIds = new Set(gwDefault.values());

  for (const f of flows) {
    const fid = f.getAttribute('id') || '';
    const s = f.getAttribute('sourceRef') || '';
    const t = f.getAttribute('targetRef') || '';
    const condEl = f.getElementsByTagNameNS('*', 'conditionExpression')[0];

    // 开始 → 首节点
    if (s === (starts[0]?.getAttribute('id') || 'start')) {
      if (idMap.has(t)) edges.push({ id: uid('e'), source: idMap.get(s)!, target: idMap.get(t)! });
      continue;
    }
    // 自动审批结果网关的出边：通过(_ok/带条件)还原为审批节点出边；驳回(_no/reject)丢弃
    if (s.startsWith('gw_')) {
      const origin = s.slice(3); // gw_<nid>
      const originModel = idMap.get(`n_${origin}`) || idMap.get(origin);
      if (!originModel) continue;
      if (t.startsWith('reject_')) continue;
      if (fid.endsWith('_no')) continue;
      if (idMap.has(t)) edges.push({ id: uid('e'), source: originModel, target: idMap.get(t)! });
      continue;
    }
    // 驳回终点入边丢弃
    if (t.startsWith('reject_')) continue;
    if (!idMap.has(s) || !idMap.has(t)) continue;

    const srcNode = nodes.find((n) => n.id === idMap.get(s));
    const branch = srcNode?.kind === 'gateway'
      ? (defaultFlowIds.has(fid) || f.getAttribute('name') === '不满足' ? 'no'
        : condEl || f.getAttribute('name') === '满足' ? 'yes' : undefined)
      : undefined;
    edges.push({ id: uid('e'), source: idMap.get(s)!, target: idMap.get(t)!, branch });
  }

  // ---------- 简单分层布局 ----------
  layout(nodes, edges);
  return { nodes, edges };
}

function layout(nodes: FlowNode[], edges: FlowEdge[]): void {
  const start = nodes.find((n) => n.kind === 'start');
  const depth = new Map<string, number>();
  if (start) {
    const outs = new Map<string, string[]>();
    for (const e of edges) outs.set(e.source, [...(outs.get(e.source) || []), e.target]);
    const queue: Array<[string, number]> = [[start.id, 0]];
    while (queue.length) {
      const [id, d] = queue.shift()!;
      if (depth.has(id) && (depth.get(id) || 0) <= d) continue;
      depth.set(id, d);
      for (const t of outs.get(id) || []) queue.push([t, d + 1]);
    }
  }
  const perCol = new Map<number, number>();
  for (const n of nodes) {
    const d = depth.get(n.id) ?? perCol.size;
    const row = perCol.get(d) ?? 0;
    perCol.set(d, row + 1);
    n.x = 120 + d * 300;
    n.y = 120 + row * 150;
  }
}
