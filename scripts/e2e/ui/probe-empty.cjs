// 针对「UI 判 EMPTY」的页面做归因：到底是接口没数据，还是接口有数据 UI 没渲染。
// 判据：抓该页所有 >=200 的 XHR 响应体 + 页面 .ant-empty 文案 + 首行文本。
// 用法：NODE=... node scripts/e2e/ui/probe-empty.cjs
const { chromium } = require('playwright');
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const BASE = 'http://localhost:3000';

const TARGETS = [
  ['iam', '/iam#/organizations'],
  ['iam', '/iam#/menus'],
  ['metadata', '/metadata#/runtime'],   // 上一轮探针点这一项超时（click timeout），页面其实没跳过去
  ['masterdata', '/masterdata#/quality-issues'],
  ['masterdata', '/masterdata#/governance'],
  ['integration', '/integration#/monitor'],
  ['extension', '/extension#/deploy'],
  ['extension', '/extension#/logs'],
];

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 950 } })).newPage();
  const out = [];
  page.on('response', r => {
    if (r.request().resourceType() !== 'xhr') return;
    if (r.status() < 200) return;
    // 失败响应也要记（含 4xx）：UI 空表往往就是一次被拒/报错的 XHR，
    // 之前只记成功响应 ⇒ 把「接口报错」误判成「产品本来就没数据」。
    try {
      const b = r.json();
      const d = b && typeof b === 'object' && 'data' in b ? b.data : b;
      // 只保留「像列表/分页」的响应，打印条数
      const n = Array.isArray(d) ? d.length
          : d && typeof d === 'object' && Array.isArray(d.records) ? d.records.length
          : d && typeof d === 'object' && Array.isArray(d.list) ? d.list.length
          : null;
      if (n !== null) out.push({ url: r.url().replace(/^.*\/api/, '/api'), status: r.status(), rows: n });
    } catch (e) {}
  });

  await page.goto(`${BASE}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);
  const u = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if (await u.count() && await u.isVisible().catch(() => false)) {
    await u.fill('admin');
    await page.locator('input[type="password"], input[placeholder="密码"]').first().fill('123456');
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await page.waitForTimeout(4000);
  }

  for (const [app, route] of TARGETS) {
    out.length = 0;
    // 注意：route 已含前导 "/"，再拼 "/" 会变成 //iam#/xxx ⇒ 页面根本不导航（hash 为空）。
    await page.goto(BASE + route, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(4200);
    await page.waitForTimeout(1500);
    const info = await page.evaluate(() => ({
      hash: location.hash,
      rows: document.querySelectorAll('tbody tr.ant-table-row').length,
      emptyText: [...document.querySelectorAll('.ant-empty')].map(e => e.innerText.replace(/\s+/g, ' ').trim().slice(0, 60)),
      firstRow: (document.querySelector('tbody tr.ant-table-row') || {}).innerText || '',
    }));
    console.log(JSON.stringify({ app, ...info, api: out }));
  }
  await browser.close();
})();
