/**
 * 自伤遮蔽告警「数据页报 shadowed>0 / 交付预览报 shadowed=0」矛盾点 · 同步取证诊断脚本。
 *
 * 为什么需要它：同一 hook、同一判据（overlayHiddenContractColumns），两页结果却相反。
 * 本脚本在**同一个时间点**同时取三份证据，避免跨轮次时序混淆：
 *   A. 浏览器内：被点开关那一行的列名 + checked 状态（点击前/后）
 *   B. 服务端  ：node 侧直读 GET /entities/{code}/ui-overlay 的真实 JSON（与浏览器同步）
 *   C. 页面文本：数据页 PersonalViewBar 文案 / 交付预览 mine 口径的告警与徽标
 *
 * 只做诊断，不改任何源码；结束前清理 overlay（DELETE）避免污染。
 * 用法：node scripts/e2e/diag-shadowed-mismatch.mjs
 */
import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
const ENTITY_ID = process.env.BONE_ENTITY_ID || '766546550728949760';
const ENTITY_CODE = process.env.BONE_ENTITY_CODE || 'claim_case_chain_792218121';
const IAM = process.env.BONE_IAM_BASE || 'http://127.0.0.1:8081';
const META = process.env.BONE_META_BASE || 'http://127.0.0.1:9001';

const log = (...a) => console.log(...a);

