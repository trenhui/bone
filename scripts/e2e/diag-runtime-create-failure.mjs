/**
 * 定位探针：业务数据（runtime 理赔实体）「新建 → 提交」为什么弹窗不关。
 * 走到提交这一步后，把**客户端校验错误文本 + 服务端响应**都抓出来，
 * 用于区分「前端校验拦下」还是「后端拒绝」。只诊断，不做全量巡检。
 * 用法：node scripts/e2e/diag-runtime-create-failure.mjs
 */
import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
const API = process.env.BONE_E2E_API || 'http://127.0.0.1:8888/api/v1';
const SHOT = '/Users/renhui.trh/wps/bone/logs/diag_runtime_create_fail.png';
const STAMP = String(Date.now()).slice(-6);

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
page.setDefaultTimeout(20000);
await page.addInitScript((s) => {
  window.prompt = () => `e2e-evidence-${s}.pdf`;
}, STAMP);
const bad = [];
page.on('response', async (r) => {
  if (r.status() >= 400) {
    let body = '';
    try { body = (await r.text()).slice(0, 500); } catch { /* ignore */ }
    bad.push(`${r.status()} ${r.url()} :: ${body.replace(/\s+/g, ' ')}`);
  }
});

const modal = () => page.locator('.ant-modal-content, .ant-drawer-content').first();

try {
  // 登录
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"]', { timeout: 20000 });
  const u = page.locator('input[placeholder*="用户名"], input[name="username"], input#username').first();
  if (await u.count()) await u.fill('admin');
  const p = page.locator('input[type="password"]').first();
  await p.fill('123456');
  await p.press('Enter');
  await page.waitForTimeout(2000);
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('button').filter({ hasText: /登\s*录/ }).first().click().catch(() => {});
    await page.waitForTimeout(3000);
  }
  console.log('登录成功：', await page.waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 }).then(() => true).catch(() => false));

  // 进页面 + 选实体
  await page.goto(`${BASE}/metadata#/runtime`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(6000);
  await page.locator('#subapp-viewport .ant-select').first().click();
  await page.waitForTimeout(800);
  const opts = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option');
  let picked = null;
  for (let i = 0; i < await opts.count(); i++) {
    const t = await opts.nth(i).innerText();
    if (/理赔/.test(t)) { picked = opts.nth(i); break; }
  }
  (picked ?? opts.first()).click();
  await page.waitForTimeout(2500);

  await page.locator('#subapp-viewport button:visible').filter({ hasText: /新建记录/ }).first().click();
  await page.waitForTimeout(2500);

  const fillByLabel = async (label, value) => {
    const item = modal().locator('.ant-form-item').filter({ hasText: label }).first();
    if (!(await item.count())) { console.log(`  [缺] ${label}`); return; }
    const input = item.locator('input:not([type=hidden]), textarea').first();
    if (await input.count()) { await input.fill(value); console.log(`  [填] ${label} = ${value}`); return; }
    console.log(`  [无输入] ${label}`);
  };
  const pickSelect = async (label) => {
    const item = modal().locator('.ant-form-item').filter({ hasText: label }).first();
    const sel = item.locator('.ant-select').first();
    if (!(await sel.count())) { console.log(`  [无下拉] ${label}`); return; }
    await sel.click();
    await page.waitForTimeout(600);
    const o = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').first();
    if (await o.count()) { const t = (await o.innerText()).trim(); await o.click(); console.log(`  [选] ${label} = ${t}`); }
    else console.log(`  [空下拉] ${label}`);
    await page.waitForTimeout(300);
  };
  const pickDate = async (label) => {
    const item = modal().locator('.ant-form-item').filter({ hasText: label }).first();
    const pk = item.locator('.ant-picker').first();
    if (!(await pk.count())) { console.log(`  [无日期] ${label}`); return; }
    await pk.click();
    await page.waitForTimeout(500);
    const today = page.locator('.ant-picker-dropdown:not(.ant-picker-dropdown-hidden) .ant-picker-cell-today').first();
    if (await today.count()) await today.click();
    else await page.keyboard.press('Enter');
    await page.waitForTimeout(300);
    const v = await pk.locator('input').first().inputValue().catch(() => '');
    console.log(`  [日期] ${label} = ${v}`);
  };

  await fillByLabel('保单号', `POL-DIAG-${STAMP}`);
  await pickSelect('理赔类型');
  await pickSelect('医院等级');
  await fillByLabel('发票号', `INV-DIAG-${STAMP}`);
  await pickDate('发票日期');
  await fillByLabel('应赔金额', '900');
  await fillByLabel('申报金额', '1000');
  await fillByLabel('发票金额', '1000');
  const at = modal().locator('button').filter({ hasText: /添加附件/ }).first();
  if (await at.count()) { await at.click(); await page.waitForTimeout(600); console.log('  [附件] 已添加'); }

  const submit = modal().locator('button:visible').filter({ hasText: /^(提\s*交|保\s*存|确\s*定)/ }).first();
  console.log('提交按钮：', (await submit.innerText().catch(() => '')).replace(/\s/g, ''));
  await submit.click();
  await page.waitForTimeout(3500);

  // 客户端校验错误
  const errs = await modal().locator('.ant-form-item-explain-error').allInnerTexts().catch(() => []);
  console.log('表单校验错误：', JSON.stringify(errs));
  const modalAlert = await modal().locator('.ant-alert').allInnerTexts().catch(() => []);
  console.log('弹窗内 Alert：', JSON.stringify(modalAlert.map((s) => s.replace(/\s+/g, ' ').slice(0, 120))));
  const toast = await page.locator('.ant-message, .ant-notification').innerText().catch(() => '');
  const toastTxt = toast.replace(/\s+/g, ' ').slice(0, 200);
  console.log('toast：', toastTxt);
  const stillOpen = await modal().isVisible().catch(() => false);
  console.log('弹窗仍开：', stillOpen);
  console.log('4xx/5xx：', bad.slice(0, 10));
  await page.screenshot({ path: SHOT, fullPage: false });
  console.log('截图：', SHOT);

  // 末尾统一断言（UI 探针只 console.log 不算验收）：创建必须真的成功。
  // 只判「弹窗关了」不够——关窗也可能是校验失败的副作用；必须同时断言成功 toast 且无 4xx。
  const ok = !stillOpen && /创建成功|已保存|保存成功/.test(toastTxt) && bad.length === 0;
  console.log(ok ? '\n✓ 理赔单新建闭环（创建成功）' : '\n✗ 理赔单新建未闭环');
  process.exitCode = ok ? 0 : 1;
} catch (e) {
  console.log('探针异常：', String(e.message).slice(0, 200));
  process.exitCode = 1;
} finally {
  await browser.close();
}
