// 用法: node render-page.js <路由> <输出png>
const puppeteer = require('puppeteer-core');
const CHROME = '/Users/renhui.trh/.wpscomate/binaries/apps/basic/tools/chromium/versions/1.61.0/chromium-1228/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing';
const route = process.argv[2] || '#/apps';
const out = process.argv[3] || '/tmp/page.png';
const wait = Number(process.argv[4] || 7000);
async function main() {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-gpu'],
    defaultViewport: { width: 1440, height: 900 },
  });
  const page = await browser.newPage();
  const logs = [];
  page.on('console', (m) => { if (m.type() === 'error') logs.push('[console.error] ' + m.text().slice(0, 150)); });
  page.on('pageerror', (e) => logs.push('[pageerror] ' + e.message.slice(0, 150)));
  await page.goto('http://localhost:3000/', { waitUntil: 'networkidle2', timeout: 30000 });
  await new Promise((r) => setTimeout(r, 1200));
  await page.type('#username', 'admin');
  await page.type('#password', '123456');
  await page.click('button');
  await new Promise((r) => setTimeout(r, 3500));
  const target = 'http://localhost:3000/metadata' + (route.startsWith('#') ? '' : '/') + route;
  await page.goto(target, { waitUntil: 'networkidle2', timeout: 30000 });
  await new Promise((r) => setTimeout(r, wait));
  const text = await page.evaluate(() => document.body.innerText.slice(0, 800));
  console.log('URL:', page.url());
  console.log('文字:', text.replace(/\n+/g, ' | ').slice(0, 600));
  await page.screenshot({ path: out });
  await browser.close();
  if (logs.length) console.log('错误日志:', logs.slice(0, 6));
}
main().catch((e) => { console.error('FAIL:', e.message); process.exit(1); });
