// G1 权限码三层（deny 路径）验证：以 dev-enforce 模式运行（approval.security.dev-enforce=true,
// dev-super-admin=admin）。超管 actor=admin 通过管理端点；非超管 actor=bob 被 403 拒绝。
// 运行：先启动 8086 实例（dev-enforce），再 node verify-g1.mjs
import fs from 'node:fs';

const BASE = process.env.BASE || 'http://localhost:8086/api/v1/approval';
const BPMN = fs.readFileSync(
  'E:/Personal/CIM/cim-ap/business/approval-ap/server/src/main/resources/processes/single-approval.bpmn20.xml',
  'utf8',
);

let pass = 0, fail = 0; const fails = [];
function ok(name, cond, extra = '') {
  if (cond) { pass++; console.log(`  ✅ ${name}${extra ? ' :: ' + extra : ''}`); }
  else { fail++; fails.push(name); console.log(`  ❌ ${name}${extra ? ' :: ' + extra : ''}`); }
}
async function call(path, method = 'GET', body, actor) {
  const sep = path.includes('?') ? '&' : '?';
  const url = actor ? path + sep + 'actor=' + encodeURIComponent(actor) : path;
  const res = await fetch(BASE + url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => null);
  return { status: res.status, body: json };
}
const data = (r) => r.body?.data;

async function main() {
  const key = 'g1-' + Date.now();
  // 超管部署 → 200
  const depAdmin = await call('/processes/deploy', 'POST',
    { processKey: key, processName: 'G1测试', bpmnXml: BPMN }, 'admin');
  ok('G1 超管(admin) 部署 200', depAdmin.status === 200 && data(depAdmin), 'code=' + depAdmin.status);

  // 非超管部署 → 403
  const depBob = await call('/processes/deploy', 'POST',
    { processKey: key + 'b', processName: 'G1测试', bpmnXml: BPMN }, 'bob');
  ok('G1 非超管(bob) 部署 403', depBob.status === 403, 'code=' + depBob.status);

  // 超管回调注册 → 200
  const regAdmin = await call('/callbacks/register', 'POST',
    { appCode: 'rms-ap', baseUrl: 'http://localhost/nope' }, 'admin');
  ok('G1 超管(admin) 回调注册 200', regAdmin.status === 200, 'code=' + regAdmin.status);

  // 非超管回调注册 → 403
  const regBob = await call('/callbacks/register', 'POST',
    { appCode: 'rms-ap', baseUrl: 'http://localhost/nope' }, 'bob');
  ok('G1 非超管(bob) 回调注册 403', regBob.status === 403, 'code=' + regBob.status);

  // 超管审计查看 → 200
  const audAdmin = await call('/audit?businessKey=G1-' + Date.now(), 'GET', undefined, 'admin');
  ok('G1 超管(admin) 审计查看 200', audAdmin.status === 200, 'code=' + audAdmin.status);

  // 非超管审计查看 → 403
  const audBob = await call('/audit?businessKey=G1-' + Date.now(), 'GET', undefined, 'bob');
  ok('G1 非超管(bob) 审计查看 403', audBob.status === 403, 'code=' + audBob.status);

  console.log(`\n结果：${pass} 通过 / ${fail} 失败` + (fails.length ? '  ⟵ ' + fails.join(', ') : ''));
  process.exit(fail === 0 ? 0 : 1);
}
main().catch((e) => { console.error(e); process.exit(1); });
