const puppeteer = require('puppeteer-core');
const CHROME = '/Users/renhui.trh/.wpscomate/binaries/apps/basic/tools/chromium/versions/1.61.0/chromium-1228/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing';
async function main() {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-gpu'],
    defaultViewport: { width: 1440, height: 900 },
  });
  const page = await browser.newPage();
  const logs = [];
  page.on('console', (m) => { if (m.type() === 'error') logs.push('[console.error] ' + m.text().slice(0, 200)); });
  page.on('pageerror', (e) => logs.push('[pageerror] ' + e.message.slice(0, 200)));

  await page.goto('http://localhost:3000/', { waitUntil: 'networkidle2', timeout: 30000 });
  await new Promise((r) => setTimeout(r, 1500));
  await page.type('#username', 'admin');
  await page.type('#password', '123456');
  await page.click('button');
  await new Promise((r) => setTimeout(r, 4000));
  // 导航到元数据应用（qiankun activeRule=/metadata）
  await page.goto('http://localhost:3000/metadata', { waitUntil: 'networkidle2', timeout: 30000 });
  await new Promise((r) => setTimeout(r, 6000));
  console.log('URL:', page.url());
  const text = await page.evaluate(() => document.body.innerText.slice(0, 500));
  console.log('页面文字:', text.replace(/\n+/g, ' | ').slice(0, 400));
  await page.screenshot({ path: '/tmp/bone-metadata.png' });
  await browser.close();
  console.log('错误日志:', logs.slice(0, 8));
}
main().catch((e) => { console.error('FAIL:', e.message); process.exit(1); });
