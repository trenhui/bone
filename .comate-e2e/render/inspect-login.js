const puppeteer = require('puppeteer-core');
const CHROME = '/Users/renhui.trh/.wpscomate/binaries/apps/basic/tools/chromium/versions/1.61.0/chromium-1228/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing';
async function main() {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox'] });
  const page = await browser.newPage();
  await page.goto('http://localhost:3000/', { waitUntil: 'networkidle2', timeout: 30000 });
  await new Promise((r) => setTimeout(r, 2000));
  const info = await page.evaluate(() => {
    const inputs = [...document.querySelectorAll('input')].map((i) => ({ ph: i.getAttribute('placeholder'), id: i.id, name: i.name }));
    const btns = [...document.querySelectorAll('button')].map((b) => ({ text: (b.textContent || '').trim().slice(0, 20) }));
    const bodyText = document.body.innerText.slice(0, 300);
    return { inputs, btns, bodyText };
  });
  console.log(JSON.stringify(info, null, 1));
  await browser.close();
}
main().catch((e) => { console.error('FAIL:', e.message); process.exit(1); });
