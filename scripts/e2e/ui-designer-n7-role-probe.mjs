/**
 * N7活体探针：字段级角色可见性（control.props.roles）
 *
 * 走 shell :3000 真实登录 → 元数据子应用 → 实体详情「界面设计器」Tab。
 *
 * 验证一条完整闭环（每段都是「负向基线 → 注入 → 正向 → 恢复」，断言有牙齿）：
 *
 *   1. 负向基线：注入前，选中角色后受限字段照常可见（证明后面的「不可见」是注入带来的，
 *      不是页面本来就空 —— 否则「什么都没渲染」也会被误判成通过）；
 *   2. 注入 props.roles=['reviewer'] + 专属 labelOverride 后：
 *      - 身份模拟选 employee ⇒ 该字段**不可见**（标签也不出现）；
 *      - 身份模拟选 reviewer ⇒ 该字段可见（双向对照，防「整体不渲染」的假绿）；
 *   3. 身份模拟清空（ctx.roles 缺失）⇒ fail-closed 隐藏（权限判定默认不放行）；
 *   4. 自检面板：注入 roles:[]（空数组）⇒ 报 error（引擎口径是「不限制」，
 *      与建模者「设了=谁都看不到」的直觉相反，必须当场拦下）；
 *   5. 恢复：清掉注入后重新读预览，字段回到基线可见。
 *
 * 用法：node scripts/e2e/ui-designer-n7-role-probe.mjs
 * 依赖：playwright（经共享 client 的 loadPlaywright 加载，见 e2e_lib/client.cjs）
 *
 * 口径说明：本探针验证 **SchemaPreview（创作侧预览）** 的渲染层。
 * 运行时 BoneDynamicForm 与详情抽屉 RuntimeDetailView 消费**同一个**
 * controlBaseStates + fieldVisible，由 vitest 单测（含授权/未授权双向对照）
 * + 变异验证覆盖。
 */
import {
  UI_BASE as BASE,
  SHOT_DIR,
  USER,
  PWD,
  loadPlaywright,
  makeChecker,
  login,
  api,
} from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const ENTITY_KEYWORD = process.env.BONE_E2E_ENTITY || '理赔案件';

const { results, check } = makeChecker();

/* ---------------- API 辅助（登录与带 token fetch 见 e2e_lib/client.cjs） ---------------- */
async function latestEntities(token, keyword) {
  const { data: d } = await api('GET', '/metadata/entities?page=1&size=500', undefined, { token });
  const recs = (d.data || {}).records || [];
  const byId = (a, b) => String(b.id).localeCompare(String(a.id));
  // 先精确匹配 displayName；退不回去再按「包含关键字」（同名实体常有多个，取最新 id）。
  // 实测踩坑：实体名是「理赔案件」而非「理赔案件实体」，精确写死很容易对不上。
  const exact = recs.filter((x) => x.displayName === keyword).sort(byId);
  if (exact.length) return exact[0];
  const loose = recs.filter((x) => String(x.displayName || '').includes(keyword)).sort(byId);
  return loose[0] || null;
}

async function getSchema(token, eid) {
  const { data: d } = await api('GET', `/metadata/entities/${eid}/ui-schema`, undefined, { token });
  const data = d.data || {};
  if (typeof data.json === 'string') data.json = JSON.parse(data.json);
  return data;
}

async function putSchema(token, eid, schema, lock) {
  const { status, data: d } = await api('PUT', `/metadata/entities/${eid}/ui-schema`, { json: schema }, {
    token,
    headers: { 'If-Match': String(lock) },
  });
  return { status, body: d };
}

/**
 * 注入 N7：给首个**文本型**控件加 props.roles，并打一个专属 labelOverride。
 *
 * 为什么要专属 label：预览 label 优先取 labelOverride，用它才能按标签精确定位那一行，
 * 不依赖「实体字段显示名恰好等于 fieldCode」（实测显示名是中文，不会等于字段码）。
 */
const N7_LABEL = 'N7探针受限字段';
function injectRoles(schema, roles) {
  const next = JSON.parse(JSON.stringify(schema));
  const ctrls = (next.views?.form?.sections ?? []).flatMap((s) => s.controls ?? []);
  const TEXT = new Set(['INPUT', 'TEXTAREA']);
  const t = ctrls.find((c) => TEXT.has(String(c.controlKind ?? 'INPUT').toUpperCase()) && c.fieldCode);
  if (!t) return { schema: next, code: null };
  t.props = { ...(t.props ?? {}), roles };
  t.labelOverride = N7_LABEL;
  return { schema: next, code: t.fieldCode };
}

/* ---------------- UI 辅助 ---------------- */
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.setDefaultTimeout(25000);
const consoleErrors = [];
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

const shot = async (n) => {
  const p = `${SHOT_DIR}/ui_designer_n7_${n}.png`;
  await page.screenshot({ path: p, fullPage: false });
  return p;
};

