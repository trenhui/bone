const { chromium } = require('playwright');

const BASE = 'http://localhost:3000';
const ENTITY_ID = '765601800232370176';
const DESIGNER_URL = `${BASE}/metadata#/entities/${ENTITY_ID}?tab=uischema`;
const API = 'http://127.0.0.1:8888/api/v1/metadata';
const results = { steps: [], consoleErrors: [], pageErrors: [] };
const note = (name, ok, detail) => { results.steps.push({ name, ok, detail }); console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name} :: ${detail || ''}`); };

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1680, height: 1000 } });
  const page = await ctx.newPage();
  const allConsole = [];
  page.on('console', (m) => {
    allConsole.push(`[${m.type()}] ${m.text()}${m.location() ? ` @ ${m.location().url}:${m.location().lineNumber}` : ''}`);
    if (m.type() === 'error') results.consoleErrors.push(m.text());
  });
  page.on('pageerror', (e) => results.pageErrors.push(String(e)));

  // ---------- token (for API round-trip) ----------
  let token = '';
  try {
    const r = await fetch('http://127.0.0.1:8888/api/v1/iam/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: '123456' }),
    });
    const j = await r.json();
    token = j.data?.token || '';
    note('login-token', !!token, `token_len=${token.length}`);
  } catch (e) { note('login-token', false, String(e)); }

  const apiGet = async () => {
    const r = await fetch(`${API}/entities/${ENTITY_ID}/ui-schema`, { headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0' } });
    return r.json();
  };
  const apiPut = async (json, lock) => {
    const r = await fetch(`${API}/entities/${ENTITY_ID}/ui-schema`, {
      method: 'PUT', headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0', 'If-Match': String(lock), 'Content-Type': 'application/json' },
      body: JSON.stringify({ json }),
    });
    return { status: r.status, json: await r.json() };
  };

  // ---------- login UI ----------
  try {
    await page.goto(BASE, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(1500);
    const u = page.locator('#username');
    if (await u.count()) await u.fill('admin'); else await page.getByPlaceholder(/用户名|user/i).fill('admin');
    const p = page.locator('#password');
    if (await p.count()) await p.fill('123456'); else await page.getByPlaceholder(/密码|password/i).fill('123456');
    await page.getByRole('button', { name: /登录|登 录|Login/i }).click({ timeout: 8000 });
    await page.waitForTimeout(2500);
    note('ui-login', true, 'login clicked');
  } catch (e) { note('ui-login', false, String(e)); }

  // ---------- open designer ----------
  try {
    await page.goto(DESIGNER_URL, { waitUntil: 'networkidle', timeout: 30000 });
    // wait for metadata micro-app + designer to mount
    await page.getByText('结构', { exact: true }).first().waitFor({ timeout: 25000 });
    await page.waitForTimeout(1000);
    note('open-designer', true, 'designer mounted (结构 tab visible)');
  } catch (e) { note('open-designer', false, String(e)); }

  // ---------- verify worktree code (rename) ----------
  try {
    const bodyText = await page.locator('body').innerText();
    const hasNew = bodyText.includes('界面设计器');
    const hasOld = bodyText.includes('界面（协议）');
    note('rename-tab', hasNew && !hasOld, `hasNew=${hasNew} hasOld=${hasOld}`);
    await page.screenshot({ path: '/tmp/e2e_designer.png', fullPage: false });
  } catch (e) { note('rename-tab', false, String(e)); }

  // ---------- onboarding modal (first visit) ----------
  try {
    const ob = page.getByText('BONE 界面设计器 · 3 步上手');
    if (await ob.count()) {
      await page.waitForTimeout(400);
      await page.screenshot({ path: '/tmp/e2e_onboard.png' });
      await page.getByRole('button', { name: '开始设计' }).click();
      await page.waitForTimeout(400);
      note('onboarding', true, 'onboarding modal shown + closed');
    } else {
      note('onboarding', true, 'already onboarded (skipped)');
    }
  } catch (e) { note('onboarding', false, String(e)); }

  // ---------- fullscreen ----------
  try {
    await page.getByRole('button', { name: /全屏/ }).click();
    await page.waitForTimeout(600);
    const fixed = await page.evaluate(() => {
      const els = [...document.querySelectorAll('div')].filter((d) => d.style.position === 'fixed' && d.style.inset === '0px');
      return els.length > 0;
    });
    await page.screenshot({ path: '/tmp/e2e_fullscreen.png' });
    await page.getByRole('button', { name: '退出全屏' }).first().click();
    await page.waitForTimeout(400);
    note('fullscreen', fixed, `fixed-overlay=${fixed}`);
  } catch (e) { note('fullscreen', false, String(e)); }

  // ---------- Cmd+K ----------
  try {
    await page.keyboard.press('Control+k');
    await page.waitForTimeout(500);
    const cmd = page.getByText('命令面板（Cmd/Ctrl + K）');
    const shown = await cmd.count() > 0;
    await page.screenshot({ path: '/tmp/e2e_cmdk.png' });
    if (shown) { await page.keyboard.press('Escape'); await page.waitForTimeout(300); }
    note('cmd-k', shown, `palette=${shown}`);
  } catch (e) { note('cmd-k', false, String(e)); }

  // ---------- FAB ----------
  try {
    const fab = page.locator('button.ant-btn-circle.ant-btn-primary').last();
    await fab.click();
    await page.waitForTimeout(500);
    const pop = page.getByText('添加字段到界面');
    const shown = await pop.count() > 0;
    await page.screenshot({ path: '/tmp/e2e_fab.png' });
    if (shown) { await page.keyboard.press('Escape'); await page.waitForTimeout(300); }
    note('fab', shown, `popover=${shown}`);
  } catch (e) { note('fab', false, String(e)); }

  // ---------- save round-trip (前后端联调核心) ----------
  try {
    const before = await apiGet();
    const origJson = before.data?.json;
    const origLock = Number(before.data?.lockVersion ?? 0);
    note('api-get-before', before.code === 200 && !!origJson, `code=${before.code} lock=${origLock} len=${origJson?.length}`);

    // make a change in UI
    await page.getByRole('button', { name: '添加分区' }).click();
    await page.waitForTimeout(600);
    const dirty = await page.getByText('未保存改动').count() > 0;
    note('ui-edit-dirty', dirty, `dirty-tag=${dirty}`);

    // save (Popconfirm)
    await page.getByRole('button', { name: /保存/ }).first().click();
    await page.waitForTimeout(500);
    const okBtn = page.locator('.ant-popconfirm button.ant-btn-primary');
    if (await okBtn.count()) { await okBtn.click(); }
    // wait for saved feedback
    let savedOk = false;
    try { await page.getByText('已保存', { exact: true }).first().waitFor({ timeout: 8000 }); savedOk = true; } catch {}
    await page.waitForTimeout(500);
    note('ui-save', savedOk, `saved-tag=${savedOk}`);

    const after = await apiGet();
    const afterJson = after.data?.json;
    const afterLock = Number(after.data?.lockVersion ?? 0);
    const changed = afterJson !== origJson;
    const lockInc = afterLock > origLock;
    note('api-get-after', changed && lockInc, `changed=${changed} lockBefore=${origLock} lockAfter=${afterLock}`);

    // restore original
    if (changed) {
      const put = await apiPut(origJson, afterLock);
      const restored = await apiGet();
      const restoredOk = restored.data?.json === origJson;
      note('api-restore', put.status === 200 && restoredOk, `put=${put.status} restoredEqOriginal=${restoredOk}`);
    } else {
      note('api-restore', true, 'no change, skip restore');
    }
  } catch (e) { note('save-roundtrip', false, String(e)); }

  // ---------- console errors (filter benign antd deprecation + single-spa mount race) ----------
  const benign = /deprecated|clearIcon|Spin` tip|single-spa|minified message #31/i;
  const realErrors = results.consoleErrors.filter((e) => !benign.test(e));
  const benignErrors = results.consoleErrors.filter((e) => benign.test(e));
  note('console-errors', realErrors.length === 0, `real=${realErrors.length} benign=${benignErrors.length}`);
  note('page-errors', results.pageErrors.length === 0, `count=${results.pageErrors.length}`);
  if (realErrors.length) console.log('REAL ERRORS:', realErrors);

  await browser.close();
  console.log('\n=== CONSOLE ERRORS ===');
  results.consoleErrors.slice(0, 20).forEach((e) => console.log('  •', e));
  console.log('=== PAGE ERRORS ===');
  results.pageErrors.slice(0, 20).forEach((e) => console.log('  •', e));
  console.log('=== ALL CONSOLE (for diagnosis) ===');
  allConsole.slice(0, 40).forEach((e) => console.log('  ', e));
  console.log('\n=== SUMMARY ===');
  const fails = results.steps.filter((s) => !s.ok);
  console.log(`total=${results.steps.length} pass=${results.steps.length - fails.length} fail=${fails.length}`);
  process.exit(fails.length ? 1 : 0);
})().catch((e) => { console.error('FATAL', e); process.exit(2); });
