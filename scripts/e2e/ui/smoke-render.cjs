// 渲染冒烟（真实入口）：Playwright 真实 UI 登录 → 在 shell(:3000) 经 qiankun 挂载 metadata-app →
// 加载实体列表，确认应用挂载（无白屏 / 无 Vite 错误覆盖层 / 无 console error），验证 ErrorBoundary + EntityDetail 重构可渲染。
// 用法：
//   NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-6/bin/node
//   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
//   $NODE scripts/e2e/ui/smoke-render.cjs
/* 共享 client：playwright 加载 / CHROME / 登录凭证（见 e2e_lib/client.cjs） */
const { loadPlaywright, CHROME, USER, PWD } = require('../e2e_lib/client.cjs');
const { chromium } = loadPlaywright();
const SHELL = 'http://localhost:3000';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 900 } });
  const page = await ctx.newPage();
  const consoleErrors = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
  page.on('pageerror', (e) => consoleErrors.push('PAGEERROR: ' + String(e.message).slice(0, 200)));

  await page.goto(`${SHELL}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await sleep(3500);
  const u = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if (await u.count() && await u.isVisible().catch(() => false)) {
    await u.fill(USER);
    await page.locator('input[type="password"], input[placeholder="密码"]').first().fill(PWD);
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await sleep(4000);
  }
  // 经 qiankun 挂载 metadata-app 并进入实体列表
  await page.goto(`${SHELL}/metadata#/entities`, { waitUntil: 'domcontentloaded' });
  await sleep(8000);

  const result = await page.evaluate(() => {
    const rootChildren = document.querySelector('#root')?.childElementCount ?? 0;
    const hasTable = !!document.querySelector('.ant-table');
    const hasViteOverlay = !!document.querySelector('vite-error-overlay');
    const hasNewEntityBtn = !!document.querySelector('button');
    const bodyText = document.body.innerText.slice(0, 300);
    return { rootChildren, hasTable, hasViteOverlay, btnCount: document.querySelectorAll('button').length, bodyText };
  });
  await browser.close();
  // 过滤掉已知的良性 antd deprecation 警告
  const realErrors = consoleErrors.filter((e) => !/deprecated|clearIcon|allowClear/.test(e));
  const ok = result.rootChildren > 0 && result.hasTable && !result.hasViteOverlay && realErrors.length === 0;
  console.log(JSON.stringify({ ok, realErrors, ...result }, null, 1));
  process.exit(ok ? 0 : 1);
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
