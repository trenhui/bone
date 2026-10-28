/**
 * 全链路 UI 探针（shell :3000 真实登录，模拟真人从应用→模块→模型→界面→数据的完整旅程）：
 *
 *  ① 应用管理（bone-iam-app /iam#/apps）        —— API 脚本新建的应用在 IAM 管理页可见
 *  ② 建模工作台（metadata#/apps）               —— 应用卡片可见，可进模块
 *  ③ 模块管理（/apps/:appId/modules）           —— 新建模块可见
 *  ④ 业务对象清单（…/entities）                 —— 实体可见且状态「已发布」
 *  ⑤ 实体详情·结构 Tab                          —— 8 个字段全部列出
 *  ⑥ 实体详情·界面设计器 Tab                     —— 契约列表 6 列 + 表单 2 分区真实渲染
 *  ⑦ 实体详情·数据 Tab                           —— **真人填协议表单（BoneDynamicForm）新建记录**
 *       → 列表出现该行 → 列头恰为契约 6 列（界面设计→列表展示闭环）
 *       → 详情抽屉回看录入值 → 刷新后仍在（落库）
 *  ⑧ API 交叉核验                              —— 界面录入的记录经 API 回读，值逐项一致
 *      （证明表单 → runtime → MySQL 的回路，而非只写进前端内存）
 *
 * 前置：先跑 scripts/e2e/app-to-data-full-chain-e2e.py（生成 /tmp/bone_e2e_fullchain_state.json）。
 *
 * 用法：node scripts/e2e/ui/ui-app-to-data-probe.mjs
 * 依赖：playwright（经共享 client 的 loadPlaywright 加载，见 e2e_lib/client.cjs）
 */
import fs from 'node:fs';
/* 共享 client：playwright 加载 / SHOT_DIR / API 登录与带 token fetch / 断言收集（见 e2e_lib/client.cjs） */
import {
  UI_BASE as BASE,
  SHOT_DIR,
  stamp,
  loadPlaywright,
  api,
  login,
  makeChecker,
} from '../e2e_lib/client.cjs';
/* 共享 UI 交互层：浏览器登录/截图/就绪判定（禁止在探针内重抄登录选择器，见 e2e_lib/ui.cjs） */
import { uiLogin } from '../e2e_lib/ui.cjs';
/* 生产化测试数据：巡检人/日期/设备名与 Python fixtures 同口径（见 e2e_lib/fixtures.cjs） */
import { dataset, EQUIPMENT } from '../e2e_lib/fixtures.cjs';
const { chromium } = loadPlaywright();

const STATE = JSON.parse(fs.readFileSync('/tmp/bone_e2e_fullchain_state.json', 'utf8'));
const STAMP = stamp(6);
const EQ_NO = `UI-EQ-${STAMP}`;
// 生产化测试数据（口径真源 e2e_lib/fixtures.py）：每个值只生成一次存变量，
// 表单录入与断言比对共用同一变量（README §3 铁律——两处各抽一次 rng 必然对不上）
const ds = dataset(STAMP);
// 设备名称与表单「设备类型=数控机床」同源：取词表 CNC（数控加工中心）词条，+ 运行后缀可检索
const EQ_NAME = `${EQUIPMENT.find((e) => e[1] === 'CNC')[2]} ${STAMP}`;
const INSP_DATE = ds.days_ago(11); // 巡检日期：运行日倒推（数据老化，不再写死）
const INSPECTOR = ds.person(); // 巡检人：受控词表人名（不用「界面测试员」随手名）

const { results, check } = makeChecker();
const shot = async (n) => {
  const p = `${SHOT_DIR}/ui_fullchain_${n}.png`;
  await page.screenshot({ path: p, fullPage: false }).catch(() => {});
  return p;
};

