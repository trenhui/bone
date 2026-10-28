/**
 * 「个人显示」运行时探针（v3 bundle · 双轨收敛后迁移到运行时数据页）：
 * shell :3000 登录 → 元数据子应用 → 实体详情「数据」Tab（运行时数据页），验证：
 *   1. 身份徽标「个人偏好 · 仅自己可见」常驻（个人覆盖层的可见标识）；
 *   2. 视图切换器基线（默认视图单套起步）；
 *   3. 新建个人视图（blank）并自动切换；
 *   4. **自伤遮蔽判据 UI 化**：在「列设置」隐藏一个契约列后，工具栏出现
 *      「N 列被个人偏好隐藏 · 恢复显示」——这正是「配了不生效」的精确状态；
 *   5. 服务端落盘：UI 操作后 GET ui-overlay 必须是 v3 bundle（2 views + 正确 activeViewId）；
 *   6. **运行时消费**：数据表表头随生效视图变化（activeOverlay 在 RuntimeDataManagement 真生效）；
 *   7. **视图隔离**：切回默认视图，被个人隐藏的列恢复显示（偏好串台是多视图最常见 bug）；
 *   8. **自伤遮蔽一键自救**：点「恢复显示」后表头恢复、遮蔽提示消失；
 *   9. 反向导航：设计器工具栏「运行时列表」按钮一键回到数据页（两层闭环）；
 *   10. 收尾恢复：「恢复默认显示」→ 单视图默认态 + 服务端清空（不污染 E2E 基线）。
 *   11. **契约变更驱动的自伤遮蔽**（S1 计划验收判据）：自产实体上建模台改契约可见性
 *       → 发布 → runtime 无任何用户操作，提示必须实时消失/重现——纯前端实时判据相对
 *       「契约指纹」的决定性场景（指纹对「契约把列改回隐藏」误报、对「契约改无关列」误报）。
 *   12. **个人列改名**（S1 双轨之个人改名维度）：弹层改名 → 表头生效 → 服务端落盘 →
 *       视图隔离 → 恢复默认列名（等值名/空名必须删条目而非写等值，否则 dirty 永久置亮）。
 *   13. **筛选字段与列可见性解耦**：被个人隐藏的契约列仍在筛选下拉（筛选是查询维度，
 *       后端接受域 = 全部已建模字段）；CHILD_TABLE 不在下拉（后端精确 400，前端 fail-closed）。
 *   14. **明细列**（自产父子实体）：CHILD_TABLE 配成列表契约列时渲染「查看明细」入口
 *       （不再是空列/排序 400）——业界对标 Airtable linked-record 字段。
 *
 * 为什么在数据页而不在建模台 Tab：个人覆盖是运行期状态，编辑器跟着生命周期走
 * （业界双轨：Salesforce/ServiceNow/Power Apps/Airtable 的个人视图编辑器都在
 * 最终用户看数据的页面）。建模台侧只核对团队契约口径；个人口径对照在同页「交付口径对照」卡片。
 *
 * 口径：overlay 后端是不透明 blob（键含 userId），探针用同一登录态操作，
 * 与真实用户路径完全一致（非 mock）。
 *
 * 用法：node scripts/e2e/ui-personal-views-probe.mjs
 */
/* 共享 client：playwright 加载 / SHOT_DIR / API 登录与带 token fetch / 断言收集（见 e2e_lib/client.cjs） */
import {
  UI_BASE as BASE,
  SHOT_DIR,
  USER,
  PWD,
  loadPlaywright,
  api,
  login,
  makeChecker,
} from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const { results, check } = makeChecker();

/* ---------------- API 辅助（登录与带 token fetch 见 e2e_lib/client.cjs） ---------------- */

/**
 * 取最新**已发布**实体（deliveryMode=1 自动生成界面 & status=1 已发布）。
 * 不能图省事取最新样板：E2E 每轮新建的是草稿态，数据 Tab 对草稿实体拒显
 * （「请先发布物理建表」），运行时消费断言会假红。
 */
async function latestPublishedEntity(token, keyword) {
  const { data: d } = await api('GET', '/metadata/entities?page=1&size=500', undefined, { token });
  const recs = (d.data || {}).records || [];
  const hits = recs
    .filter((x) => x.displayName === keyword && x.deliveryMode === 1 && x.status === 1)
    .sort((a, b) => String(b.id).localeCompare(String(a.id)));
  return hits[0] || null;
}

async function getOverlay(token, entityCode) {
  const { data: d } = await api('GET', `/metadata/entities/${encodeURIComponent(entityCode)}/ui-overlay`, undefined, { token });
  return (d.data || {}).json ?? (d.data || null);
}

async function deleteOverlay(token, entityCode) {
  await api('DELETE', `/metadata/entities/${encodeURIComponent(entityCode)}/ui-overlay`, undefined, { token });
}

/**
 * 契约列（fieldCode + 契约标题 + 是否契约隐藏）。
 * 表头显示的是**契约标题优先**的显示名，不是 fieldCode——断言必须用同一口径，
 * 否则「按 code 匹配表头」会写出恒假断言。
 */
async function getContractColumns(token, eid) {
  const { data: d } = await api('GET', `/metadata/entities/${eid}/ui-schema`, undefined, { token });
  const raw = (d.data || {}).json;
  if (!raw) return [];
  const s = typeof raw === 'string' ? JSON.parse(raw) : raw;
  return (s.views?.list?.columns || [])
    .filter((c) => c && c.fieldCode)
    .map((c) => ({ fieldCode: c.fieldCode, title: c.format?.title ?? null, hidden: c.visible === false }));
}

/**
 * 实例化模板实体（自产自销）：契约切换场景只碰自己的实体，
 * 不 mutate 共享 E2E 实体（报销单/理赔案件）的已发布契约——那会打断并行探针。
 */
