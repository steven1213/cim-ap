// P1 批次 E2E：超时标红（待办/我发起的）+ 列表关键字过滤 + 发起草稿恢复
// 前置：后端以 --approval.task.timeout-minutes=0.05（3 秒）启动，扫描间隔 5s
import { createRequire } from 'node:module';
const require = createRequire('C:/Users/CL/.workbuddy/binaries/node/workspace/node_modules/playwright-core/package.json');
const { chromium } = require('playwright-core');
const exe = 'C:/Users/CL/.agent-browser/browsers/chrome-155.0.8059.79/chrome.exe';

const API = 'http://localhost:8085/api/v1/approval';
const RUN = 'P1' + (Date.now() % 100000);
let pass = 0, fail = 0;
function assert(name, cond, extra = '') {
  if (cond) { pass++; console.log(`PASS ${name}`); }
  else { fail++; console.log(`FAIL ${name} ${extra}`); }
}

async function api(path, opts = {}) {
  const res = await fetch(API + path, {
    headers: { 'Content-Type': 'application/json' },
    ...opts,
  });
  if (!res.ok) throw new Error(`API ${path} -> ${res.status}`);
  return res.json();
}

// ---- 1. 发起实例（alice → bob 单级审批）----
const bk = `P1-OD-${RUN}`;
await api('/processes/start', {
  method: 'POST',
  body: JSON.stringify({
    processKey: 'singleApproval', businessKey: bk, tenantId: 'default',
    initiator: 'alice', title: `超时验证-${bk}`, assignee: 'bob',
    formData: { item: '超时测试项' },
  }),
});
console.log(`started ${bk}, waiting 9s for overdue threshold(3s) + scan(5s)...`);
await new Promise((r) => setTimeout(r, 9000));

// ---- 2. API 层：待办带 overdueMinutes ----
const todo = await api('/tasks/todo?userId=bob&tenantId=default');
const od = todo.find((t) => t.businessKey === bk);
assert('api.todo.overdueMinutes', od && od.overdueMinutes > 0, JSON.stringify(od?.overdueMinutes));
const initiated = await api('/tasks/initiated?userId=alice&tenantId=default');
const ini = initiated.find((p) => p.businessKey === bk);
assert('api.initiated.overdueMinutes', ini && ini.overdueMinutes > 0, JSON.stringify(ini?.overdueMinutes));

// ---- 3. UI：待办页超时标红 + 关键字过滤（切换身份为 bob）----
const browser = await chromium.launch({ executablePath: exe, headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const setIdentity = (u) =>
  page.evaluate((userId) => {
    localStorage.setItem('approval-identity', JSON.stringify({ state: { userId, tenantId: 'default' }, version: 0 }));
  }, u);
await page.goto('http://[::1]:5175/');
await page.waitForTimeout(500);
await setIdentity('bob');
await page.goto('http://[::1]:5175/todo');
await page.waitForSelector('table.data tbody tr');
await page.waitForTimeout(800);
const todoRow = page.locator('tr', { hasText: bk }).first();
assert('ui.todo.overdueTag', await todoRow.locator('.tag.err', { hasText: '已超时' }).count() > 0);

// 过滤：命中关键字
await page.locator('.search-inline input').fill(bk);
await page.waitForTimeout(300);
const hitRows = await page.locator('tbody tr', { hasText: bk }).count();
const otherRows = await page.locator('tbody tr').count();
assert('ui.todo.filter.hit', hitRows === 1 && otherRows === 1, `hit=${hitRows} all=${otherRows}`);
// 过滤：无关关键字清空列表
await page.locator('.search-inline input').fill('ZZZ_NO_MATCH');
await page.waitForTimeout(300);
assert('ui.todo.filter.miss', await page.locator('.empty').count() > 0);
await page.locator('.search-inline input').fill('');

// ---- 4. UI：我发起的超时标红（切回 alice）----
await setIdentity('alice');
await page.goto('http://[::1]:5175/initiated');
await page.waitForSelector('table.data tbody tr');
await page.waitForTimeout(800);
const iniRow = page.locator('tr', { hasText: bk }).first();
assert('ui.initiated.overdueTag', await iniRow.locator('.tag.err', { hasText: '已超时' }).count() > 0);
await page.screenshot({ path: '_p1_overdue.png', fullPage: false });

// ---- 5. 草稿箱：填写 → 关闭 → 重开恢复 ----
await page.goto('http://[::1]:5175/definitions');
await page.waitForSelector('table.data tbody tr');
await page.waitForTimeout(600);
await page.locator('.panel-actions button', { hasText: '发起' }).first().click();
await page.waitForSelector('.scrim .field');
await page.locator('input[placeholder="BK-2026-001"]').fill(`DRAFT-${RUN}`);
await page.locator('input[placeholder*="配方签核"]').fill('草稿恢复验证标题');
const draftKey = `d${RUN}`;
await page.locator('.form-data-row input').first().fill(draftKey);
await page.locator('.form-data-row input').nth(2).fill('草稿字段值');
// 关闭弹窗（取消按钮）
await page.locator('.scrim button', { hasText: '取消' }).click();
await page.waitForTimeout(500);
// 重开
await page.locator('.panel-actions button', { hasText: '发起' }).first().click();
await page.waitForSelector('.scrim .field');
await page.waitForTimeout(400);
const restoredBk = await page.locator('input[placeholder="BK-2026-001"]').inputValue();
const restoredTitle = await page.locator('input[placeholder*="配方签核"]').inputValue();
const restoredRow = await page.locator('.form-data-row input').first().inputValue();
assert('ui.draft.restored.bk', restoredBk === `DRAFT-${RUN}`, restoredBk);
assert('ui.draft.restored.title', restoredTitle === '草稿恢复验证标题', restoredTitle);
assert('ui.draft.restored.row', restoredRow === draftKey, restoredRow);
assert('ui.draft.hint', await page.locator('.field-hint', { hasText: '已恢复' }).count() > 0);
await page.screenshot({ path: '_p1_draft.png', fullPage: false });

await browser.close();
console.log(`\nRESULT: ${pass} pass, ${fail} fail`);
process.exit(fail > 0 ? 1 : 0);
