/**
 * 诊断「业务数据 → 新建记录」弹窗里 ATTACHMENT（材料证据）控件的真实 DOM，
 * 确认探针能否用 input[type=file] 模拟人工添加附件。
 */
/* 共享 client：playwright 加载与登录凭证（BASE 保持本探针原默认口径，见下） */
import { USER, PWD, loadPlaywright } from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const BASE = 'http://127.0.0.1:3000';
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });
page.on('pageerror', (e) => console.log('[pageerror]', String(e).slice(0, 200)));

await page.goto(`${BASE}/#/login`, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(2000);
// 走 shell 登录
const userInput = page.locator('input[placeholder*="用户名"], input[id*=username], input[name*=username]').first();
if (await userInput.count()) {
  await userInput.fill(USER);
  await page.locator('input[type=password]').first().fill(PWD);
  await page.locator('button:has-text("登")').first().click();
  await page.waitForTimeout(3000);
  console.log('登录后 URL:', page.url());
}
await page.goto(`${BASE}/metadata#/runtime`, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(4000);

// 选第一个实体
const entSel = page.locator('#subapp-viewport .ant-select').first();
await entSel.click({ timeout: 5000 }).catch(() => {});
await page.waitForTimeout(600);
await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').first().click().catch(() => {});
await page.waitForTimeout(1500);

// 打开新建
await page.locator('#subapp-viewport button:visible').filter({ hasText: /^新建/ }).first().click();
await page.waitForTimeout(2000);

const modal = page.locator('.ant-modal:visible, .ant-drawer:visible').first();
const items = await modal.locator('.ant-form-item').all();
console.log('表单项数:', items.length);
for (const it of items) {
  const label = (await it.locator('label').first().innerText().catch(() => '')).replace(/\s/g, '');
  if (!/材料|证据|附件/.test(label)) continue;
  console.log('=== 目标项:', label);
  const html = await it.evaluate((el) => el.outerHTML);
  console.log(html.slice(0, 2500));
  const fileInputs = await it.locator('input[type=file]').count();
  console.log('file inputs in item:', fileInputs);
  const modalFileInputs = await modal.locator('input[type=file]').count();
  console.log('file inputs in modal:', modalFileInputs);
}
await browser.close();
process.exit(0);
