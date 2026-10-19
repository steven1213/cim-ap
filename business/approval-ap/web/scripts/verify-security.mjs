// 开放 API 安全（G9）+ 业务域隔离（G3）端到端验证。
// 前置：后端 8085 已启动，且 approval.api.security.enabled=true（运行时 application.yml 已置 true）。
// 该开关仅对携带 X-App-Key 的服务间调用生效；JWT 用户态与 dev 放行不受影响。
//
// 测试覆盖：
//   G9  合法签名 → 200；缺签名 / 错签名 / 超重放窗口 → 403；按 appCode 限流 → 403；
//       跨业务域（tenantId/appCode 参数 != appKey 绑定域）→ 403。
//   G3  audit 跨业务域查询（开放 API）→ 403。
//   sanity 无 app-key 调用（部署/发起/待办）→ 200（确认开放面开关不破坏既有 E2E）。
import crypto from 'node:crypto';
import fs from 'node:fs';

const BASE = process.env.BASE || 'http://localhost:8085/api/v1/approval';
const KEYS = {
  'rms-ap': 'sec-rms-ap-2026',
  'mds-ap': 'sec-mds-ap-2026',
  ratetest: 'sec-ratetest-2026',
};

let pass = 0, fail = 0;
const fails = [];
function ok(name, cond, extra = '') {
  if (cond) { pass++; console.log(`  ✅ ${name}${extra ? ' :: ' + extra : ''}`); }
  else { fail++; fails.push(name); console.log(`  ❌ ${name}${extra ? ' :: ' + extra : ''}`); }
}

function sign(secret, ts, method, path, body = '') {
  const canonical = `${ts}|${method}|${path}|${body}`;
  return crypto.createHmac('sha256', secret).update(canonical).digest('hex');
}

// 计算签名所需的 path（不含 query）
function pathOf(url) { return new URL(url).pathname; }

async function api(path, { method = 'GET', body, appKey } = {}) {
  const url = BASE + path;
  const headers = { 'Content-Type': 'application/json' };
  let payload;
  if (body !== undefined) {
    payload = JSON.stringify(body);
    headers['Content-Type'] = 'application/json';
  }
  if (appKey) {
    const secret = KEYS[appKey];
    const ts = Date.now();
    headers['X-App-Key'] = appKey;
    headers['X-Timestamp'] = String(ts);
    headers['X-Signature'] = sign(secret, ts, method, pathOf(url), payload || '');
  }
  const res = await fetch(url, { method, headers, body: payload });
  const json = await res.json().catch(() => null);
  return { status: res.status, body: json, url };
}
const data = (r) => r.body?.data;

async function main() {
  const BPMN = fs.readFileSync(
    'E:/Personal/CIM/cim-ap/business/approval-ap/server/src/main/resources/processes/single-approval.bpmn20.xml',
    'utf8',
  );

  // ---- sanity: 无 app-key 调用（dev 放行，确认开关不破坏既有 E2E） ----
  const dep = await api('/processes/deploy', { method: 'POST', body: { processKey: 'singleApproval', processName: '单级审批', bpmnXml: BPMN } });
  ok('sanity: 部署（无 app-key）200', dep.status === 200 && data(dep), 'code=' + dep.status);

  const bk = 'SEC-' + Date.now();
  const st = await api('/processes/start', { method: 'POST', body: { processKey: 'singleApproval', businessKey: bk, tenantId: 'rms-ap', initiator: 'alice', title: '安全回归', assignee: 'bob', formData: { amount: 1 } } });
  ok('sanity: 发起（无 app-key）200', st.status === 200 && data(st)?.processInstanceId, 'code=' + st.status);
  const todo = await api('/tasks/todo?userId=bob&tenantId=rms-ap');
  ok('sanity: 待办（无 app-key）200', todo.status === 200, 'code=' + todo.status);

  // ---- G9: 合法签名 → 200 ----
  const good = await api('/definitions', { appKey: 'rms-ap' });
  ok('G9 合法签名 → 200', good.status === 200 && Array.isArray(data(good)), 'code=' + good.status);

  // ---- G9: 缺签名（有 app-key 无 X-Signature）→ 403 ----
  {
    const url = BASE + '/definitions';
    const res = await fetch(url, { headers: { 'X-App-Key': 'rms-ap' } });
    ok('G9 缺签名 → 403', res.status === 403, 'code=' + res.status);
  }

  // ---- G9: 错签名 → 403 ----
  {
    const url = BASE + '/definitions';
    const ts = Date.now();
    const res = await fetch(url, { headers: { 'X-App-Key': 'rms-ap', 'X-Timestamp': String(ts), 'X-Signature': 'deadbeef'.repeat(8) } });
    ok('G9 错签名 → 403', res.status === 403, 'code=' + res.status);
  }

  // ---- G9: 超重放窗口（10 分钟前）→ 403 ----
  {
    const url = BASE + '/definitions';
    const ts = Date.now() - 10 * 60 * 1000;
    const sig = sign(KEYS['rms-ap'], ts, 'GET', pathOf(url), '');
    const res = await fetch(url, { headers: { 'X-App-Key': 'rms-ap', 'X-Timestamp': String(ts), 'X-Signature': sig } });
    ok('G9 超重放窗口 → 403', res.status === 403, 'code=' + res.status);
  }

  // ---- G9 + G3: 跨业务域（开放 API 以 rms-ap 身份请求 tenantId=mds-ap）→ 403 ----
  {
    const path = '/tasks/todo?userId=bob&tenantId=mds-ap';
    const r = await api(path, { appKey: 'rms-ap' });
    ok('G9/G3 跨业务域(rms-ap→mds-ap) → 403', r.status === 403, 'code=' + r.status);
  }

  // ---- G3: audit 跨业务域（开放 API 以 rms-ap 查询 appCode=mds-ap）→ 403 ----
  {
    const path = `/audit?businessKey=${bk}&appCode=mds-ap`;
    const r = await api(path, { appKey: 'rms-ap' });
    ok('G3 audit 跨业务域(rms-ap→mds-ap) → 403', r.status === 403, 'code=' + r.status);
  }

  // ---- G3: audit 同域（开放 API 以 rms-ap 查询 appCode=rms-ap）→ 200 ----
  {
    const path = `/audit?businessKey=${bk}&appCode=rms-ap`;
    const r = await api(path, { appKey: 'rms-ap' });
    ok('G3 audit 同域(rms-ap→rms-ap) → 200', r.status === 200, 'code=' + r.status);
  }

  // ---- G9: 按 appCode 限流（ratetest 配额=3，连续 5 次，第 4 次起 403） ----
  {
    let okCount = 0, rejectCount = 0;
    for (let i = 0; i < 5; i++) {
      const r = await api('/definitions', { appKey: 'ratetest' });
      if (r.status === 200) okCount++;
      else if (r.status === 403) rejectCount++;
    }
    ok('G9 限流：前若干 200 且后续 403', okCount >= 1 && rejectCount >= 1, `ok=${okCount} reject=${rejectCount}`);
  }

  console.log(`\n结果：${pass} 通过 / ${fail} 失败` + (fails.length ? '  ⟵ ' + fails.join(', ') : ''));
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((e) => { console.error(e); process.exit(1); });
