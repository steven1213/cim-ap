// M4 选人与触达验证：G6 角色选人 + G8 企微免登。
// 覆盖：
//  G6-1 部署角色选人 BPMN（userTask 引用 ${ccg_review} 候选组变量）
//  G6-2 角色发起（candidateGroups={review:["manager"]}）成功并落实例
//  G6-3 待办按 roles claim 命中：manager 命中 / engineer 未命中 / 无 roles 未命中
//  G6-4 角色成员办理成功（complete 200），完成后待办清空
//  G8-1 生成企微免登链接（token）成功
//  G8-2 核销令牌返回 userId + taskId（免登闭环）
// 运行：先启动 8085（dev-bypass，cim.auth.enabled=false），再 node verify-g6.mjs
const BASE = process.env.BASE || 'http://localhost:8085/api/v1/approval';

const ROLE_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://cim/approval/test">
  <process id="roleG6Approval" name="角色选人审批" isExecutable="true">
    <startEvent id="start"/>
    <userTask id="review" name="部门经理审批" flowable:candidateGroups="\${ccg_review}"/>
    <endEvent id="end"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="review"/>
    <sequenceFlow id="f2" sourceRef="review" targetRef="end"/>
  </process>
</definitions>`;

let pass = 0, fail = 0; const fails = [];
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
const q = (obj) => Object.entries(obj)
  .map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&');

async function main() {
  // ⚠️ deployProcess 以 BPMN 内 <process id> 为定义 key（不改写资源名），
  // 故部署 key 必须与 XML id 一致；重复部署自动递增版本，发起取 latestVersion。
  const key = 'roleG6Approval';
  const domain = 'g6-domain';

  // G6-1 部署角色选人 BPMN
  const dep = await call('/processes/deploy', 'POST',
    { processKey: key, processName: 'G6角色选人', bpmnXml: ROLE_BPMN });
  ok('G6-1 部署角色BPMN 200', dep.status === 200 && data(dep), 'code=' + dep.status);
  if (!data(dep)) { console.log('\n结果：0 通过 / 1 失败 :: deploy 失败，终止'); process.exit(1); }

  // G6-2 角色发起（仅 candidateGroups，无 assignee/approvers/variables）
  const bk = 'g6-' + Date.now();
  const start = await call('/processes/start', 'POST', {
    processKey: key, businessKey: bk, tenantId: domain, initiator: 'g6init',
    title: 'G6 角色选人', candidateGroups: { review: ['manager'] },
  });
  ok('G6-2 角色发起 200', start.status === 200, 'code=' + start.status);
  const instId = data(start)?.processInstanceId;
  ok('G6-2 返回实例ID', !!instId, 'inst=' + instId);

  // G6-3 待办按 roles 命中
  const todoMgr = data(await call(`/tasks/todo?${q({ userId: 'alice', roles: 'manager', tenantId: domain })}`));
  ok('G6-3 manager 角色命中待办',
    Array.isArray(todoMgr) && todoMgr.some((t) => t.processInstanceId === instId), 'n=' + (todoMgr || []).length);

  const todoEng = data(await call(`/tasks/todo?${q({ userId: 'bob', roles: 'engineer', tenantId: domain })}`));
  ok('G6-3 engineer 角色未命中', !(todoEng || []).some((t) => t.processInstanceId === instId));

  const todoNone = data(await call(`/tasks/todo?${q({ userId: 'carol', roles: '', tenantId: domain })}`));
  ok('G6-3 无角色 claim 未命中', !(todoNone || []).some((t) => t.processInstanceId === instId));

  const task = (todoMgr || []).find((t) => t.processInstanceId === instId);
  ok('G6-3 取得任务ID', !!task?.taskId);
  const taskId = task?.taskId;

  // G8-1 生成企微免登链接（绑定 userId=alice + taskId）
  const link = await call(`/tasks/${taskId}/wecom-link?${q({ userId: 'alice' })}`, 'POST');
  ok('G8-1 生成免登令牌 200', link.status === 200 && !!data(link)?.token, 'code=' + link.status);
  const token = data(link)?.token;
  ok('G8-1 返回免登链接', !!data(link)?.link);

  // G8-2 核销令牌闭环
  const cb = await call(`/wecom/callback?${q({ token })}`);
  ok('G8-2 核销令牌 200', cb.status === 200, 'code=' + cb.status);
  ok('G8-2 返回 userId=alice', data(cb)?.userId === 'alice', 'uid=' + data(cb)?.userId);
  ok('G8-2 返回 taskId 一致', data(cb)?.taskId === taskId, 'tid=' + data(cb)?.taskId);

  // G6-4 角色成员办理（带 roles=manager 通过授权校验）
  const done = await call(`/tasks/${taskId}/complete?${q({ roles: 'manager' })}`, 'POST',
    { outcome: 'APPROVE', comment: 'ok', actor: 'alice' });
  ok('G6-4 角色成员办理 200', done.status === 200, 'code=' + done.status);

  const todoAfter = data(await call(`/tasks/todo?${q({ userId: 'alice', roles: 'manager', tenantId: domain })}`));
  ok('G6-4 完成后待办清空', !(todoAfter || []).some((t) => t.processInstanceId === instId));

  console.log(`\n结果：${pass} 通过 / ${fail} 失败` + (fails.length ? '  ⟵ ' + fails.join(', ') : ''));
  process.exit(fail === 0 ? 0 : 1);
}
main().catch((e) => { console.error(e); process.exit(1); });