async function login() {
  const r = await fetch(`${IAM}/api/v1/iam/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' }),
  });
  const j = await r.json();
  return j?.data?.token;
}

/** 服务端 overlay 真值（与浏览器同一时刻取） */
async function serverOverlay(token, tag) {
  const r = await fetch(
    `${META}/api/v1/metadata/entities/${encodeURIComponent(ENTITY_CODE)}/ui-overlay`,
    { headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0' } },
  );
  const j = await r.json();
  log(`  [服务端@${tag}] code=${j.code} data=${JSON.stringify(j.data)}`);
  return j.data;
}

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(25000);

try {
  const token = await login();
  log(`登录 token 长度=${(token || '').length}`);

  // 0. 起点：清干净，避免历史残留干扰
  await fetch(`${META}/api/v1/metadata/entities/${encodeURIComponent(ENTITY_CODE)}/ui-overlay`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0' },
  });
  await serverOverlay(token, '清理后');

  // 1. 浏览器登录
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"]', { timeout: 20000 });
  const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
  if (await page.locator(userSel).first().count()) await page.locator(userSel).first().fill('admin');
  await page.locator('input[type="password"]').first().fill('123456');
  await page.locator('input[type="password"]').first().press('Enter');
  await page.waitForTimeout(1500);
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('button').filter({ hasText: /登\s*录/ }).first().click().catch(() => {});
  }
  await page.waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 });
  log('浏览器登录 OK');

  // 2. 进数据页，dump 列设置弹层里每一行的「列名 + 是否契约列 + 开关状态」
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=data`, { waitUntil: 'domcontentloaded' });
  await page.locator('button').filter({ hasText: '列设置' }).first()
    .waitFor({ state: 'visible', timeout: 40000 });
  await page.locator('button').filter({ hasText: '列设置' }).first().click();
  await page.locator('.col-settings-row').first().waitFor({ state: 'visible', timeout: 15000 });
  await page.waitForTimeout(800);

  const rows = await page.evaluate(() => {
    return Array.from(document.querySelectorAll('.col-settings-row')).map((r, i) => ({
      i,
      text: (r.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 60),
      sw: r.querySelector('.col-settings-switch') ? r.querySelector('.col-settings-switch').className : null,
      checked: r.querySelector('.ant-switch')?.classList.contains('ant-switch-checked') ?? null,
      disabled: r.querySelector('.ant-switch')?.classList.contains('ant-switch-disabled') ?? null,
    }));
  });
  log('\n=== 列设置弹层行快照（点击前）===');
  rows.forEach((r) => log(`  #${r.i} checked=${r.checked} disabled=${r.disabled} | ${r.text}`));

  // 3. 点第一个非禁用开关
  const target = page.locator('.col-settings-row').filter({ hasNot: page.locator('.ant-switch-disabled') }).first();
  const targetText = (await target.innerText().catch(() => '')).replace(/\s+/g, ' ').trim().slice(0, 60);
  log(`\n点击目标行：${targetText}`);
  await target.locator('.col-settings-switch').first().click();
  await page.waitForTimeout(3000);

  const rowsAfter = await page.evaluate(() => {
    return Array.from(document.querySelectorAll('.col-settings-row')).map((r, i) => ({
      i,
      checked: r.querySelector('.ant-switch')?.classList.contains('ant-switch-checked') ?? null,
      text: (r.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 40),
    }));
  });
  log('=== 点击后各行 checked ===');
  rowsAfter.forEach((r) => log(`  #${r.i} checked=${r.checked} | ${r.text}`));
  log('\n=== 点击后 · 服务端 overlay ===');
  await serverOverlay(token, '点击后3s');

  // 4. 本地 localStorage 缓存值（fetchOverlay 服务端优先时会忽略它，但要看差异）
  const localCache = await page.evaluate(() => {
    const out = {};
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (/overlay|ui.?schema|bone/i.test(k)) out[k] = String(localStorage.getItem(k)).slice(0, 400);
    }
    return out;
  });
  log('\n=== localStorage 相关键 ===');
  Object.entries(localCache).forEach(([k, v]) => log(`  ${k} = ${v}`));

  const dataBody = await page.locator('body').innerText();
  const selfHelp = /(\d+)\s*列被个人偏好隐藏/.exec(dataBody);
  log(`\n数据页 PersonalViewBar 自救入口：${selfHelp ? `出现（N=${selfHelp[1]}）` : '未出现'}`);

  // 5. 刷新进交付预览，切 mine 口径取证
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=ui`, { waitUntil: 'domcontentloaded' });
  await page.locator('button').filter({ hasText: /团队列|界面设计器/ }).first()
    .waitFor({ state: 'visible', timeout: 40000 });
  await page.waitForFunction(
    () => /列集合、列序、列宽、固定列与列标题由「界面设计器」契约定义/.test(document.body.innerText),
    { timeout: 20000 },
  ).catch(() => log('  (契约 info alert 未出现)'));
  log('\n=== 交付预览刷新后 · 服务端 overlay ===');
  await serverOverlay(token, '交付预览');

  await page.locator('.ant-segmented-item', { hasText: '我的所见' }).first().click();
  await page.waitForTimeout(1500);
  const mineBody = await page.locator('body').innerText();
  const hasMineTag = /个人偏好\s*·\s*仅自己可见/.test(mineBody);
  const hasAlert = /你的个人偏好正在隐藏\s*\d+\s*个契约列/.test(mineBody);
  log(`\n交付预览 mine 口径：个人层徽标=${hasMineTag}  告警=${hasAlert}`);
  log(`表格可见列数（mine）= ${await page.locator('.ant-table-tbody tr.ant-table-row').count()}`);

  // 6. 回到 contract 口径数一次，做差分
  await page.locator('.ant-segmented-item', { hasText: '全员所见' }).first().click();
  await page.waitForTimeout(1200);
  log(`表格可见列数（contract）= ${await page.locator('.ant-table-tbody tr.ant-table-row').count()}`);

  // 7. 清理
  await page.keyboard.press('Escape');
  await fetch(`${META}/api/v1/metadata/entities/${encodeURIComponent(ENTITY_CODE)}/ui-overlay`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0' },
  });
  log('\n=== 清理后 · 服务端 overlay ===');
  await serverOverlay(token, '清理后');
  log('\n诊断结束');
} catch (e) {
  log('诊断异常：', e?.message || e);
} finally {
  await browser.close();
}