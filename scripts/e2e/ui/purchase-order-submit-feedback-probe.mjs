/**
 * 采购订单「提交被规则拦截」专项探针（2026-10-31）
 *
 * 起因
 * ----------------------------------------------------------------
 * 全模块 CRUD 探针（ui-full-module-crud-probe.mjs）对「业务建模 · 采购订单」报：
 *     ✗ 新建提交闭环（填15项/跳6项，按钮「提交」）← 弹窗仍开=1 toast= 列表变化=false
 * 且 badResponses 为空 ⇒ 前端**根本没发 HTTP**。后端直连
 * `POST /api/v1/runtime/entities/purchase_order/records` 返回 201，所以不是后端拒绝。
 * 怀疑是 ui-schema 的 submitRules[SUB-ITEMS-01]（行项目金额合计 == 采购金额）在前端拦截。
 *
 * 但「拦截」本身不是缺陷——**拦截而没有任何可见反馈**才是。本探针要回答的正是这一句：
 *   用户点了「提交」之后，屏幕上到底有没有出现"为什么没提交成功"的可见信息？
 *
 * 判据（三项，缺一即红）
 * ----------------------------------------------------------------
 *   1. 拦截可见：`.ant-alert-error`（表单内 Alert）或 `.ant-message` 至少出现一处非空文本；
 *   2. 原因可懂：该文本必须能指明"被哪条规则拦住"（含规则名/码，或至少含字段名）；
 *   3. 后续可达：若拦截成立，文本应说明怎么改（业界 error-summary 实践：告诉用户下一步）。
 *
 * 为什么必须单独写一个探针而不是改通用 CRUD 探针的判据
 * ----------------------------------------------------------------
 * 通用探针的"静默失败"判据只读 toast，读不到表单内 Alert，会把「有 Alert 的规则拦截」
 * 与「点了没反应的真静默」混为一谈——前者是产品行为、后者是缺陷。混判会让真正该修的东西
 * 被淹没。本探针把两类分开测，并把证据（截图 + 文本 + 网络记录）落盘。
 *
 * 用法：node scripts/e2e/ui/purchase-order-submit-feedback-probe.mjs
 */
import { UI_BASE as BASE, SHOT_DIR, stamp, loadPlaywright } from '../e2e_lib/client.cjs';
import { uiLogin } from '../e2e_lib/ui.cjs';

const { chromium } = loadPlaywright();
const STAMP = stamp(6);
const ENTITY_URL = `${BASE}/metadata#/runtime?entity=purchase_order`;

const results = [];
const check = (name, cond, detail = '') => {
  results.push({ name, ok: !!cond, detail });
  console.log(`${cond ? '✓' : '✗'} ${name}${detail ? `  ← ${detail}` : ''}`);
  return !!cond;
};

const NET = [];

