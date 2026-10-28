/**
 * 深挖：引导 Modal 为什么不关
 *   - dump 全部 .ant-modal-wrap 的身份（标题/可见性）
 *   - 数 designer 实例数（载入场景样例按钮数 / FAB circle 数）
 *   - 点「开始使用」后观察每个 wrap 的存亡与 React 状态
 *   - 检查 EntityDetail 是否把 designer 渲染了多份
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

  const entRes = await fetch(GATEWAY + '/api/v1/metadata/entities?page=1&size=5', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const entEnv = await entRes.json();
  const recs = entEnv?.data?.data?.records || entEnv?.data?.records || [];
  const ent = recs.find((e) => e.deliveryMode === 1) || recs[0];

  await page.goto(`${SHELL}/metadata#/entities/${ent.id}?tab=uischema`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('text=载入场景样例', { timeout: 25000 });
  await sleep(3000);

  const dumpWraps = () => page.evaluate(() => {
    const wraps = Array.from(document.querySelectorAll('.ant-modal-wrap'));
    return {
      designerCount: Array.from(document.querySelectorAll('button')).filter((b) => b.textContent.includes('载入场景样例')).length,
      fabCount: document.querySelectorAll('button.ant-btn-circle').length,
      wraps: wraps.map((w) => ({
        title: w.querySelector('.ant-modal-title')?.textContent?.trim() ?? null,
        visible: getComputedStyle(w).display !== 'none',
        okText: Array.from(w.querySelectorAll('.ant-modal button')).map((b) => b.textContent.trim()).join('|'),
      })),
    };
  });

  console.log('点击前:', JSON.stringify(await dumpWraps(), null, 1));
  await page.locator('.ant-modal button').filter({ hasText: '开始使用' }).click();
  for (let i = 1; i <= 4; i++) {
    await sleep(700);
    const d = await dumpWraps();
    console.log(`t=${i * 700}ms:`, JSON.stringify(d));
  }
  console.log('pageerrors:', JSON.stringify(pageerrors.slice(0, 5)));
  await browser.close();
})().catch((e) => { console.error('FATAL', e); process.exit(2); });
