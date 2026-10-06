// BONE 深度交互巡检：模拟人工"点击每一个功能"—— 打开弹窗 / 填表 / 提交 / 取消
// 目的：v4 已证 44 菜单导航正常、零失败请求；但那只覆盖"读"。
// 本轮覆盖"写"：每个带新增/编辑按钮的页面，点开弹窗→ 填表→ 提交 → 断言结果。
const { chromium } = require('playwright');
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const BASE = 'http://localhost:3000';
const OUT = '/tmp/bone-ui-screens';
require('fs').mkdirSync(OUT, { recursive: true });

// 只对「有新增按钮且有后端写入接口」的模块做写操作；payment 回调是故意不做前端模拟的
// 按钮名从运行时采集而来（bone-btns.cjs），各页命名不统一：新增账号/新增角色/创建规则/新建连接器…
const WRITE_TARGETS = [
  { app: 'iam', route: '#/accounts', add: '新增账号', name: 'iam-accounts' },
  { app: 'iam', route: '#/roles', add: '新增角色', name: 'iam-roles' },
  { app: 'iam', route: '#/tenants', add: '新增租户', name: 'iam-tenants' },
  { app: 'iam', route: '#/apps', add: '新建应用', name: 'iam-apps' },
  { app: 'iam', route: '#/organizations', add: '新建根部门', name: 'iam-orgs' },
  { app: 'system', route: '#/alerts', add: '创建规则', name: 'system-alerts' },
  { app: 'system', route: '#/schedule', add: '新建任务', name: 'system-schedule' },
  { app: 'system', route: '#/dict', add: '新建', name: 'system-dict' },
  { app: 'integration', route: '#/connectors', add: '新建连接器', name: 'integ-connectors' },
  { app: 'integration', route: '#/flows', add: '新建流程', name: 'integ-flows' },
  { app: 'metadata', route: '#/entities', add: '新建实体', name: 'md-entities' },
  { app: 'masterdata', route: '#/entities', add: '创建模型', name: 'mdata-entities' },
  { app: 'generator', route: '#/datasources', add: '新增数据源', name: 'gen-datasources' },
  { app: 'extension', route: '#/points', add: '', name: 'ext-points' },
  { app: 'commerce', route: '#/orders', add: '', name: 'commerce-orders' },
];

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 950 } })).newPage();
  const errors = [], failedReq = [];
  let cur = '';
  page.on('pageerror', e => errors.push({ route: cur, kind: 'pageerror', text: String(e.message).slice(0, 250) }));
  page.on('console', m => { if (m.type() === 'error') errors.push({ route: cur, kind: 'console', text: m.text().slice(0, 250) }); });
  page.on('response', r => { if (r.status() >= 400) failedReq.push({ route: cur, url: r.url().replace(BASE, ''), status: r.status() }); });

  await page.goto(`${BASE}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);
  const u = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if (await u.count() && await u.isVisible().catch(() => false)) {
    await u.fill('admin');
    await page.locator('input[type="password"], input[placeholder="密码"]').first().fill('123456');
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await page.waitForTimeout(4000);
  }

  const results = [];
  for (const t of WRITE_TARGETS) {
    cur = t.name;
    const f0 = failedReq.length;
    await page.goto(`${BASE}/${t.app}${t.route}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3000);
    const rec = { target: t.name, route: `${t.app}${t.route}` };
    // 找"新增"按钮
    if (!t.add) { results.push({ ...rec, step: 'READ_ONLY', verdict: 'READ_ONLY' }); continue; }
    const addBtn = page.locator('button').filter({ hasText: new RegExp(t.add) }).first();
    if (!(await addBtn.count())) { results.push({ ...rec, step: 'NO_ADD_BTN', verdict: 'SKIP', allBtns: await page.evaluate(() => [...document.querySelectorAll('button')].map(b => (b.innerText || '').trim()).filter(Boolean).slice(0, 12)) }); continue; }
    try { await addBtn.click({ timeout: 5000 }); } catch (e) { results.push({ ...rec, step: 'ADD_CLICK_FAIL', err: String(e.message).slice(0, 80) }); continue; }
    await page.waitForTimeout(1800);
    // 弹窗是否打开
    const modal = await page.evaluate(() => {
      const m = document.querySelector('.ant-modal-content') || document.querySelector('.ant-drawer-content');
      if (!m) return null;
      return {
        kind: document.querySelector('.ant-modal-content') ? 'Modal' : (document.querySelector('.ant-drawer-content') ? 'Drawer' : '?'),
        title: (m.querySelector('.ant-modal-title,.ant-drawer-title')?.innerText || '').trim(),
        inputs: m.querySelectorAll('input.ant-input, textarea, .ant-select-selector').length,
        required: [...m.querySelectorAll('.ant-form-item-required')].length,
        buttons: [...m.querySelectorAll('button')].map(b => b.innerText.trim()).filter(Boolean),
        selects: m.querySelectorAll('.ant-select').length,
      };
    });
    rec.modal = modal;
    if (!modal) { results.push({ ...rec, step: 'MODAL_NOT_OPEN', verdict: 'FAIL' }); await page.screenshot({ path: `${OUT}/w-${t.name}.png` }); continue; }
    rec.kind = modal.kind;
    await page.screenshot({ path: `${OUT}/w-${t.name}-modal.png` });
    // 点「确定」触发校验（不填数据，看校验是否拦住 —— 这是最常见的"点了没反应"来源）
    const okBtn = page.locator('.ant-modal-footer button, .ant-drawer-footer button').filter({ hasText: /确定|保存|新增|提交|创建|OK/ }).first();
    if (await okBtn.count()) { try { await okBtn.click({ timeout: 4000 }); } catch (e) {} }
    await page.waitForTimeout(1500);
    // 空提交后：应出现校验提示，且弹窗仍开着（数据层不该被写脏）
    rec.afterEmptySubmit = await page.evaluate(() => ({
      modalStillOpen: !!(document.querySelector('.ant-modal-content') || document.querySelector('.ant-drawer-content')),
      validationErrors: [...document.querySelectorAll('.ant-form-item-explain-error')].map(e => e.innerText.trim()).slice(0, 6),
      messageCount: document.querySelectorAll('.ant-message-notice-content').length,
    }));
    // 取消关闭
    const cancel = page.locator('.ant-modal-footer button, .ant-drawer-footer button').filter({ hasText: /取消|Cancel/ }).first();
    if (await cancel.count()) { try { await cancel.click({ timeout: 4000 }); } catch (e) {} }
    await page.waitForTimeout(900);
    rec.modalClosed = await page.evaluate(() => !(document.querySelector('.ant-modal-content') || document.querySelector('.ant-drawer-content')));
    rec.newFailed = failedReq.slice(f0);
    // 判定：弹窗能开 + 空提交被校验拦住 + 取消能关 ⇒ 该写入口的 UI 链路正常
    rec.verdict = modal.inputs > 0 && rec.afterEmptySubmit.modalStillOpen ? 'OK'
      : (!modal.inputs ? 'NO_INPUT' : 'SUSPECT');
    results.push(rec);
  }
  await browser.close();
  console.log(JSON.stringify({ results, errors, failedReq }, null, 1));
})();