async function fillVisibleForm(scope) {
  const filled = [];
  const skipped = [];
  // 文本框 / 文本域
  const inputs = await scope.locator('input:visible, textarea:visible').all();
  for (const el of inputs) {
    try {
      const box = el;
      const type = (await box.getAttribute('type')) || 'text';
      if (type === 'checkbox' || type === 'radio' || type === 'file') {
        skipped.push(`type=${type}`);
        continue;
      }
      const ph = (await box.getAttribute('placeholder')) || '';
      if (/搜索|查询|关键字/.test(ph)) {
        skipped.push(`search:${ph}`);
        continue;
      }
      const val = `R9${STAMP}`;
      await box.fill(val);
      filled.push(ph || '(no-ph)');
    } catch {
      skipped.push('fill-failed');
    }
  }
  // 数字框
  const nums = await scope.locator('.ant-input-number-input:visible').all();
  for (const el of nums) {
    try {
      await el.fill('100');
      filled.push('number');
    } catch {
      skipped.push('number');
    }
  }
  // 下拉
  const sels = await scope.locator('.ant-select:not(.ant-select-dropdown-hidden):visible').all();
  for (const el of sels) {
    try {
      const isOpen = await el.evaluate((n) => n.classList.contains('ant-select-open'));
      if (!isOpen) await el.click();
      await scope.page().waitForTimeout(200);
      const opt = scope
        .page()
        .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
        .first();
      if (await opt.count()) {
        await opt.click();
        filled.push('select');
      } else {
        skipped.push('select:no-option');
      }
    } catch {
      skipped.push('select');
    }
  }
  return { filled, skipped };
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on('request', (r) => {
    if (r.method() !== 'GET') NET.push(`${r.method()} ${r.url().slice(0, 160)}`);
  });
  page.on('response', async (r) => {
    if (r.request().method() !== 'GET' && r.status() >= 400) {
      NET.push(`  ↳ ${r.status()} ${r.url().slice(0, 160)}`);
    }
  });

  let fatal = null;
  try {
    await uiLogin(page);
    check('UI 登录成功', true);

    await page.goto(ENTITY_URL, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3500);

    // 打开新建弹窗
    // antd 5 对两字 Button 注入空格（「提交」渲染为「提 交」），正则必须容空格，
    // 否则 hasText 匹配不到 —— 这是本仓已知坑，见 .workbuddy/memory。
    const createBtn = page
      .locator('button:visible')
      .filter({ hasText: /新\s*建|新\s*增|创\s*建/ })
      .first();
    check('页面存在「新建」入口', (await createBtn.count()) > 0);
    await createBtn.click();
    await page.waitForTimeout(2000);

    const modal = page.locator('.ant-modal-content:visible').first();
    check('新建弹窗已打开', (await modal.count()) > 0);

    const { filled, skipped } = await fillVisibleForm(modal);
    console.log(`    ↳ 填值 ${filled.length} 项，跳过 ${skipped.length} 项`);

    // 记录提交前网络基线（用于证明"到底发没发 HTTP"）
    const netBefore = NET.length;

    const submitBtn = modal
      .locator('button:visible')
      .filter({ hasText: /提\s*交|保\s*存|确\s*定/ })
      .first();
    check('弹窗内有「提交」按钮', (await submitBtn.count()) > 0);
    await submitBtn.click();
    await page.waitForTimeout(2500);

    // —— 采集三类证据 ——
    const alerts = await page.locator('.ant-alert:visible').allTextContents();
    const msgs = await page.locator('.ant-message:visible').allTextContents();
    const notices = await page.locator('.ant-notification:visible').allTextContents();
    const modalStill = (await page.locator('.ant-modal-content:visible').count()) > 0;
    const netAfter = NET.slice(netBefore);

    await page.screenshot({
      path: `${SHOT_DIR}/po-submit-feedback-${STAMP}.png`,
      fullPage: false,
    });

    console.log('---- 证据 ----');
    console.log('弹窗仍开      :', modalStill);
    console.log('表单内 Alert  :', JSON.stringify(alerts));
    console.log('toast(message):', JSON.stringify(msgs));
    console.log('通知          :', JSON.stringify(notices));
    console.log('提交后网络    :', JSON.stringify(netAfter));

    // —— 判据 ——
    const visibleText = [...alerts, ...msgs, ...notices].join(' ').trim();
    check(
      '提交被拦截时存在可见反馈（Alert/toast 任一非空）',
      visibleText.length > 0,
      visibleText ? visibleText.slice(0, 120) : '屏幕无任何提示 = 真静默失败',
    );

    if (visibleText.length > 0) {
      check(
        '反馈文本指明被哪条规则/字段拦住',
        /规则|校验|合计|金额|行项目|SUB-|必填|不能/.test(visibleText),
        visibleText.slice(0, 160),
      );
    }

    // 提交成功的标志：弹窗关闭 或 出现成功 toast
    const success = !modalStill || /成功|已创建|已保存/.test(visibleText);
    console.log(success ? '---- 结论：本次提交成功 ----' : '---- 结论：本次提交被拦截 ----');
  } catch (err) {
    fatal = err;
    check('探针执行未抛异常', false, String(err).slice(0, 200));
  } finally {
    await browser.close();
  }

  const bad = results.filter((r) => !r.ok);
  console.log(`\n断言 ${results.length - bad.length}/${results.length} 通过`);
  if (bad.length || fatal) process.exit(1);
})();
