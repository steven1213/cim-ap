// 统一契约 + 原型借鉴功能全链路验证（dev 模式：身份经 query/body 传参）
// 契约：响应 {code:0,msg,data,...}（平台 Result）；分页 PageResult{records,total,page(1起),size}
// 断言：授权/驳回必填/撤回策略/催办频控/委派归还/复制定义/回调统计+测试/统计端点
const BASE = 'http://localhost:8085/api/v1/approval';
const results = [];
function ok(name, cond, extra = '') {
  results.push({ name, pass: !!cond, extra });
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${name}${extra ? '  [' + extra + ']' : ''}`);
}

async function api(path, opts = {}) {
  const res = await fetch(BASE + path, { headers: { 'Content-Type': 'application/json' }, ...opts });
  let body = null;
  try { body = await res.json(); } catch { /* empty */ }
  // 平台 Result 包装：业务数据在 data；失败时根级 code!=0
  const data = body && typeof body === 'object' && 'code' in body ? body.data : body;
  return { status: res.status, body: data, raw: body };
}

const RUN = 'H' + (Date.now() % 100000);
async function start(bkSuffix, assignee = 'bob') {
  const r = await api('/processes/start', {
    method: 'POST',
    body: JSON.stringify({
      processKey: 'singleApproval', businessKey: `${bkSuffix}-${RUN}`, tenantId: 'default',
      initiator: 'alice', title: `统一契约-${bkSuffix}`, assignee,
      formData: { item: '测试项', qty: 1 },
    }),
  });
  return r.body?.processInstanceId;
}

// ---- 1. 分页结构（PageResult：records/total/page 1 起）----
const todoPage = await api(`/tasks/todo/page?page=1&size=5&userId=bob&tenantId=default`);
ok('page-result-structure', Array.isArray(todoPage.body?.records) && typeof todoPage.body?.total === 'number'
  && todoPage.body?.page === 1, JSON.stringify(todoPage.body).slice(0, 60));

// ---- 2. 授权：非办理人代办被拒 ----
const inst1 = await start('AUTH');
ok('start#1', !!inst1);
const todoBob = await api(`/tasks/todo/page?page=1&size=50&userId=bob&tenantId=default`);
const task1 = todoBob.body.records.find((x) => x.businessKey === `AUTH-${RUN}`);
const forged = await api(`/tasks/${task1.taskId}/complete`, {
  method: 'POST',
  body: JSON.stringify({ outcome: 'APPROVE', comment: '冒充', actor: 'carol' }),
});
ok('operator-authz-reject', forged.status === 403 || forged.raw?.code === 2003, `status=${forged.status}`);

// ---- 3. 驳回意见必填 ----
const rejNoComment = await api(`/tasks/${task1.taskId}/complete`, {
  method: 'POST',
  body: JSON.stringify({ outcome: 'REJECT', comment: '', actor: 'bob' }),
});
ok('reject-comment-required', rejNoComment.status === 400, `status=${rejNoComment.status} msg=${rejNoComment.raw?.msg}`);
const rejOk = await api(`/tasks/${task1.taskId}/complete`, {
  method: 'POST',
  body: JSON.stringify({ outcome: 'REJECT', comment: '材料不全', actor: 'bob' }),
});
ok('reject-with-comment-ok', rejOk.status === 200 || rejOk.status === 204, `status=${rejOk.status}`);

// ---- 4. 撤回策略：未审批可撤 / 已审批不可撤 ----
const inst3 = await start('WD1');
const wd1 = await api(`/processes/instances/${inst3}/withdraw?actor=alice`, { method: 'POST' });
ok('withdraw-before-approval-ok', wd1.status === 200 || wd1.status === 204, `status=${wd1.status}`);
const inst4 = await start('WD2');
const todoBob2 = await api(`/tasks/todo/page?page=1&size=50&userId=bob&tenantId=default`);
const task4 = todoBob2.body.records.find((x) => x.businessKey === `WD2-${RUN}`);
await api(`/tasks/${task4.taskId}/complete`, {
  method: 'POST', body: JSON.stringify({ outcome: 'APPROVE', comment: 'ok', actor: 'bob' }),
});
const wd2 = await api(`/processes/instances/${inst4}/withdraw?actor=alice`, { method: 'POST' });
ok('withdraw-after-approval-rejected', wd2.status === 400, `status=${wd2.status} msg=${wd2.raw?.msg}`);

// ---- 5. 催办频控 ----
const inst5 = await start('URGE');
const u1 = await api(`/processes/instances/${inst5}/urge?actor=alice`, { method: 'POST' });
const u2 = await api(`/processes/instances/${inst5}/urge?actor=alice`, { method: 'POST' });
ok('urge-first-ok', u1.status === 200 || u1.status === 204);
ok('urge-second-cooldown', !(u2.status === 200 || u2.status === 204), `status=${u2.status}`);

// ---- 6. 委派/归还 ----
const inst6 = await start('DELG');
const t6 = (await api(`/tasks/todo/page?page=1&size=50&userId=bob&tenantId=default`)).body.records
  .find((x) => x.businessKey === `DELG-${RUN}`);
await api(`/tasks/${t6.taskId}/delegate`, { method: 'POST', body: JSON.stringify({ assignee: 'carol', reason: '休假', actor: 'bob' }) });
const carolTodo = await api(`/tasks/todo/page?page=1&size=50&userId=carol&tenantId=default`);
const dTask = carolTodo.body.records.find((x) => x.businessKey === `DELG-${RUN}`);
ok('delegatee-sees-pending', !!dTask && dTask.delegationState === 'PENDING');
await api(`/tasks/${dTask.taskId}/resolve`, { method: 'POST', body: JSON.stringify({ comment: '归还', actor: 'carol' }) });
const bobBack = await api(`/tasks/todo/page?page=1&size=50&userId=bob&tenantId=default`);
const backTask = bobBack.body.records.find((x) => x.businessKey === `DELG-${RUN}`);
ok('task-returned', !!backTask && backTask.delegationState !== 'PENDING');

// ---- 7. 复制定义 ----
const defs = await api('/definitions');
const single = defs.body.find((d) => d.key === 'singleApproval');
const dup = await api(`/processes/${single.id}/duplicate`, { method: 'POST' });
ok('duplicate-definition', !!dup.body?.key && dup.body.key.startsWith('singleApproval_copy'),
  `key=${dup.body?.key} name=${dup.body?.name}`);

// ---- 8. 回调统计 + 测试请求 ----
const stats = await api('/callbacks/outbox/stats');
ok('outbox-stats', stats.body && 'sent' in stats.body && 'successRate' in stats.body,
  JSON.stringify(stats.body).slice(0, 90));
await api('/callbacks/register', {
  method: 'POST',
  body: JSON.stringify({ appCode: 'e2e-probe', baseUrl: 'http://127.0.0.1:9/none', secret: 'test-secret' }),
});
const test = await api(`/callbacks/test/e2e-probe`, { method: 'POST' });
ok('callback-test', ['SENT','DEAD'].includes(test.body?.status),
  `status=${test.body?.status} err=${test.body?.lastError ?? '无'}`);

// ---- 9. 统计端点 ----
const st = await api('/stats?tenantId=default');
ok('stats-endpoint', typeof st.body?.todoCount === 'number' && Array.isArray(st.body?.trend));

console.log('\n==== SUMMARY ====');
const fail = results.filter((r) => !r.pass);
console.log(`${results.length - fail.length}/${results.length} passed`);
if (fail.length) {
  console.log('FAILED:', fail.map((f) => f.name).join(', '));
  process.exit(1);
}
