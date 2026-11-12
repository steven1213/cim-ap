// 开放 API 安全（G9）+ 业务域隔离（G3）端到端验证。
// 前置：后端 8085 已启动，且 approval.api.security.enabled=true（运行时 application.yml 已置 true）。
// 该开关仅对携带 X-App-Key 的服务间调用生效；JWT 用户态与 dev 放行不受影响。
//
// 本脚本**全部走 X-App-Key 通道**（服务间面）。原因：cim.auth.enabled 已默认 true，
// 无凭据的「dev 放行」路径不复存在，而这些用例验的正是服务间调用；用 JWT 反而不成立
// ——用户令牌的 apps 常同时含多个 ap，跨域用例会被合法放行。
//
// 测试覆盖：
//   G9  合法签名 → 200；缺签名 / 错签名 / 超重放窗口 → 403；按 appCode 限流 → 403；
//       跨业务域（tenantId/appCode 参数 != appKey 绑定域）→ 403。
//   G3  audit 跨业务域查询（开放 API）→ 403。
//   G3  不传 appCode 时收敛为授权域（「不传参 ≠ 全量」）：造两域数据，断言异域审计为空、
//       本域审计可见、回调列表不含异域、回调 KPI 不传参 200 / 传异域 403。
//   sanity 开放 API 贯通（部署/发起/待办）→ 200（认证开启后服务身份仍能过 /api/** 的 authenticated）。
import crypto from 'node:crypto';
import fs from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = process.env.BASE || 'http://localhost:8085/api/v1/approval';
// 相对本脚本定位 BPMN：写死绝对路径时换机器/换目录就挂，而失败的报错是
// ENOENT 而不是「路径不对」，容易误判成流程定义缺失。
const BPMN_PATH = resolve(dirname(fileURLToPath(import.meta.url)),
  '../../server/src/main/resources/processes/single-approval.bpmn20.xml');
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
  const BPMN = fs.readFileSync(BPMN_PATH, 'utf8');

  // ---- sanity: 开放 API 贯通（认证开启后服务身份仍须通过 /api/** 的 authenticated） ----
  // ApiAuthFilter 若只写域作用域、不写 SecurityContext，这里会全 401——那正是本轮修掉的断裂。
  const dep = await api('/processes/deploy', { method: 'POST', appKey: 'rms-ap', body: { processKey: 'singleApproval', processName: '单级审批', bpmnXml: BPMN } });
  ok('sanity: 部署（开放 API）200', dep.status === 200 && data(dep), 'code=' + dep.status);

  const bk = 'SEC-' + Date.now();
  const st = await api('/processes/start', { method: 'POST', appKey: 'rms-ap', body: { processKey: 'singleApproval', businessKey: bk, tenantId: 'rms-ap', initiator: 'alice', title: '安全回归', assignee: 'bob', formData: { amount: 1 } } });
  ok('sanity: 发起（开放 API）200', st.status === 200 && data(st)?.processInstanceId, 'code=' + st.status);
  const todo = await api('/tasks/todo?userId=bob&tenantId=rms-ap', { appKey: 'rms-ap' });
  ok('sanity: 待办（开放 API）200', todo.status === 200, 'code=' + todo.status);

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

  // ---- G3: 不传 appCode 时收敛为授权域（「不传参 ≠ 全量」） ----
  // 上面所有跨域用例都是「显式传异域参数 → 403」。那挡不住真正的洞：appCode 是可选参数，
  // 不传时若走「不限域」分支，知道 businessKey 就能读到跨域数据。故这里造两域数据，
  // 断言不传参时的**结果集**（不是状态码）——状态码 200 + 空集才是收敛的正确形态。
  {
    const ts0 = Date.now();
    const bkRms = `SEC-RMS-${ts0}`;
    const bkMds = `SEC-MDS-${ts0}`;
    const body = (businessKey, tenantId) => ({
      processKey: 'singleApproval', businessKey, tenantId,
      initiator: 'alice', title: '域收敛回归', assignee: 'bob',
    });
    const s1 = await api('/processes/start', { method: 'POST', appKey: 'rms-ap', body: body(bkRms, 'rms-ap') });
    const s2 = await api('/processes/start', { method: 'POST', appKey: 'mds-ap', body: body(bkMds, 'mds-ap') });
    ok('G3 造数：rms-ap / mds-ap 各发起一单', s1.status === 200 && s2.status === 200,
      `rms=${s1.status} mds=${s2.status}`);

    // 不传 appCode 查异域单号的审计 → 应为空集（403 也算挡住了，但不是预期形态）
    const cross = await api(`/audit?businessKey=${bkMds}`, { appKey: 'rms-ap' });
    const crossLen = (data(cross) || []).length;
    ok('G3 不传 appCode → 异域审计为空', cross.status === 200 && crossLen === 0,
      `status=${cross.status} len=${crossLen}`);

    // 不传 appCode 查本域单号 → 应能看到（证明收敛不是「一律查不到」）
    const own = await api(`/audit?businessKey=${bkRms}`, { appKey: 'rms-ap' });
    const ownLen = (data(own) || []).length;
    ok('G3 不传 appCode → 本域审计可见', own.status === 200 && ownLen > 0,
      `status=${own.status} len=${ownLen}`);

    // 回调配置列表不传参 → 不得出现异域条目
    const cbs = await api('/callbacks', { appKey: 'rms-ap' });
    const leaked = (data(cbs) || []).filter((v) => v.appCode !== 'rms-ap');
    ok('G3 回调列表不传参 → 不含异域', cbs.status === 200 && leaked.length === 0,
      'code=' + cbs.status + ' leaked=' + (leaked.map((v) => v.appCode).join(',') || '无'));

    // 回调 KPI：不传参 200；显式传异域 403（此前恒为全局计数，不校验域）
    const kpi = await api('/callbacks/outbox/stats', { appKey: 'rms-ap' });
    ok('G3 回调 KPI 不传参 → 200', kpi.status === 200 && data(kpi) != null, 'code=' + kpi.status);
    const kpiCross = await api('/callbacks/outbox/stats?appCode=mds-ap', { appKey: 'rms-ap' });
    ok('G3 回调 KPI 跨域 → 403', kpiCross.status === 403, 'code=' + kpiCross.status);
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
