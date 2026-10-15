// 无头浏览器 E2E：formSchema 感知的审批内容渲染（分组/标签/类型格式化/未声明字段兜底）。
import { createRequire } from 'node:module';
const require = createRequire('C:/Users/CL/.workbuddy/binaries/node/workspace/node_modules/playwright-core/package.json');
const { chromium } = require('playwright-core');

const exe = 'C:/Users/CL/.agent-browser/browsers/chrome-155.0.8059.79/chrome.exe';
const browser = await chromium.launch({ executablePath: exe, headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

await page.goto('http://[::1]:5175/initiated');
await page.waitForSelector('table.data tbody tr');
await page.waitForTimeout(600);
await page.locator('tr', { hasText: 'SCHEMA-TEST-001' }).locator('button', { hasText: '详情' }).first().click();
await page.waitForSelector('.detail-form-group');
await page.waitForTimeout(400);

const r = await page.evaluate(() => {
  const groups = [...document.querySelectorAll('.detail-form-group')].map((g) => ({
    title: g.querySelector('.detail-form-group-title')?.innerText,
    items: [...g.querySelectorAll('.detail-form-item')].map((it) => ({
      label: it.querySelector('.detail-form-label')?.innerText,
      value: it.querySelector('.detail-form-value')?.innerText,
      wide: it.classList.contains('wide'),
    })),
  }));
  const fallbackTable = document.querySelector('.detail-kv table.data');
  return { groups, hasFallbackTable: !!fallbackTable };
});

let pass = 0;
let fail = 0;
const check = (cond, msg) => {
  if (cond) { pass++; console.log('PASS', msg); }
  else { fail++; console.log('FAIL', msg); }
};

const g1 = r.groups.find((g) => g.title === '基本信息');
const g2 = r.groups.find((g) => g.title === '财务信息');
const g3 = r.groups.find((g) => g.title === '其他信息');
check(!!g1 && !!g2 && !!g3, '三个分组（基本信息/财务信息/其他信息）齐全');
check(g1.items.some((i) => i.label === '申请类型' && i.value === '设备采购'), 'text 类型按中文标签渲染');
check(g1.items.some((i) => i.label === '数量' && i.value === '2 台'), 'number 类型 + 单位「台」');
check(g1.items.some((i) => i.label === '是否加急' && i.value === '是'), 'boolean 类型 → 是');
check(g2.items.some((i) => i.label === '预算金额' && i.value === '¥ 860,000.00'), 'money 类型 → ¥ 千分位');
check(g2.items.some((i) => i.label === '期望到货' && i.value === '2026-11-30'), 'date 类型展示日期');
check(g2.items.some((i) => i.label === '采购原因' && i.wide), 'textarea 长文本独占整行');
check(g3 && g3.items.some((i) => i.label === 'extraNote'), 'schema 未声明字段兜底进「其他信息」不丢失');
check(!r.hasFallbackTable, '有 schema 时不渲染原始键值表');

console.log(`\n${pass} passed, ${fail} failed`);
await page.screenshot({ path: '_schema_detail.png' });
await browser.close();
process.exit(fail > 0 ? 1 : 0);
