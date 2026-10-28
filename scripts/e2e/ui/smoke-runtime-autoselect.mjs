// W0-1 / PRD D12 回归验证：进入 /metadata#/runtime（不带 :id、不带 ?entity=）时，
// **不得**替用户静默选中第一个已发布实体（否则用户以为在看自己选的实体，实为列表首项）。
// 判据：页面不应出现「物理表：」（仅 selectedEntity 非空才渲染），且应出现实体选择器占位文案。
// 用法：
//   NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-6/bin/node
//   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
//   $NODE scripts/e2e/ui/smoke-runtime-autoselect.mjs
/* 共享 client：playwright 加载 / CHROME / 登录凭证（见 e2e_lib/client.cjs） */
import { loadPlaywright, CHROME, USER, PWD } from '../e2e_lib/client.cjs';
const { chromium } = loadPlaywright();
const SHELL = 'http://localhost:3000';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 900 } });
  const page = await ctx.newPage();

  await page.goto(`${SHELL}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await sleep(3500);
  const u = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if (await u.count() && await u.isVisible().catch(() => false)) {
    await u.fill(USER);
    await page.locator('input[type="password"], input[placeholder="密码"]').first().fill(PWD);
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await sleep(4000);
  }

  await page.goto(`${SHELL}/metadata#/runtime`, { waitUntil: 'domcontentloaded' });
  await sleep(8000);

  const r = await page.evaluate(() => {
    const t = document.body.innerText;
    return {
      // 判据必须唯一：「物理表：」（带全角冒号）只在 `{selectedEntity && ...}` 块内渲染。
      // 注意不能只判「物理表」——该词还出现在常驻 Alert「…的实体有物理表」等静态文案里。
      hasSelectedEntity: t.includes('物理表：'),
      hasSelectorPlaceholder: t.includes('选择 RUNTIME 已发布实体'),
      snippet: t.slice(0, 120),
    };
  });
  await browser.close();

  const ok = !r.hasSelectedEntity && r.hasSelectorPlaceholder;
  console.log(JSON.stringify({ ok, ...r }, null, 1));
  process.exit(ok ? 0 : 1);
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
