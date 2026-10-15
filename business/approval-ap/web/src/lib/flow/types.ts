// 拖拽流程设计器的数据模型。
//
// <p>节点类型覆盖中式审批常用形态：开始 / 单人审批 / 或签 / 会签 / 条件分支 / 并行分支 / 并行汇聚 / 结束。
// 审批类节点（单人、或签、会签）在导出 BPMN 时自动插入「审批结果」排他网关，
// 通过走主流程、驳回走自动生成的 rejectEnd（result=REJECT），与引擎 completeTask 的 outcome 语义一致。
// 条件分支的条件表达式写在<b>节点</b>上，出边只区分「满足 / 不满足」两种语义。
// 并行分支（发散）与并行汇聚（合流）成对使用，导出均为 parallelGateway，由引擎按拓扑判定 fork/join。</p>

export type NodeKind =
  | 'start'
  | 'approval'
  | 'anyone'
  | 'countersign'
  | 'gateway'
  | 'parallel'
  | 'join'
  | 'end';

export interface FlowNode {
  id: string;
  kind: NodeKind;
  x: number;
  y: number;
  label: string;

  /** 条件分支：条件表达式（写在节点上），如 ${amount > 1000}。满足走 yes 出边，不满足走 no 出边。 */
  condition?: string;

  // 审批节点通用属性
  /** 单人审批：办理人变量名（默认 assignee，发起时由 variables 传入）。 */
  assigneeVar?: string;
  /** 会签 / 或签：审批人列表变量名（默认 approvers）。 */
  approversVar?: string;
  /** 会签 / 或签：通过阈值策略（one=任意一人；half=半数向上取整；all=全部；数字=N 人）。 */
  threshold?: 'one' | 'half' | 'all' | number;

  /** 候选人用户 ID，逗号分隔。与 assigneeVar 二选一即可，优先级低于直接办理人。 */
  candidateUsers?: string;
  /** 候选组，逗号分隔。用于 group-task 场景。 */
  candidateGroups?: string;
  /** 任务截止时长 ISO-8601，如 PT2H（2 小时）。 */
  dueDate?: string;
  /** 任务优先级 0-100。 */
  priority?: number;
}

export interface FlowEdge {
  id: string;
  source: string;
  target: string;
  /** 条件分支出边的语义：yes=条件满足；no=条件不满足（导出为网关 default flow）。 */
  branch?: 'yes' | 'no';
}

export interface FlowModel {
  nodes: FlowNode[];
  edges: FlowEdge[];
}

export interface PaletteDef {
  kind: NodeKind;
  label: string;
  desc: string;
  color: string;
}

export const PALETTE: PaletteDef[] = [
  { kind: 'start', label: '开始', desc: '流程起点', color: 'var(--ok)' },
  { kind: 'approval', label: '审批', desc: '单人审批，指定办理人', color: 'var(--primary)' },
  { kind: 'anyone', label: '或签', desc: '多人中任意一人审批即可', color: 'var(--primary)' },
  { kind: 'countersign', label: '会签', desc: '多人并行，按阈值通过', color: 'var(--warn)' },
  { kind: 'gateway', label: '条件分支', desc: '按条件二选一路由（满足/不满足）', color: 'var(--info)' },
  { kind: 'parallel', label: '并行分支', desc: '同时分发到所有出口', color: 'var(--info)' },
  { kind: 'join', label: '并行汇聚', desc: '等待所有分支到达后合流', color: 'var(--info)' },
  { kind: 'end', label: '结束', desc: '流程终点（通过）', color: 'var(--muted)' },
];

export const KIND_LABEL: Record<NodeKind, string> = {
  start: '开始',
  approval: '审批',
  anyone: '或签',
  countersign: '会签',
  gateway: '条件分支',
  parallel: '并行分支',
  join: '并行汇聚',
  end: '结束',
};

export const NODE_W = 168;
export const NODE_H = 58;

let _seq = 0;
export function uid(prefix = 'n'): string {
  _seq += 1;
  return `${prefix}_${Date.now().toString(36)}_${_seq}`;
}

/** 校验模型，返回错误数组（空表示通过）。 */
export function validateModel(m: FlowModel): string[] {
  const errs: string[] = [];
  const starts = m.nodes.filter((n) => n.kind === 'start');
  const ends = m.nodes.filter((n) => n.kind === 'end');
  if (starts.length === 0) errs.push('缺少「开始」节点');
  if (starts.length > 1) errs.push('只能有一个「开始」节点');
  if (ends.length === 0) errs.push('缺少「结束」节点');
  if (ends.length > 1) errs.push('只能有一个「结束」节点');
  if (m.nodes.length < 2) errs.push('流程至少需要开始与结束两个节点');

  const outEdgesOf = (id: string) => m.edges.filter((e) => e.source === id);
  const inEdgesOf = (id: string) => m.edges.filter((e) => e.target === id);

  for (const n of m.nodes) {
    if (n.kind !== 'start' && n.kind !== 'end' && inEdgesOf(n.id).length === 0) {
      errs.push(`节点「${n.label}」没有入口连线`);
    }
    if (n.kind !== 'end' && outEdgesOf(n.id).length === 0) {
      errs.push(`节点「${n.label}」没有出口连线`);
    }
    if (n.kind === 'gateway') {
      const outs = outEdgesOf(n.id);
      const yes = outs.filter((e) => e.branch === 'yes').length;
      const no = outs.filter((e) => e.branch === 'no').length;
      if (outs.length !== 2 || yes !== 1 || no !== 1) {
        errs.push(`条件分支「${n.label}」需要恰好两条出口：一条「满足」、一条「不满足」`);
      }
      if (!n.condition?.trim()) {
        errs.push(`条件分支「${n.label}」未设置条件表达式`);
      }
    }
    if (n.kind === 'parallel') {
      if (outEdgesOf(n.id).length < 2) {
        errs.push(`并行分支「${n.label}」至少需要 2 条出口（各分支同时流转）`);
      }
    }
    if (n.kind === 'join') {
      if (inEdgesOf(n.id).length < 2) {
        errs.push(`并行汇聚「${n.label}」至少需要 2 条入口（来自各并行分支）`);
      }
      if (outEdgesOf(n.id).length !== 1) {
        errs.push(`并行汇聚「${n.label}」需要恰好 1 条出口`);
      }
    }
  }
  return errs;
}
