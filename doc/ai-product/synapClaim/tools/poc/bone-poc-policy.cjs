const { chromium } = require('playwright');
const BASE = 'http://127.0.0.1:3013';
const TOKEN = process.env.POC_TOKEN || '';
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 950 } });
  await ctx.addInitScript((t) => localStorage.setItem('token', t), TOKEN);
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));

  await page.goto(BASE + '/sclaim/claim/policy?page=1&size=10', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3800);
  await page.locator('button:has-text("编辑")').first().click();
  await page.waitForTimeout(1400);

  const insurerInput = page.locator('.ant-modal-content input#insurerName');
  const before = await insurerInput.inputValue();
  const select = page.locator('.ant-modal-content .ant-select').first();
  await select.click();
  await page.waitForTimeout(700);
  const options = await page.locator('.ant-select-item-option').allInnerTexts();
  // InsurerSelect 的 label 是「名称（编码）」，onChange 第二参回写的是纯 insurerName。
  const label = options.map((s) => s.trim()).find((s) => s && s !== before);
  const target = label ? label.replace(/（[^）]*）$/, '') : null;

  if (!target) {
    console.log('[FAIL] 承保主体选择 :: 无可选的不同主体, before=' + before + ', options=' + JSON.stringify(options));
    await browser.close();
    process.exit(1);
  }
  await page.locator('.ant-select-item-option').filter({ hasText: target }).first().click();
  await page.waitForTimeout(900);
  const after = await insurerInput.inputValue();
  const ok = after === target && after !== before;
  console.log((ok ? '[PASS] ' : '[FAIL] ') +
    '保单编辑承保主体写回 editForm :: ' + before + ' → ' + after + ' (期望 ' + target + ')');
  await page.screenshot({ path: '/tmp/bone-poc-screens/05-policy-insurer-writeback.png' });
  await page.locator('.ant-modal-content button:has-text("取消")').first().click().catch(() => {});

  console.log((errors.length === 0 ? '[PASS] ' : '[FAIL] ') +
    '保单页无 pageerror :: 错误数=' + errors.length);
  await browser.close();
  if (!ok || errors.length) process.exit(1);
})().catch((e) => { console.error('POLICY POC FAIL:', e.message); process.exit(1); });
