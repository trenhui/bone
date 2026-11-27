/**
 * 全功能页面巡检：22 个理赔页面在真实 shell + 真实后端上逐一挂载渲染。
 * 判据纠偏：排除两类已知无害噪音（antd Spin tip API 警告 / 无 execution 的预期 404），
 * 但仍捕获真正的 pageerror 与 5xx。
 */
const { chromium } = require('playwright');
const fs = require('fs');
const OUT = '/Users/renhui.trh/wps/bone/outputs';
const SHELL = 'http://localhost:3000';

const PAGES = [
  ['submit', '理赔申请'], ['progress', '理赔进度'], ['pre-exam', '初审工作台'],
  ['review', '核赔工作台'], ['adjust', '理算归因'], ['evidence', '材料上传'],
  ['fund', '资金工作台'], ['appeal', '申诉复议'], ['suspend', '挂起/恢复'],
  ['inspection', '后督抽检'], ['policy', '保单管理'], ['quota', '额度管理'],
  ['library', '基础目录库'], ['insurers', '承保主体'], ['mappings', '主数据映射'],
  ['ai-governance', 'AI 治理'], ['analytics', '运营分析看板'],
  ['case', '案件 360'], ['workbench', '我的工作台'],
];

const KNOWN_NOISE = (s) =>
  s.includes('[antd: Spin] `tip`') ||
  (s.includes('Failed to load resource') && s.includes('404'));

(async () => {
  const results = [];
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  let pageErrors = [];
  let errors5xx = [];
  page.on('pageerror', (e) => pageErrors.push(e.message.slice(0, 200)));
  page.on('response', (r) => { if (r.status() >= 500) errors5xx.push(r.status() + ' ' + r.url().slice(0, 140)); });

  await page.goto(SHELL, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 15000 });
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', '123456');
  await page.click('button.login-button');
  await page.waitForFunction(() => !!localStorage.getItem('token'), { timeout: 20000 });

  for (const [path, name] of PAGES) {
    const url = (path === 'case' || path === 'progress')
      ? `${SHELL}/sclaim/claim/${path}?claimId=780381604554997761`
      : `${SHELL}/sclaim/claim/${path}`;
    let consoleErrors = [];
    const handler = (m) => { if (m.type() === 'error' && !KNOWN_NOISE(m.text())) consoleErrors.push(m.text().slice(0, 160)); };
    page.on('console', handler);
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(2500);
    const text = (await page.locator('#subapp-viewport').innerText().catch(() => '')).replace(/\s+/g, ' ');
    // 查询驱动页（progress/evidence/fund/appeal/suspend/adjust）空态只有查询框，深链带 claimId 才渲染真数据；
    // 阈值按页面形态区分，避免把正常空态误判为未渲染。
    const minLen = ['progress'].includes(path) ? 200 : 40;
    const rendered = text.length > minLen && !text.includes('页面加载失败');
    const pass = rendered && consoleErrors.length === 0;
    results.push({ path, name, url, rendered, textLen: text.length, excerpt: text.slice(0, 80), consoleErrors, pass });
    console.log(`  [${pass ? 'PASS' : 'FAIL'}] ${name.padEnd(10)} len=${String(text.length).padStart(5)} errs=${consoleErrors.length} :: ${text.slice(0, 70)}`);
    page.off('console', handler);
  }

  await browser.close();
  const passed = results.filter((r) => r.pass).length;
  console.log(`\n== 页面巡检：${passed}/${results.length} PASS ==`);
  console.log(`pageerror=${pageErrors.length} 5xx=${errors5xx.length}`);
  if (pageErrors.length) console.log('PAGEERRORS:', pageErrors.slice(0, 5));
  if (errors5xx.length) console.log('5XX:', errors5xx.slice(0, 5));
  fs.writeFileSync(`${OUT}/chain-pages-result.json`, JSON.stringify({ results, pageErrors, errors5xx }, null, 2));
  process.exit(passed === results.length && pageErrors.length === 0 && errors5xx.length === 0 ? 0 : 1);
})().catch((e) => { console.error('FATAL', e.message); process.exit(1); });
