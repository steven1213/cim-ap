// 退回业务流程 E2E 回归（Product-Framework §2.2.7 四条 GWT）
// 运行：node scripts/verify-return.mjs  （需后端 8085 已启动，dev 态 cim.auth.enabled=false）
const BASE = process.env.BASE || 'http://localhost:8085/api/v1/approval';
const TID = 'default';

let pass = 0, fail = 0;
const fails = [];
function ok(name, cond, extra = '') {
  if (cond) { pass++; console.log(`  ✅ ${name}${extra ? ' :: ' + extra : ''}`); }
  else { fail++; fails.push(name); console.log(`  ❌ ${name}${extra ? ' :: ' + extra : ''}`); }
}

async function call(path, method = 'GET', body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => null);
  return { status: res.status, body: json };
}
const data = (r) => r.body?.data;
async function start(key, bk, initiator, opts = {}) {
  const r = await call('/processes/start', 'POST', {
    processKey: key, businessKey: bk, tenantId: TID, initiator,
    title: 'RT-' + bk, assignee: opts.assignee, approvers: opts.approvers,
    variables: opts.variables, formData: opts.formData || { amount: 100 },
  });
  if (!r.body?.data) throw new Error('start failed: ' + JSON.stringify(r.body));
  return r.body.data.processInstanceId;
}
async function todo(userId) {
  const r = await call(`/tasks/todo?userId=${userId}&tenantId=${TID}`);
  return data(r) ?? [];
}
async function firstTask(userId) {
  const t = await todo(userId);
  if (!t.length) throw new Error('no task for ' + userId);
  return t[0];
}
async function detail(piId, userId) {
  const r = await call(`/processes/instances/${piId}/detail?userId=${userId}`);
  return data(r);
}
async function deploy(key, xml) {
  const r = await call('/processes/deploy', 'POST', { processKey: key, processName: key, bpmnXml: xml });
  ok('deploy ' + key, r.body?.success, 'code=' + r.status);
  return r;
}

const PROBE_MI = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://cim/approval">
  <process id="rtMi" name="RT-MI" isExecutable="true">
    <startEvent id="start"/>
    <userTask id="leader" name="主管审批" flowable:assignee="\${assignee}"/>
    <userTask id="cs" name="会签" flowable:assignee="\${approver}">
      <extensionElements>
        <flowable:taskListener event="complete" delegateExpression="\${countersignListener}"/>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="\${approvers}" flowable:elementVariable="approver">
        <completionCondition>\${approvedCount &gt;= approvalThreshold || rejectedCount &gt; (nrOfInstances - approvalThreshold)}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <endEvent id="approveEnd" name="通过">
      <extensionElements><flowable:executionListener event="start" expression="\${execution.setVariable('result','APPROVE')}"/></extensionElements>
    </endEvent>
    <sequenceFlow id="f1" sourceRef="start" targetRef="leader"/>
    <sequenceFlow id="f2" sourceRef="leader" targetRef="cs"/>
    <sequenceFlow id="f3" sourceRef="cs" targetRef="approveEnd"/>
  </process>
</definitions>`;

const PROBE_PAR = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://cim/approval">
  <process id="rtPar" name="RT-PAR" isExecutable="true">
    <startEvent id="start"/>
    <userTask id="prep" name="初审" flowable:assignee="\${prepUser}"/>
    <parallelGateway id="fork" name="并行分发"/>
    <userTask id="nodeA" name="A岗" flowable:assignee="\${userA}"/>
    <userTask id="nodeB" name="B岗" flowable:assignee="\${userB}"/>
    <parallelGateway id="join" name="汇聚"/>
    <endEvent id="end" name="通过"/>
    <sequenceFlow id="p1" sourceRef="start" targetRef="prep"/>
    <sequenceFlow id="p2" sourceRef="prep" targetRef="fork"/>
    <sequenceFlow id="p3" sourceRef="fork" targetRef="nodeA"/>
    <sequenceFlow id="p4" sourceRef="fork" targetRef="nodeB"/>
    <sequenceFlow id="p5" sourceRef="nodeA" targetRef="join"/>
    <sequenceFlow id="p6" sourceRef="nodeB" targetRef="join"/>
    <sequenceFlow id="p7" sourceRef="join" targetRef="end"/>
  </process>
</definitions>`;

