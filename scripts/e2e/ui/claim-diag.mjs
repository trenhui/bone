/**
 * 智能理赔（bone-claim-app）定向诊断探针。
 *
 * 目的：ui-all-menu-regression 只打印 body.slice(0,200)，「加载失败」字样落在 200 字之后，
 * 无法定位到底哪个请求炸了。本探针拦截所有 4xx/5xx 响应，打印 URL + 状态 + 响应体，
 * 并 dump 完整页面文本，用于定位真实后端错误。
 *
 * 用法：node scripts/e2e/ui/claim-diag.mjs [menuPath ...]
 */
import { UI_BASE as BASE, loadPlaywright } from '../e2e_lib/client.cjs';
import { uiLogin } from '../e2e_lib/ui.cjs';
const { chromium } = loadPlaywright();

const targets = process.argv.slice(2).length
  ? process.argv.slice(2)
  : ['/claim#/review', '/claim#/adjust', '/claim#/evidence', '/claim#/fund', '/claim#/appeal', '/claim#/progress'];

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(20000);

const failures = [];
page.on('response', async (r) => {
  if (r.status() >= 400) {
    let body = '';
    try {
      body = (await r.text()).slice(0, 300);
    } catch {
      body = '<no body>';
    }
    failures.push(`${r.status()} ${r.request().method()} ${r.url()}\n     ${body}`);
  }
});
page.on('pageerror', (e) => failures.push('pageerror: ' + e.message.slice(0, 300)));

await uiLogin(page);
console.log('登录完成\n');

for (const t of targets) {
  failures.length = 0;
  await page.goto(`${BASE}${t}`, { waitUntil: 'domcontentloaded' }).catch(() => {});
  // 复刻 ui-all-menu-regression 的采样时机：先等微应用渲染出内容，再等 1.2s 采样。
  // 关键：不能固定等 6s —— antd message toast 默认 3s 就消失，等太久会漏掉「页面真的报了错」，
  // 等太短又会在请求发出前采样。BONE_DIAG_WAIT 可覆盖稳定后的额外等待（默认 1200ms）。
  const deadline = Date.now() + 12000;
  while (Date.now() < deadline) {
    const len = await page
      .evaluate(() => {
        const e = document.querySelector('#subapp-viewport');
        return e ? (e.innerText || '').trim().length : -1;
      })
      .catch(() => -1);
    if (len > 20) break;
    await page.waitForTimeout(500);
  }
  await page.waitForTimeout(Number(process.env.BONE_DIAG_WAIT || 1200));
  const view = await page
    .locator('#subapp-viewport')
    .innerText()
    .catch(() => '<no viewport>');
  const controls = await page
    .locator(
      '#subapp-viewport input:visible, #subapp-viewport button:visible, #subapp-viewport table:visible'
    )
    .count()
    .catch(() => 0);
  const body = await page.locator('body').innerText().catch(() => '');
  const hit = /页面出错了|Something went wrong|应用加载失败|加载失败|500\s*Internal/i.exec(body);
  console.log(`===== ${t} =====`);
  console.log(`viewport len=${view.trim().length}`);
  console.log(
    `body 命中错误文案: ${hit ? '是 → ' + JSON.stringify(body.slice(Math.max(0, hit.index - 80), hit.index + 80)) : '否'}`
  );
  if (failures.length) {
    console.log('失败请求 / 页面异常:');
    failures.forEach((f) => console.log('  ' + f));
  } else {
    console.log('无 4xx/5xx / 无 pageerror');
  }
  console.log('--- viewport 全文 ---');
  console.log(view.slice(0, 1500));
  console.log();
}

await browser.close();
