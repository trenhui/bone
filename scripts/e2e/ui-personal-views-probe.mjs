/**
 * 「列表显示」个人视图（v3 bundle）生产 UI 探针：
 * shell :3000 登录 → 元数据子应用 → 实体详情「列表显示」Tab，验证：
 *   1. P0.1 身份徽标「个人偏好 · 仅自己可见」常驻；
 *   2. 个人视图切换器（默认视图单套起步）；
 *   3. **视图隔离（核心断言）**：新建视图 B 隐藏某列 → 切回默认视图该列必须恢复可见
 *      （偏好串台是多视图最常见 bug，单测已覆盖纯函数，这里验证真实 React 状态流）；
 *   4. 服务端落盘：UI 操作后 GET ui-overlay 必须是 v3 bundle（2 views + 正确 activeViewId）；
 *   5. **运行时消费**：数据 Tab 的表头随生效视图变化（activeOverlay 在 RuntimeDataManagement 真生效）；
 *   6. 反向导航：设计器工具栏「个人偏好」按钮一键回到本页（P1.5 两层闭环）；
 *   7. 收尾恢复：DELETE overlay → UI 回到单视图默认态（不污染 E2E 基线）。
 *
 * 口径：overlay 后端是不透明 blob（键含 userId），探针用同一登录态操作，
 * 与真实用户路径完全一致（非 mock）。
 *
 * 用法：node scripts/e2e/ui-personal-views-probe.mjs
 */
import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const SHOT_DIR = '/Users/renhui.trh/wps/bone/logs';
const BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
const API = process.env.BONE_E2E_BASE || 'http://127.0.0.1:8888/api/v1';
const USER = process.env.BONE_E2E_USER || 'admin';
const PWD = process.env.BONE_E2E_PWD || '123456';

const results = [];
const check = (name, cond, detail = '') => {
  results.push({ name, ok: !!cond, detail });
  console.log(`${cond ? '✓' : '✗'} ${name}${cond ? '' : '  ← ' + detail}`);
};

