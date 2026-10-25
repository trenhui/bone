/**
 * 定向回归探针：业务数据（runtime）「材料缺失不阻塞提交」。
 *
 * <p><b>背景缺陷</b>：理赔样例联动规则 LINK-EVID-01 写成
 * {@code when evidences isNull ⇒ then evidences required} 的自引用死锁；
 * evidences 是 ATTACHMENT 控件（值归关系端点管、主记录无物理列），前端却对它强制必填
 * ⇒ 红色横幅「【必填】evidences 不能为空」、弹窗关不掉，而<b>后端本就放行</b>
 * （UiSchemaRuleEnforcer#physicallyBackedFields 与 RuntimeWriteValidator 只遍历物理列）。
 *
 * <p><b>修复</b>：ui-schema 引擎新增 enforceableFieldCodes 收窄必填强制域，
 * ATTACHMENT/CHILD_TABLE 不参与必填（与后端同口径）。
 *
 * <p><b>判据</b>（三条全中才算修好）：
 *   1. 提交后<b>不出现</b>「evidences 不能为空」红字（修复的核心）；
 *   2. 弹窗关闭或出现成功 toast（闭环真的走通）；
 *   3. 列表区文本发生变化（记录真的落库，而非静默失败）。
 *
 * 用法：node scripts/e2e/ui-runtime-evidences-regression.mjs
 */
import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
const API = process.env.BONE_E2E_API || 'http://127.0.0.1:8888/api/v1';
const SHOT_DIR = '/Users/renhui.trh/wps/bone/logs';
const STAMP = String(Date.now()).slice(-6);
const E2E_USER = process.env.BONE_E2E_USER || 'admin';
const E2E_PWD = process.env.BONE_E2E_PWD || '123456';

let API_TOKEN = null;
const failures = [];
const check = (name, ok, detail) => {
  console.log(`${ok ? '✓' : '✗'} ${name}${ok ? '' : `  ← ${detail || ''}`}`);
  if (!ok) failures.push(`${name}${detail ? `（${detail}）` : ''}`);
};
const shot = async (page, tag) => {
  await page.screenshot({ path: `${SHOT_DIR}/evid__${tag}.png`, fullPage: false }).catch(() => {});
};

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.on('dialog', (d) => d.accept().catch(() => {}));

// 收集 5xx 与业务异常 toast，用于把「服务端故障」与「前端拦截」区分开
const bad = [];
const failedWrites = [];
page.on('response', async (r) => {
  if (r.status() >= 500) bad.push(`${r.status()} ${r.url().slice(0, 120)}`);
  // 抓 runtime 写端点（含动作式 :create）的失败响应体，拿到后端真正的拒绝原因
  const u = r.url();
  if (r.request().method() !== 'GET' && /\/runtime\/entities\/[^/]+\/records/.test(u) && r.status() >= 400) {
    try {
      const body = await r.text();
      failedWrites.push(`${r.status()} ${u.slice(0, 110)} :: ${body.slice(0, 300)}`);
    } catch {
      failedWrites.push(`${r.status()} ${u.slice(0, 110)} :: <body unreadable>`);
    }
  }
});

