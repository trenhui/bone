/**
 * Generator 微应用 UI 冒烟（Playwright）。
 *
 *   npx playwright install chromium
 *   npm run e2e:ui
 */

import { chromium } from 'playwright';

const BASE = process.env.GENERATOR_APP_URL ?? 'http://localhost:3009';
const errors = [];

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();

page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
page.on('response', (res) => {
  if (res.url().includes('/api/v1/generator') && res.status() >= 400) {
    errors.push(`api ${res.status()} ${res.url()}`);
  }
});

try {
  await page.goto(`${BASE}/data-sources`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.getByRole('button', { name: /刷新|新建|添加/ }).first().waitFor({ timeout: 15000 });
  const hasTable = (await page.locator('.ant-table, .ant-list').count()) > 0;
  const bodyText = await page.locator('body').innerText();
  const ok = hasTable || bodyText.includes('数据源');
  console.log(
    JSON.stringify(
      { ok, url: page.url(), hasTable, errors: errors.slice(0, 10), bodySnippet: bodyText.slice(0, 400) },
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