async function instantiateTemplateEntity(token, stamp) {
  const tpls = await api('GET', '/metadata/templates', undefined, { token }).then((r) => r.data);
  const tpl = ((tpls.data) || []).find((t) => t.code === 'tpl_device_inspection');
  if (!tpl) return null;
  const code = `shield_${stamp}`;
  const { data: d } = await api('POST', `/metadata/templates/${tpl.id}/instantiate`, {
    name: `Shield_${stamp}`, code, displayName: '遮蔽探针', tableName: `md_${code}`, moduleId: null, deliveryMode: 1,
  }, { token });
  return d.data || null;
}

/* ---------------- 浏览器辅助 ---------------- */
let browser;
const page = await (async () => {
  browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  return await ctx.newPage();
})();

const pageErrors = [];
page.on('pageerror', (e) => pageErrors.push(String(e)));
page.on('console', (m) => { if (m.type() === 'error') pageErrors.push(m.text()); });

async function uiLogin() {
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"], input[placeholder*="密码"]', { timeout: 20000 });
  const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
  if (await page.locator(userSel).first().count()) {
    await page.locator(userSel).first().fill(USER);
  }
  await page.locator('input[type="password"]').first().fill(PWD);
  await page.locator('button[type="submit"], button').filter({ hasText: /登录|登 录/ }).first().click();
  await page.waitForTimeout(2500);
  return !page.url().includes('login');
}

/** 打开实体详情指定 Tab（必须 reload：同 URL 含 hash 的 goto 是同文档导航，SPA 不重拉数据） */
async function openTab(entityId, tab) {
  await page.goto(`${BASE}/metadata#/entities/${entityId}?tab=${tab}`, { waitUntil: 'domcontentloaded' });
  await page.reload({ waitUntil: 'domcontentloaded' });
}

/** 清浏览器侧 overlay 本地缓存（键含 userId/entityCode）：API DELETE 只清服务端，
 *  localhost 缓存会在 hydrate 时「复活」旧 bundle——探针基线必须两端同清才幂等 */
async function clearLocalOverlayCache(entityCode) {
  await page.evaluate((code) => {
    Object.keys(localStorage)
      .filter((k) => k.includes('bone.meta.overlay.') && k.endsWith(`.${code}`))
      .forEach((k) => localStorage.removeItem(k));
  }, entityCode);
}

/** 等运行时数据页就绪（个人显示组徽标 + 表格） */
async function waitRuntimeReady() {
  await page.locator('.ant-tag').filter({ hasText: '个人偏好 · 仅自己可见' }).first()
    .waitFor({ state: 'visible', timeout: 25000 });
  await page.locator('#subapp-viewport .ant-table').first().waitFor({ state: 'visible', timeout: 25000 });
}

/** 运行时表头文本（限定可见表格：antd Tabs 隐藏 pane 的 DOM 仍在文档里） */
async function runtimeHeaders() {
  return (await page.locator('#subapp-viewport .ant-table thead:visible').first().innerText()
    .catch(async () => (await page.locator('#subapp-viewport .ant-table thead').first().innerText())))
    .replace(/\s+/g, '');
}

/** 打开「列设置」弹层并等其内容出现（幂等：先确保无可见弹层，避免「toggle 成关闭」） */
async function openColumnSettings() {
  // toggle 感知：受控 Popover（trigger=[]）不响应 Escape，且「列设置」按钮点击即 toggle。
  // 与 antd 点击触发实现都兼容：已有可见弹层则不动；否则点开。绝不再用 Escape 误关。
  const pop = page.locator('.ant-popover:not(.ant-popover-hidden)');
  if ((await pop.count()) === 0) {
    await page.locator('button').filter({ hasText: '列设置' }).first().click();
    await pop.first().waitFor({ state: 'visible', timeout: 8000 });
  }
  await pop.first().locator('.col-settings-row').first().waitFor({ state: 'visible', timeout: 8000 });
  return pop;
}

async function shot(name) {
  await page.screenshot({ path: `${SHOT_DIR}/ui_personal_views_${name}.png`, fullPage: false });
}

const VIEW_A = '探针视图A';
let token;
let entity;
let shieldId = null;
let shieldCode = null;
/** shield 实体的契约写入/发布助手（5 节与 5.6 节共用，故提到 try 作用域外） */
let putShieldSchema = null;
let publishShield = null;
let ccCode = null;

