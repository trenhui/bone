// 元数据低代码最小闭环 E2E：模拟人工在 metadata-app 走完
//   新建 RUNTIME 实体 → 加字段 → 发布 → 打开数据 → 新建记录 → 编辑(If-Match)
// 用法：
//   NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-6/bin/node
//   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
//   $NODE scripts/e2e/ui/probe-metadata-loop.cjs > /tmp/md-loop.json
const { chromium } = require('playwright');
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const BASE = 'http://localhost:3000';
const OUT = '/tmp/md-loop-shots';
require('fs').mkdirSync(OUT, { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function fillByLabel(page, modalSel, label, value) {
  const item = page.locator(`${modalSel} .ant-form-item`).filter({ hasText: label }).first();
  const input = item.locator('input.ant-input, textarea.ant-input').first();
  await input.fill(value);
}

async function selectFirstOption(page, modalSel, label) {
  const item = page.locator(`${modalSel} .ant-form-item`).filter({ hasText: label }).first();
  await item.locator('.ant-select-selector').click();
  await sleep(500);
  await page.locator('.ant-select-item-option').first().click();
}

const report = { steps: [], errors: [], failedReq: [] };
function step(name, ok, info) {
  report.steps.push({ name, ok, ...(info || {}) });
  console.error(`[${ok ? 'OK ' : 'FAIL'}] ${name}${info && info.detail ? ' :: ' + info.detail : ''}`);
}

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 1000 } })).newPage();
  page.on('pageerror', (e) => report.errors.push({ kind: 'pageerror', text: String(e.message).slice(0, 300) }));
  page.on('console', (m) => { if (m.type() === 'error') report.errors.push({ kind: 'console', text: m.text().slice(0, 300) }); });
  page.on('response', (r) => { if (r.status() >= 400) report.failedReq.push({ url: r.url().replace(BASE, '').slice(0, 140), status: r.status() }); });

  // ---- 登录 ----
  await page.goto(`${BASE}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await sleep(3500);
  const u = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if (await u.count() && await u.isVisible().catch(() => false)) {
    await u.fill('admin');
    await page.locator('input[type="password"], input[placeholder="密码"]').first().fill('123456');
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await sleep(4000);
  }

  const stamp = Date.now();
  const entCode = `e2eloop${stamp}`;
  const entName = `E2E闭环${stamp}`;

  // ---- 进入实体列表 ----
  try {
    await page.goto(`${BASE}/metadata#/entities`, { waitUntil: 'domcontentloaded' });
    await sleep(4500);
    const addBtn = page.locator('button').filter({ hasText: '新建实体' }).first();
    if (await addBtn.count()) { await addBtn.click({ timeout: 6000 }); await sleep(1500); }
    const modalOpen = await page.locator('.ant-modal-content').count();
    step('打开新建实体弹窗', !!modalOpen, { modalOpen });
  } catch (e) { step('打开新建实体弹窗', false, { detail: String(e.message).slice(0, 120) }); await page.screenshot({ path: `${OUT}/01-create-modal.png` }); }

  // ---- 填表单并创建（默认 RUNTIME=1）----
  try {
    // 按输入顺序填（避免「名称」被「显示名」子串误匹配）：显示名/名称/编码/表名
    const txt = page.locator('.ant-modal-content input.ant-input');
    await txt.nth(0).fill(entName);
    await txt.nth(1).fill(entName);
    await txt.nth(2).fill(entCode);
    await txt.nth(3).fill(`md_${entCode}`);
    // 所属模块（非 scoped 必填）：选第一个
    await selectFirstOption(page, '.ant-modal-content', '所属模块');
    await sleep(500);
    const ok = page.locator('.ant-modal-footer button').filter({ hasText: '确定' }).first();
    if (await ok.count()) await ok.click({ timeout: 5000 });
    await sleep(2000);
    // 期望落到详情 ?tab=structure
    const hash = await page.evaluate(() => location.hash);
    if (!/tab=structure/.test(hash)) {
      const diag = await page.evaluate(() => ({
        modalStillOpen: !!document.querySelector('.ant-modal-content'),
        validationErrors: [...document.querySelectorAll('.ant-form-item-explain-error')].map((e) => e.innerText.trim()),
        msg: [...document.querySelectorAll('.ant-message-notice-content')].map((e) => e.innerText).join(' | '),
      }));
      step('创建实体并落到详情结构 Tab', false, { hash, ...diag });
      await page.screenshot({ path: `${OUT}/02-after-create.png` });
    } else {
      step('创建实体并落到详情结构 Tab', true, { hash });
    }
  } catch (e) { step('创建实体', false, { detail: String(e.message).slice(0, 140) }); await page.screenshot({ path: `${OUT}/02-create-fail.png` }); }

  // ---- 加字段（字段向导）----
  try {
    await page.locator('button').filter({ hasText: '新建字段' }).first().click({ timeout: 6000 });
    await sleep(1200);
    // step0 选 STRING 类型卡片
    await page.locator('.ant-modal-content div[role="button"]').filter({ hasText: 'STRING' }).first().click();
    await sleep(400);
    await page.locator('.ant-modal-content button').filter({ hasText: '下一步' }).first().click();
    await sleep(800);
    await fillByLabel(page, '.ant-modal-content', '显示名', '姓名');
    await fillByLabel(page, '.ant-modal-content', '名称（英文）', 'fullName');
    await fillByLabel(page, '.ant-modal-content', '编码', 'full_name');
    await sleep(300);
    await page.locator('.ant-modal-content button').filter({ hasText: '创建字段' }).first().click({ timeout: 5000 });
    await sleep(2500);
    const fieldRows = await page.evaluate(() => document.querySelectorAll('tbody tr.ant-table-row').length);
    step('加字段成功（结构表出现行）', fieldRows >= 1, { fieldRows });
    if (fieldRows < 1) await page.screenshot({ path: `${OUT}/03-field-fail.png` });
  } catch (e) { step('加字段', false, { detail: String(e.message).slice(0, 140) }); await page.screenshot({ path: `${OUT}/03-field-fail.png` }); }

  // ---- 发布 ----
  try {
    await page.locator('button').filter({ hasText: /^发布$/ }).first().click({ timeout: 6000 });
    await sleep(1800);
    // 发布 Modal 内的发布按钮
    const pub = page.locator('.ant-modal-content button').filter({ hasText: /^发布$/ }).first();
    if (await pub.count()) await pub.click({ timeout: 6000 });
    await sleep(3000);
    const msg = await page.evaluate(() => [...document.querySelectorAll('.ant-message-notice-content')].map(e => e.innerText).join(' | '));
    const success = /已发布/.test(msg) || (await page.locator('.ant-alert-success').count()) > 0;
    step('发布成功', success, { msg: msg.slice(0, 160) });
    if (!success) await page.screenshot({ path: `${OUT}/04-publish-fail.png` });
  } catch (e) { step('发布', false, { detail: String(e.message).slice(0, 140) }); await page.screenshot({ path: `${OUT}/04-publish-fail.png` }); }

  // ---- 打开数据 ----
  try {
    const openData = page.locator('button').filter({ hasText: '打开数据' }).first();
    if (await openData.count()) { await openData.click({ timeout: 6000 }); await sleep(2500); }
    const hash = await page.evaluate(() => location.hash);
    step('打开数据进入数据 Tab', /tab=data/.test(hash), { hash });
  } catch (e) { step('打开数据', false, { detail: String(e.message).slice(0, 140) }); await page.screenshot({ path: `${OUT}/05-opendata-fail.png` }); }

  // ---- 新建记录 ----
  let recordOk = false;
  try {
    await page.locator('button').filter({ hasText: '新建记录' }).first().click({ timeout: 6000 });
    await sleep(1200);
    // 记录弹窗里的字段输入（STRING → input.ant-input）
    const input = page.locator('.ant-modal-content input.ant-input').first();
    if (await input.count()) await input.fill('张三');
    await sleep(300);
    await page.locator('.ant-modal-content button').filter({ hasText: '确定' }).first().click({ timeout: 5000 });
    await sleep(2500);
    const msg = await page.evaluate(() => [...document.querySelectorAll('.ant-message-notice-content')].map(e => e.innerText).join(' | '));
    recordOk = /创建成功/.test(msg);
    const dataRows = await page.evaluate(() => document.querySelectorAll('tbody tr.ant-table-row').length);
    step('新建记录成功', recordOk, { msg: msg.slice(0, 160), dataRows });
    if (!recordOk) await page.screenshot({ path: `${OUT}/06-record-fail.png` });
  } catch (e) { step('新建记录', false, { detail: String(e.message).slice(0, 140) }); await page.screenshot({ path: `${OUT}/06-record-fail.png` }); }

  // ---- 编辑记录（走 If-Match / 412 代码路径）----
  try {
    const edit = page.locator('tbody tr.ant-table-row').first().locator('button').filter({ hasText: '编辑' }).first();
    if (await edit.count()) {
      await edit.click({ timeout: 5000 });
      await sleep(1200);
      const input = page.locator('.ant-modal-content input.ant-input').first();
      if (await input.count()) await input.fill('李四');
      await sleep(200);
      await page.locator('.ant-modal-content button').filter({ hasText: /确定|保存|更新|提交/ }).first().click({ timeout: 5000 });
      await sleep(2200);
      const msg = await page.evaluate(() => [...document.querySelectorAll('.ant-message-notice-content')].map(e => e.innerText).join(' | '));
      step('编辑记录（If-Match 正常提交）', /更新成功/.test(msg), { msg: msg.slice(0, 160) });
    } else { step('编辑记录', false, { detail: '无编辑按钮' }); }
  } catch (e) { step('编辑记录', false, { detail: String(e.message).slice(0, 140) }); await page.screenshot({ path: `${OUT}/07-edit-fail.png` }); }

  report.entityCode = entCode;
  report.entityName = entName;
  await browser.close();
  console.log(JSON.stringify(report, null, 1));
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
