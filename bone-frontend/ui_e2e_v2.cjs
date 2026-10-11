// 费用报销 UI E2E v2：登录 → 打开报销列表 → 抓「新建报销单」按钮 → 新建弹窗/跳转 → 全程截图
const puppeteer = require('puppeteer-core');

const CHROME = '/Users/renhui.trh/.wpscomate/binaries/apps/basic/tools/chromium/versions/1.61.0/chromium-1228/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing';
const OUT = '/Users/renhui.trh/wps/bone/doc/evidence/expense-e2e-20261011';
require('fs').mkdirSync(OUT, { recursive: true });

const shot = (page, name) => page.screenshot({ path: `${OUT}/${name}.png`, fullPage: false });

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--window-size=1440,900'],
    defaultViewport: { width: 1440, height: 900 },
  });
  const page = await browser.newPage();
  const errors = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text().slice(0, 200)); });
  page.on('pageerror', (e) => errors.push('PAGEERROR:' + String(e).slice(0, 200)));

  // 1. 登录
  await page.goto('http://localhost:3000/', { waitUntil: 'networkidle2', timeout: 30000 });
  await page.waitForSelector('input', { timeout: 15000 });
  await shot(page, '01-login');
  const inputs = await page.$$('input');
  await inputs[0].type('admin');
  await inputs[1].type('123456');
  await Promise.all([page.click('button[type=submit], button'), page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})]);
  await new Promise((r) => setTimeout(r, 2500));
  await shot(page, '02-after-login');
  console.log('URL_AFTER_LOGIN=' + page.url());

  // 2. 直接进入元数据子应用的报销页面（qiankun 微前端路由）
  const routes = [
    '/metadata/expense',
    '/metadata/expense_claim',
    '/metadata/runtime/expense_claim',
    '/metadata/app/metadata/expense_claim',
    '/metadata-app/expense_claim',
  ];
  let landed = null;
  for (const r of routes) {
    await page.goto('http://localhost:3000' + r, { waitUntil: 'networkidle2', timeout: 30000 }).catch(() => {});
    await new Promise((s) => setTimeout(s, 2200));
    const body = await page.evaluate(() => document.body.innerText.slice(0, 1200));
    await shot(page, '03-route' + r.replace(/\//g, '_'));
    const hit = /报销/.test(body);
    console.log(`ROUTE ${r} hit=${hit} text=${JSON.stringify(body.slice(0, 160))}`);
    if (hit) { landed = r; break; }
  }

  // 3. 若找到报销页，点「新建」抓弹窗
  if (landed) {
    const clicked = await page.evaluate(() => {
      const btns = [...document.querySelectorAll('button')];
      const b = btns.find((x) => /新建|新增|创建/.test(x.innerText));
      if (b) { b.click(); return b.innerText; }
      return null;
    });
    console.log('CLICK_NEW=' + clicked);
    await new Promise((s) => setTimeout(s, 1800));
    await shot(page, '04-new-dialog');
  }

  console.log('CONSOLE_ERRORS=' + errors.length);
  errors.slice(0, 5).forEach((e) => console.log('ERR: ' + e));
  await browser.close();
  console.log('DONE');
})().catch((e) => { console.error('FATAL ' + e.message); process.exit(1); });
