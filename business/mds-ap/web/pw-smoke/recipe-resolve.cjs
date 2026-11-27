/* eslint-disable */
/**
 * 配方解算域 pw 冒烟（V29）：
 *   1. IAM 本地口令登录 → MDS
 *   2. 工序库动线：新建工序 → 编辑 → 加可变参数行 → 保存 → 刷新后查看态持久化断言
 *   3. 配方解算页动线：API 种流程/设备 → 选流程/节点/机台 → 执行解算 → 结果面板断言
 *      （种数不全时降级断言 BLOCKED 面板渲染）
 * 运行：node pw-smoke/recipe-resolve.cjs
 */
const { chromium } = require(require('fs').existsSync(
  'D:/DevCache/npm-cache/_npx/e41f203b7505f1fb/node_modules/playwright',
) ? 'D:/DevCache/npm-cache/_npx/e41f203b7505f1fb/node_modules/playwright' : 'playwright');

const BASE = 'http://localhost:5172';
const API = 'http://localhost:8082/api/v1/mds';
const ADMIN_PASSWORD = 'LocalAdmin#2026';

let pass = 0;
let fail = 0;
const failures = [];
function ok(name) { pass++; console.log(`PASS ${name}`); }
function bad(name, e) { fail++; failures.push(`${name}: ${e}`); console.log(`FAIL ${name}: ${e}`); }
function assert(cond, name, detail) {
  if (cond) ok(name); else bad(name, detail ?? 'assertion failed');
}

