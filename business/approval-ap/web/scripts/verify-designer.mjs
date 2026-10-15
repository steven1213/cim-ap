// 设计器连线修复验证（完整用户流）：
// 1) 连线线条可见（stroke 非 none、颜色正确）
// 2) 箭头 marker 有色
// 3) 清空 → 加两个审批节点 → 拖拽连线 → 边真实创建
// 4) 拖拽过程出现临时虚线 + 目标高亮
import { createRequire } from 'node:module';
const require = createRequire('C:/Users/CL/.workbuddy/binaries/node/workspace/package.json');
const { chromium } = require('playwright-core');

const exe = 'C:/Users/CL/.agent-browser/browsers/chrome-155.0.8059.79/chrome.exe';
const results = [];
const ok = (name, cond) => { results.push(`${cond ? 'PASS' : 'FAIL'} ${name}`); console.log(results[results.length - 1]); };

const browser = await chromium.launch({ executablePath: exe, headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.on('console', (m) => { if (!m.text().includes('React DevTools') && !m.text().includes('React Router')) console.log('PAGE', m.type(), m.text().slice(0, 200)); });
await page.goto('http://[::1]:5175/designer');
await page.waitForSelector('.flow-node', { timeout: 15000 });
await page.waitForTimeout(600);

// 1. 颜色渲染（示例模型自带连线）+ 条件分支标签
const c = await page.evaluate(() => {
  const p = document.querySelector('.edge-line');
  const m = document.querySelector('#arrow path');
  const labels = [...document.querySelectorAll('.edge-label')].map((t) => t.textContent?.trim());
  return {
    edge: p ? getComputedStyle(p).stroke : 'missing',
    marker: m ? getComputedStyle(m).fill : 'missing',
    labels,
  };
});
ok(`连线 stroke=${c.edge}（可见且非黑）`, c.edge !== 'none' && c.edge !== 'rgb(0, 0, 0)');
ok(`箭头 fill=${c.marker}（非黑）`, c.marker !== 'rgb(0, 0, 0)' && c.marker !== 'none');
ok(`条件分支出边有 ✓满足/✗不满足 标签（${c.labels.join(' / ')}）`,
  c.labels.includes('✓ 满足') && c.labels.includes('✗ 不满足'));

// 2. 清空画布
await page.getByRole('button', { name: 'Clear' }).click();
await page.waitForTimeout(200);

// 3. 双击节点面板「审批」添加两个节点
await page.locator('.palette-item').nth(1).dblclick();
await page.locator('.palette-item').nth(1).dblclick();
await page.waitForTimeout(200);
ok('已添加 2 个审批节点', (await page.locator('.flow-node').count()) === 2);
ok('校验横幅提示无入口连线', (await page.locator('.designer-errors').count()) === 1);

// 4. 把第二个节点拖到右下方，避免重叠
const nodes = page.locator('.flow-node');
const n2 = nodes.nth(1);
const b2 = await n2.boundingBox();
await page.mouse.move(b2.x + 60, b2.y + 20);
await page.mouse.down();
await page.mouse.move(b2.x + 340, b2.y + 200, { steps: 6 });
await page.mouse.up();
await page.waitForTimeout(200);

// 5. 从节点1的 link-handle 拖到节点2 → 创建连线
const h1 = page.locator('.flow-node .link-handle').first();
const hb = await h1.boundingBox();
const eb = await n2.boundingBox();
await page.mouse.move(hb.x + hb.width / 2, hb.y + hb.height / 2);
await page.mouse.down();
await page.mouse.move((hb.x + eb.x + eb.width / 2) / 2, (hb.y + eb.y + eb.height / 2) / 2, { steps: 4 });
await page.waitForTimeout(120);
const tempSeen = (await page.locator('.temp-edge').count()) > 0;
await page.mouse.move(eb.x + eb.width / 2, eb.y + eb.height / 2, { steps: 3 });
await page.waitForTimeout(120);
const targetHi = (await page.locator('.flow-node.link-target').count()) > 0;
await page.mouse.up();
await page.waitForTimeout(300);
const edgeCount = await page.locator('.edge-line').count();
ok('拖拽过程显示临时虚线', tempSeen);
ok('拖拽悬停时目标节点高亮', targetHi);
ok(`连线已创建（edge-line=${edgeCount}）`, edgeCount === 1);

// 6. 撤销验证：点选连线可选中（edge-hit 可点击）
const hitCount = await page.locator('.edge-hit').count();
if (hitCount > 0) {
  await page.locator('.edge-hit').first().click({ force: true });
  await page.waitForTimeout(100);
  ok('点击连线可选中并弹出属性面板', (await page.locator('.props-form').count()) > 0);
} else {
  console.log('NOTE 无 .edge-hit（连线未创建），跳过选中验证');
}

// 7. 开始节点必须有连线出口（link-handle 在 start 上可拖）
await page.getByRole('button', { name: 'Clear' }).click();
await page.waitForTimeout(200);
await page.locator('.palette-item').nth(0).dblclick(); // 开始
await page.locator('.palette-item').nth(1).dblclick(); // 审批
await page.waitForTimeout(200);
const startHandle = page.locator('.flow-node.start .link-handle');
ok('开始节点渲染连线出口（link-handle）', (await startHandle.count()) === 1);
const approvalNode = page.locator('.flow-node.approval');
const shb = await startHandle.boundingBox();
const ab = await approvalNode.boundingBox();
if (shb && ab) {
  await page.mouse.move(shb.x + shb.width / 2, shb.y + shb.height / 2);
  await page.mouse.down();
  await page.mouse.move(ab.x + ab.width / 2, ab.y + ab.height / 2, { steps: 5 });
  await page.mouse.up();
  await page.waitForTimeout(300);
  ok('从开始节点拖拽可创建连线', (await page.locator('.edge-line').count()) === 1);
} else {
  console.log('NOTE 未取到开始节点出口坐标，跳过拖拽验证');
}

await page.screenshot({ path: 'E:/Personal/CIM/cim-ap/business/approval-ap/web/_designer_check.png' });
await browser.close();
console.log(results.join('\n'));
if (results.some((r) => r.startsWith('FAIL'))) process.exit(1);