async function waitForStable(sel, rounds = 3, gap = 400) {
  let last = '';
  for (let i = 0; i < rounds; i++) {
    await page.waitForTimeout(gap);
    const cur = await page.locator(sel).first().innerText().catch(() => '');
    if (cur && cur === last) return cur;
    last = cur;
  }
  return last;
}

async function uiLogin() {
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"], input[placeholder*="密码"]', { timeout: 20000 });
  const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
  if (await page.locator(userSel).first().count()) await page.locator(userSel).first().fill(USER);
  const pwd = page.locator('input[type="password"]').first();
  await pwd.fill(PWD);
  await pwd.press('Enter');
  await page.waitForTimeout(1200);
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('button').filter({ hasText: /登\s*录/ }).first().click().catch(() => {});
    await page.waitForTimeout(3000);
  }
  try {
    await page.waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 });
    return true;
  } catch { return false; }
}

async function dismissTour() {
  const skip = page.locator('button, a').filter({ hasText: '跳过引导' }).first();
  if (await skip.count()) {
    await skip.click({ timeout: 4000 }).catch(() => {});
    await page.waitForTimeout(400);
  }
}

async function openDesigner(entityId) {
  await page.goto(`${BASE}/metadata#/entities/${entityId}`, { waitUntil: 'domcontentloaded' });
  await page.reload({ waitUntil: 'domcontentloaded' });
  const tab = page.locator('.ant-tabs-tab').filter({ hasText: '界面设计器' }).first();
  await tab.waitFor({ state: 'visible', timeout: 25000 });
  await tab.click();
  await page.waitForSelector('#subapp-viewport .uis-root, .uis-root', { timeout: 25000 });
  await dismissTour();
  return waitForStable('.uis-root');
}

/** 切身份模拟里的角色：opts.off = true 表示「取消勾选该角色」 */
async function setSimRoles(role, opts = {}) {
  const bar = page.locator('.uis-idbar').first();
  await bar.waitFor({ state: 'visible', timeout: 10000 });
  // 身份模拟栏里第一个 Select 是「角色」
  const sel = bar.locator('.ant-select').first();
  await sel.click();
  await page.waitForTimeout(400);
  const opt = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
    .filter({ hasText: role }).first();
  const found = await opt.count();
  if (found) {
    const checked = (await opt.getAttribute('aria-selected')) === 'true';
    const want = !opts.off;
    if (checked !== want) await opt.click();
    await page.waitForTimeout(500);
  }
  // 关闭下拉（Esc），否则会遮住后续判断
  await page.keyboard.press('Escape');
  await page.waitForTimeout(500);
  return !!found;
}

/** 受限字段在预览里是否可见（按专属 label 定位 .uis-fld 行） */
async function isRestrictedVisible() {
  const row = page.locator('.uis-fld').filter({ hasText: N7_LABEL }).first();
  return await row.isVisible().catch(() => false);
}

/** 切到自检面板并读文本（锚点：LintPanel 独有的「后端校验」按钮） */
async function readLintPanel() {
  const tab = page.locator('.ant-tabs-tab').filter({ hasText: /^自\s*检/ }).first();
  try {
    await tab.waitFor({ state: 'visible', timeout: 6000 });
    await tab.click({ timeout: 6000 });
  } catch { return { landed: false, text: '' }; }
  await page.waitForTimeout(1200);
  const anchor = page.locator('button', { hasText: /^后端校验$/ }).first();
  const landed = await anchor.isVisible().catch(() => false);
  const text = landed
    ? await anchor.locator('xpath=ancestor::div[contains(@class,"ant-tabs-tabpane")][1]').innerText().catch(() => '')
    : '';
  return { landed, text };
}

/** 回到「结构」面板（精确匹配：实体外层也有「结构16」tab，用前缀会点错） */
async function backToStructure() {
  const tab = page.locator('.ant-tabs-tab').filter({ hasText: /^结构$/ }).last();
  try { await tab.click({ timeout: 6000 }); await page.waitForTimeout(800); } catch { /* 忽略 */ }
}

/* ---------------- 主流程 ---------------- */
let exitCode = 0;
let token = null;
let eid = null;
let origSchema = null;
let origLock = null;