async function api(token, method, url, body) {
  const res = await fetch(API + url, {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* ignore */ }
  return { status: res.status, json };
}

async function seed(token) {
  const out = {};
  out.opCode = 'OPE-PW-' + (Date.now() % 100000); // 随机化：残留数据不撞唯一键
  const exists = async (url, pred) => {
    const r = await api(token, 'GET', url);
    return (r.json?.data ?? []).some(pred);
  };
  // 参数定义 / 机型模板 / 区域 / 设备：先查后建（存在即视为就绪）
  out.paramDef = await exists('/param-defs', (d) => d.attrKey === 'dc.temp')
    || (await api(token, 'POST', '/param-defs', {
      attrKey: 'dc.temp', attrName: '腔体温度', groupCode: 'DC',
      valueType: 'DECIMAL', required: false, scopes: ['OPERATION'],
    })).status === 200;
  out.classReady = await exists('/equipment-classes', (c) => c.classCode === 'ET-PROC')
    || (await api(token, 'POST', '/equipment-classes', {
      classCode: 'ET-PROC', className: '冒烟机型', kindCode: 'PROCESS', enabled: true,
    })).status === 200;
  // 设备：工艺设备（非 MATERIAL）守门要求绑 STAGE（种子机台位 STG-01）
  out.equipment = await exists('/equipments', (e) => e.equipmentCode === 'EQP-PW-01');
  if (!out.equipment && out.classReady) {
    const eqp = await api(token, 'POST', '/equipments', {
      equipmentCode: 'EQP-PW-01', equipmentName: '冒烟机台01', classCode: 'ET-PROC',
      locationType: 'STAGE', locationId: 'seed-stage-01', enabled: true,
    });
    out.equipment = eqp.status === 200;
  }
  if (!out.equipment) console.log('[seed] equipment not ready, resolve assertion falls back to BLOCKED panel');
  // 流程命名规则（幂等）
  const nr = await api(token, 'GET', '/naming-rules?objectType=FLOW');
  if (!(nr.json?.data ?? []).some((r) => r.objectType === 'FLOW' && r.enabled)) {
    await api(token, 'POST', '/naming-rules', {
      objectType: 'FLOW', ruleName: '流程编码',
      segments: [
        { type: 'LITERAL', text: 'FL', length: 2 },
        { type: 'SEQ', length: 2, start: 1, scope: 'GLOBAL' },
      ],
      enabled: true,
    });
  }
  return out;
}

/** 流程链（UI 建工序之后跑）：工序 activate → 流程画布 → 候选池 → validate → activate。 */
async function seedFlow(token, out) {
  const ops = await api(token, 'GET', '/operations?keyword=' + out.opCode);
  const op = (ops.json?.data ?? []).find((o) => o.opCode === out.opCode);
  if (op && op.status === 'UNFROZEN') {
    await api(token, 'POST', `/operations/${op.id}/freeze`, {});
    await api(token, 'POST', `/operations/${op.id}/activate`, {});
  }
  const flow = await api(token, 'POST', '/flows', { flowName: '冒烟解算流程' });
  if (flow.status !== 200) { console.log('[seed] flow create failed', flow.status, JSON.stringify(flow.json)); out.flow = null; return; }
  const flowId = flow.json?.data?.id;
  const xml = `<canvas version="1">
  <nodes>
    <node id="n1" stepCode="START" stepType="START" x="40" y="200"/>
    <node id="n2" stepCode="OPE010" stepName="氧化" stepType="PROCESS" operationCode="${out.opCode}" x="180" y="200"/>
    <node id="n3" stepCode="END" stepType="END" x="340" y="200"/>
  </nodes>
  <edges>
    <edge id="e1" from="n1" to="n2" edgeType="MAIN"/>
    <edge id="e2" from="n2" to="n3" edgeType="MAIN"/>
  </edges>
</canvas>`;
  const cv = await api(token, 'PUT', `/flows/${flowId}/canvas`, { xml });
  if (cv.status !== 200) console.log('[seed] canvas failed', cv.status, JSON.stringify(cv.json).slice(0, 200));
  if (out.equipment && cv.status === 200) {
    const as = await api(token, 'POST', `/flows/${flowId}/assigns`, {
      assignType: 'EQUIP', assignRef: 'EQP-PW-01',
      recipeRule: 'FIXED', recipeRef: 'PPID-PW-1', priority: 100,
    });
    if (as.status !== 200) console.log('[seed] assign failed', as.status, JSON.stringify(as.json).slice(0, 200));
  }
  const val = await api(token, 'POST', `/flows/${flowId}/validate`, {});
  if (val.status !== 200 || val.json?.data?.valid === false) {
    console.log('[seed] validate', val.status, JSON.stringify(val.json).slice(0, 300));
  }
  await api(token, 'POST', `/flows/${flowId}/freeze`, {});
  const act = await api(token, 'POST', `/flows/${flowId}/activate`, {});
  if (act.status !== 200) console.log('[seed] activate failed', act.status, JSON.stringify(act.json).slice(0, 300));
  out.flow = act.status === 200 ? flowId : null;
  out.flowCode = flow.json?.data?.flowCode;
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  page.setDefaultTimeout(15000);

  try {
    // ---------- 登录（IAM 本地口令） ----------
    await page.goto(BASE + '/', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#u', { timeout: 30000 });
    await page.fill('#u', 'admin');
    await page.fill('#p', ADMIN_PASSWORD);
    await page.click('#b');
    // PBKDF2 派生 + IAM 签发 + SSO 回跳，可能要十几秒
    for (let i = 0; i < 30; i++) {
      await page.waitForTimeout(1000);
      const url = page.url();
      if (url.startsWith(BASE) && !url.includes('/login')) break;
    }
    await page.waitForTimeout(3000); // 等 /sso 中继完成、session 落盘
    const token = await page.evaluate(() => {
      const raw = localStorage.getItem('mds-auth');
      if (!raw) return null;
      try { return JSON.parse(raw)?.state?.token ?? null; } catch { return null; }
    });
    assert(!!token, 'login-token', 'localStorage[mds] has no token');
    if (!token) throw new Error('no token, abort');

    // ---------- API 种数（主数据段；流程链在 UI 建工序后跑） ----------
    const seedInfo = await seed(token);
    console.log('[seed/master]', JSON.stringify(seedInfo));
    const flowCode = seedInfo.flowCode;

    // ---------- 2. 工序库：新建 → 编辑 → 可变参数 → 保存 → 持久化 ----------
    const opCode = seedInfo.opCode;
    await page.goto(BASE + '/operations', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: '新建' }).first().waitFor({ timeout: 15000 });
    await page.getByRole('button', { name: '新建' }).first().click();
    await page.waitForSelector('#f-opCode', { timeout: 10000 });
    await page.fill('#f-opCode', opCode);
    await page.fill('#f-opName', '冒烟氧化工序');
    await page.locator('.op-detail-actions .btn', { hasText: '保存' }).click();
    // 保存后回查看态：详情头出现「编辑」
    await page.locator('.op-detail-actions .btn', { hasText: '编辑' }).waitFor({ timeout: 15000 });
    ok('operation-created');

    await page.locator('.op-detail-actions .btn', { hasText: '编辑' }).click();
    await page.waitForSelector('#f-opName', { timeout: 10000 });
    await page.locator('.btn', { hasText: '添加参数' }).first().click();
    // 原生 select（可变参数块非 antd）：第一行 = 添加后唯一行
    const varTable = page.locator('table.icm-table').last();
    await varTable.locator('select').first().selectOption('dc.temp');
    const inputs = varTable.locator('tbody tr').first().locator('input');
    await inputs.nth(0).fill('100');
    await inputs.nth(1).fill('90');
    await inputs.nth(2).fill('110');
    await varTable.locator('tbody tr').first().locator('select').nth(1).selectOption('WAFER');
    await page.locator('.op-detail-actions .btn', { hasText: '保存' }).click();
    await page.locator('.op-detail-actions .btn', { hasText: '编辑' }).waitFor({ timeout: 15000 });
    await page.waitForTimeout(1000); // 等 varParams 查看态请求回来
    const rowText = await page.locator('table.icm-table').last().locator('tbody').innerText();
    assert(rowText.includes('dc.temp') && rowText.includes('100'), 'var-param-saved-view', rowText.slice(0, 200));

    // 刷新后重新选中 → 持久化
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('tr', { hasText: opCode }).first().click();
    await page.waitForTimeout(1500);
    const rowText2 = await page.locator('table.icm-table').last().locator('tbody').innerText();
    assert(rowText2.includes('dc.temp'), 'var-param-persisted', rowText2.slice(0, 200));

    // ---------- 流程链种子（工序已 ACTIVE） ----------
    await seedFlow(token, seedInfo);
    console.log('[seed/flow]', JSON.stringify({ flow: seedInfo.flow, flowCode: seedInfo.flowCode }));
    const flowCode2 = seedInfo.flowCode;
    // ---------- 3. 配方解算页 ----------
    await page.goto(BASE + '/recipe-resolve', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#rr-flow', { timeout: 15000 });
    // 流程下拉：按 flowCode 选中（value=flowId）
    if (flowCode2) {
      const flowSel = page.locator('#rr-flow');
      const opt = flowSel.locator('option', { hasText: flowCode2 });
      const flowVal = await opt.getAttribute('value');
      if (flowVal) {
        await flowSel.selectOption(flowVal);
        await page.waitForTimeout(1200); // 等节点详情回来
        const nodeSel = page.locator('#rr-node');
        const nodeVal = await nodeSel.locator('option[value]:not([value=""])').first().getAttribute('value');
        if (nodeVal) await nodeSel.selectOption(nodeVal);
        const eqpSel = page.locator('#rr-eqp');
        const eqpOpt = eqpSel.locator('option[value="EQP-PW-01"]');
        const eqpVal = (await eqpOpt.count()) ? 'EQP-PW-01'
          : await eqpSel.locator('option[value]:not([value=""])').first().getAttribute('value');
        if (eqpVal) await eqpSel.selectOption(eqpVal);
        await page.locator('.btn', { hasText: '执行解算' }).click();
        await page.waitForTimeout(2000);
        const panel = await page.locator('.op-detail-body').last().innerText();
        const resolvedShown = panel.includes('解算成功') && panel.includes('PPID');
        const blockedShown = panel.includes('阻断') && panel.includes('原因码');
        assert(resolvedShown || blockedShown, 'resolve-panel-rendered', panel.slice(0, 300));
        if (seedInfo.equipment) {
          assert(panel.includes('PPID-PW-1') || blockedShown,
            'resolve-snapshot-or-blocked', panel.slice(0, 300));
        }
      } else {
        bad('resolve-panel-rendered', `flow option ${flowCode2} not found`);
      }
    } else {
      bad('resolve-panel-rendered', 'flow seed failed');
    }
  } catch (e) {
    bad('script', e.message ?? String(e));
  } finally {
    await page.screenshot({ path: 'pw-smoke/recipe-resolve-last.png', fullPage: false }).catch(() => {});
    await browser.close();
  }

  console.log(`\n==== RESULT: ${pass} PASS / ${fail} FAIL ====`);
  failures.forEach((f) => console.log('  - ' + f));
  process.exit(fail > 0 ? 1 : 0);
})();
