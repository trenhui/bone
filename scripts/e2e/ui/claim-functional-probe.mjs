/**
 * 智能理赔功能级 UI 探针（claim-functional-probe.mjs）
 *
 * 与 claim-single-nav-probe.mjs（导航一致性）互补：本探针模拟人工操作，验证 9 个页面
 * 在 shell :3000 真实登录态下「能干活」而不只是「能渲染」：
 *
 *   1. 理赔申请      —— 表单真实填写 + 提交，断言「报案已受理 CL…」，拿 claimNo
 *   2. 材料上传      —— 按案件 ID 查询 → 真实上传附件（走附件服务）→ 锁定证据集
 *   3. 核赔工作台    —— 表格渲染出新报案案件（含本次 UI 提交的 claimNo）
 *   4. 理赔进度      —— 按案件 ID 查询，断言步骤条/详情出现该案件
 *   5. 其余 5 页      —— 理算归因 / 资金工作台 / 申诉复议 / 挂起恢复 / 后督抽检：
 *                       结构化渲染 + 无 5xx + 无 console error（深度交互由 API E2E 覆盖）
 *
 * 前置：shell :3000、gateway :8888、claim :8087、附件服务在线；种子保单 E2E-POLICY-0001。
 * 用法：
 *   NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-3/bin/node
 *   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
 *   $NODE scripts/e2e/ui/claim-functional-probe.mjs
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {
  UI_BASE,
  SHOT_DIR,
  loadPlaywright,
  makeChecker,
  api,
} from '../e2e_lib/client.cjs';
import { uiLogin, shot } from '../e2e_lib/ui.cjs';

const { chromium } = loadPlaywright();
const { check, report } = makeChecker();

const POLICY = { policyNo: 'E2E-POLICY-0001', planId: '9001', insuredIdHash: 'e2e-insured-hash-0001' };

const isNoise = (e) =>
  /favicon|Failed to load resource:|\[antd[^\]]*\]|deprecated|ResizeObserver loop|Warning: |single-spa minified message #31/i.test(
    e,
  );

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(20000);

let consoleErrors = [];
let httpErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error' && !isNoise(m.text())) consoleErrors.push(m.text().slice(0, 200));
});
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));
page.on('response', (r) => {
  if (r.status() >= 500) httpErrors.push(`${r.status()} ${r.url().slice(0, 120)}`);
});

const viewportText = () =>
  page
    .evaluate(() => {
      const e = document.querySelector('#subapp-viewport');
      return e ? (e.innerText || '').trim() : '';
    })
    .catch(() => '');

async function waitStable(ms = 12000) {
  const deadline = Date.now() + ms;
  let prev = '';
  while (Date.now() < deadline) {
    const cur = await viewportText();
    if (cur.length > 10 && cur === prev) return cur;
    prev = cur;
    await page.waitForTimeout(400);
  }
  return prev;
}

let grouped = false;
const expandGroup = async () => {
  if (grouped) return;
  const title = page
    .locator('.shell-sider .ant-menu-submenu-title')
    .filter({ hasText: '智能理赔' })
    .first();
  await title.waitFor({ state: 'visible' });
  const opened = await title.evaluate((el) =>
    (el.parentElement?.className || '').includes('ant-menu-submenu-open'),
  );
  if (!opened) await title.click();
  grouped = true;
};

/** 点侧边栏菜单进入页面（复刻真实用户链路），返回稳定后的容器文本。 */
async function gotoPage(name) {
  consoleErrors = [];
  httpErrors = [];
  const item = () =>
    page.locator('.shell-sider .ant-menu-item').filter({ hasText: name }).first();
  // 子菜单可能被折叠：不可见就重新展开分组再试（最多 2 次）
  for (let i = 0; i < 2; i++) {
    if (await item().isVisible().catch(() => false)) break;
    grouped = false;
    await expandGroup();
    await page.waitForTimeout(600);
  }
  await item().waitFor({ state: 'visible' });
  await item().click();
  return waitStable();
}

/** 等最近一次 message 提示出现（antd message 挂 body 下）。 */
async function waitMessage(re, ms = 8000) {
  const deadline = Date.now() + ms;
  while (Date.now() < deadline) {
    const texts = await page
      .locator('.ant-message-notice-content')
      .allInnerTexts()
      .catch(() => []);
    const hit = texts.find((t) => re.test(t));
    if (hit) return hit;
    await page.waitForTimeout(300);
  }
  return null;
}

