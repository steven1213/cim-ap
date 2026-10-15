// 无头浏览器验证：详情弹窗超高内容可滚动（面板限高、正文独立滚动、底部完整可见）。
import { createRequire } from 'node:module';
const require = createRequire('C:/Users/CL/.workbuddy/binaries/node/workspace/node_modules/playwright-core/package.json');
const { chromium } = require('playwright-core');

const exe = 'C:/Users/CL/.agent-browser/browsers/chrome-155.0.8059.79/chrome.exe';
const browser = await chromium.launch({ executablePath: exe, headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 700 } }); // 故意用矮视口迫使内容超高

await page.goto('http://[::1]:5175/initiated');
await page.waitForSelector('table.data tbody tr');
await page.waitForTimeout(600);
// 打开 SCROLL-TEST-002 的详情
await page.locator('tr', { hasText: 'SCROLL-TEST-002' }).locator('button', { hasText: '详情' }).first().click();
await page.waitForSelector('.detail-body');
await page.waitForTimeout(400);

const before = await page.evaluate(() => {
  const panel = document.querySelector('.modal-panel');
  const body = document.querySelector('.modal-panel > .panel-body');
  return {
    viewportH: window.innerHeight,
    panelHeight: panel?.getBoundingClientRect().height,
    panelMaxH: panel ? parseFloat(getComputedStyle(panel).maxHeight) : null,
    bodyScrollH: body?.scrollHeight,
    bodyClientH: body?.clientHeight,
    bodyOverflowY: body ? getComputedStyle(body).overflowY : null,
    scrollTop: body?.scrollTop,
  };
});

// 滚动到底部，检查最后一个区块是否完整可见
await page.evaluate(() => {
  const body = document.querySelector('.modal-panel > .panel-body');
  body.scrollTop = body.scrollHeight;
});
await page.waitForTimeout(300);

const after = await page.evaluate(() => {
  const body = document.querySelector('.modal-panel > .panel-body');
  const bodyRect = body.getBoundingClientRect();
  const sections = [...document.querySelectorAll('.detail-section')];
  const last = sections[sections.length - 1];
  const lastRect = last.getBoundingClientRect();
  const header = document.querySelector('.modal-panel .panel-head');
  const headerVisible = header && header.getBoundingClientRect().bottom > 0 && header.getBoundingClientRect().top >= 0;
  return {
    scrollTopAfter: body.scrollTop,
    lastSectionTitle: last.querySelector('h3,h4')?.innerText,
    lastFullyInside: lastRect.top >= bodyRect.top - 1 && lastRect.bottom <= bodyRect.bottom + 1,
    headerStillVisible: headerVisible,
  };
});

console.log(JSON.stringify({ before, after }, null, 1));
await page.screenshot({ path: '_detail_scroll_bottom.png' });
await browser.close();