/* ---------------- API 辅助（登录与带 token fetch 见 e2e_lib/client.cjs） ---------------- */

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.setDefaultTimeout(25000);
const consoleErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200));
});
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));
let badResponses = [];
page.on('response', async (r) => {
  if (!r.url().includes('/api/')) return;
  if (r.status() >= 400) {
    let body = '';
    try { body = (await r.text()).slice(0, 200); } catch {}
    badResponses.push({ url: r.url().replace(/^https?:\/\/[^/]+/, ''), status: r.status(), body });
  }
});

/* ---------------- UI 辅助 ---------------- */
const viewportText = () =>
  page.evaluate(() => {
    const e = document.querySelector('#subapp-viewport');
    return e ? (e.innerText || '').trim() : '';
  });

/** 等微应用内容稳定（连续两次采样一致，防 qiankun 挂载抢跑点到上一页残留 DOM） */
async function waitForStable(ms = 12000) {
  const deadline = Date.now() + ms;
  let prev = '';
  while (Date.now() < deadline) {
    const cur = await viewportText().catch(() => '');
    if (cur && cur === prev) return cur;
    prev = cur;
    await page.waitForTimeout(500);
  }
  return prev;
}

async function gotoSubapp(hashPath, expectText) {
  await page.goto(`${BASE}${hashPath}`, { waitUntil: 'domcontentloaded' });
  const txt = await waitForStable();
  if (expectText && !txt.includes(expectText)) {
    // 子应用可能仍在拉数据：再给一轮稳定采样
    await page.waitForTimeout(1500);
    return await waitForStable();
  }
  return txt;
}

/** 关闭新人引导遮罩（首屏 .tour-card 吞点击） */
async function dismissTour() {
  const skip = page.locator('button, a').filter({ hasText: '跳过引导' }).first();
  if (await skip.count()) {
    await skip.click({ timeout: 4000 }).catch(() => {});
    await page.waitForTimeout(400);
  }
}

/** 按标签定位表单项并填值（BoneDynamicForm：label=displayName，extra=code·type） */
async function fillFormItem(modal, labelText, kind, value) {
  const item = modal.locator('.ant-form-item').filter({ hasText: labelText }).last();
  if (!(await item.count())) throw new Error(`表单项未找到：${labelText}`);
  if (kind === 'select') {
    await item.locator('.ant-select').first().click();
    await page.waitForTimeout(400);
    const opt = page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .filter({ hasText: value })
      .first();
    if (!(await opt.count())) throw new Error(`下拉选项未找到：${value}`);
    await opt.click();
    await page.waitForTimeout(200);
  } else if (kind === 'date') {
    const inp = item.locator('.ant-picker input').first();
    await inp.click();
    await page.keyboard.type(value);
    await page.keyboard.press('Enter');
    await page.waitForTimeout(300);
  } else if (kind === 'number') {
    await item.locator('.ant-input-number input').first().fill(value);
  } else if (kind === 'textarea') {
    await item.locator('textarea').first().fill(value);
  } else {
    await item.locator('input').first().fill(value);
  }
}

/** 点击实体详情外层 Tab 并等 URL ?tab= 落地（antd Tabs onChange 走 setSearchParams） */
async function clickOuterTab(key, label) {
  const tab = page.getByRole('tab').filter({ hasText: label }).first();
  await tab.click({ timeout: 15000 });
  for (let i = 0; i < 30; i++) {
    const url = page.url();
    if (url.includes(`tab=${key}`)) return true;
    await page.waitForTimeout(400);
  }
  return false;
}

