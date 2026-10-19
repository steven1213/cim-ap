// 企微免登（G8/D9）端到端验证：令牌生成 → 核销 → 篡改拒绝 → 过期拒绝。
// 同时确认退回流程未被 SPI 重构破坏（NEW_TASK 仍正常派发）。
// 运行：node scripts/verify-wecom.mjs  （需后端 8085 已启动，dev 态 cim.auth.enabled=false）
import crypto from 'node:crypto';
import fs from 'node:fs';

const BASE = process.env.BASE || 'http://localhost:8085/api/v1/approval';
const TID = 'default';
const BPMN = fs.readFileSync(
  'E:/Personal/CIM/cim-ap/business/approval-ap/server/src/main/resources/processes/single-approval.bpmn20.xml',
  'utf8',
);

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
function b64url(buf) { return Buffer.from(buf).toString('base64url'); }

// 0) 部署内置 singleApproval（内置 BPMN 未自动部署时显式部署，与 verify-return.mjs 一致）
const dep = await call('/processes/deploy', 'POST', {
  processKey: 'singleApproval', processName: '单级审批', bpmnXml: BPMN,
});
ok('部署 singleApproval 成功', dep.status === 200 && data(dep), 'code=' + dep.status);

// 1) 起流程，拿到 bob 的待办
const bk = 'WECOM-' + Date.now();
const st = await call('/processes/start', 'POST', {
  processKey: 'singleApproval', businessKey: bk, tenantId: TID,
  initiator: 'alice', title: '免登回归', assignee: 'bob', formData: { amount: 1 },
});
ok('启动流程成功', st.status === 200 && data(st)?.processInstanceId, 'code=' + st.status);

const todo = await call(`/tasks/todo?userId=bob&tenantId=${TID}`);
const taskId = data(todo)?.[0]?.taskId;
ok('bob 有待办', !!taskId, 'taskId=' + taskId);

// 2) 生成免登令牌
const link = await call(`/tasks/${taskId}/wecom-link?userId=bob`, 'POST');
ok('生成免登链接 200', link.status === 200 && data(link)?.token, 'code=' + link.status);
const token = data(link)?.token;

// 3) 核销：应返回 userId=bob + valid
const cb = await call(`/wecom/callback?token=${encodeURIComponent(token)}`);
ok('核销成功 200', cb.status === 200, 'code=' + cb.status);
ok('核销返回 userId=bob', data(cb)?.userId === 'bob', 'userId=' + data(cb)?.userId);
ok('核销返回 taskId 一致', data(cb)?.taskId === taskId, 'taskId=' + data(cb)?.taskId);
ok('核销 valid=true', data(cb)?.valid === true);

// 4) 篡改令牌：改最后一个字符 → 应 400 无效
const tampered = token.slice(0, -1) + (token.slice(-1) === 'A' ? 'B' : 'A');
const bad = await call(`/wecom/callback?token=${encodeURIComponent(tampered)}`);
ok('篡改令牌被拒 400', bad.status === 400, 'code=' + bad.status);

// 5) 过期令牌：用默认密钥自签 exp 在过去 → 应 400 过期
const secret = 'dev-sign-secret-change-me';
const payload = { u: 'bob', t: taskId, n: 'x', exp: Date.now() - 60_000 };
const b = b64url(JSON.stringify(payload));
const sig = b64url(crypto.createHmac('sha256', secret).update(b).digest());
const expired = b + '.' + sig;
const expRes = await call(`/wecom/callback?token=${encodeURIComponent(expired)}`);
ok('过期令牌被拒 400', expRes.status === 400, 'code=' + expRes.status);

// 6) SPI 未破坏退回：bob 退回给发起人，alice 出现重提任务
const rt = await call(`/tasks/${taskId}/return`, 'POST', { targetActivityId: null, reason: 'wecom-test', actor: 'bob' });
ok('退回成功 200', rt.status === 200, 'code=' + rt.status);
const aliceTodo = await call(`/tasks/todo?userId=alice&tenantId=${TID}`);
const hasResubmit = (data(aliceTodo) ?? []).some((t) => t.name === '修改并重新提交');
ok('发起人出现「修改并重新提交」任务', hasResubmit);

console.log(`\n结果：${pass} 通过 / ${fail} 失败` + (fails.length ? '  ⟵ ' + fails.join(', ') : ''));
process.exit(fail === 0 ? 0 : 1);
