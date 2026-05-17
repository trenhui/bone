/**
 * 扩展微应用 UI 冒烟（Playwright）。
 *
 * 前置：npm run dev（3008）且后端可访问（Gateway 8888 或 Studio 8088）。
 *
 *   npx playwright install chromium
 *   node e2e-extension-ui.mjs
 */

import { chromium } from 'playwright';

const BASE = process.env.EXTENSION_APP_URL ?? 'http://localhost:3008';
const errors = [];

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();

page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
page.on('response', (res) => {
  if (res.url().includes('/api/v1/extension') && res.status() >= 400) {
    errors.push(`api ${res.status()} ${res.url()}`);
  }
});

try {
  await page.goto(`${BASE}/extension/plugin`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.getByRole('button', { name: '刷新' }).waitFor({ timeout: 15000 });
  const hasTable = (await page.locator('.ant-table').count()) > 0;
  const bodyText = await page.locator('body').innerText();
  const ok = hasTable && !bodyText.includes('加载插件失败');
  console.log(
    JSON.stringify(
      {
        ok,
        url: page.url(),
        hasTable,
        errors: errors.slice(0, 10),
        bodySnippet: bodyText.slice(0, 400),
      },
      null,
      2,
    ),
  );
  process.exit(ok ? 0 : 1);
} catch (e) {
  console.log(JSON.stringify({ ok: false, error: String(e), errors }, null, 2));
  process.exit(1);
} finally {
  await browser.close();
}