/* ================= 主流程 ================= */
try {
  check('UI 登录成功', await uiLogin(page), `url=${page.url()}`);
  const token = await login();

  /* ① 应用管理（IAM）：API 新建的应用在管理页可见 */
  const t1 = await gotoSubapp('/iam#/apps', STATE.appName);
  check('① 应用管理页可见新建应用', t1.includes(STATE.appName), t1.slice(0, 160));
  await shot('1_iam_apps');

  /* ② 建模工作台：右栏「我的应用」折叠区可见应用卡片（含模块计数） */
  const t2 = await gotoSubapp('/metadata#/apps');
  let t2b = '';
  for (let i = 0; i < 20; i++) {
    const header = page.locator('.ant-collapse-header').filter({ hasText: '我的应用' }).first();
    if (await header.count()) {
      await header.click({ timeout: 5000 }).catch(() => {});
      await page.waitForTimeout(600);
    }
    t2b = await waitForStable(4000);
    if (t2b.includes(STATE.appName)) break;
    await page.waitForTimeout(800);
  }
  check('② 建模工作台「我的应用」可见新建应用', t2b.includes(STATE.appName), t2b.slice(0, 200));
  check('② 应用卡片模块计数=1（模块添加生效）', /1\s*模块/.test(t2b), t2b.slice(0, 300));

  /* ③ 模块管理：新建模块可见 */
  const t3 = await gotoSubapp(`/metadata#/apps/${STATE.appId}/modules`, STATE.moduleName);
  check('③ 模块管理页可见新建模块', t3.includes(STATE.moduleName), t3.slice(0, 160));
  await shot('3_modules');

  /* ④ 业务对象清单：实体可见且已发布 */
  const t4 = await gotoSubapp(
    `/metadata#/apps/${STATE.appId}/modules/${STATE.moduleId}/entities`, STATE.entityName);
  // 列表按创建时间倒序且分页：实体不必然在首屏（本模块已有 18+ 个对象）。
  // 用搜索框按名称收敛——真实用户在对象堆里找自己的实体也是如此，
  // 避免「首屏位置假设」导致的分页假红（后端 moduleId+keyword 组合过滤已实测）。
  const searchBox = page.locator('#subapp-viewport input[placeholder*="搜索对象"]').first();
  await searchBox.fill(STATE.entityName);
  await page.keyboard.press('Enter');
  await page.waitForTimeout(1500);
  let t4q = await waitForStable();
  if (!t4q.includes(STATE.entityName)) {
    // Enter 未触发 onSearch 时点搜索按钮兜底
    await page.locator('#subapp-viewport .ant-input-search-button').first().click().catch(() => {});
    await page.waitForTimeout(1500);
    t4q = await waitForStable();
  }
  check('④ 实体清单可见新实体', t4q.includes(STATE.entityName), t4q.slice(0, 160));
  check('④ 实体状态=已发布', t4q.includes('已发布'), t4q.slice(0, 200));
  await shot('4_entities');

  /* ⑤ 实体详情·结构 Tab：点行内「打开」进对象工作区，8 字段全部列出 */
  const entRow = page.locator('#subapp-viewport .ant-table-row').filter({ hasText: STATE.entityName }).first();
  await entRow.locator('button').filter({ hasText: '打开' }).first().click({ timeout: 10000 });
  await page.waitForTimeout(3000);
  await dismissTour();
  await page.waitForSelector('.ant-tabs-tab', { timeout: 20000 });
  let t5 = await waitForStable();
  check('⑤ 实体详情已打开（URL 含实体 ID）', page.url().includes(String(STATE.entityId)), page.url());
  const fieldLabels = STATE.fields.map((f) => f.label);
  const missingLabels = fieldLabels.filter((l) => !t5.includes(l));
  check('⑤ 结构 Tab：8 个字段全部列出', missingLabels.length === 0, `缺=${missingLabels.join(',')} text=${t5.slice(0, 120)}`);
  await shot('5_structure');

  /* ⑥ 界面设计器：契约 6 列 + 表单 2 分区真实渲染 */
  check('⑥ 切换到界面设计器 Tab', await clickOuterTab('uischema', '界面设计器'), page.url());
  await page.waitForSelector('.uis-root', { timeout: 25000 });
  await dismissTour();
  const t6 = await waitForStable(15000);
  const contractCols = ['设备编号', '设备名称', '设备类型', '巡检日期', '巡检人', '巡检结果'];
  const missingCols = contractCols.filter((c) => !t6.includes(c));
  check('⑥ 设计器预览：契约列表 6 列全部渲染', missingCols.length === 0, `缺=${missingCols.join(',')}`);
  check('⑥ 设计器预览：表单分区标题渲染（设备信息/巡检情况）',
    t6.includes('设备信息') && t6.includes('巡检情况'), t6.slice(0, 200));
  await shot('6_designer');

  /* ⑦ 数据 Tab：API 预置 3 条记录可见 → 真人填协议表单新建第 4 条 */
  check('⑦ 切换到数据 Tab', await clickOuterTab('data', '数据'), page.url());
  await page.waitForTimeout(1500);
  let t7 = await waitForStable();
  const apiRecs = STATE.records.map((r) => r.equipment_no);
  const missingRecs = apiRecs.filter((c) => !t7.includes(c));
  check('⑦ 数据 Tab 可见 API 预置的 3 条记录', missingRecs.length === 0,
    `缺=${missingRecs.join(',')} text=${t7.slice(0, 200)}`);

  const newBtn = page.locator('#subapp-viewport button').filter({ hasText: '新建记录' }).first();
  await newBtn.waitFor({ state: 'visible', timeout: 15000 });
  await newBtn.click();
  const createModal = page.locator('.ant-modal').filter({ hasText: '新建记录' }).last();
  await createModal.waitFor({ state: 'visible', timeout: 15000 });
  await page.waitForTimeout(800);

  await fillFormItem(createModal, '设备编号', 'text', EQ_NO);
  await fillFormItem(createModal, '设备名称', 'text', EQ_NAME);
  await fillFormItem(createModal, '设备类型', 'select', '数控机床');
  await fillFormItem(createModal, '巡检日期', 'date', INSP_DATE);
  await fillFormItem(createModal, '巡检人', 'text', INSPECTOR);
  await fillFormItem(createModal, '巡检结果', 'select', '正常');
  await fillFormItem(createModal, '巡检工时', 'number', '45');
  await fillFormItem(createModal, '异常描述', 'textarea', '端到端验证：经界面设计器协议表单录入');
  await shot('7_form_filled');

  // antd 5 对「恰好两个汉字」的 Button 会自动注入空格（two-CN-char spacing），
  // innerText 实为「提 交」⇒ hasText 必须用正则容忍空白，精确字符串会匹配不到。
  await createModal.locator('button').filter({ hasText: /提\s*交/ }).first().click();
  // 提交成功 → 「创建成功」toast + 弹窗关闭 + 列表刷新
  let modalClosed = false;
  for (let i = 0; i < 24; i++) {
    await page.waitForTimeout(500);
    if ((await createModal.count()) === 0 || !(await createModal.isVisible().catch(() => false))) { modalClosed = true; break; }
  }
  check('⑦ 协议表单提交后弹窗关闭', modalClosed, 'modal 仍打开');
  await page.waitForTimeout(2500);
  const t7b = await waitForStable();
  check('⑦ 列表出现新录入记录', t7b.includes(EQ_NO), t7b.slice(0, 200));
  // 限定可见表格行：antd Tabs 的隐藏内容 + 抽屉内的表格都留在 DOM 里，
  // 不过滤会把「隐藏 table 的行」也算进行数（实测 4 条记录数出 12 行）。
  // 探针可重跑不幂等（每次新增 1 条界面记录）⇒ 不断言固定行数，
  // 改为「3 条预置 + 新录入必须全部可见」——缺任何一条即红。
  const rowTexts = await page.locator('#subapp-viewport .ant-table-row:visible').allInnerTexts().catch(() => []);
  const allRows = rowTexts.join('|');
  const expectNos = [...STATE.records.map((r) => r.equipment_no), EQ_NO];
  const missingRows = expectNos.filter((n) => !allRows.includes(n));
  check('⑦ 列表含 3 条预置记录 + 新录入记录', missingRows.length === 0,
    `缺=${missingRows.join(',')} rows=${rowTexts.length}`);

  // 列表列头恰为契约 6 列（界面设计 → 列表展示闭环；未配契约的 2 列不得出现）
  const headers = await page.locator('#subapp-viewport .ant-table-thead th').allInnerTexts().catch(() => []);
  const headerText = headers.join('|');
  check('⑦ 列表列头=契约 6 列（不含未配置的异常描述/巡检工时）',
    contractCols.every((c) => headerText.includes(c)) && !headerText.includes('异常描述') && !headerText.includes('巡检工时'),
    `headers=${headerText}`);
  await shot('7_list');

  /* 数据查看：详情抽屉回看录入值 */
  const row = page.locator('#subapp-viewport .ant-table-row').filter({ hasText: EQ_NO }).first();
  await row.locator('button, a').filter({ hasText: /详\s*情/ }).first().click();
  await page.waitForTimeout(1500);
  const drawerText = await page.locator('.ant-drawer:not(.ant-drawer-hidden)').last().innerText().catch(() => '');
  check('⑧ 详情抽屉回看：录入值一致（编号/名称/巡检人/结果）',
    drawerText.includes(EQ_NO) && drawerText.includes(EQ_NAME)
    && drawerText.includes(INSPECTOR) && drawerText.includes('正常'),
    drawerText.slice(0, 300));
  await shot('8_detail_drawer');
  await page.keyboard.press('Escape').catch(() => {});

  /* 持久化：整页刷新后记录仍在 */
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);
  const t7c = await waitForStable();
  check('⑧ 刷新后记录仍在（数据已落库）', t7c.includes(EQ_NO), t7c.slice(0, 160));

  /* API 交叉核验：界面录入的记录经 API 回读，值逐项一致 */
  const { data: d } = await api(
    'GET',
    `/runtime/entities/${STATE.entityCode}/records?page=1&size=100&fields=equipment_no,equipment_name,equipment_type,inspection_date,inspector,inspection_result,abnormal_desc,duration_minutes`,
    undefined,
    { token },
  );
  const rows = (d.data || {}).records || [];
  const hit = rows.find((x) => x.equipment_no === EQ_NO);
  check('⑧ API 交叉核验：界面录入记录存在于后端', !!hit, `rows=${rows.length}`);
  check('⑧ API 交叉核验：8 个字段值与界面录入逐一一致',
    !!hit && hit.equipment_name === EQ_NAME && hit.equipment_type === 'cnc'
    && String(hit.inspection_date).slice(0, 10) === INSP_DATE && hit.inspector === INSPECTOR
    && hit.inspection_result === 'normal' && hit.abnormal_desc === '端到端验证：经界面设计器协议表单录入'
    && String(hit.duration_minutes) === '45',
    hit ? JSON.stringify(hit) : 'record not found');

  /* 全程无 4xx/5xx 与致命前端错误 */
  check('全程无 4xx/5xx 响应', badResponses.length === 0,
    badResponses.map((b) => `${b.status} ${b.url} ${b.body.slice(0, 80)}`).join(' | ').slice(0, 300));
  // 已知无害的官方提示（console.error 通道但语义是 warning/deprecation）：
  // ① antd 组件 API deprecation（如 Descriptions labelStyle → styles）；
  // ② React 对 antd Form 实例未连接的已知警告（useForm 先于 Form 挂载的时序提示）。
  // 只放行这两类文本，其余 console error 仍算致命——保持断言判别力。
  const noise = (e) => /favicon|React DevTools|\[vite\]|websocket|Download the|\[antd:[^\]]+\] .*deprecated|Instance created by `useForm` is not connected/i.test(e);
  const fatal = consoleErrors.filter((e) => !noise(e));
  check('全程无致命 console/page 错误', fatal.length === 0, fatal.slice(0, 3).join(' | ').slice(0, 300));
} catch (e) {
  check('探针主流程未抛异常', false, e.message.slice(0, 300));
  await shot('9_error').catch(() => {});
} finally {
  await browser.close().catch(() => {});
}

console.log(`\nUI 全链路探针结果：PASS=${results.filter((r) => r.ok).length} FAIL=${results.filter((r) => !r.ok).length}`);
for (const r of results.filter((r) => !r.ok)) console.log(`  ✗ ${r.name}  ← ${r.detail}`);
process.exit(results.some((r) => !r.ok) ? 1 : 0);