try {
  check('UI 登录成功', await uiLogin(), `url=${page.url()}`);

  token = await login();
  entity = await latestPublishedEntity(token, '报销单');
  check('取到已发布报销实体（运行时消费断言前提）', !!entity, entity ? `${entity.code} ${entity.id} status=${entity.status}` : '未找到');
  if (!entity) throw new Error('无实体，探针中止');

  const contractCols = await getContractColumns(token, entity.id);
  check('报销实体有列表契约列（隔离断言需要契约列）', contractCols.length > 0, `cols=${contractCols.map((c) => c.fieldCode).join(',')}`);
  const probeCol = contractCols.find((c) => !c.hidden) ?? contractCols[0];

  /* ---------- 0. 基线：两端同清 overlay → 单视图默认态 ---------- */
  await deleteOverlay(token, entity.code);
  await openTab(entity.id, 'data');
  await clearLocalOverlayCache(entity.code);
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitRuntimeReady();

  const badge = await page.locator('.ant-tag').filter({ hasText: '个人偏好 · 仅自己可见' }).count();
  check('P0.1 身份徽标「个人偏好 · 仅自己可见」常驻', badge === 1, `badge=${badge}`);

  const segItems = await page.locator('.ant-segmented-item').allInnerTexts();
  const viewItems = segItems.filter((t) => /默认视图|探针视图/.test(t));
  check('基线：视图切换器仅有「默认视图」（单套起步）', viewItems.length === 1 && viewItems[0] === '默认视图', `items=${viewItems.join(',')}`);
  await shot('baseline');

  /* ---------- 1. UI 新建视图（blank） ---------- */
  await page.locator('button').filter({ hasText: '管理' }).first().click();
  await page.waitForTimeout(400);
  await page.locator('.ant-dropdown-menu-item').filter({ hasText: '新建视图' }).first().click();
  // ⚠ 必须按标题限定：EntityDetail 的「编辑字段」Modal 是 forceRender（DOM 常驻但隐藏），
  // 裸 waitForSelector('.ant-modal') 会命中它并永远等不到 visible（探针首跑假红根因）
  const createModal = page.locator('.ant-modal').filter({ hasText: '新建个人视图' });
  await createModal.waitFor({ state: 'visible', timeout: 8000 });
  await createModal.locator('input').first().fill(VIEW_A);
  // blank 模式：取消勾选「复制当前视图的列偏好」
  const copyCb = createModal.locator('.ant-checkbox-input').first();
  if (await copyCb.count()) await copyCb.uncheck();
  await createModal.locator('.ant-modal-footer .ant-btn-primary').first().click();
  await page.waitForTimeout(1200);

  const segAfterCreate = await page.locator('.ant-segmented-item').allInnerTexts();
  check(`新建视图「${VIEW_A}」出现在切换器`, segAfterCreate.some((t) => t.includes(VIEW_A)), `items=${segAfterCreate.join(',')}`);
  check('新建后自动切换到新视图',
    (await page.locator('.ant-segmented-item-selected').allInnerTexts()).some((t) => t.includes(VIEW_A)));
  await shot('view_created');

  /* ---------- 2. 列设置弹层隐藏首契约列 → 自伤遮蔽判据 UI 化 ---------- */
  const pop = await openColumnSettings();
  // 第一个「可点」的开关 = 第一个契约可见列的个人开关（契约隐藏列置灰，避开）
  const targetRow = pop.locator('.col-settings-row')
    .filter({ has: page.locator('.ant-switch:not(.ant-switch-disabled)') }).first();
  const hiddenColName = (await targetRow.locator('.col-settings-name').innerText()).trim();
  await targetRow.locator('.ant-switch').click();
  await page.waitForTimeout(600);

  const switchCls = (await targetRow.locator('.ant-switch').getAttribute('class')) || '';
  check(`视图 A 隐藏「${hiddenColName}」生效（开关关闭）`, !switchCls.includes('ant-switch-checked'), `cls=${switchCls}`);

  // 自伤遮蔽：该列契约默认显示、被个人隐藏 ⇒ 工具栏必须出现自救入口
  const shadowBtn = page.locator('button').filter({ hasText: /被个人偏好隐藏/ });
  check('自伤遮蔽判据 UI 化：工具栏出现「N 列被个人偏好隐藏 · 恢复显示」', await shadowBtn.count() === 1, `count=${await shadowBtn.count()}`);

  // 「恢复默认显示」在 dirty 态可用（isOverlayDirty 判据）
  const restoreBtn = pop.locator('button').filter({ hasText: '恢复默认显示' }).first();
  check('「恢复默认显示」在 dirty 态可用', await restoreBtn.isEnabled().catch(() => false));

  // 服务端落盘核对：必须是 v3 bundle，2 views，active=A
  const blob1 = await getOverlay(token, entity.code);
  let bundle1 = null;
  try { bundle1 = typeof blob1 === 'string' ? JSON.parse(blob1) : blob1; } catch { /* ignore */ }
  check('服务端落盘为 v3 bundle（2 视图）',
    !!bundle1 && Array.isArray(bundle1.views) && bundle1.views.length === 2 && bundle1.version === 3,
    JSON.stringify(bundle1).slice(0, 160));

  /* ---------- 2.5 个人列改名（S1 双轨之「个人改名」维度：契约标题团队统一、个人叫法自助） ---------- */
  // 为什么测这个：投影层早就能读 displayName（个人改名优先于契约标题），但双轨收敛时
  // 只接了显隐/密度——改名维度留在模型里没有编辑器。本场景验证写口全闭环：
  // 弹层改名 → 表头生效 → 服务端落盘 → 恢复默认列名（等值名/空名不得写入，见单测负向探针）。
  // 弹层开关由 openColumnSettings 幂等处理（先确保旧弹层关闭动画结束再点开，避免 toggle 成关闭）。
  const popRN = await openColumnSettings();
  // 目标列 = 首个契约可见且非 probeCol 的列（行序 = 契约序，nth 锚定不受改名后文本变化影响）
  const renameIdx = contractCols.findIndex((c) => !c.hidden && c.fieldCode !== probeCol.fieldCode);
  const renameCol = contractCols[renameIdx] ?? contractCols.find((c) => !c.hidden);
  // 按字段 code 锚定行（data-field-code）：弹层 rows 会过滤掉契约列里 fields 中无对应字段的行，
  // 用 nth(契约下标) 会错位到别的行（可能恰是契约隐藏列→无编辑按钮）。code 锚点零歧义。
  const renameRow = popRN.locator(`.col-settings-row[data-field-code="${renameCol.fieldCode}"]`);
  const renameColName = (await renameRow.locator('.col-settings-name').innerText()).trim();
  const RENAME_TO = '探针改名列';
  // 弹层内部 overflow:auto，行可能在折叠区 ⇒ 先滚入视口
  await page.evaluate((code) => {
    const row = document.querySelector(`.ant-popover:not(.ant-popover-hidden) .col-settings-row[data-field-code="${code}"]`);
    if (row) row.scrollIntoView({ block: 'center' });
  }, renameCol.fieldCode);
  await page.waitForTimeout(300);
  const editBtn = renameRow.locator('button:has(.anticon-edit)');
  // 编辑按钮已去 antd Tooltip（改原生 title，不拦截点击）；弹层受控打开，内部交互不误关。
  await editBtn.click({ timeout: 10000 }).catch(async () => {
    await editBtn.click({ force: true, timeout: 8000 }).catch(() => {});
  });
  await page.waitForTimeout(600);
  const renameInput = renameRow.locator('input').first();
  await renameInput.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await renameInput.fill(RENAME_TO);
  await renameInput.press('Enter');
  await page.waitForTimeout(700);

  check(`个人改名：弹层行显示「${RENAME_TO}」并带「个人」徽标`,
    (await renameRow.locator('.col-settings-name').innerText()).trim() === RENAME_TO
    && (await renameRow.locator('.ant-tag').filter({ hasText: '个人' }).count()) === 1,
    `name=${(await renameRow.locator('.col-settings-name').innerText()).trim()}`);
  const headersRenamed = await runtimeHeaders();
  check(`个人改名：运行时表头生效（${renameColName} → ${RENAME_TO}）`,
    headersRenamed.includes(RENAME_TO) && !headersRenamed.includes(renameColName),
    `header=${headersRenamed.slice(0, 140)}`);
  const blobRN = await getOverlay(token, entity.code);
  let bundleRN = null;
  try { bundleRN = typeof blobRN === 'string' ? JSON.parse(blobRN) : blobRN; } catch { /* ignore */ }
  const activeRN = bundleRN?.views?.find((v) => v.id === bundleRN.activeViewId) ?? bundleRN?.views?.[0];
  check('个人改名：服务端落盘 displayName（仅改生效视图，其他视图不受影响）',
    activeRN?.overrides?.[renameCol.fieldCode]?.displayName === RENAME_TO,
    JSON.stringify(activeRN?.overrides?.[renameCol.fieldCode] ?? null));
  const otherView = bundleRN?.views?.find((v) => v.id !== bundleRN.activeViewId);
  check('个人改名：视图隔离（非生效视图的 overrides 不含该改名）',
    otherView?.overrides?.[renameCol.fieldCode]?.displayName !== RENAME_TO);

  // 恢复默认列名：等值名/空名必须「删条目」而非写等值（否则 dirty 判据永久置亮）
  await renameRow.locator('button:has(.anticon-undo)').click().catch(() => {});
  await page.waitForTimeout(700);
  check('恢复默认列名：弹层行回落原列名且「个人」徽标消失',
    (await renameRow.locator('.col-settings-name').innerText()).trim() === renameColName
    && (await renameRow.locator('.ant-tag').filter({ hasText: '个人' }).count()) === 0);
  const headersUnrenamed = await runtimeHeaders();
  check('恢复默认列名：表头回落（契约标题/字段名）',
    headersUnrenamed.includes(renameColName) && !headersUnrenamed.includes(RENAME_TO),
    `header=${headersUnrenamed.slice(0, 140)}`);
  const blobURN = await getOverlay(token, entity.code);
  let bundleURN = null;
  try { bundleURN = typeof blobURN === 'string' ? JSON.parse(blobURN) : blobURN; } catch { /* ignore */ }
  const activeURN = bundleURN?.views?.find((v) => v.id === bundleURN.activeViewId) ?? bundleURN?.views?.[0];
  check('恢复默认列名：服务端 displayName 键被删除（不是写空串/等值名）',
    activeURN?.overrides?.[renameCol.fieldCode] != null && !('displayName' in activeURN.overrides[renameCol.fieldCode]),
    JSON.stringify(activeURN?.overrides?.[renameCol.fieldCode] ?? null));

  /* ---------- 2.6 筛选字段与列可见性解耦（后端接受域 = 全部已建模字段，仅排除 CHILD_TABLE） ---------- */
  // 为什么测这个：筛选字段下拉曾是「当前可见列」⇒ 隐藏一列即失去对它的筛选能力
  // （企业场景按内部字段/已隐藏列过滤直接失能，且用户看不到因果）。视图 A 此刻正 hiding probeCol。
  await page.keyboard.press('Escape');
  await page.waitForTimeout(300);
  await page.locator('.ant-space-compact button').filter({ hasText: '添加条件' }).first().click();
  await page.waitForTimeout(400);
  // 按 placeholder 锚定「筛选字段」下拉（空条件行的第一个 Select），不依赖 Space 内部 DOM 结构
  const fieldSelect = page.locator('.ant-select')
    .filter({ has: page.locator('.ant-select-selection-placeholder', { hasText: '筛选字段' }) })
    .last();
  await fieldSelect.click();
  await page.waitForTimeout(500);
  const filterOpts = await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option-content').allInnerTexts();
  // hiddenColName = probeCol 的字段 displayName（步骤 2 从弹层行取得）——它此刻正被视图 A 隐藏
  check('筛选解耦：被个人隐藏的契约列仍在筛选字段下拉（隐藏不影响可筛选性）',
    filterOpts.some((o) => o.includes(hiddenColName)),
    `hiddenCol=${hiddenColName} opts=${filterOpts.slice(0, 10).join(',')}`);
  check('筛选解耦：CHILD_TABLE 字段（items）不在筛选下拉（后端对该类型精确 400，前端 fail-closed 不提供）',
    !filterOpts.some((o) => o.includes('items')),
    `opts=${filterOpts.slice(0, 12).join(',')}`);
  await page.keyboard.press('Escape');
  await page.waitForTimeout(200);
  // 清掉草稿条件行，恢复基线（草稿未 Apply 不影响列表，但保持流程纯净）
  await page.locator('.ant-space-compact button:has(.anticon-delete)').last().click();
  await page.waitForTimeout(300);

  /* ---------- 3. 运行时消费：数据表表头随生效视图变化（同一页，无需切 Tab） ---------- */
  const expectName = probeCol.title || hiddenColName;
  const headersA = await runtimeHeaders();
  check('运行时（视图 A）：被个人隐藏的列不出现在数据表表头（且其他列在场，非空断言）',
    headersA.length > 0 && !headersA.includes(expectName) && headersA.includes('操作'),
    `probeCol=${probeCol.fieldCode}/${expectName} header=${headersA.slice(0, 120)}`);
  await shot('runtime_view_a');

  // 切回默认视图：隐藏必须恢复（串台 bug 会在这里现形）
  await page.locator('.ant-segmented-item').filter({ hasText: '默认视图' }).first().click();
  await page.waitForTimeout(900);
  const headersDefault = await runtimeHeaders();
  check('视图隔离：切回默认视图，同一列恢复显示（activeOverlay 在 RuntimeDataManagement 真生效）',
    headersDefault.includes(expectName), `probeCol=${probeCol.fieldCode}/${expectName} header=${headersDefault.slice(0, 120)}`);
  check('视图隔离：切回默认视图后自伤遮蔽提示消失', await page.locator('button').filter({ hasText: /被个人偏好隐藏/ }).count() === 0);
  await shot('runtime_default_view');

  /* ---------- 4. 自伤遮蔽一键自救：回视图 A（遮蔽态）→ 点「恢复显示」 ---------- */
  await page.locator('.ant-segmented-item').filter({ hasText: VIEW_A }).first().click();
  await page.waitForTimeout(800);
  const headersA2 = await runtimeHeaders();
  check('前置条件自证：视图 A 中该列仍被个人隐藏', !headersA2.includes(expectName), `header=${headersA2.slice(0, 120)}`);
  await page.locator('button').filter({ hasText: /被个人偏好隐藏/ }).first().click();
  await page.waitForTimeout(800);
  const headersRescued = await runtimeHeaders();
  check('一键自救：点「恢复显示」后该列回到数据表表头', headersRescued.includes(expectName), `header=${headersRescued.slice(0, 120)}`);
  check('一键自救：遮蔽提示随之消失', await page.locator('button').filter({ hasText: /被个人偏好隐藏/ }).count() === 0);
  await shot('shadow_rescued');

  /* ---------- 5. 契约变更驱动的自伤遮蔽（自产实体，契约切换不碰共享实体） ---------- */
  // S1 计划 Phase 1 item 3 的验收判据：「建模台改契约 → runtime 无用户操作，提示实时出现/消失」。
  // 这也是纯前端实时判据相对「契约指纹」的决定性场景：
  //   指纹只回答「契约自你保存后变过吗」——契约把列改回隐藏时列集合没变 ⇒ 指纹不匹配会误报；
  //   契约新增无关列时指纹不匹配 ⇒ 用户却什么都没被盖住，同样误报。
  //   实时判据只回答「此刻哪些契约默认显示的列被你个人盖住」——精确、无存量盲区。
  const stamp = Date.now();
  shieldCode = `shield_${stamp}`;
  shieldId = await instantiateTemplateEntity(token, stamp);
  check('自产遮蔽实体实例化（自产自销，契约切换不碰共享实体）', !!shieldId, `id=${shieldId}`);
  if (shieldId) {
    const SHIELD_FIELDS = ['insp_no', 'device_code', 'inspector', 'insp_date', 'result', 'status', 'remark'];
    // 手写最小合法 schema：root 必填 schemaVersion/entityCode/views；列必填 fieldCode
    // （bone-ui-schema.schema.json 根与各对象均 additionalProperties:false，不多写键）
    const buildShieldSchema = (hiddenCode, addOutside = false) => ({
      schemaVersion: 1,
      entityCode: shieldCode,
      displayModes: ['create', 'edit', 'view'],
      views: {
        list: {
          columns: ['insp_no', 'device_code', 'inspector', 'insp_date', 'result']
            .concat(addOutside ? ['status', 'remark'] : [])
            .map((f) => ({ fieldCode: f, visible: f !== hiddenCode, width: 140 })),
          searchFields: ['insp_no'],
        },
        form: {
          sections: [{ id: 'sec_main', title: '基本信息', controls: SHIELD_FIELDS.map((f) => ({ fieldCode: f })) }],
        },
      },
      linkageRules: [],
      submitRules: [],
      actionBar: [],
    });
    // PUT 必须带当前 lockVersion（If-Match），否则 409 META_UI_SCHEMA_VERSION_CONFLICT
    putShieldSchema = async (hiddenCode, opts) => {
      const cur = await api('GET', `/metadata/entities/${shieldId}/ui-schema`, undefined, { token }).then((r) => r.data);
      const lock = (cur.data || {}).lockVersion ?? '0';
      const { status } = await api('PUT', `/metadata/entities/${shieldId}/ui-schema`,
        { json: JSON.stringify(buildShieldSchema(hiddenCode, opts?.addOutside)) },
        { token, headers: { 'If-Match': String(lock) } });
      return status;
    };
    publishShield = async () => {
      const { status } = await api('POST', `/metadata/entities/${shieldId}/publish`, undefined, { token });
      return status;
    };

    check('自产实体契约 v1 发布（5 列全可见）',
      (await putShieldSchema(null)) === 200 && (await publishShield()) === 200);

    // 目标列 = 首个契约可见列 insp_no（displayName 巡检单号）
    const shieldFields = await api('GET', `/metadata/entities/${shieldId}/fields?page=1&size=100`, undefined, { token }).then((r) => r.data);
    const fMap = Object.fromEntries(((shieldFields.data || {}).records || []).map((f) => [f.code, f.displayName]));
    const shieldCol = 'insp_no';
    const shieldColName = fMap[shieldCol] || shieldCol;

    await openTab(shieldId, 'data');
    await clearLocalOverlayCache(shieldCode);
    await page.reload({ waitUntil: 'domcontentloaded' });
    await waitRuntimeReady();

    // 建立遮蔽：隐藏契约可见列 insp_no（自伤，与主流程同一交互）
    const popS = await openColumnSettings();
    const rowS = popS.locator('.col-settings-row').filter({ hasText: shieldColName }).first();
    const rowFound = (await rowS.count()) === 1;
    check(`自产实体：列设置找到契约列「${shieldColName}」`, rowFound);
    if (rowFound) {
      const swCls0 = (await rowS.locator('.ant-switch').getAttribute('class')) || '';
      check(`遮蔽建立：「${shieldColName}」开关初始为开（契约可见列可点）`, swCls0.includes('ant-switch-checked'), `cls=${swCls0}`);
      await rowS.locator('.ant-switch').click();
      await page.waitForTimeout(600);
      check('遮蔽建立：工具栏出现自伤遮蔽提示', await page.locator('button').filter({ hasText: /被个人偏好隐藏/ }).count() === 1);
    }

    // 契约 v2：建模台把 insp_no 改回契约隐藏 → 发布 → 提示必须消失
    check('契约 v2：insp_no 改契约隐藏并发布',
      (await putShieldSchema(shieldCol)) === 200 && (await publishShield()) === 200);
    await openTab(shieldId, 'data');
    await waitRuntimeReady();
    check('契约 v2 后：提示消失（契约已不显示该列，无从遮蔽）',
      await page.locator('button').filter({ hasText: /被个人偏好隐藏/ }).count() === 0);
    // 判别力断言：提示消失必须是「判据实时重算」而非「overlay 被清掉」——
    // 服务端 bundle 仍盖着 insp_no（杀掉「靠清偏好消提示」的伪实现）
    const blobV2 = await getOverlay(token, shieldCode);
    let bundleV2 = null;
    try { bundleV2 = typeof blobV2 === 'string' ? JSON.parse(blobV2) : blobV2; } catch { /* ignore */ }
    const activeV2 = bundleV2?.views?.find((v) => v.id === bundleV2.activeViewId) ?? bundleV2?.views?.[0];
    check('契约 v2 后：overlay 仍盖着 insp_no（提示消失源于判据重算，非偏好被清）',
      activeV2?.overrides?.[shieldCol]?.visible === false,
      JSON.stringify(activeV2?.overrides?.[shieldCol] ?? null));

    // 契约 v3：建模台恢复 insp_no 契约可见 → 发布 → 提示必须重现（overlay 从未被动过）
    check('契约 v3：insp_no 恢复契约可见并发布',
      (await putShieldSchema(null)) === 200 && (await publishShield()) === 200);
    await openTab(shieldId, 'data');
    await waitRuntimeReady();
    check('契约 v3 后：提示重现（契约可见 ∧ 个人仍隐藏 = 遮蔽态回归）',
      await page.locator('button').filter({ hasText: /被个人偏好隐藏/ }).count() === 1);

    // 一键自救：恢复显示 → 提示消失 + 列回表头
    await page.locator('button').filter({ hasText: /被个人偏好隐藏/ }).first().click();
    await page.waitForTimeout(800);
    const headersShield = await runtimeHeaders();
    check('一键自救：提示消失且该列回到数据表表头',
      await page.locator('button').filter({ hasText: /被个人偏好隐藏/ }).count() === 0 && headersShield.includes(shieldColName),
      `header=${headersShield.slice(0, 120)}`);
    await shot('contract_update_shield');
  }

  /* ---------- 5.6 契约外字段提示：回答「我建的字段呢」（strict 契约集合的可观测面） ---------- */
  // 复用 shield 实体：SHIELD_FIELDS 里 status/remark 已建模且可写，但契约 list.columns
  // 只列了 5 列（insp_no/device_code/inspector/insp_date/result）⇒ 这两个字段是「契约外字段」。
  // 交付工程师最常见的困惑就发生在这里：字段建了、发布了，列表里却没有，且毫无迹象。
  if (shieldId && putShieldSchema) {
    await openTab(shieldId, 'data');
    await waitRuntimeReady();
    // 基线前置自证：这两个字段确实已建模（否则本组断言无判别力——字段不存在也能"提示不出现"）
    const sfRes = await api('GET', `/metadata/entities/${shieldId}/fields?page=1&size=100`, undefined, { token }).then((r) => r.data);
    const modeled = ((sfRes.data || {}).records || []).map((f) => f.displayName || f.code);
    const outsideNames = ['状态', '备注'];
    check('契约外字段场景：前置自证——两个契约外字段确已建模（提示非虚报的前提）',
      outsideNames.every((n) => modeled.some((m) => m.includes(n))),
      `modeled=${modeled.join(',')}`);

    const outsideTip = page.locator('#subapp-viewport').getByText(/未列入团队契约/);
    check('契约外字段：运行时出现低噪音提示（「N 个字段未列入团队契约」）',
      (await outsideTip.count()) === 1);
    const shieldHeadersNow = await runtimeHeaders();
    check('契约外字段：提示的字段确实不在数据表表头（提示与真值一致）',
      !outsideNames.some((n) => shieldHeadersNow.includes(n)),
      `header=${shieldHeadersNow.slice(0, 140)}`);

    // 修复路径：把契约外字段加入列表契约并发布 → 提示消失 + 列进入表头（闭环可操作）
    const ccPut4 = await putShieldSchema(null, { addOutside: true });
    check('契约外字段：契约加入 status/remark 并发布', ccPut4 === 200 && (await publishShield()) === 200);
    await openTab(shieldId, 'data');
    await waitRuntimeReady();
    const shieldHeadersAfter = await runtimeHeaders();
    check('契约外字段：加入契约后提示消失且列进入表头（闭环）',
      (await page.locator('#subapp-viewport').getByText(/未列入团队契约/).count()) === 0
        && outsideNames.every((n) => shieldHeadersAfter.includes(n)),
      `header=${shieldHeadersAfter.slice(0, 140)}`);
    await shot('outside_contract_hint');
  }

  /* ---------- 5.5 明细列：CHILD_TABLE 契约列 = 行内「查看明细」入口（自产父子实体） ---------- */
  // 解决上轮遗留的产品口径问题：CHILD_TABLE 配成列表契约列时，运行时不再渲染空列
  // （无物理列可取）也不再让表头排序报 400，而是渲染「查看明细」入口——
  // 业界对标 Airtable linked-record 字段（可点开）、Salesforce related list 承载 1—N。
  // 自产自销：父子实体全部经 API 新建，契约手写，不碰任何共享实体。
  {
    const ccStamp = Date.now();
    ccCode = `childcol_${ccStamp}`;
    const ccChildCode = `childcol_item_${ccStamp}`;
    // 后端实体创建为最终一致：刚创建的实体立即可建字段会偶发 400（实体表/元数据未就绪）。
    // 故建实体后 settle 2s，且字段写入带重试（对齐 two-scenario 的慢节奏，属测试脚手架健壮性）。
    const createEntity = async (name, code, table) => {
      for (let attempt = 0; attempt < 3; attempt++) {
        const r = await api('POST', '/metadata/entities',
          { name, code, displayName: name, tableName: table, type: 1, deliveryMode: 1, description: 'E2E 明细列探针' },
          { token });
        if (r.status === 200 || r.status === 201) return r.data.data;
        await new Promise((res) => setTimeout(res, 800));
      }
      return null;
    };
    const ensureField = async (eid, f, retry = 3) => {
      const fb = { name: f.code, code: f.code, displayName: f.label, type: f.type };
      if (f.validationRules) fb.validationRules = JSON.stringify(f.validationRules);
      for (let attempt = 0; attempt < retry; attempt++) {
        const r = await api('POST', `/metadata/entities/${eid}/fields`, fb, { token }).catch(() => ({ status: 0 }));
        if (r.status === 200 || r.status === 201) return true;
        await new Promise((res) => setTimeout(res, 800));
      }
      return false;
    };
    const ccParent = await createEntity('明细列探针', ccCode, `t_${ccCode}`);
    const ccChild = await createEntity('明细列探针子表', ccChildCode, `t_${ccChildCode}`);
    check('明细列场景：自产父子实体创建成功', !!ccParent && !!ccChild, `parent=${ccParent} child=${ccChild}`);
    if (ccParent && ccChild) {
      await new Promise((res) => setTimeout(res, 2000)); // 实体落库 settle
      await ensureField(ccChild, { code: 'claim_id', label: '巡检单ID', type: 'LONG' });
      await ensureField(ccChild, { code: 'sort_order', label: '行序', type: 'INTEGER' });
      await ensureField(ccChild, { code: 'amount', label: '金额', type: 'DECIMAL' });
      await ensureField(ccChild, { code: 'remark', label: '备注', type: 'STRING' });
      await ensureField(ccParent, { code: 'claim_no', label: '单号', type: 'STRING' });
      await ensureField(ccParent, { code: 'title', label: '标题', type: 'STRING' });
      await ensureField(ccParent, { code: 'amount', label: '金额', type: 'DECIMAL' });
      await ensureField(ccParent, { code: 'status', label: '状态', type: 'STRING' });
      await ensureField(ccParent, {
        code: 'items', label: '明细行', type: 'CHILD_TABLE',
        validationRules: { childTable: { childEntityCode: ccChildCode, foreignKey: 'claim_id', orderColumn: 'sort_order', cascadeDelete: true, minRows: 0, maxRows: 200 } },
      });
      const ccChildPub = await api('POST', `/metadata/entities/${ccChild}/publish`, undefined, { token }).then((r) => r.status);
      // 父实体契约：items 作为列表契约列（第 4 列）——被测对象
      const ccSchema = {
        schemaVersion: 1, entityCode: ccCode, displayModes: ['create', 'edit', 'view'],
        views: {
          list: {
            columns: [
              { fieldCode: 'claim_no', width: 140 },
              { fieldCode: 'title', width: 200 },
              { fieldCode: 'amount', width: 120 },
              { fieldCode: 'items', width: 120 },
              { fieldCode: 'status', width: 100 },
            ],
            searchFields: ['claim_no'],
          },
          form: { sections: [{ id: 'sec_main', title: '基本信息', controls: ['claim_no', 'title', 'amount', 'status'].map((f) => ({ fieldCode: f })) }] },
        },
        linkageRules: [], submitRules: [], actionBar: [],
      };
      let ccPut = await api('PUT', `/metadata/entities/${ccParent}/ui-schema`,
        { json: JSON.stringify(ccSchema) },
        { token, headers: { 'If-Match': '-1' } });
      if (ccPut.status === 412) {
        const cur = await api('GET', `/metadata/entities/${ccParent}/ui-schema`, undefined, { token }).then((r) => r.data);
        ccPut = await api('PUT', `/metadata/entities/${ccParent}/ui-schema`,
          { json: JSON.stringify(ccSchema) },
          { token, headers: { 'If-Match': String((cur.data || {}).lockVersion ?? '0') } });
      }
      const ccParentPub = await api('POST', `/metadata/entities/${ccParent}/publish`, undefined, { token }).then((r) => r.status);
      check('明细列场景：子表发布 + 父实体契约（含 items 列）写入并发布',
        ccChildPub === 200 && ccPut.status === 200 && ccParentPub === 200,
        `child=${ccChildPub} put=${ccPut.status} parent=${ccParentPub}`);
      // 造一条记录（否则空表没有行可点「查看明细」）
      await api('POST', `/runtime/entities/${ccCode}/records`,
        { claim_no: 'CC-1', title: '明细列探针记录', amount: 123.45, status: 'DRAFT' },
        { token }).catch(() => {});
      await openTab(ccParent, 'data');
      await waitRuntimeReady();
      await page.waitForTimeout(1500); // 等列表数据落位
      const ccHeadersText = await runtimeHeaders();
      check('明细列：items 契约列渲染为「明细行」列表头（不再是空列）', ccHeadersText.includes('明细行'), `header=${ccHeadersText.slice(0, 140)}`);
      // 表头不可排序：该列 th 内无 sorter（后端对 sort=items 精确 400，前端不提供入口）
      const ccTh = page.locator('#subapp-viewport .ant-table thead:visible th').filter({ hasText: '明细行' }).first();
      check('明细列：表头无排序器（CHILD_TABLE 无物理列，不可排序）',
        (await ccTh.count()) === 1 && (await ccTh.locator('.ant-table-column-sorter').count()) === 0);
      // 行内「查看明细」入口：点击打开明细编辑器（与操作列明细下拉同源 openChildren）
      const ccDetailBtn = page.locator('#subapp-viewport').getByRole('button', { name: '查看明细' }).first();
      check('明细列：行内渲染「查看明细」入口', (await ccDetailBtn.count()) === 1);
      if (await ccDetailBtn.count()) {
        await ccDetailBtn.click();
        const ccModal = page.locator('.ant-modal').filter({ hasText: '明细：明细行' });
        await ccModal.waitFor({ state: 'visible', timeout: 8000 });
        check('明细列：点击「查看明细」打开明细编辑器（可增删行）', true);
        await page.locator('.ant-modal').filter({ hasText: '明细：明细行' }).locator('.ant-modal-close').first().click();
        await page.waitForTimeout(400);
      }
      // 筛选下拉同样不含子表字段（fail-closed，与主流程同款）
      await page.locator('.ant-space-compact button').filter({ hasText: '添加条件' }).first().click();
      await page.waitForTimeout(400);
      await page.locator('.ant-select')
        .filter({ has: page.locator('.ant-select-selection-placeholder', { hasText: '筛选字段' }) })
        .last().click();
      await page.waitForTimeout(500);
      const ccFilterOpts = await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option-content').allInnerTexts();
      check('明细列：子表字段不在筛选下拉（与主流程同款 fail-closed）',
        !ccFilterOpts.some((o) => o.includes('明细行') || o.includes('items')),
        `opts=${ccFilterOpts.slice(0, 12).join(',')}`);
      await page.keyboard.press('Escape');
      await page.waitForTimeout(200);
      await page.locator('.ant-space-compact button:has(.anticon-delete)').last().click();
      await page.waitForTimeout(300);
      await shot('child_column');
    }
  }

  /* ---------- 6. 反向导航（两层闭环）：设计器 → 运行时列表 ---------- */
  await openTab(entity.id, 'uischema');
  await page.waitForSelector('.uis-root', { timeout: 25000 });
  const skip = page.locator('button, a').filter({ hasText: '跳过引导' }).first();
  if (await skip.count()) { await skip.click({ timeout: 4000 }).catch(() => {}); await page.waitForTimeout(400); }
  const runtimeBtn = page.locator('button').filter({ hasText: '运行时列表' }).first();
  check('设计器工具栏渲染「运行时列表」反向入口', await runtimeBtn.count() === 1);
  if (await runtimeBtn.count()) {
    await runtimeBtn.click();
    await page.waitForTimeout(1800);
    check('点击后回到数据页（两层闭环）', page.url().includes('tab=data'), `url=${page.url()}`);
    await waitRuntimeReady().catch(() => {});
    await shot('reverse_nav');
  }

  /* ---------- 7. 收尾：「恢复默认显示」→ 单视图默认态 + 服务端清空 ---------- */
  // 先制造 dirty（隐藏一列），否则自救后已恢复默认、按钮置灰
  const pop2 = await openColumnSettings();
  const row2 = pop2.locator('.col-settings-row')
    .filter({ has: page.locator('.ant-switch:not(.ant-switch-disabled)') }).first();
  await row2.locator('.ant-switch').click();
  await page.waitForTimeout(500);
  const restore2 = pop2.locator('button').filter({ hasText: '恢复默认显示' }).first();
  check('收尾前自证：dirty 态「恢复默认显示」可用', await restore2.isEnabled().catch(() => false));
  await restore2.click();
  await page.waitForTimeout(600);
  // 优化2：>1 个命名视图时，全局恢复先弹确认（爆炸半径说清：N 个视图 + 契约不受影响）；
  // 单视图时无确认框（与业界 per-view 即时重置一致）——两条路径都要走到「单默认视图」终态。
  const resetConfirm = page.locator('.ant-popconfirm').filter({ hasText: '清空全部个人视图' }).first();
  const hasResetConfirm = (await resetConfirm.count()) > 0;
  check('优化2：多视图全局恢复先确认（视图数与契约不受影响说明在框内）', hasResetConfirm, '未见确认框');
  if (hasResetConfirm) {
    await page.locator('.ant-popconfirm button').filter({ hasText: '清空并恢复' }).first().click();
  }
  await page.waitForTimeout(1500);
  const segFinal = await page.locator('.ant-segmented-item').allInnerTexts();
  const viewItemsFinal = segFinal.filter((t) => /默认视图|探针视图/.test(t));
  check('恢复基线：点击「恢复默认显示」后 UI 回到单「默认视图」', viewItemsFinal.length === 1 && viewItemsFinal[0] === '默认视图', `items=${viewItemsFinal.join(',')}`);
  const blobFinal = await getOverlay(token, entity.code);
  check('恢复基线：服务端 overlay 已清空（两端同清）', blobFinal == null || blobFinal === '', `blob=${JSON.stringify(blobFinal).slice(0, 80)}`);
  await shot('restored');
} catch (e) {
  check('探针执行未抛异常', false, String(e).slice(0, 300));
  await page.screenshot({ path: `${SHOT_DIR}/ui_personal_views_error.png` }).catch(() => {});
} finally {
  // 无论成败都清理，防止污染后续 E2E
  try {
    if (token && entity) {
      await deleteOverlay(token, entity.code);
      await clearLocalOverlayCache(entity.code).catch(() => {});
    }
    if (token && shieldCode) {
      await deleteOverlay(token, shieldCode);
      await clearLocalOverlayCache(shieldCode).catch(() => {});
      console.log('[cleanup] overlay 已清理（服务端 + 本地缓存）');
    }
    if (token && ccCode) {
      await deleteOverlay(token, ccCode);
      await clearLocalOverlayCache(ccCode).catch(() => {});
    }
  } catch { /* ignore */ }
  const fatal = pageErrors.filter((e) => !/favicon|ResizeObserver|Download the React DevTools/i.test(e));
  check('全流程无致命 console/page 错误', fatal.length === 0, fatal.slice(0, 3).join(' | ').slice(0, 300));
  await browser.close();
}

const failed = results.filter((r) => !r.ok);
console.log(`\n===== 个人显示运行时探针：${results.length - failed.length}/${results.length} 通过 =====`);
if (failed.length) {
  console.log('失败项：');
  failed.forEach((f) => console.log(`  ✗ ${f.name}  ← ${f.detail}`));
  process.exit(1);
}
