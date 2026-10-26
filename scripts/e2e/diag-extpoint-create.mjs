/**
 * 定向诊断：扩展点目录「新建」弹窗提交后仍不关闭——是真实缺陷还是探针填表不全？
 *
 * 判据（不靠猜）：
 *   1. 弹窗打开后逐个打印表单项 label + 控件类型（确认探针是否漏填必填项）；
 *   2. 拦截失败响应，打印**后端真实拒绝体**（memory 铁律：不能靠「弹窗没关」反推）；
 *   3. 页面上出现的 antd message 文案一并抓出。
 *
 * 用法：node scripts/e2e/diag-extpoint-create.mjs
 */
import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
const API = process.env.BONE_E2E_API || 'http://127.0.0.1:8888/api/v1';
const USER = process.env.BONE_E2E_USER || 'admin';
const PWD = process.env.BONE_E2E_PWD || '123456';

const lr = await fetch(`${API}/iam/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: USER, password: PWD }),
});
const lj = await lr.json();
const token = lj?.data?.token;
if (!token) throw new Error('登录失败: ' + JSON.stringify(lj).slice(0, 200));

const mr = await fetch(`${API}/iam/menus/tree`, {
  headers: { Authorization: 'Bearer ' + token, 'X-Tenant-Id': '0' },
});
const mj = await mr.json();
const menus = [];
const walk = (n) => {
  if (Array.isArray(n)) return n.forEach(walk);
  if (!n || typeof n !== 'object') return;
  if (n.path) menus.push({ path: n.path, name: n.name });
  (n.children || []).forEach(walk);
};
walk(mj?.data ?? mj);
const target = menus.find((m) => (m.name || '').includes('扩展点目录')) || menus.find((m) => (m.name || '').includes('扩展点'));
if (!target) throw new Error('未找到扩展点菜单; 候选: ' + menus.map((m) => m.name).join(' | '));
console.log('目标菜单:', target.name, target.path);

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

// 拦截失败响应：必须打印后端真实拒绝体，不能靠 UI 现象反推
page.on('response', async (resp) => {
  const u = resp.url();
  if (!u.includes('/api/')) return;
  if (resp.status() < 400) return;
  let body = '';
  try {
    body = (await resp.text()).slice(0, 400);
  } catch {
    /* ignore */
  }
  console.log(`  [HTTP ${resp.status()}] ${resp.request().method()} ${u.slice(-70)}\n      ${body}`);
});

// 登录姿势与 ui-full-module-crud-probe 同口径：等密码框出现 → 填 → Enter → 等表单消失
await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('input[type="password"]', { timeout: 20000 });
const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
if (await page.locator(userSel).first().count()) await page.locator(userSel).first().fill(USER);
const pwd = page.locator('input[type="password"]').first();
await pwd.fill(PWD);
await pwd.press('Enter');
await page.waitForTimeout(1500);
if (await page.locator('input[type="password"]').count()) {
  await page.locator('button').filter({ hasText: /登\s*录/ }).first().click().catch(() => {});
  await page.waitForTimeout(3000);
}
const logged = await page
  .waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 })
  .then(() => true)
  .catch(() => false);
console.log('UI 登录成功:', logged, '| URL:', page.url());
if (!logged) throw new Error('登录失败，后续诊断无意义');

const hash = target.path.startsWith('/') ? target.path : '/' + target.path;
await page.goto(BASE + '/#' + hash, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(4000);

// 1) 打开新建弹窗
const clicked = await page
  .getByRole('button', { name: /新建|新增|创建/ })
  .first()
  .click({ timeout: 8000 })
  .then(() => true)
  .catch(() => false);
console.log('点开新建弹窗:', clicked);
await page.waitForTimeout(1200);

// 2) 打印表单项与控件类型
const items = await page.$$eval('.ant-modal-content .ant-form-item', (els) =>
  els.map((el) => {
    const label = el.querySelector('.ant-form-item-label')?.textContent?.trim() || '';
    const req = !!el.querySelector('.ant-form-item-required');
    const ctrl = el.querySelector(
      'input, textarea, .ant-select, .ant-switch, .ant-checkbox, .ant-radio-group, .ant-picker'
    );
    let kind = 'NONE';
    if (ctrl) {
      const tag = ctrl.tagName.toLowerCase();
      if (ctrl.classList.contains('ant-select')) kind = 'select';
      else if (ctrl.classList.contains('ant-switch')) kind = 'switch';
      else if (ctrl.classList.contains('ant-picker')) kind = 'picker';
      else if (tag === 'textarea') kind = 'textarea';
      else kind = tag + (ctrl.getAttribute('type') ? '[' + ctrl.getAttribute('type') + ']' : '');
    }
    return { label, req, kind };
  })
);
console.log('表单项:', JSON.stringify(items, null, 1));

// 3) 逐项填表（必填的文本类填值）
for (const it of items) {
  if (!it.req) continue;
  const fi = page.locator('.ant-modal-content .ant-form-item', { hasText: it.label }).first();
  try {
    if (it.kind === 'select') {
      await fi.locator('.ant-select').click();
      await page.waitForTimeout(400);
      await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').first().click({ timeout: 3000 });
    } else if (it.kind === 'switch') {
      /* 保持默认 */
    } else {
      await fi.locator('input, textarea').first().fill('E2E' + Date.now());
    }
  } catch (e) {
    console.log('  填表失败:', it.label, e.message.slice(0, 80));
  }
}
await page.waitForTimeout(500);

const stillOpenBefore = await page.locator('.ant-modal-content').count();
console.log('提交前弹窗数:', stillOpenBefore);
if (!stillOpenBefore) {
  console.log('★ 填表过程就把弹窗关掉了 —— 属 UI 缺陷或探针误点遮罩');
  await browser.close();
  process.exit(1);
}

// 4) 提交
await page.locator('.ant-modal-footer button.ant-btn-primary').click();
await page.waitForTimeout(3500);

const stillOpen = await page.locator('.ant-modal-content:visible').count();
const msgs = await page.$$eval('.ant-message-notice-content', (els) => els.map((e) => e.textContent?.trim()));
const errs = await page.$$eval('.ant-form-item-explain-error', (els) => els.map((e) => e.textContent?.trim()));
console.log('提交后弹窗仍打开:', stillOpen > 0);
console.log('页面 message:', JSON.stringify(msgs));
console.log('表单校验错误:', JSON.stringify(errs));

await browser.close();