async function main() {
  console.log('\n=== GWT1：会签一人退 → 其余取消 + round=2 + 发起人重提任务 ===');
  const ts = Date.now();
  await deploy('rtMi', PROBE_MI);
  const bk1 = 'GWT1-' + ts;
  const pi1 = await start('rtMi', bk1, 'alice', { assignee: 'bob', approvers: ['carol', 'dave', 'erin'], variables: { assignee: 'bob' }, formData: { amount: 100 } });
  let bob = await firstTask('bob');
  await call(`/tasks/${bob.taskId}/complete`, 'POST', { outcome: 'APPROVE', comment: 'ok', actor: 'bob' });
  const carol = await firstTask('carol');
  const rt = await call(`/tasks/${carol.taskId}/return`, 'POST', { targetActivityId: null, reason: '金额存疑', actor: 'carol' });
  ok('会签退回返回 200', rt.status === 200, 'code=' + rt.status);
  const dave = await todo('dave'); const erin = await todo('erin');
  ok('其余会签人任务被取消', dave.length === 0 && erin.length === 0, `dave=${dave.length} erin=${erin.length}`);
  const alice = await todo('alice');
  ok('发起人出现「修改并重新提交」任务', alice.length === 1 && alice[0].name === '修改并重新提交', JSON.stringify(alice.map(t => t.name)));
  const det1 = await detail(pi1, 'alice');
  ok('实例 round=2', det1.round === 2, 'round=' + det1.round);
  ok('awaitingResubmit=true', det1.awaitingResubmit === true);

  console.log('\n=== GWT2：发起人重提（改表单）→ 全链重走 + 表单更新 ===');
  const rs = await call(`/processes/instances/${pi1}/resubmit`, 'POST', {
    formData: { amount: 999 }, formSchema: [{ key: 'amount', label: '金额', type: 'number' }], actor: 'alice',
  });
  ok('重提返回 200', rs.status === 200, 'code=' + rs.status);
  const bob2 = await firstTask('bob');
  ok('重提后流程回到主管审批(bob)', bob2 && bob2.name === '主管审批', bob2?.name);
  const det2 = await detail(pi1, 'alice');
  ok('重提后表单金额更新为 999', det2.formData?.amount === 999, 'amount=' + det2.formData?.amount);
  ok('重提后 awaitingResubmit=false', det2.awaitingResubmit === false);
  ok('重提后 round 保持 2（不另计轮）', det2.round === 2, 'round=' + det2.round);

  console.log('\n=== GWT2b：顺序流(resubmit 模式)返回上一节点 + 重提全链重走 ===');
  const bk2 = 'GWT2b-' + ts;
  const pi2 = await start('twoStepApproval', bk2, 'u1', { variables: { leader: 'u2', manager: 'u2' }, formData: { amount: 100 } });
  let mgr = await firstTask('u2'); // leaderReview
  await call(`/tasks/${mgr.taskId}/complete`, 'POST', { outcome: 'APPROVE', comment: 'ok', actor: 'u2' });
  mgr = await firstTask('u2'); // managerReview
  const rt2 = await call(`/tasks/${mgr.taskId}/return`, 'POST', { targetActivityId: null, reason: '补材料', actor: 'u2' });
  ok('顺序流退回发起人 200', rt2.status === 200, 'code=' + rt2.status);
  let u1 = await firstTask('u1');
  ok('发起人获重提任务', u1.name === '修改并重新提交', u1.name);
  const rs2 = await call(`/processes/instances/${pi2}/resubmit`, 'POST', { formData: { amount: 777 }, actor: 'u1' });
  ok('顺序流重提 200', rs2.status === 200);
  const u2b = await firstTask('u2');
  ok('重提后回到主管审批', u2b.name === '主管审批', u2b.name);
  const detB = await detail(pi2, 'u1');
  ok('顺序流重提金额=777', detB.formData?.amount === 777);
  ok('顺序流重提 round=2', detB.round === 2, 'round=' + detB.round);

  console.log('\n=== GWT3：轮次达上限 → 前后端双保险 400 ===');
  const bk3 = 'GWT3-' + ts;
  const pi3 = await start('rtMi', bk3, 'p1', { assignee: 'bob', approvers: ['carol', 'dave', 'erin'], variables: { assignee: 'bob' }, formData: { amount: 1 } });
  const bob0 = await firstTask('bob');
  await call(`/tasks/${bob0.taskId}/complete`, 'POST', { outcome: 'APPROVE', comment: 'ok', actor: 'bob' });
  let last = null;
  for (let i = 0; i < 4; i++) {            // 4 轮成功退回（round 1→2→3→4→5）
    const c = await firstTask('carol');    // 会签节点
    last = await call(`/tasks/${c.taskId}/return`, 'POST', { targetActivityId: null, reason: 'r' + i, actor: 'carol' });
    if (last.status !== 200) { ok('前 4 次退回应成功 #' + i, false, 'code=' + last.status + ' body=' + JSON.stringify(last.body)); 
      const dbug = await detail(pi3, 'p1'); console.log('  debug round=', dbug.round, 'awaiting=', dbug.awaitingResubmit, 'activeTasks=', JSON.stringify((dbug.activeTasks||[]).map(t=>({n:t.name,a:t.assignee}))));
      break; }
    const rsx = await call(`/processes/instances/${pi3}/resubmit`, 'POST', { formData: { amount: 1 }, actor: 'p1' });
    if (rsx.status !== 200) { ok('重提应成功 #' + i, false, 'code=' + rsx.status); break; }
    const b = await firstTask('bob');      // 重走主管审批
    await call(`/tasks/${b.taskId}/complete`, 'POST', { outcome: 'APPROVE', comment: 'ok', actor: 'bob' });
    // 会签重新展开，carol 再次获得任务
  }
  ok('前 4 轮退回+重提均成功', last && last.status === 200, last ? 'last=' + last.status : 'none');
  const det3 = await detail(pi3, 'p1');
  ok('已达第 5 轮', det3.round === 5, 'round=' + det3.round);
  // 第 5 轮再发起退回 → 应被拒 400
  const c5 = await firstTask('carol');
  const blocked = await call(`/tasks/${c5.taskId}/return`, 'POST', { targetActivityId: null, reason: 'over', actor: 'carol' });
  ok('第 5 轮退回被拒 400', blocked.status === 400, 'code=' + blocked.status);

  console.log('\n=== GWT4：并行段内非法目标 → 400（BR-05） ===');
  await deploy('rtPar', PROBE_PAR);
  const bk4 = 'GWT4-' + ts;
  const pi4 = await start('rtPar', bk4, 'pp', { variables: { prepUser: 'pa', userA: 'ua', userB: 'ub' }, formData: { amount: 1 } });
  const paT = await firstTask('pa');
  await call(`/tasks/${paT.taskId}/complete`, 'POST', { outcome: 'APPROVE', comment: 'ok', actor: 'pa' });
  const uaT = await firstTask('ua'); // nodeA
  const tg = await call(`/tasks/${uaT.taskId}/return-targets`);
  ok('return-targets 返回 200', tg.status === 200, 'code=' + tg.status);
  const illegal = await call(`/tasks/${uaT.taskId}/return`, 'POST', { targetActivityId: 'nodeB', reason: '非法', actor: 'ua' });
  ok('并行段内退到 nodeB 被拒 400', illegal.status === 400, 'code=' + illegal.status);
  const legal = await call(`/tasks/${uaT.taskId}/return`, 'POST', { targetActivityId: 'prep', reason: '合法', actor: 'ua' });
  ok('并行段前节点 prep 允许退回 200', legal.status === 200, 'code=' + legal.status);

  console.log(`\n=== 结果：通过 ${pass} / 失败 ${fail} ===`);
  if (fail) { console.log('失败项：', fails.join(', ')); process.exit(1); }
}

main().catch((e) => { console.error('E2E 异常：', e); process.exit(2); });
