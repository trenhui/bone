/**
 * 综合诊断：一次性问完四个疑点
 *   1. 探针实体字段是否真实存在（Node 侧 API）
 *   2. 设计器页面侧：字段库文本（判断 fields 是否加载/是否全部使用）
 *   3. 变体卡四维命中条件的真实 DOM（placeholder 在 input 还是 span）
 *   4. 引导 Modal 点击「开始使用」后 4s 内的关闭时序 + localStorage 标记
 */
/* 共享 client：playwright 加载 / CHROME / 登录凭证（见 e2e_lib/client.cjs） */
const { loadPlaywright, CHROME, USER, PWD } = require('./e2e_lib/client.cjs');
const { chromium } = loadPlaywright();
const SHELL = 'http://localhost:3000';
const GATEWAY = 'http://localhost:8888';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const pageerrors = [];
  page.on('pageerror', (e) => pageerrors.push(e.message));

  await page.goto(`${SHELL}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await sleep(3500);
  const u = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if ((await u.count()) && (await u.isVisible().catch(() => false))) {
    await u.fill(USER);
    await page.locator('input[type="password"], input[placeholder="密码"]').first().fill(PWD);
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await sleep(4000);
  }
  const token = await page.evaluate(() => localStorage.getItem('token'));
  if (!token) { await browser.close(); throw new Error('login failed'); }

  // 1. 实体字段（Node 侧 API 实证）
  const entRes = await fetch(GATEWAY + '/api/v1/metadata/entities?page=1&size=5', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const entEnv = await entRes.json();
  const recs = entEnv?.data?.data?.records || entEnv?.data?.records || [];
  const ent = recs.find((e) => e.deliveryMode === 1) || recs[0];
  const fRes = await fetch(GATEWAY + `/api/v1/metadata/entities/${ent.id}/fields?page=1&size=200`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const fEnv = await fRes.json();
  const fields = fEnv?.data?.data?.records || fEnv?.data?.records || [];
  console.log('1) 实体', ent.id, ent.code, '字段数:', Array.isArray(fields) ? fields.length : JSON.stringify(fEnv).slice(0, 200));

  await page.goto(`${SHELL}/metadata#/entities/${ent.id}?tab=uischema`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('text=载入场景样例', { timeout: 25000 });
  await sleep(3000);

  // 4. 引导 Modal 关闭时序（先做，趁首访弹窗还在）
  const guideCount0 = await page.locator('text=BONE 界面设计器 · 3 步上手').count();
  console.log('4) 初始引导 count:', guideCount0);
  if (guideCount0 > 0) {
    await page.locator('.ant-modal button').filter({ hasText: '开始使用' }).click();
    for (let i = 1; i <= 8; i++) {
      await sleep(500);
      const c = await page.locator('text=BONE 界面设计器 · 3 步上手').count();
      const wrap = await page.locator('.ant-modal-wrap').count();
      const flag = await page.evaluate(() => localStorage.getItem('bone-designer-guide-v1'));
      console.log(`   t=${i * 500}ms  text-count=${c}  modal-wrap=${wrap}  guide-flag=${flag}`);
      if (c === 0) break;
    }
  }

  // 2. 字段库状态（点 FAB 看 popover 内容）
  await page.locator('button.ant-btn-circle').last().click();
  await sleep(1200);
  const pop = await page.locator('.ant-popover').last().innerText().catch(() => '(no popover)');
  console.log('2) 字段库 popover 内容:', JSON.stringify(pop.slice(0, 300)));
  const tagInPop = await page.locator('.ant-popover .ant-tag').count();
  console.log('   popover 内 .ant-tag 数:', tagInPop);
  await page.keyboard.press('Escape');

  // 3. 变体卡四维命中条件 DOM
  await page.locator('.ant-tabs-tab').filter({ hasText: '变体' }).first().click();
  await sleep(800);
  await page.locator('button').filter({ hasText: '新增变体' }).first().click();
  await sleep(1000);
  const dom = await page.evaluate(() => {
    const out = [];
    document.querySelectorAll('.ant-select').forEach((sel) => {
      const ph = sel.querySelector('.ant-select-selection-placeholder');
      const inp = sel.querySelector('input');
      out.push({
        placeholderSpan: ph ? ph.textContent.trim() : null,
        inputPlaceholder: inp ? inp.getAttribute('placeholder') : null,
        cls: sel.className.replace('ant-select ', '').slice(0, 40),
      });
    });
    return out;
  });
  console.log('3) 页面全部 .ant-select 的 placeholder DOM:');
  dom.forEach((d) => console.log('   ', JSON.stringify(d)));
  console.log('pageerrors:', JSON.stringify(pageerrors.slice(0, 3)));
  await browser.close();
})().catch((e) => { console.error('FATAL', e); process.exit(2); });