try {
  // ---------- 登录 ----------
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"]', { timeout: 20000 });
  const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
  if (await page.locator(userSel).first().count()) await page.locator(userSel).first().fill(E2E_USER);
  const pwd = page.locator('input[type="password"]').first();
  await pwd.fill(E2E_PWD);
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
  check('UI 登录成功', logged, '登录表单未消失');
  if (!logged) throw new Error('登录失败');

  // ---------- 取菜单真源，定位「业务数据」 ----------
  const lr = await fetch(`${API}/iam/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: E2E_USER, password: E2E_PWD }),
  });
  API_TOKEN = (await lr.json())?.data?.token;
  const mr = await fetch(`${API}/iam/menus/tree`, {
    headers: { Authorization: `Bearer ${API_TOKEN}`, 'X-Tenant-Id': '0' },
  });
  const mj = await mr.json();
  const menus = [];
  const walk = (ns, parent) => {
    for (const n of ns || []) {
      if (n.path && n.path.includes('#')) menus.push({ name: `${parent ? parent + ' · ' : ''}${n.name}`, path: n.path });
      walk(n.children || [], n.name);
    }
  };
  walk(mj.data || [], '');
  const target = menus.find((m) => /业务数据|runtime/i.test(m.name));
  check('找到「业务数据」菜单', !!target, `菜单树 ${menus.length} 个节点中未命中`);
  if (!target) throw new Error('未找到业务数据菜单');

  // ---------- 进入页面 ----------
  await page.goto(`${BASE}${target.path}`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2500);
  const bodyLen = (await page.locator('#subapp-viewport').innerText().catch(() => '')) || '';
  check('业务数据列表页渲染', bodyLen.length > 20, 'qiankun 容器未渲染（白屏）');
  if (bodyLen.length <= 20) throw new Error('页面白屏');

  // 「新建记录」在选实体前 disabled，先选一个已发布实体
  const entSel = page.locator('#subapp-viewport .ant-select').first();
  if (await entSel.count()) {
    await entSel.click({ timeout: 3000 }).catch(() => {});
    await page.waitForTimeout(600);
    const opt = page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .first();
    if (await opt.count()) {
      await opt.click({ timeout: 3000 }).catch(() => {});
      await page.waitForTimeout(1800);
    } else {
      await page.locator('.ant-modal-title, .ant-drawer-title, .ant-modal-body').first().click({ timeout: 2000 }).catch(() => {});
    }
  }

  const btn = page.locator('#subapp-viewport button:visible').filter({ hasText: /^(新建|新增|创建|添加)/ }).first();
  check('「新建」按钮可用', await btn.count() > 0, '选实体后仍未出现新建按钮');
  if (!(await btn.count())) throw new Error('无新建按钮');

  const listBefore = await page.locator('#subapp-viewport').innerText();
  await btn.click();
  await page.waitForTimeout(1800);
  const modal = page.locator('.ant-modal:visible, .ant-drawer:visible').first();
  check('点「新建」打开表单', await modal.count() > 0, '未出现弹窗/抽屉');
  if (!(await modal.count())) throw new Error('未打开表单');

  // ---------- 自动填表（与全模块探针同口径；evidences 附件项会被跳过，这正是缺陷场景） ----------
  const filled = [];
  const skipped = [];
  let items = await modal.locator('.ant-form-item').all();
  for (const item of items) {
    try {
      if (!(await item.isVisible().catch(() => false))) continue;
      const label = ((await item.locator('label').first().innerText().catch(() => '')) || '').replace(/[:：*\s]/g, '');
      const ph = (await item.locator('input,textarea').first().getAttribute('placeholder').catch(() => '')) || '';
      const sem = (label + ' ' + ph).toLowerCase();

      // 附件/上传类控件：无真实文件可传，跳过（用户手工也要先准备材料）
      if (/附件|上传|材料|证据|evidence|attach/.test(sem)) {
        skipped.push(label || ph || 'attachment');
        continue;
      }
      // 日期类： antd DatePicker 走专用输入
      if (/日期|时间|date/.test(sem)) {
        const inp = item.locator('input').first();
        if (await inp.count()) {
          await inp.fill('2026-10-25').catch(() => {});
          filled.push(label || ph || 'date');
        }
        continue;
      }
      // 下拉
      const sel = item.locator('.ant-select').first();
      if (await sel.count()) {
        await sel.click({ timeout: 2500 }).catch(() => {});
        await page.waitForTimeout(400);
        const opt = page
          .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
          .first();
        if (await opt.count()) {
          await opt.click({ timeout: 2500 }).catch(() => {});
          filled.push(label || ph || 'select');
        } else {
          skipped.push(label || ph || 'select-empty');
          await page.keyboard.press('Escape').catch(() => {});
        }
        continue;
      }
      // 文本/数字
      const inp = item.locator('input:not([type=hidden]), textarea').first();
      if (await inp.count()) {
        const preset = await inp.inputValue().catch(() => '');
        if (preset) {
          filled.push(`${label || ph || 'field'}(预设)`);
          continue;
        }
        let v = `E2E${STAMP}`;
        if (/保单|单号|编号|号码/.test(sem)) v = `P${STAMP}`;
        else if (/金额|价格|额度/.test(sem)) v = '900.00';
        else if (/哈希|指纹/.test(sem)) v = `h${STAMP}`;
        else if (/标记|标签|tags/.test(sem)) v = `flag_${STAMP}`;
        else if (/描述|remark|说明|trace/.test(sem)) v = `E2E 回归 ${STAMP}`;
        await inp.fill(v).catch(() => {});
        filled.push(label || ph || 'text');
      }
    } catch {
      /* 单个控件失败不阻断整体 */
    }
  }
  console.log(`  ↳ 填值 ${filled.length} 项：${filled.join(' | ')}`);
  console.log(`  ↳ 跳过 ${skipped.length} 项：${skipped.join(' | ')}`);

  // ---------- 提交 ----------
  // ★ antd 5 会给两个汉字的 Button 注入空格，「提交」实测渲染成「提 交」⇒ 必须用 \s* 匹配
  const submitBtn = modal
    .locator('button:visible')
    .filter({ hasText: /^(确\s*定|确\s*认|保\s*存|提\s*交|创\s*建|新\s*增|OK|Save)/ })
    .first();
  check('找到提交按钮', await submitBtn.count() > 0, '弹窗内无提交按钮');
  if (!(await submitBtn.count())) throw new Error('无提交按钮');
  await submitBtn.click();
  // 提交后立刻抓状态：antd message 约 3s 自动消失，轮询太久再读 toast 会读到空（假红）
  const early = [];
  for (let i = 0; i < 6; i++) {
    await page.waitForTimeout(400);
    const t = await page.locator('.ant-message, .ant-notification').innerText().catch(() => '');
    const open = await modal.isVisible().catch(() => false);
    early.push(`t+${(i + 1) * 400}ms toast="${(t || '').replace(/\s+/g, ' ').slice(0, 40)}" modalOpen=${open ? 1 : 0}`);
    if (!open && t) break;
  }
  console.log(`  ↳ 提交后状态：${early.join(' | ')}`);
  const toast = await page.locator('.ant-message, .ant-notification').innerText().catch(() => '');
  await page.waitForTimeout(1000);

  // ---------- 判据 ----------
  const modalText = (await modal.innerText().catch(() => '')) || '';
  const viewportText = (await page.locator('#subapp-viewport').innerText().catch(() => '')) || '';
  const allText = modalText + '\n' + viewportText;

  const evidError = /【必填】\s*evidences\s*不能为空/.test(allText);
  check('①不出现「evidences 不能为空」必填拦截', !evidError, evidError ? '红色必填横幅仍在（收窄未生效）' : '');

  const stillOpen = await modal.isVisible().catch(() => false);
  const successToast = /成功|已创建|已保存|提交成功/.test(viewportText + ' ' + toast);
  check('②弹窗关闭或出现成功提示', !stillOpen || successToast, `弹窗仍开=${stillOpen ? 1 : 0} toast=${(toast || '').replace(/\s+/g, ' ').slice(0, 80)}`);

  const listAfter = await page.locator('#subapp-viewport').innerText().catch(() => '');
  check('③列表区文本发生变化（记录已落库）', listAfter !== listBefore, '列表文本无变化');

  check('④提交过程无 5xx', bad.length === 0, bad.slice(0, 3).join('; '));
  if (failedWrites.length) {
    console.log('  ↳ 后端拒绝明细：');
    for (const w of failedWrites.slice(0, 4)) console.log(`      ${w}`);
  }

  await shot(page, evidError ? 'fail' : 'pass');
} catch (err) {
  check('探针执行', false, String(err?.message || err));
  await shot(page, 'error').catch(() => {});
} finally {
  await browser.close();
}

console.log(`\n定向回归结果：${failures.length === 0 ? '全部通过' : `${failures.length} 项失败`}`);
if (failures.length) {
  console.log('失败项：');
  for (const f of failures) console.log(`  - ${f}`);
}
process.exit(failures.length === 0 ? 0 : 1);
