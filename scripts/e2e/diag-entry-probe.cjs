/**
 * 诊断：浏览器加载 metadata 子应用时，各模块请求实际落在哪个端口。
 * 登录走 shell :3000（复用主探针逻辑），进设计器后打印：
 *   1. 全部网络请求 URL（过滤 3000/3004/3014/8888）
 *   2. qiankun 入口 HTML 请求
 *   3. 引导 Modal 内容
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
  const reqs = [];
  page.on('request', (r) => {
    const u = r.url();
    if (/localhost:(3000|3004|3014|8888)/.test(u)) reqs.push(`${r.method()} ${u}`);
  });

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

  // 复用探针实体：取最近一个 deliveryMode=1 的实体
  const res = await fetch(GATEWAY + '/api/v1/metadata/entities?page=1&size=5', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const env = await res.json();
  const recs = env?.data?.data?.records || env?.data?.records || [];
  const ent = recs.find((e) => e.deliveryMode === 1) || recs[0];
  console.log('entity:', ent?.id, ent?.code, 'deliveryMode:', ent?.deliveryMode);

  reqs.length = 0;
  await page.goto(`${SHELL}/metadata#/entities/${ent.id}?tab=uischema`, { waitUntil: 'domcontentloaded' });
  await sleep(9000);

  const byPort = {};
  for (const r of reqs) {
    const m = r.match(/localhost:(\d+)/);
    const p = m ? m[1] : 'other';
    byPort[p] = (byPort[p] || 0) + 1;
  }
  console.log('请求端口分布:', JSON.stringify(byPort));
  console.log('--- 3014 请求样例(前5) ---');
  console.log(reqs.filter((r) => r.includes(':3014')).slice(0, 5).join('\n'));
  console.log('--- 3004 请求样例(前5) ---');
  console.log(reqs.filter((r) => r.includes(':3004')).slice(0, 5).join('\n'));
  const guide = await page.locator('.ant-modal').filter({ hasText: '3 步上手' }).innerText().catch(() => '(no guide modal)');
  console.log('--- 引导 Modal 内容 ---');
  console.log(guide.slice(0, 400));
  await browser.close();
})().catch((e) => { console.error('FATAL', e); process.exit(2); });
