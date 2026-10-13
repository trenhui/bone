const puppeteer = require('puppeteer-core');
(async () => {
  const browser = await puppeteer.launch({
    executablePath: '/Users/renhui.trh/.wpscomate/binaries/apps/basic/tools/chromium/versions/1.61.0/chromium-1228/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing',
    headless: 'new',
    args: ['--no-sandbox']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });
  await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle2', timeout: 30000 });
  await new Promise(r => setTimeout(r, 1500));
  const inputs = await page.$$eval('input', els => els.map(e => ({ type: e.type, placeholder: e.placeholder, name: e.name, id: e.id })));
  console.log('INPUTS=' + JSON.stringify(inputs));
  const buttons = await page.$$eval('button', els => els.map(e => e.textContent.trim()));
  console.log('BUTTONS=' + JSON.stringify(buttons));
  // 登录:填表并点击
  await page.type('input[type="text"], input:not([type="password"])', 'admin');
  await page.type('input[type="password"]', '123456');
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(()=>),
    page.click('button')
  ]);
  await new Promise(r => setTimeout(r, 2500));
  console.log('URL_AFTER=' + page.url());
  console.log('TOKEN_SET=' + await page.evaluate(() => !!localStorage.getItem('token')));
  await page.screenshot({ path: 'evidence_ui_after_login.png' });
  await browser.close();
})().catch(e => { console.error('FAIL=' + e.message); process.exit(1); });
