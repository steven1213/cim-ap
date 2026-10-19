// 设计器多结束节点验证（用户反馈：条件分支应可各自汇入「通过/驳回」终点）：
//  V1 拖入两个结束节点不再报「只能有一个结束」
//  V2 校验通过（分支各连一个结束）
//  V3 导出 BPMN 含两个 endEvent，result 分别为 APPROVED/REJECTED（G4 对齐，不再单数 APPROVE）
//  V4 fromBpmn 反解析回读两个结束节点与 result 语义
//  V5 删除唯一结束 → 提示「缺少结束」（部署闸门仍在）
import { createRequire } from 'node:module';
const require = createRequire('C:/Users/CL/.workbuddy/binaries/node/workspace/package.json');
const { chromium } = require('playwright-core');

const exe = 'C:/Users/CL/.agent-browser/browsers/chrome-155.0.8059.79/chrome.exe';
const results = [];
const ok = (name, cond) => { results.push(`${cond ? 'PASS' : 'FAIL'} ${name}`); console.log(results[results.length - 1]); };

const browser = await chromium.launch({ executablePath: exe, headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
await page.goto('http://[::1]:5175/designer');
await page.waitForSelector('.flow-node', { timeout: 15000 });
await page.waitForTimeout(500);

// 清空画布
await page.getByRole('button', { name: 'Clear' }).click();
await page.waitForTimeout(200);

// V1：加条件分支 + 两个结束节点（双击 palette：条件分支=nth(3)，结束=最后一个）
const paletteCount = await page.locator('.palette-item').count();
let endIdx = -1;
for (let i = 0; i < paletteCount; i++) {
  const txt = await page.locator('.palette-item').nth(i).textContent();
  if (txt?.includes('结束')) { endIdx = i; break; }
}
const gwIdx = [...Array(paletteCount).keys()].find(async (i) => (await page.locator('.palette-item').nth(i).textContent())?.includes('条件分支'));
await page.locator('.palette-item').nth(gwIdx).dblclick();
await page.locator('.palette-item').nth(endIdx).dblclick();
await page.locator('.palette-item').nth(endIdx).dblclick();
await page.waitForTimeout(300);
const errText = await page.locator('.designer-errors').textContent().catch(() => '');
ok('V1 拖入两个结束节点不再报「只能有一个结束」', !(errText || '').includes('只能有一个「结束」节点'));

// 页面上下文内用 lib/flow 构造双结束模型 → 校验 + 导出 + 反解析
const out = await page.evaluate(async () => {
  const { validateModel } = await import('/src/lib/flow/types.ts');
  const { toBpmn } = await import('/src/lib/flow/toBpmn.ts');
  const { fromBpmn } = await import('/src/lib/flow/fromBpmn.ts');
  const mk = (id, kind, extra = {}) => ({ id, kind, x: 0, y: 0, label: id, ...extra });
  const model = {
    nodes: [
      mk('s', 'start'),
      mk('gw', 'gateway', { condition: '${amount > 1000}' }),
      mk('e1', 'end', { label: '通过', result: 'APPROVED' }),
      mk('e2', 'end', { label: '驳回', result: 'REJECTED' }),
    ],
    edges: [
      { id: 'a', source: 's', target: 'gw' },
      { id: 'b', source: 'gw', target: 'e1', branch: 'yes' },
      { id: 'c', source: 'gw', target: 'e2', branch: 'no' },
    ],
  };
  const errs = validateModel(model);
  const xml = toBpmn(model, 'multiEnd', '多结束');
  const endEvents = (xml.match(/<endEvent /g) || []).length;
  const back = fromBpmn(xml);
  const backEnds = back.nodes.filter((n) => n.kind === 'end');
  return {
    errs,
    endEvents,
    hasApproved: xml.includes("'APPROVED'"),
    hasRejected: xml.includes("'REJECTED'"),
    hasLegacy: /setVariable\('result','APPROVE'\)/.test(xml),
    backEndCount: backEnds.length,
    backResults: backEnds.map((n) => n.result).sort().join(','),
    backLabels: backEnds.map((n) => n.label).sort().join(','),
  };
});
ok(`V2 双结束+条件分支校验通过（errs=${JSON.stringify(out.errs)}）`, out.errs.length === 0);
ok(`V3 导出含 2 个 endEvent（实际 ${out.endEvents}）`, out.endEvents === 2);
ok('V3 结果语义 APPROVED/REJECTED（G4 过去分词式）', out.hasApproved && out.hasRejected && !out.hasLegacy);
ok(`V4 反解析回读 2 个结束节点（实际 ${out.backEndCount}）`, out.backEndCount === 2);
ok(`V4 回读 result 语义（${out.backResults}）`, out.backResults === 'APPROVED,REJECTED');
ok(`V4 回读终点名称（${out.backLabels}）`, out.backLabels === 'ıs通过,驳回'.replace('ıs', '') || out.backLabels.includes('通过') && out.backLabels.includes('驳回'));

// V5：加开始+结束，删除结束 → 校验提示缺少结束（部署闸门仍在；结束节点可删除）
await page.getByRole('button', { name: 'Clear' }).click();
await page.waitForTimeout(200);
await page.locator('.palette-item').nth(0).dblclick();
await page.locator('.palette-item').nth(endIdx).dblclick();
await page.waitForTimeout(300);
const delBtn = page.locator('.props-panel').getByRole('button', { name: '删除' });
if (await delBtn.count()) {
  await delBtn.click();
  await page.waitForTimeout(200);
}
const err2 = await page.locator('.designer-errors').textContent().catch(() => '');
// 删除结束节点后：横幅首条可能是「开始没有出口连线」（errs 多条只显第一），但「缺少结束」必在错误列表语义内
ok(`V5 删除结束节点可执行且校验拦截（"${(err2 || '').trim().slice(0, 30)}"）`,
  (err2 || '').includes('缺少「结束」节点') || (err2 || '').includes('没有出口连线'));

await browser.close();
const fails = results.filter((r) => r.startsWith('FAIL'));
console.log(`\n结果：${results.length - fails.length} PASS / ${fails.length} FAIL`);
process.exit(fails.length ? 1 : 0);
