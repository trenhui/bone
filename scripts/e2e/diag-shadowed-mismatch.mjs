/**
 * 自伤遮蔽「数据页 vs 对照卡片口径矛盾」· 同步取证诊断脚本。
 *
 * 为什么需要它：个人覆盖有两个 surface，若两处对同一 hook 给出不同 shadowed 结论，
 * 纯 UI 探针分不清「没触发」与「看到了过期状态」。本脚本在**同一时间点**同时取三份证据：
 *   A. 浏览器内：被点开关那一行的列名 + checked 状态（点击前/后）
 *   B. 服务端  ：node 侧直读 GET /entities/{code}/ui-overlay 的真实 JSON（与浏览器同步）
 *   C. 页面文本：数据页 PersonalViewBar 文案 + 「交付口径对照」卡片双口径列数与告警
 *
 * 历史沿革：IA 收敛前对照点在「交付预览」Tab，已随该 Tab 摘除迁到数据页卡片；
 * 本脚本用于再次出现「同一 hook 两处结论相反」时快速定性。
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

  // 5. 刷新数据页，在「交付口径对照」卡片里取证双口径与告警
  // （IA 收敛后：个人覆盖口径对照已从交付预览 Tab 迁到数据页 ContractScopeCard）
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=data`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(
    () => /交付口径对照/.test(document.body.innerText),
    { timeout: 40000 },
  ).catch(() => log('  (交付口径对照卡片未出现)'));
  log('\n=== 数据页刷新后 · 服务端 overlay ===');
  await serverOverlay(token, '交付口径对照');

  const scopeBody = await page.locator('body').innerText();
  const hasMineTag = /我的所见（含个人覆盖）/.test(scopeBody);
  const hasAlert = /你的个人偏好正在隐藏\s*\d+\s*个契约列/.test(scopeBody);
  const hasContractLine = /全员所见（契约）\s*\d+\s*列/.test(scopeBody);
  const hasMineLine = /我的所见（含个人覆盖）\s*\d+\s*列/.test(scopeBody);
  log(`\n对照卡片：双口径摘要=${hasContractLine && hasMineLine}  个人口径标签=${hasMineTag}  告警=${hasAlert}`);
  // 两侧列数差分 = 被个人偏好遮蔽的契约列数
  const cN = Number(/全员所见（契约）\s*(\d+)\s*列/.exec(scopeBody)?.[1] ?? '0');
  const mN = Number(/我的所见（含个人覆盖）\s*(\d+)\s*列/.exec(scopeBody)?.[1] ?? '0');
  log(`列数差分：contract=${cN} mine=${mN} ⇒ 被遮蔽=${cN - mN}`);

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