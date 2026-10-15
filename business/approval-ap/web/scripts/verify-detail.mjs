// 无头浏览器验证：发起列表 → 详情弹窗（审批内容/附件/历史渲染）。
import { createRequire } from 'node:module';
const require = createRequire('C:/Users/CL/.workbuddy/binaries/node/workspace/node_modules/playwright-core/package.json');
const { chromium } = require('playwright-core');

const exe = 'C:/Users/CL/.agent-browser/browsers/chrome-155.0.8059.79/chrome.exe';
const browser = await chromium.launch({ executablePath: exe, headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

await page.goto('http://[::1]:5175/done');
await page.waitForSelector('table.data tbody tr');
await page.waitForTimeout(600);
await page.locator('button', { hasText: '详情' }).first().click();
await page.waitForSelector('.detail-body');
await page.waitForTimeout(400);

const r = await page.evaluate(() => ({
  title: document.querySelector('.detail-section-title')?.innerText,
  status: document.querySelector('.detail-grid .tag')?.innerText,
  fields: [...document.querySelectorAll('.detail-kv td')].slice(0, 8).map((td) => td.innerText).filter(Boolean),
  headings: [...document.querySelectorAll('.detail-heading')].map((h) => h.innerText.replace(/\n/g, ' ')),
  historyCount: document.querySelectorAll('.detail-timeline-item').length,
  historyTags: [...document.querySelectorAll('.detail-timeline-item .tag')].map((t) => t.innerText),
  comment: document.querySelector('.detail-comment')?.innerText || null,
  attachRow: document.querySelector('.detail-file')?.innerText || null,
}));
console.log(JSON.stringify(r, null, 1));
await page.screenshot({ path: '_detail_modal.png' });
await browser.close();