try {
  token = await login();
  const ent = await latestEntities(token, ENTITY_KEYWORD);
  if (!ent) throw new Error(`未找到实体「${ENTITY_KEYWORD}」`);
  eid = ent.id;
  console.log(`实体：${ent.displayName}（${eid}）`);

  const cur = await getSchema(token, eid);
  origSchema = cur.json;
  // 乐观锁字段是 lockVersion（实测：写错成 lock/version 会拿到 412
  // META_UI_SCHEMA_PRECONDITION_REQUIRED —— 注入全部失败、后续「字段不可见」断言
  // 因字段压根没被注入而全部假绿）。
  origLock = cur.lockVersion;

  // ---------- 1. 负向基线：注入前该 label 不存在 ----------
  if (!(await uiLogin())) throw new Error('UI 登录失败');
  await openDesigner(eid);
  check('G0 预览已加载', !!(await page.locator('.uis-root').count()));

  // 注入 roles:['reviewer']
  const inj = injectRoles(origSchema, ['admin']);
  if (!inj.code) throw new Error('未找到可注入的文本型控件');
  const put1 = await putSchema(token, eid, inj.schema, origLock);
  check('G1 注入 props.roles 成功', put1.status === 200 || put1.status === 204, `HTTP ${put1.status} ${JSON.stringify(put1.body).slice(0, 200)}`);
  // **注入失败必须立即中止**：字段压根没写进草稿 ⇒ 后续所有「该字段不可见」断言都会
  // 因为「找不到这个 label」而恒为真 = 典型假绿（实测踩过：乐观锁字段取错 → 412 →
  // G2/G4 全绿，误导为功能通过）。
  if (!(put1.status === 200 || put1.status === 204)) {
    throw new Error('注入失败，后续断言无意义（会假绿），已中止');
  }

  // 重新打开（探针必须 reload：同 URL goto 不触发真刷新）
  await openDesigner(eid);

  // ---------- 2. 身份模拟 = employee ⇒ 受限字段不可见 ----------
  await setSimRoles('employee');
  const hiddenForEmployee = !(await isRestrictedVisible());
  check('G2 未授权角色（employee）看不到受限字段', hiddenForEmployee, '该 label 仍可见');
  await shot('employee');

  // ---------- 3. 身份模拟 = reviewer ⇒ 可见（双向对照，防整体不渲染假绿） ----------
  await setSimRoles('admin');
  const visibleForAdmin = await isRestrictedVisible();
  check('G3 授权角色（admin）能看到受限字段（双向对照）', visibleForAdmin, '授权角色也看不到 ⇒ 疑似整体不渲染');
  await shot('admin');

  // ---------- 4. 身份模拟清空 ⇒ fail-closed 隐藏 ----------
  await setSimRoles('admin', { off: true });
  await setSimRoles('employee', { off: true });
  const hiddenNoCtx = !(await isRestrictedVisible());
  check('G4 身份清空（ctx.roles 缺失）⇒ fail-closed 隐藏', hiddenNoCtx, '无身份反而可见（权限判定必须默认不放行）');

  // ---------- 5. 自检：roles:[] 报 error ----------
  backToStructure();
  const inj2 = injectRoles(origSchema, []);
  const curMid = await getSchema(token, eid);
  const put2 = await putSchema(token, eid, inj2.schema, curMid.lockVersion);
  check('G5 注入 roles:[] 成功', put2.status === 200 || put2.status === 204, `HTTP ${put2.status}`);
  if (!(put2.status === 200 || put2.status === 204)) {
    throw new Error('roles:[] 注入失败，自检判据会空过，已中止');
  }
  await openDesigner(eid);
  const lint = await readLintPanel();
  check('G6 自检面板已就位', lint.landed, '未点到自检面板，判据会空过');
  check('G7 空数组 roles 在自检里报 error', /空数组/.test(lint.text), '自检未提示空数组等于不限制');
  await shot('lint');

  // ---------- 6. 恢复 ----------
  const cur2 = await getSchema(token, eid);
  const put3 = await putSchema(token, eid, origSchema, cur2.lockVersion);
  check('G8 恢复原草稿成功', put3.status === 200 || put3.status === 204, `HTTP ${put3.status}`);
  await openDesigner(eid);
  const restoredVisible = await isRestrictedVisible();
  check('G9 恢复后字段回到基线（无残留 roles 限制）', !restoredVisible, '恢复后仍不可见 ⇒ 恢复未生效');
} catch (e) {
  check('探针执行', false, String(e && e.message ? e.message : e).slice(0, 300));
} finally {
  // 无论成败都必须恢复草稿（否则污染用户数据）
  if (token && eid && origSchema) {
    try {
      const cur = await getSchema(token, eid);
      await putSchema(token, eid, origSchema, cur.lockVersion);
      console.log('草稿已恢复');
    } catch (e) {
      console.log('⚠ 恢复草稿失败：' + String(e && e.message ? e.message : e).slice(0, 200));
    }
  }
  if (consoleErrors.length) {
    console.log('\nconsole 错误（' + consoleErrors.length + '）：');
    consoleErrors.slice(0, 5).forEach((e) => console.log('  - ' + e));
  }
  const failed = results.filter((r) => !r.ok);
  console.log(`\n===== N7 探针结果：${results.length - failed.length}/${results.length} 通过 =====`);
  if (failed.length) {
    console.log('失败项：');
    failed.forEach((f) => console.log('  ✗ ' + f.name + (f.detail ? '  ← ' + f.detail : '')));
    exitCode = 1;
  }
  await browser.close();
  process.exit(exitCode);
}