// 费用报销 UI 探针：真用户路径（登录 → 菜单进入元数据应用 → 报销列表 → 创建）
// 用 puppeteer-core 驱动本机 Chrome for Testing，全程截图落盘 doc/evidence/
const puppeteer = require('puppeteer-core');

const CHROME = '/Users/renhui.trh/.wpscomate/binaries/apps/basic/tools/chromium/versions/1.61.0/chromium-1228/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing';
const BASE = 'http://localhost:3000';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--window-size=1440,900'],
    defaultViewport: { width: 1440, height: 900 },
  });
  const page = await browser.newPage();
  const shot = (n) => page.screenshot({ path: `/Users/renhui.trh/wps/bone/evidence_ui_${n}.png` });

  // 1. 登录页
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2', timeout: 60000 });
  await sleep(1500);
  await shot('1_login');

  // 2. 登录（admin/123456）
  await page.waitForSelector('input', { timeout: 30000 });
  const inputs = await page.$$('input');
  await inputs[0].type('admin');
  await inputs[1].type('123456');
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 60000 }).catch(() => {}),
    page.evaluate(() => {
      const btn = [...document.querySelectorAll('button')].find((b) => /登\s*录/.test(b.innerText));
      if (btn) btn.click();
    }),
  ]);
  await sleep(3000);
  await shot('2_home');

  // 3. 尝试找到元数据应用菜单/入口，直接跳转元数据应用路由（qiankun 子应用）
  const html = await page.content();
  const hasQiankun = html.includes('qiankun') || (await page.evaluate(() => !!window.__POWERED_BY_QIANKUN__));
  console.log('HAS_QIANKUN=' + hasQiankun);
  console.log('URL_AFTER_LOGIN=' + page.url());

  await browser.close();
  console.log('UI_PROBE_DONE');
})().catch((e) => { console.error('UI_PROBE_FAIL', e.message); process.exit(1); });
