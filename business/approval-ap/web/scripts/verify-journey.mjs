// 无头浏览器 E2E：催办（SSE 到达+审计）→ 撤回（状态 WITHDRAWN+审计）→ 分级菜单。
import { createRequire } from 'node:module';
const require = createRequire('C:/Users/CL/.workbuddy/binaries/node/workspace/node_modules/playwright-core/package.json');
const { chromium } = require('playwright-core');

const API = 'http://localhost:8085/api/v1/approval';
async function api(path, opts = {}) {
  const r = await fetch(`${API}${path}`, { headers: { 'Content-Type': 'application/json' }, ...opts });
  const body = await r.json().catch(() => null);
  return { status: r.status, body };
}
const RUN = 'J' + (Date.now() % 100000);
const start = (bk) => {
  const businessKey = `${bk}-${RUN}`;
  return api('/processes/start', {
    method: 'POST',
    body: JSON.stringify({
      processKey: 'singleApproval', businessKey, tenantId: 'default',
      initiator: 'alice', title: `动线验证-${businessKey}`, assignee: 'bob',
      formData: { item: '测试项' },
    }),
  });
};

// 发起两条实例：URGE-J*（催办）、WD-J*（撤回），每次运行唯一后缀
const u = await start('URGE-J');
const w = await start('WD-J');
const urgeId = u.body?.processInstanceId;
const wdId = w.body?.processInstanceId;
if (!urgeId || !wdId) { console.log('FAIL 发起失败', u, w); process.exit(1); }
console.log('started:', urgeId, wdId);

const exe = 'C:/Users/CL/.agent-browser/browsers/chrome-155.0.8059.79/chrome.exe';
const browser = await chromium.launch({ executablePath: exe, headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

let pass = 0, fail = 0;
const check = (cond, msg) => { cond ? (pass++, console.log('PASS', msg)) : (fail++, console.log('FAIL', msg)); };

// ---- 1) SSE：bob 订阅通知流，alice 催办 URGE-J-1，断言 URGE 事件到达 ----
await page.goto('http://[::1]:5175/todo');
await page.waitForTimeout(1500);
const urgePromise = page.evaluate(
  (piId) =>
    new Promise((resolve) => {
      const es = new EventSource(`/api/v1/approval/notifications/stream?userId=bob`);
      const timer = setTimeout(() => { es.close(); resolve(null); }, 15000);
      es.addEventListener('URGE', (ev) => {
        clearTimeout(timer);
        es.close();
        try { resolve(JSON.parse(ev.data)); } catch { resolve(null); }
      });
    }),
  urgeId,
);
await page.waitForTimeout(800); // 等 SSE 握手完成
const urgeResp = await api(`/processes/instances/${urgeId}/urge?actor=alice`, { method: 'POST' });
const urgeEvent = await urgePromise;
check(urgeResp.status === 200, '催办接口 200');
check(urgeEvent && urgeEvent.type === 'URGE' && urgeEvent.businessKey.startsWith('URGE-J-'), 'SSE 收到 URGE 催办事件（bob 端）');

// 非发起人催办应被拒
const urgeBob = await api(`/processes/instances/${urgeId}/urge?actor=bob`, { method: 'POST' });
check(urgeBob.status === 403 || urgeBob.body?.code === 2003, '非发起人催办被拒（403/ACCESS_DENIED）');

// ---- 2) 我发起的：ACTIVE 行有催办/撤回按钮；撤回 WD-J-1 ----
await page.goto('http://[::1]:5175/initiated');
await page.waitForSelector('table.data tbody tr');
await page.waitForTimeout(600);
const activeRow = page.locator('tr', { hasText: `URGE-J-${RUN}` }).first();
check(await activeRow.locator('button', { hasText: '催办' }).count() === 1, '运行中行显示「催办」按钮');
check(await activeRow.locator('button', { hasText: '撤回' }).count() === 1, '运行中行显示「撤回」按钮');

page.on('dialog', (d) => d.accept());
const wdRow = page.locator('tr', { hasText: `WD-J-${RUN}` }).first();
await wdRow.locator('button', { hasText: '撤回' }).first().click();
await page.waitForTimeout(1500);
const wdText = await wdRow.innerText().catch(() => '');
check(wdText.includes('已撤回'), '撤回后状态变「已撤回」');
const wRowAfter = page.locator('tr', { hasText: `WD-J-${RUN}` }).first();
check(await wRowAfter.locator('button', { hasText: '催办' }).count() === 0, '已撤回行不再显示催办/撤回按钮');

// ---- 3) 详情：历史含 WITHDRAW 标签；状态「已撤回」 ----
await wRowAfter.locator('button', { hasText: '详情' }).first().click();
await page.waitForSelector('.detail-body');
await page.waitForTimeout(500);
const detail = await page.evaluate(() => ({
  status: document.querySelector('.detail-grid .tag')?.innerText,
  actions: [...document.querySelectorAll('.detail-timeline-item .tag')].map((t) => t.innerText),
}));
check(detail.status === '已撤回', '详情状态展示「已撤回」');
check(detail.actions.includes('撤回'), '审批历史含「撤回」审计标签');
await page.keyboard.press('Escape');

// ---- 4) 分级菜单 ----
await page.goto('http://[::1]:5175/');
await page.waitForTimeout(800);
const groups = await page.evaluate(() =>
  [...document.querySelectorAll('.side-nav .side-cap')].map((e) => e.innerText.trim()),
);
check(JSON.stringify(groups) === JSON.stringify(['工作台', '审批中心', '流程配置', '合规治理']),
  '侧边栏四级分组：' + JSON.stringify(groups));

// ---- 5) 审计页可见 URGE 记录 ----
await page.goto(`http://[::1]:5175/audit?businessKey=${encodeURIComponent(`URGE-J-${RUN}`)}`);
await page.waitForSelector('table.data tbody tr');
await page.waitForTimeout(600);
const auditText = await page.evaluate(() => document.body.innerText);
check(auditText.includes('催办'), '审计追溯含 URGE（催办）记录');

console.log(`\n${pass} passed, ${fail} failed`);
await page.screenshot({ path: '_journey_menu.png' });
await browser.close();
process.exit(fail > 0 ? 1 : 0);
