/**
 * 定点诊断：「业务数据」(/metadata#/runtime) 新建记录时 ENUM 字段被填成非法值 → 400。
 *
 * 目的只有一个：拿到实证，区分
 *   A) 产品缺陷 —— 运行时表单没把 ENUM 渲染成带合法选项的下拉；
 *   B) 探针缺陷 —— 表单渲染正确，是探针的操作方式取到了非法值。
 *
 * 做法：登录后进 /metadata#/runtime，选实体，打开新建弹窗，逐项 dump
 * 「label / 控件类型 / 下拉选项列表 / 填完后控件内的实际值」，不做提交。
 */
/* 共享 client：playwright 加载与登录凭证（BASE 保持本探针原默认口径，见下） */
import { USER, PWD, loadPlaywright } from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const BASE = process.env.BONE_UI_BASE || 'http://127.0.0.1:3000';
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.setDefaultTimeout(20000);

const waitReady = async (ms = 20000) => {
  const deadline = Date.now() + ms;
  let prev = '';
  while (Date.now() < deadline) {
    const txt = await page.evaluate(() => {
      const e = document.querySelector('#subapp-viewport');
      return e ? (e.innerText || '').trim() : '';
    });
    if (txt.length > 20 && txt === prev) return txt;
    prev = txt;
    await page.waitForTimeout(700);
  }
  return prev;
};

try {
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"]', { timeout: 20000 });
  await page.fill('input[placeholder*="用户名"], input[name="username"], input#username', USER);
  await page.fill('input[type="password"]', PWD);
  await page.click('button[type="submit"], .ant-btn-primary');
  await page.waitForTimeout(3000);

  await page.goto(`${BASE}/metadata#/runtime`, { waitUntil: 'domcontentloaded' });
  await waitReady();
  await page.waitForTimeout(2000);

  // 选第一个实体（与主探针同口径）
  const entSel = page.locator('#subapp-viewport .ant-select').first();
  const entText = (await entSel.innerText().catch(() => '')) || '';
  console.log('实体下拉当前值:', JSON.stringify(entText.trim()));
  await entSel.click({ timeout: 3000 }).catch(() => {});
  await page.waitForTimeout(600);
  const opts = await page
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
    .allInnerTexts()
    .catch(() => []);
  console.log('实体下拉选项(前5):', opts.slice(0, 5));
  const opt = page
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
    .first();
  if (await opt.count()) await opt.click({ timeout: 3000 }).catch(() => {});
  await page.waitForTimeout(2000);
  console.log('选后实体下拉:', JSON.stringify(((await page.locator('#subapp-viewport .ant-select').first().innerText().catch(() => '')) || '').trim()));

  const btn = page.locator('#subapp-viewport button:visible').filter({ hasText: /^(新建|新增|创建|添加)/ }).first();
  console.log('新建按钮:', (await btn.count()) ? (await btn.innerText()) : '(无)');
  await btn.click();
  await page.waitForTimeout(1800);
  const modal = page.locator('.ant-modal:visible, .ant-drawer:visible').first();
  console.log('弹窗:', (await modal.count()) ? '已打开' : '未打开');

  const items = await modal.locator('.ant-form-item').all();
  console.log(`\n=== 弹窗刚打开时，各控件的「初值」（判定 preset 分支是否会误跳过）===`);
  for (const item of items) {
    if (!(await item.isVisible().catch(() => false))) continue;
    const label = (await item.locator('label').first().innerText().catch(() => '')).replace(/[:：*\s]/g, '');
    const selItem = await item.locator('.ant-select-selection-item').count();
    const selItemTxt = selItem
      ? ((await item.locator('.ant-select-selection-item').first().innerText().catch(() => '')) || '').trim()
      : '';
    const inpVal = (await item.locator('input:not([type=hidden])').first().inputValue().catch(() => '')) || '';
    console.log(`  - ${label || '(匿名)'}: .ant-select-selection-item 数=${selItem} 文本=${JSON.stringify(selItemTxt)} input值=${JSON.stringify(inpVal)}`);
  }

  console.log(`\n表单项 ${items.length} 个：`);
  for (const item of items) {
    if (!(await item.isVisible().catch(() => false))) continue;
    const label = (await item.locator('label').first().innerText().catch(() => '')).replace(/[:：*\s]/g, '');
    const hasSelect = await item.locator('.ant-select').count();
    const hasInput = await item.locator('input:not([type=hidden])').count();
    const hasNum = await item.locator('.ant-input-number').count();
    const hasPicker = await item.locator('.ant-picker').count();
    const hasTa = await item.locator('textarea').count();
    const kind = hasSelect ? 'Select' : hasNum ? 'InputNumber' : hasPicker ? 'DatePicker' : hasTa ? 'Textarea' : hasInput ? 'Input' : '(无控件)';

    let optsList = [];
    if (kind === 'Select') {
      // 展开该字段自己的下拉，读它真实提供的选项
      await item.locator('.ant-select').first().click({ timeout: 3000 }).catch(() => {});
      await page.waitForTimeout(500);
      optsList = await page
        .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
        .allInnerTexts()
        .catch(() => []);
      // 收起：点标题，不用 Esc（Esc 会冒泡关弹窗）
      await modal.locator('.ant-modal-title, .ant-modal-body').first().click({ timeout: 2000 }).catch(() => {});
      await page.waitForTimeout(200);
    }
    console.log(`  - ${label || '(匿名)'} [${kind}] 选项=${JSON.stringify(optsList.slice(0, 6))}`);
  }

  // ── 复刻主探针的取值方式：点开 Select → 取「全局第一个可见 option」 ──
  console.log('\n=== 复刻主探针取值（全局 option）后，控件里的实际值 ===');
  for (const item of items) {
    if (!(await item.isVisible().catch(() => false))) continue;
    const label = (await item.locator('label').first().innerText().catch(() => '')).replace(/[:：*\s]/g, '');
    const sel = item.locator('.ant-select').first();
    if (!(await sel.count())) continue;
    await sel.click({ timeout: 3000 }).catch(() => {});
    await page.waitForTimeout(400);
    const globalOpt = page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .first();
    const n = await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').count();
    const txt = (await globalOpt.count()) ? (await globalOpt.innerText().catch(() => '')) : '(无)';
    if (await globalOpt.count()) await globalOpt.click({ timeout: 3000 }).catch(() => {});
    await page.waitForTimeout(300);
    const val = (await sel.locator('.ant-select-selection-item').first().innerText().catch(() => '')) || '';
    console.log(`  - ${label}: 可见下拉容器数=${n} 取到的option=${JSON.stringify(txt.trim())} → 控件值=${JSON.stringify(val.trim())}`);
  }
} catch (e) {
  console.log('DIAG ERROR:', String(e.message).slice(0, 300));
} finally {
  await browser.close();
}
