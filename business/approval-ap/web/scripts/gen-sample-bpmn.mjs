// E2E 脚本：用真实的 toBpmn.ts + 覆盖所有节点类型的模型生成 BPMN，供后端 /processes/deploy 验证。
// 模型语义：主管审批 → 金额判断（条件在网关节点上）
//   ✓满足(amount>1000) → 并行复核(fork) → [法务审批 ∥ 财务审批] → 汇聚(join) → 结束
//   ✗不满足            → 主管复核 → 结束
import { toBpmn } from '../src/lib/flow/toBpmn.ts';

const model = {
  nodes: [
    { id: 's1', kind: 'start', x: 60, y: 300, label: '开始' },
    { id: 'a1', kind: 'approval', x: 300, y: 280, label: '主管审批', assigneeVar: 'assignee' },
    { id: 'g1', kind: 'gateway', x: 560, y: 280, label: '金额判断', condition: '${amount > 1000}' },
    { id: 'p1', kind: 'parallel', x: 840, y: 180, label: '并行复核' },
    { id: 'l1', kind: 'approval', x: 1120, y: 100, label: '法务审批', assigneeVar: 'legal' },
    { id: 'f1', kind: 'approval', x: 1120, y: 280, label: '财务审批', assigneeVar: 'finance' },
    { id: 'j1', kind: 'join', x: 1400, y: 180, label: '汇聚' },
    { id: 'r1', kind: 'approval', x: 840, y: 460, label: '主管复核', assigneeVar: 'reviewer' },
    { id: 'e1', kind: 'end', x: 1660, y: 280, label: '结束' },
  ],
  edges: [
    { id: 'e1', source: 's1', target: 'a1' },
    { id: 'e2', source: 'a1', target: 'g1' },
    { id: 'e3', source: 'g1', target: 'p1', branch: 'yes' },
    { id: 'e4', source: 'g1', target: 'r1', branch: 'no' },
    { id: 'e5', source: 'p1', target: 'l1' },
    { id: 'e6', source: 'p1', target: 'f1' },
    { id: 'e7', source: 'l1', target: 'j1' },
    { id: 'e8', source: 'f1', target: 'j1' },
    { id: 'e9', source: 'j1', target: 'e1' },
    { id: 'e10', source: 'r1', target: 'e1' },
  ],
};

const xml = toBpmn(model, 'branchFlow', '条件分支与并行汇聚示例');
process.stdout.write(xml);