/* ---------------- API 辅助 ---------------- */
async function login() {
  const r = await fetch(`${API}/iam/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PWD }),
  });
  const d = await r.json();
  const tok = (d.data || {}).token;
  if (!tok) throw new Error(`登录失败 ${r.status} ${JSON.stringify(d).slice(0, 200)}`);
  return tok;
}

/**
 * 取最新**已发布**实体（deliveryMode=1 自动生成界面 & status=1 已发布）。
 * 不能图省事取最新样板：E2E 每轮新建的是草稿态，数据 Tab 对草稿实体拒显
 * （「请先发布物理建表」），运行时消费断言会假红。
 */
async function latestPublishedEntity(token, keyword) {
  const r = await fetch(`${API}/metadata/entities?page=1&size=500`, {
    headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0' },
  });
  const d = await r.json();
  const recs = (d.data || {}).records || [];
  const hits = recs
    .filter((x) => x.displayName === keyword && x.deliveryMode === 1 && x.status === 1)
    .sort((a, b) => String(b.id).localeCompare(String(a.id)));
  return hits[0] || null;
}

async function getOverlay(token, entityCode) {
  const r = await fetch(`${API}/metadata/entities/${encodeURIComponent(entityCode)}/ui-overlay`, {
    headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0' },
  });
  const d = await r.json().catch(() => ({}));
  return (d.data || {}).json ?? (d.data || null);
}

async function deleteOverlay(token, entityCode) {
  await fetch(`${API}/metadata/entities/${encodeURIComponent(entityCode)}/ui-overlay`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0' },
  });
}

async function getContractColumns(token, eid) {
  const r = await fetch(`${API}/metadata/entities/${eid}/ui-schema`, {
    headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0' },
  });
  const d = await r.json();
  const raw = (d.data || {}).json;
  if (!raw) return [];
  const s = typeof raw === 'string' ? JSON.parse(raw) : raw;
  return (s.views?.list?.columns || []).filter((c) => c && c.fieldCode).map((c) => c.fieldCode);
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

/** 等界面 Tab 挂载（徽标出现即就绪——服务端 overlay hydrate 完成后才渲染切换器） */
async function waitInterfaceReady() {
  await page.locator('.ant-tag').filter({ hasText: '个人偏好 · 仅自己可见' }).first()
    .waitFor({ state: 'visible', timeout: 25000 });
  // 视图切换器 Segmented（含「默认视图」项）就绪
  await page.locator('.ant-segmented-item').filter({ hasText: '默认视图' }).first()
    .waitFor({ state: 'visible', timeout: 10000 }).catch(() => {});
}

/** 视图切换器里当前选中项的名字 */
async function activeViewName() {
  const sel = page.locator('.ant-segmented-item-selected').filter({ hasText: /视图|默认/ });
  if (await sel.count()) return (await sel.first().innerText()).trim();
  // 兜底：从含视图名的 segmented 里找 selected
  const any = await page.locator('.ant-segmented-item-selected').allInnerTexts();
  return any.join('|');
}

async function shot(name) {
  await page.screenshot({ path: `${SHOT_DIR}/ui_personal_views_${name}.png`, fullPage: false });
}

const VIEW_A = '探针视图A';
let token;
let entity;

try {
  check('UI 登录成功', await uiLogin(), `url=${page.url()}`);

  token = await login();
  entity = await latestPublishedEntity(token, '报销单');
  check('取到已发布报销实体（运行时消费断言前提）', !!entity, entity ? `${entity.code} ${entity.id} status=${entity.status}` : '未找到');
  if (!entity) throw new Error('无实体，探针中止');

  const contractCols = await getContractColumns(token, entity.id);
  check('报销实体有列表契约列（隔离断言需要契约列）', contractCols.length > 0, `cols=${contractCols.join(',')}`);
  const probeCol = contractCols[0];

  /* ---------- 0. 基线：清 overlay → 单视图默认态 ---------- */
  await deleteOverlay(token, entity.code);
  await openTab(entity.id, 'ui');
  await waitInterfaceReady();

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
  const activeAfterCreate = await activeViewName();
  check('新建后自动切换到新视图', activeAfterCreate.includes(VIEW_A), `active=${activeAfterCreate}`);
  await shot('view_created');

  /* ---------- 2. 视图隔离（核心）：新视图隐藏一列 → 切回默认视图必须恢复 ---------- */
  // 配置表格里第一个可点的 Switch（避开契约置灰/未在契约的行）
  const rowSwitch = page.locator('.ant-table-row .ant-switch:not(.ant-switch-disabled)').first();
  await rowSwitch.waitFor({ state: 'visible', timeout: 8000 });
  await rowSwitch.click();
  await page.waitForTimeout(600);
  const hiddenTagA = await page.locator('.ant-table-row').filter({ hasText: '个人隐藏' }).count();
  check(`视图 A 隐藏首列生效（出现「个人隐藏」标记）`, hiddenTagA >= 1, `rows=${hiddenTagA}`);

  // 服务端落盘核对：必须是 v3 bundle，2 views，active=A
  const blob1 = await getOverlay(token, entity.code);
  let bundle1 = null;
  try { bundle1 = typeof blob1 === 'string' ? JSON.parse(blob1) : blob1; } catch { /* ignore */ }
  check('服务端落盘为 v3 bundle（2 视图）',
    !!bundle1 && Array.isArray(bundle1.views) && bundle1.views.length === 2 && bundle1.version === 3,
    JSON.stringify(bundle1).slice(0, 160));

  // 切回默认视图：隐藏必须恢复（串台 bug 会在这里现形）
  await page.locator('.ant-segmented-item').filter({ hasText: '默认视图' }).first().click();
  await page.waitForTimeout(900);
  const hiddenTagDefault = await page.locator('.ant-table-row').filter({ hasText: '个人隐藏' }).count();
  check('视图隔离：切回默认视图，个人隐藏标记消失（不串台）', hiddenTagDefault === 0, `rows=${hiddenTagDefault}`);

  /* ---------- 3. 运行时消费：数据 Tab 表头随生效视图变化 ---------- */
  // 回视图 A，确保探针列处于「个人隐藏」态。
  // ⚠ 口径：antd Switch 的 ant-switch-checked = 开（可见）；隐藏态没有该 class。
  //   曾把条件写反（「未选中就点」）⇒ 把已隐藏的列又点开 ⇒ 下游断言假红。
  //   现在先自证前置条件（开关确已关闭），再断言运行时表头。
  await page.locator('.ant-segmented-item').filter({ hasText: VIEW_A }).first().click();
  await page.waitForTimeout(800);
  const probeRow = page.locator('.ant-table-row').filter({ hasText: probeCol }).first();
  const aSwitch = probeRow.locator('.ant-switch').first();
  let aCls = (await aSwitch.getAttribute('class')) || '';
  if (aCls.includes('ant-switch-checked')) {
    await aSwitch.click(); // 可见 → 置为隐藏
    await page.waitForTimeout(600);
    aCls = (await aSwitch.getAttribute('class')) || '';
  }
  check('前置条件自证：视图 A 中探针列确为个人隐藏态', !aCls.includes('ant-switch-checked'), `cls=${aCls}`);

  // 数据 Tab（视图 A 生效中）：表头不应包含该列
  // ⚠ 断言口径：表头是**显示名/契约标题**，不是 fieldCode——曾按 code 匹配写出
  // 「恒真空断言」（负向永远为真、正向才现形）。这里从配置表格行取显示名，
  // 并同时断言其他契约列在场（证明表格真的渲染了，不是空表头的假绿）。
  const probeColName = (await page.locator('.ant-table-row').filter({ hasText: probeCol }).first().locator('td').first().innerText()).trim();
  await openTab(entity.id, 'data');
  await page.waitForSelector('#subapp-viewport .ant-table', { timeout: 25000 });
  await page.waitForTimeout(2500); // 等 overlay hydrate + 列投影
  const headersA = (await page.locator('#subapp-viewport .ant-table thead').first().innerText()).replace(/\s+/g, '');
  check('运行时（视图 A）：被个人隐藏的列不出现在数据表表头（且其他列在场，非空断言）',
    headersA.length > 0 && !headersA.includes(probeColName) && headersA.includes('操作'),
    `probeCol=${probeCol}/${probeColName} header=${headersA.slice(0, 120)}`);
  await shot('runtime_view_a');

  // 切回默认视图（在列表显示页操作）→ 数据 Tab 表头应恢复
  await openTab(entity.id, 'ui');
  await waitInterfaceReady();
  await page.locator('.ant-segmented-item').filter({ hasText: '默认视图' }).first().click();
  await page.waitForTimeout(900);
  await openTab(entity.id, 'data');
  await page.waitForSelector('#subapp-viewport .ant-table', { timeout: 25000 });
  await page.waitForTimeout(2500);
  const headersDefault = (await page.locator('#subapp-viewport .ant-table thead').first().innerText()).replace(/\s+/g, '');
  check('运行时（默认视图）：同一列恢复显示（activeOverlay 在 RuntimeDataManagement 真生效）',
    headersDefault.includes(probeColName), `probeCol=${probeCol}/${probeColName} header=${headersDefault.slice(0, 120)}`);
  await shot('runtime_default_view');

  /* ---------- 4. 反向导航（P1.5）：设计器 → 个人偏好 ---------- */
  await openTab(entity.id, 'uischema');
  await page.waitForSelector('.uis-root', { timeout: 25000 });
  const skip = page.locator('button, a').filter({ hasText: '跳过引导' }).first();
  if (await skip.count()) { await skip.click({ timeout: 4000 }).catch(() => {}); await page.waitForTimeout(400); }
  const personalBtn = page.locator('button').filter({ hasText: '个人偏好' }).first();
  check('设计器工具栏渲染「个人偏好」反向入口', await personalBtn.count() === 1);
  if (await personalBtn.count()) {
    await personalBtn.click();
    await page.waitForTimeout(1500);
    const backBadge = await page.locator('.ant-tag').filter({ hasText: '个人偏好 · 仅自己可见' }).count();
    check('点击后回到「列表显示」Tab（两层闭环）', backBadge === 1, `badge=${backBadge}`);
    await shot('reverse_nav');
  }

  /* ---------- 5. 收尾：UI「恢复默认显示」（resetOverlay 本地+服务端两端同清） ---------- */
  // ⚠ 不能只调 API DELETE：浏览器 localStorage 缓存还留着旧 bundle，hydrate 会
  // 「复活」它（overlaySync 注释写明的两端同清纪律）。必须走产品自己的按钮。
  await openTab(entity.id, 'ui');
  await waitInterfaceReady();
  await page.waitForTimeout(600);
  // 先切到有未存偏好调整的视图（恢复按钮 dirty 时才可用）
  await page.locator('.ant-segmented-item').filter({ hasText: VIEW_A }).first().click();
  await page.waitForTimeout(800);
  const restoreBtn = page.locator('button').filter({ hasText: '恢复默认显示' }).first();
  const restoreEnabled = await restoreBtn.isEnabled().catch(() => false);
  check('「恢复默认显示」在 dirty 态可用', restoreEnabled);
  if (restoreEnabled) {
    await restoreBtn.click();
    await page.waitForTimeout(1500);
  }
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
    if (token && entity) await deleteOverlay(token, entity.code);
    console.log('[cleanup] overlay 已清理');
  } catch { /* ignore */ }
  const fatal = pageErrors.filter((e) => !/favicon|ResizeObserver|Download the React DevTools/i.test(e));
  check('全流程无致命 console/page 错误', fatal.length === 0, fatal.slice(0, 3).join(' | ').slice(0, 300));
  await browser.close();
}

const failed = results.filter((r) => !r.ok);
console.log(`\n===== 个人视图探针：${results.length - failed.length}/${results.length} 通过 =====`);
if (failed.length) {
  console.log('失败项：');
  failed.forEach((f) => console.log(`  ✗ ${f.name}  ← ${f.detail}`));
  process.exit(1);
}
