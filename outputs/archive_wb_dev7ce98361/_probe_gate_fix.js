const { chromium } = require('playwright');

(async () => {
  const out = [];
  const note = (name, ok, detail = '') => { out.push({ name, ok, detail }); };
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1680, height: 1000 } });
  const page = await ctx.newPage();
  const consoleErrors = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });

  // login
  await page.goto('http://localhost:3000/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(2500);
  if (await page.locator('#username').count()) {
    await page.fill('#username', 'admin');
    await page.fill('#password', '123456');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(3000);
  }

  // open designer tab
  await page.goto('http://localhost:3000/metadata#/entities/765601800232370176?tab=uischema', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(6000);

  const body = await page.evaluate(() => document.body.innerText);
  note('designer-mounted', body.includes('界面设计器') || body.includes('添加分区'), '');

  // hard-gate alert must be GONE
  const alertVisible = body.includes('界面协议存在') || body.includes('no_such_field');
  note('no-hard-gate-alert', !alertVisible, alertVisible ? 'ALERT still present' : 'alert absent');

  // lint panel should show 0 errors (if 自检 tab visible text)
  note('no-console-errors-of-refs', !consoleErrors.some((e) => e.includes('no_such_field')), `consoleErr=${consoleErrors.length}`);

  await page.screenshot({ path: '/tmp/e2e_gate_fixed.png' });
  await browser.close();

  let fail = 0;
  for (const r of out) { console.log(`${r.ok ? 'PASS' : 'FAIL'}  ${r.name}  ${r.detail}`); if (!r.ok) fail++; }
  process.exit(fail ? 1 : 0);
})();
