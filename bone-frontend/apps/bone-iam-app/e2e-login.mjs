import { chromium } from 'playwright';

const BASE = process.env.IAM_APP_URL || 'http://localhost:3003';
const errors = [];
const consoleLogs = [];

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();

page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
page.on('console', (msg) => {
  if (msg.type() === 'error') consoleLogs.push(`console.error: ${msg.text()}`);
});

try {
  await page.goto(`${BASE}/#/login`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.fill('input[placeholder="用户名"]', 'admin');
  await page.fill('input[placeholder="密码"]', '123456');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);
  const url = page.url();
  const token = await page.evaluate(() => localStorage.getItem('token'));
  const bodyText = await page.locator('body').innerText().catch(() => '');
  console.log(JSON.stringify({
    ok: !!token,
    url,
    tokenPrefix: token ? token.slice(0, 20) : null,
    errors,
    consoleLogs: consoleLogs.slice(0, 20),
    bodySnippet: bodyText.slice(0, 500),
  }, null, 2));
  process.exit(token ? 0 : 1);
} catch (e) {
  console.log(JSON.stringify({ ok: false, error: String(e), errors, consoleLogs }, null, 2));
  process.exit(1);
} finally {
  await browser.close();
}
