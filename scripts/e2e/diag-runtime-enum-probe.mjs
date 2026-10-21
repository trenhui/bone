/**
 * 定位探针：业务数据（runtime 理赔实体）新建弹窗里 ENUM 控件为何选不到选项。
 * 只做「诊断 + 输出 DOM 取证」，不做全量巡检。
 * 用法：node scripts/e2e/diag-runtime-enum-probe.mjs
 */
import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
const API = process.env.BONE_E2E_API || 'http://127.0.0.1:8888/api/v1';
const SHOT = '/Users/renhui.trh/wps/bone/logs/diag_runtime_enum.png';
const USER = 'admin';
const PWD = '123456';

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const netFails = [];
page.on('response', (r) => {
  if (r.status() >= 400) netFails.push(`${r.status()} ${r.url()}`);
});

try {
  // 取运行时菜单，定位「业务数据」
  const lr = await fetch(`${API}/iam/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PWD }),
  });
  const token = (await lr.json())?.data?.token;
  const mr = await fetch(`${API}/iam/menus/tree`, {
    headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0' },
  });
  const mj = await mr.json();
  const menus = [];
  const walk = (ns, parent) => {
    for (const n of ns || []) {
      if (n.path && n.path.includes('#'))
        menus.push({ name: `${parent ? parent + ' · ' : ''}${n.name}`, path: n.path });
      walk(n.children || [], n.name);
    }
  };
  walk(mj.data || [], '');
  const target = menus.find((m) => /业务数据|runtime/i.test(m.name));
  console.log('目标菜单：', JSON.stringify(target));

  // UI 登录
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"]', { timeout: 20000 });
  const u = page.locator('input[placeholder*="用户名"], input[name="username"], input#username').first();
  if (await u.count()) await u.fill(USER);
  const p = page.locator('input[type="password"]').first();
  await p.fill(PWD);
  await p.press('Enter');
  await page.waitForTimeout(2000);
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('button').filter({ hasText: /登\s*录/ }).first().click().catch(() => {});
    await page.waitForTimeout(3000);
  }
  const logged = await page
    .waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 })
    .then(() => true)
    .catch(() => false);
  console.log('登录成功：', logged);
  if (!logged) throw new Error('登录失败');

  await page.goto(`${BASE}/#${target.path}`, { waitUntil: 'domcontentloaded' }).catch(() => {});
  await page.goto(`${BASE}${target.path}`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(6000);
  console.log('viewport 文本：', (await page.locator('#subapp-viewport').innerText().catch(() => '(无)')).replace(/\s+/g, ' ').slice(0, 300));
  console.log('按钮：', JSON.stringify((await page.locator('#subapp-viewport button').allInnerTexts().catch(() => [])).map((s) => s.replace(/\s+/g, '')).slice(0, 30)));
  console.log('select 数：', await page.locator('#subapp-viewport .ant-select').count());

  // 选实体
  const entSel = page.locator('#subapp-viewport .ant-select').first();
  if (await entSel.count()) {
    await entSel.click();
    await page.waitForTimeout(1000);
    const opts = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option');
    console.log('实体选项数：', await opts.count());
    const opt = opts.first();
    if (await opt.count()) {
      console.log('选中实体：', (await opt.innerText()).trim());
      await opt.click();
      await page.waitForTimeout(3000);
    }
  }
  console.log('选实体后按钮：', JSON.stringify((await page.locator('#subapp-viewport button').allInnerTexts().catch(() => [])).map((s) => s.replace(/\s+/g, '')).slice(0, 30)));

  const createBtn = page
    .locator('#subapp-viewport button:visible')
    .filter({ hasText: /新\s*建|新\s*增|创\s*建/ })
    .first();
  console.log('新建按钮数：', await createBtn.count());
  await createBtn.click();
  await page.waitForTimeout(3000);

  // 抓「理赔类型 / 医院等级」所在 form item 的 DOM
  const rows = page.locator('.ant-modal-content .ant-row, .ant-drawer-content .ant-row');
  const n = await rows.count();
  console.log('弹窗内 form row 数：', n);
  for (let i = 0; i < n; i++) {
    const txt = (await rows.nth(i).innerText().catch(() => '')).replace(/\s+/g, ' ').trim();
    if (!/理赔类型|医院等级|发票日期|材料证据|投保人哈希|费用明细/.test(txt)) continue;
    const html = (await rows.nth(i).innerHTML().catch(() => '')).replace(/\s+/g, ' ');
    console.log(`--- row[${i}] text="${txt.slice(0, 40)}"`);
    console.log('    html:', html.slice(0, 700));
  }

  // 逐个点开 select 看有没有选项
  for (const label of ['理赔类型', '医院等级']) {
    const item = page
      .locator('.ant-modal-content .ant-row, .ant-drawer-content .ant-row')
      .filter({ hasText: label })
      .first();
    const sel = item.locator('.ant-select').first();
    const has = await sel.count();
    console.log(`\n[${label}] .ant-select count=${has}`);
    if (!has) continue;
    await sel.click();
    await page.waitForTimeout(1200);
    const opts = page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option');
    const cnt = await opts.count();
    const texts = [];
    for (let i = 0; i < Math.min(cnt, 8); i++) texts.push((await opts.nth(i).innerText()).trim());
    console.log(`[${label}] 选项数=${cnt} 前8=${JSON.stringify(texts)}`);
    const empty = await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-empty')
      .count();
    console.log(`[${label}] 空态(.ant-empty)=${empty}`);
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(400);
  }

  await page.screenshot({ path: SHOT, fullPage: false });
  console.log('\n截图：', SHOT);
  console.log('4xx/5xx 响应：', netFails.slice(0, 15));
} finally {
  await browser.close();
}
