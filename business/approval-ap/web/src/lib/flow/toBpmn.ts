// 流程模型 → Flowable 兼容的 BPMN 2.0 XML。
//
// <p>导出规则：
// <ul>
//   <li>开始→各节点、节点→节点的连线直接转 sequenceFlow；</li>
//   <li>审批 / 会签节点自动插入「审批结果」排他网关：通过（outcome==APPROVE 或
//       approvedCount>=approvalThreshold）走主流程，驳回走自动生成的 rejectEnd（result=REJECT）；</li>
//   <li>会签节点挂 multiInstanceLoopCharacteristics + ${countersignListener}（与引擎 countersign BPMN 一致）；</li>
//   <li>条件分支（exclusiveGateway）条件写在节点上：满足（yes）出边带 conditionExpression，
//       不满足（no）出边为网关 default flow；</li>
//   <li>并行分支（fork）与并行汇聚（join）均导出 parallelGateway，由引擎按入/出度判定语义；</li>
//   <li>结束节点 executionListener 写入 result=APPROVE，使流程完成语义与引擎一致。</li>
// </ul></p>
import type { FlowModel, FlowNode } from './types';

function esc(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function nodeId(n: FlowNode): string {
  if (n.kind === 'start') return 'start';
  if (n.kind === 'end') return 'end';
  return `n_${n.id}`;
}

function thresholdExpr(n: FlowNode): string {
  if (n.kind === 'anyone') return '1';
  const t = n.threshold;
  if (t === 'one' || t === 1) return '1';
  if (t === 'all') return 'approvers.size()';
  if (t === 'half' || t === undefined || t === null) return 'approvalThreshold';
  if (typeof t === 'number' && !Number.isNaN(t)) return String(t);
  return 'approvalThreshold';
}

export function toBpmn(m: FlowModel, processKey: string, processName: string): string {
  const outEdges = (id: string) => m.edges.filter((e) => e.source === id);
  const startNode = m.nodes.find((n) => n.kind === 'start');
  const endNode = m.nodes.find((n) => n.kind === 'end');

  const elements: string[] = [];

  // 开始
  if (startNode) elements.push(`    <startEvent id="start"/>`);

  // 节点
  for (const n of m.nodes) {
    if (n.kind === 'start') continue;
    if (n.kind === 'end') {
      elements.push(
        `    <endEvent id="end" name="${esc(n.label)}">`,
        `      <extensionElements>`,
        `        <flowable:executionListener event="start" expression="\${execution.setVariable('result','APPROVE')}"/>`,
        `      </extensionElements>`,
        `    </endEvent>`,
      );
      continue;
    }
    if (n.kind === 'gateway') {
      // 不满足（no）出边作为 default flow，避免条件漏配时流程卡死
      const noEdge = m.edges.find((e) => e.source === n.id && e.branch === 'no');
      const defAttr = noEdge ? ` default="f_${noEdge.id}"` : '';
      elements.push(`    <exclusiveGateway id="${nodeId(n)}" name="${esc(n.label)}"${defAttr}/>`);
      continue;
    }
    if (n.kind === 'parallel' || n.kind === 'join') {
      // fork / join 均为 parallelGateway，引擎按拓扑（入度/出度）自动判定
      elements.push(`    <parallelGateway id="${nodeId(n)}" name="${esc(n.label)}"/>`);
      continue;
    }

    // approval | anyone | countersign
    const needsMulti = n.kind === 'countersign' || n.kind === 'anyone';
    const assigneeVar = needsMulti ? n.approversVar || 'approvers' : n.assigneeVar || 'assignee';
    const extraAttrs: string[] = [];
    if (n.candidateUsers?.trim()) extraAttrs.push(`flowable:candidateUsers="${esc(n.candidateUsers.trim())}"`);
    if (n.candidateGroups?.trim()) extraAttrs.push(`flowable:candidateGroups="${esc(n.candidateGroups.trim())}"`);
    if (n.dueDate?.trim()) extraAttrs.push(`flowable:dueDate="${esc(n.dueDate.trim())}"`);
    if (n.priority !== undefined && !Number.isNaN(n.priority)) {
      extraAttrs.push(`flowable:priority="${Math.max(0, Math.min(100, n.priority))}"`);
    }
    if (needsMulti) {
      // 会签 / 或签 使用 element variable，不直接挂 assignee 属性
    } else {
      extraAttrs.push(`flowable:assignee="\${${assigneeVar}}"`);
    }
    const attrs = extraAttrs.join(' ');

    const ext: string[] = [];
    if (needsMulti) {
      ext.push(`        <flowable:taskListener event="complete" delegateExpression="\${countersignListener}"/>`);
    }
    const multi: string[] = [];
    if (needsMulti) {
      const ccExpr = esc("${approvedCount >= " + thresholdExpr(n) + "}");
      multi.push(
        `      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="\${${assigneeVar}}" flowable:elementVariable="approver">`,
        `        <completionCondition>${ccExpr}</completionCondition>`,
        `      </multiInstanceLoopCharacteristics>`,
      );
    }
    const children: string[] = [];
    if (ext.length > 0) {
      children.push(`      <extensionElements>`, ...ext, `      </extensionElements>`);
    }
    children.push(...multi);
    if (children.length > 0) {
      elements.push(`    <userTask id="${nodeId(n)}" name="${esc(n.label)}" ${attrs}>`, ...children, `    </userTask>`);
    } else {
      elements.push(`    <userTask id="${nodeId(n)}" name="${esc(n.label)}" ${attrs}/>`);
    }
    // 自动审批结果网关（单人/会签/或签都生成通过/驳回）
    elements.push(`    <exclusiveGateway id="gw_${n.id}" name="审批结果"/>`);
  }

  // 连线
  const flows: string[] = [];

  // start → 其出边目标
  if (startNode) {
    for (const e of outEdges(startNode.id)) {
      const tn = m.nodes.find((x) => x.id === e.target);
      if (tn) flows.push(`    <sequenceFlow id="f_start_${e.id}" sourceRef="start" targetRef="${nodeId(tn)}"/>`);
    }
  }

  for (const n of m.nodes) {
    if (n.kind === 'start') continue;

    if (n.kind === 'approval' || n.kind === 'anyone' || n.kind === 'countersign') {
      const outs = outEdges(n.id);
      const approveEdge = outs[0];
      const tn = approveEdge ? m.nodes.find((x) => x.id === approveEdge.target) : undefined;
      // 用户任务 → 网关
      flows.push(`    <sequenceFlow id="f_${n.id}_in" sourceRef="${nodeId(n)}" targetRef="gw_${n.id}"/>`);
      if (tn) {
        const raw = n.kind === 'countersign' || n.kind === 'anyone'
          ? "${approvedCount >= " + thresholdExpr(n) + "}"
          : "${outcome == 'APPROVE'}";
        const cond = esc(raw);
        flows.push(
          `    <sequenceFlow id="f_${n.id}_ok" name="通过" sourceRef="gw_${n.id}" targetRef="${nodeId(tn)}">`,
          `      <conditionExpression xsi:type="tFormalExpression">${cond}</conditionExpression>`,
          `    </sequenceFlow>`,
        );
      }
      // 驳回 → 自动 rejectEnd
      flows.push(
        `    <endEvent id="reject_${n.id}" name="驳回">`,
        `      <extensionElements>`,
        `        <flowable:executionListener event="start" expression="\${execution.setVariable('result','REJECT')}"/>`,
        `      </extensionElements>`,
        `    </endEvent>`,
        `    <sequenceFlow id="f_${n.id}_no" sourceRef="gw_${n.id}" targetRef="reject_${n.id}"/>`,
      );
      // 网关默认走驳回
      elements.forEach((line, i) => {
        if (line.includes(`id="gw_${n.id}"`)) {
          elements[i] = `    <exclusiveGateway id="gw_${n.id}" name="审批结果" default="f_${n.id}_no"/>`;
        }
      });
    } else if (n.kind === 'gateway') {
      const outs = outEdges(n.id);
      outs.forEach((e) => {
        const tn = m.nodes.find((x) => x.id === e.target);
        if (!tn) return;
        if (e.branch === 'no') {
          // 不满足：default flow（网关 default 属性已指向本边），无条件表达式
          flows.push(`    <sequenceFlow id="f_${e.id}" name="不满足" sourceRef="${nodeId(n)}" targetRef="${nodeId(tn)}"/>`);
        } else {
          // 满足：条件取网关节点上的表达式
          const raw = n.condition?.trim() || '${true}';
          const cond = esc(raw);
          flows.push(
            `    <sequenceFlow id="f_${e.id}" name="满足" sourceRef="${nodeId(n)}" targetRef="${nodeId(tn)}">`,
            `      <conditionExpression xsi:type="tFormalExpression">${cond}</conditionExpression>`,
            `    </sequenceFlow>`,
          );
        }
      });
    } else if (n.kind === 'parallel' || n.kind === 'join') {
      const outs = outEdges(n.id);
      outs.forEach((e) => {
        const tn = m.nodes.find((x) => x.id === e.target);
        if (!tn) return;
        flows.push(`    <sequenceFlow id="f_${e.id}" sourceRef="${nodeId(n)}" targetRef="${nodeId(tn)}"/>`);
      });
    }
    // end 节点无出边
  }

  void endNode;

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"',
    '             xmlns:flowable="http://flowable.org/bpmn"',
    '             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
    '             targetNamespace="http://cim/approval">',
    `    <process id="${esc(processKey)}" name="${esc(processName)}" isExecutable="true">`,
    ...elements,
    ...flows,
    '    </process>',
    '</definitions>',
  ].join('\n');
}
