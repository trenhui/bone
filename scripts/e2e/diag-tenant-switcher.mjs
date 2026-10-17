import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const BASE = 'http://localhost:3000';
const API = 'http://127.0.0.1:8888';

async function main() {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  page.on('console', (m) => { if (m.type() === 'error') console.log('[console.error]', m.text().slice(0, 200)); });
  page.on('pageerror', (e) => console.log('[pageerror]', e.message.slice(0, 200)));

  // login as admin
  await page.goto(BASE, { waitUntil: 'domcontentloaded' });
  await page.fill('input#username', 'admin').catch(() => {});
  await page.fill('input#password', '123456').catch(() => {});
  await page.click('button[type=submit]').catch(() => {});
  await page.waitForTimeout(3000);

  // open tenant switcher dropdown
  const switcher = page.locator('.tenant-switcher');
  console.log('switcher visible:', await switcher.count());
  await switcher.click();
  await page.waitForTimeout(800);
  const options = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option');
  const optCount = await options.count();
  console.log('tenant option count:', optCount);
  const labels = [];
  for (let i = 0; i < optCount; i++) {
    labels.push((await options.nth(i).innerText()).trim());
  }
  console.log('labels:', JSON.stringify(labels));

  // check duplicates
  const unique = new Set(labels);
  console.log('unique count:', unique.size, 'duplicate?', unique.size !== labels.length);

  // select first tenant (e.g., 梅山科技)
  const target = labels.find((l) => l.includes('梅山') || l.includes('验证') || l.includes('UAT'));
  if (target) {
    await options.filter({ hasText: target }).first().click();
    await page.waitForTimeout(2500);
    const ctx = await page.evaluate(() => (window).__BONE_GLOBAL_CONTEXT__?.tenantId);
    console.log('after switch, globalContext tenantId:', ctx, 'target was', target);
    // read switcher current value
    const val = await switcher.locator('.ant-select-selection-item').innerText().catch(() => '(none)');
    console.log('switcher now shows:', val);
  }

  await browser.close();
}
main().catch((e) => { console.error('FATAL', e); process.exit(1); });