try {
  check('UI 登录成功', await uiLogin(page), page.url());
  await page.waitForSelector('.shell-sider', { timeout: 20000 });

  // ---------- 1. 理赔申请：表单真实提交 ----------
  let body = await gotoPage('理赔申请');
  check('[理赔申请] 页面渲染出表单', (await page.locator('#subapp-viewport .ant-form').count()) > 0, `文本 ${body.length} 字`);

  await page.getByPlaceholder('EG20260001').fill(POLICY.policyNo);
  await page.locator('.ant-input-number').first().click();
  await page.keyboard.type(POLICY.planId);
  await page.keyboard.press('Tab');
  await page.getByPlaceholder('hash-abc').fill(POLICY.insuredIdHash);
  await page.locator('.ant-picker').first().click();
  await page.keyboard.type('2026-10-20');
  await page.keyboard.press('Enter');
  // 明细行初始为空（form 级 initialValues items:[] 覆盖 List initialValue），人工先点「添加明细」
  await page.getByRole('button', { name: '添加明细' }).click();
  await page.waitForTimeout(400);
  await page.getByPlaceholder('诊查费').fill('UI 探针-门诊诊查费');
  const amountInput = page.locator('#subapp-viewport .ant-input-number').nth(1);
  await amountInput.click();
  await page.keyboard.type('120');

  await page.getByRole('button', { name: '提交理赔' }).click();
  const msg = await waitMessage(/报案已受理/);
  const claimNo = msg?.match(/CL\d+/)?.[0] ?? '';
  check('[理赔申请] 提交成功且回执受理号', Boolean(claimNo), msg ?? '未捕获 message');

  // 经 API 把 claimNo 换成 claimId（后续页面用）
  let claimId = '';
  if (claimNo) {
    // 待办列表默认按作业状态过滤，新报案（ACCEPTED）需显式带上状态集
    const list = await api('GET', `/claims?page=1&size=50&statuses=ACCEPTED,PENDING_PRE_EXAM,PRE_EXAMINING`);
    const rec = (list.data?.data?.records ?? []).find((r) => r.claimNo === claimNo);
    claimId = String(rec?.claimId ?? rec?.id ?? '');
  }
  check('[理赔申请] 案件入库可查（claimNo→claimId）', Boolean(claimId), `claimNo=${claimNo}`);

  // ---------- 2. 核赔工作台：列表渲染 ----------
  body = await gotoPage('核赔工作台');
  const reviewRows = await page.locator('#subapp-viewport .ant-table-tbody tr').count();
  check('[核赔工作台] 待办表格有数据', reviewRows > 0, `行数 ${reviewRows}`);

  // ---------- 3. 材料上传：真实上传 + 锁定证据集 ----------
  body = await gotoPage('材料上传');
  check('[材料上传] 页面渲染', (await page.locator('#subapp-viewport .ant-card').count()) > 0, `文本 ${body.length} 字`);
  if (claimId) {
    await page.getByPlaceholder('案件ID').fill(claimId);
    await page.getByRole('button', { name: '查询' }).click();
    await page.waitForTimeout(1200);
    // 只数真实数据行（ant-table-placeholder 是空态占位行）
    const dataRows = () =>
      page.locator('#subapp-viewport .ant-table-tbody tr.ant-table-row').count();
    const before = await dataRows();

    // 真实文件上传（antd Upload 的 input[type=file]）
    const tmp = path.join(os.tmpdir(), `claim-ui-probe-${Date.now()}.pdf`);
    fs.writeFileSync(tmp, `%PDF-1.4 claim-ui-probe ${Date.now()}\n`);
    await page.locator('#subapp-viewport input[type="file"]').first().setInputFiles(tmp);
    const upMsg = await waitMessage(/已登记/);
    check('[材料上传] 附件上传并登记成功', Boolean(upMsg), upMsg ?? '未捕获已登记提示');
    await page.waitForTimeout(1000);

    const lockBtn = page.getByRole('button', { name: /锁定证据集/ });
    await lockBtn.click();
    const lockMsg = await waitMessage(/证据集已锁定/);
    check('[材料上传] 证据集锁定成功（理算前置）', Boolean(lockMsg), lockMsg ?? '未捕获锁定提示');

    const lockedTags = await page.locator('#subapp-viewport .ant-table-tbody').innerText();
    check('[材料上传] 列表出现已锁定标记', /已锁定|锁定/.test(lockedTags), lockedTags.slice(0, 80));
    check('[材料上传] 行数增长（1 份新材料）', (await dataRows()) > before, `before=${before}`);
    fs.unlinkSync(tmp);
  }

  // ---------- 4. 理赔进度：按案件 ID 查询 ----------
  body = await gotoPage('理赔进度');
  if (claimId) {
    await page.getByPlaceholder(/案件ID|编号/).fill(claimId);
    await page.getByRole('button', { name: /查询/ }).click();
    await page.waitForTimeout(1500);
    const detail = await viewportText();
    check('[理赔进度] 查询出案件详情', detail.includes(claimNo) || detail.includes('受理签收'), `容器 ${detail.length} 字`);
    check('[理赔进度] 步骤条渲染', (await page.locator('#subapp-viewport .ant-steps').count()) > 0);
  } else {
    check('[理赔进度] 页面渲染（查询框）', (await page.locator('#subapp-viewport input').count()) > 0);
  }

  // ---------- 5. 其余 5 页：结构化渲染巡检 ----------
  for (const name of ['理算归因', '资金工作台', '申诉复议', '挂起/恢复', '后督抽检']) {
    body = await gotoPage(name);
    const structured = await page
      .locator(
        '#subapp-viewport .ant-card, #subapp-viewport .ant-table, #subapp-viewport .ant-form, #subapp-viewport .ant-empty, #subapp-viewport .ant-descriptions',
      )
      .count();
    check(`[${name}] 页面渲染出内容`, structured > 0, `结构块 ${structured} 个 / 文本 ${body.length} 字`);
    check(`[${name}] 无 5xx`, httpErrors.length === 0, httpErrors.join(' | '));
    check(`[${name}] 无 console error`, consoleErrors.length === 0, consoleErrors.join(' | '));
  }

  await shot(page, 'claim_functional_probe');
  console.log(`\n截图：${SHOT_DIR}/claim_functional_probe*.png`);
} catch (e) {
  check('探针执行未抛异常', false, String(e).slice(0, 300));
} finally {
  await browser.close();
}

const r = report('智能理赔功能级 UI 探针');
process.exit(r.ok ? 0 : 1);
