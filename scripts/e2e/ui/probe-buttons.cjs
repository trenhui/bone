// 取各页面真实可点击元素（按钮文字 + 图标按钮的 title/aria-label），用于确认"新增"入口的真实形态
const { chromium } = require('playwright');
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const BASE = 'http://localhost:3000';
const PAGES = [
  ['iam', '#/accounts'], ['iam', '#/roles'], ['iam', '#/tenants'], ['iam', '#/organizations'], ['iam', '#/apps'],
  ['system', '#/alerts'], ['system', '#/config'], ['system', '#/dict'], ['system', '#/schedule'],
  ['integration', '#/connectors'], ['integration', '#/flows'],
  ['metadata', '#/entities'], ['masterdata', '#/entities'], ['generator', '#/datasources'],
];
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 950 } })).newPage();
  await page.goto(`${BASE}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);
  const u = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if (await u.count() && await u.isVisible().catch(() => false)) {
    await u.fill('admin');
    await page.locator('input[type="password"], input[placeholder="密码"]').first().fill('123456');
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await page.waitForTimeout(4000);
  }
  const out = {};
  for (const [app, hash] of PAGES) {
    await page.goto(`${BASE}/${app}${hash}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2800);
    out[`${app}${hash}`] = await page.evaluate(() => {
      const btns = [...document.querySelectorAll('button, [role="button"], a.ant-btn')].map(b => ({
        text: (b.innerText || '').trim().slice(0, 14),
        title: b.getAttribute('title') || b.getAttribute('aria-label') || '',
        cls: b.className.slice(0, 50),
      })).filter(b => b.text || b.title);
      // 只保留页面主操作区的（排除侧边栏与顶栏）
      const main = [...document.querySelectorAll('.ant-card button, .ant-table button, .ant-btn-primary, [class*="toolbar"] button, [class*="header"] button, .ant-space button')].map(b => (b.innerText || '').trim() || b.getAttribute('title') || b.getAttribute('aria-label') || '(图标)').slice(0, 16);
      return { allButtons: [...new Set(btns.map(b => b.text || b.title))].slice(0, 22), mainActions: [...new Set(main)].slice(0, 12) };
    });
  }
  await browser.close();
  console.log(JSON.stringify(out, null, 1));
})();
