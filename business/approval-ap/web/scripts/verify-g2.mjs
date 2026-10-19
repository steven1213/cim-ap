// G2 网关验证：回调注册收编为受信操作 —— 重复注册同 appCode 需二次确认。
// 覆盖：首注创建 / 重复首调签发令牌 / 错误令牌 400 / 回填令牌覆盖 / 覆盖后再次触发二次确认 / secret 不回显 / 注册入审计。
// 运行：先启动 8085（dev-bypass，cim.auth.enabled=false），再 node verify-g2.mjs
const BASE = process.env.BASE || 'http://localhost:8085/api/v1/approval';

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
  const appCode = 'g2-' + Date.now();

  // 1. 首注：创建（confirmRequired 应为 false/undefined）
  const c1 = await call('/callbacks/register', 'POST', { appCode, baseUrl: 'http://localhost/svc-1' }, 'admin');
  const v1 = data(c1);
  ok('G2 首次注册 200', c1.status === 200, 'code=' + c1.status);
  ok('G2 首次注册 confirmRequired=false', v1 && v1.confirmRequired !== true, 'confirmRequired=' + v1?.confirmRequired);
  ok('G2 secret 不回显', v1 && !('secret' in v1), Object.keys(v1 || {}).join(','));

  // 2. 重复注册首调：签发令牌（confirmRequired=true + confirmToken 非空），且不覆盖
  const c2 = await call('/callbacks/register', 'POST', { appCode, baseUrl: 'http://localhost/svc-2' }, 'admin');
  const v2 = data(c2);
  ok('G2 重复首调 200', c2.status === 200, 'code=' + c2.status);
  ok('G2 重复首调 confirmRequired=true', v2 && v2.confirmRequired === true, 'confirmRequired=' + v2?.confirmRequired);
  ok('G2 重复首调 返回 confirmToken', !!(v2 && v2.confirmToken), 'token=' + (v2?.confirmToken ? 'yes' : 'no'));
  ok('G2 重复首调 未覆盖（仍 svc-1）', v2 && v2.baseUrl === 'http://localhost/svc-1', 'baseUrl=' + v2?.baseUrl);
  const token = v2?.confirmToken;

  // 3. 错误令牌：应 400
  const c3 = await call('/callbacks/register?confirmToken=bad-token', 'POST', { appCode, baseUrl: 'http://localhost/svc-3' }, 'admin');
  ok('G2 错误令牌 400', c3.status === 400, 'code=' + c3.status);

  // 4. 回填正确令牌：覆盖为 svc-3
  const c4 = await call('/callbacks/register?confirmToken=' + encodeURIComponent(token), 'POST', { appCode, baseUrl: 'http://localhost/svc-3' }, 'admin');
  const v4 = data(c4);
  ok('G2 回填令牌 200', c4.status === 200, 'code=' + c4.status);
  ok('G2 回填后 confirmRequired=false', v4 && v4.confirmRequired !== true, 'confirmRequired=' + v4?.confirmRequired);
  ok('G2 回填后已覆盖为 svc-3', v4 && v4.baseUrl === 'http://localhost/svc-3', 'baseUrl=' + v4?.baseUrl);

  // 5. 覆盖后再次注册：应再次触发二次确认（证明状态已更新）
  const c5 = await call('/callbacks/register', 'POST', { appCode, baseUrl: 'http://localhost/svc-4' }, 'admin');
  const v5 = data(c5);
  ok('G2 覆盖后再注册 再次二次确认', v5 && v5.confirmRequired === true, 'confirmRequired=' + v5?.confirmRequired);

  // 6. 注册/变更入审计（G3 按 appCode 收敛，businessKey="-"）
  const aud = await call('/audit?businessKey=-&appCode=' + encodeURIComponent(appCode), 'GET', undefined, 'admin');
  const acts = (data(aud) || []).map((a) => a.action);
  ok('G2 注册入审计', acts.some((a) => a === 'CALLBACK_REGISTER'), acts.join(','));
  ok('G2 覆盖变更入审计', acts.some((a) => a === 'CALLBACK_UPDATE'), acts.join(','));

  console.log(`\n结果：${pass} 通过 / ${fail} 失败` + (fails.length ? '  ⟵ ' + fails.join(', ') : ''));
  process.exit(fail === 0 ? 0 : 1);
}
main().catch((e) => { console.error(e); process.exit(1); });
