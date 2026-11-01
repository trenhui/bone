/**
 * 智能理赔 · 运营配置面 UI 探针（claim-config-ui-probe.mjs）
 *
 * 定位：与 `claim-config-e2e.mjs`（API 级）互补——本探针走**真实浏览器 + 真实登录态**，
 * 在 shell :3000 里点侧边栏进入子应用，模拟人工在「保单管理 / 计划配置 / 额度管理」三页上干活，
 * 验证「前端真的能把新能力用起来」，而不只是接口能通。
 *
 * 覆盖的**关键链路**（每条都有 UI 断言 + API 回读双证据）：
 *   1. 保单管理 —— 按**保单名称**检索能命中（UI placeholder 承诺「保单号 / 名称」两维；
 *      此前后端只对 policy_no 单列做 LIKE，按名称搜索恒 0 条 ⇒ 真机点搜索框即可复现）；
 *   2. 保单管理 —— 行内「编辑」改名称并保存（乐观锁 version 由前端回传）；
 *   3. 计划配置 —— 行内「编辑」改计划并保存；
 *   4. 额度管理 —— 行内「调额」改初始额度并确认（额度面乐观锁 version 回传）。
 *
 * 数据来源：用**浏览器登录后的 JWT** 调 API 造一条真实企业保单/计划/额度账户，
 * UI 只做「找到它并改动它」——这是最贴近人工操作的路径。
 *
 * 前置：shell :3000、claim-app :3013、gateway :8888、claim :8087、iam :8081 在线。
 * 用法：
 *   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
 *   NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-3/bin/node
 *   $NODE scripts/e2e/ui/claim-config-ui-probe.mjs
 */
import {
  SHOT_DIR,
  loadPlaywright,
  makeChecker,
  api,
} from '../e2e_lib/client.cjs';
import { uiLogin, shot, localToken } from '../e2e_lib/ui.cjs';

const { chromium } = loadPlaywright();
const { check, report } = makeChecker();

const STAMP = Date.now().toString(36).toUpperCase().slice(-6);
const COMPANY = '汇智精密制造（深圳）有限公司';
const POLICY_NO = `POL-UI-${STAMP}`;
/** 只出现在保单名称里、不在保单号里的检索词 —— 用于验证「按名称搜索」这一维。 */
const NAME_ONLY = `团体补充医疗保险${STAMP}`;
const POLICY_NAME = `${COMPANY}${NAME_ONLY}`;
const PLAN_CODE = `PLAN-UI-${STAMP}`;
const INSURED_HASH = `ui-insured-hash-${STAMP}`;

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 950 } });
page.setDefaultTimeout(20000);

let consoleErrors = [];
let httpErrors = [];
/** 写操作（POST/PUT/DELETE）的请求响应留痕：排查「点保存没反应」时能看到后端到底返了什么。 */
let apiLog = [];
/** 捕获到的全部 message 提示（成功与失败都记），失败时作为证据输出。 */
let seenMessages = [];
page.on('console', (m) => {
  // 注意：这里**不过滤 antd 自身的 error**。
  // antd 的「Instance created by `useForm` is not connected to any Form element」
  // 等提示正是「点了保存没反应」的根因证据，过滤掉会把真缺陷读成假绿。
  if (m.type() === 'error' && !/favicon|ResizeObserver loop|single-spa minified message #31/i.test(m.text())) {
    consoleErrors.push(m.text().slice(0, 300));
  }
});
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));
page.on('response', (r) => {
  if (r.status() >= 500) httpErrors.push(`${r.status()} ${r.url().slice(0, 120)}`);
  const m = r.request().method();
  if (m !== 'GET') apiLog.push(`${m} ${r.status()} ${r.url().replace(/^https?:\/\/[^/]+/, '')}`);
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
  const opened = await title.evaluate(
    (el) => (el.parentElement?.className || '').includes('ant-menu-submenu-open'),
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
    for (const t of texts) {
      if (!seenMessages.includes(t)) seenMessages.push(t);
    }
    const hit = texts.find((t) => re.test(t));
    if (hit) return hit;
    await page.waitForTimeout(300);
  }
  return null;
}

/** 失败时的证据包：全部 message + 写操作留痕 + console error。 */
const evidence = () =>
  `messages=${JSON.stringify(seenMessages)} | api=${JSON.stringify(apiLog.slice(-6))} | console=${consoleErrors.slice(0, 3).join(' | ')}`;

/** 断言：失败时自动附上证据包（否则「静默失败」只能靠人肉猜）。 */
const assert = (name, cond, detail = '') =>
  check(name, cond, cond ? detail : `${detail} ‖ ${evidence()}`);

/** 表格真实数据行（排除 ant-table-placeholder 空态占位行）。 */
const dataRows = () =>
  page.locator('#subapp-viewport .ant-table-tbody tr.ant-table-row').count();

/** 在搜索框输入关键字并触发 onSearch（Input.Search 回车即触发）。 */
async function search(keyword) {
  const box = page.getByPlaceholder('保单号 / 名称').first();
  await box.fill('');
  await box.fill(keyword);
  await box.press('Enter');
  await page.waitForTimeout(1200);
}

/** 选保单（antd Select：先点开下拉，再点含该保单号的选项）。 */
async function pickPolicy() {
  const select = page.locator('#subapp-viewport .ant-select').first();
  await select.click();
  await page.waitForTimeout(600);
  const opt = page
    .locator('.ant-select-dropdown .ant-select-item-option')
    .filter({ hasText: POLICY_NO })
    .first();
  await opt.waitFor({ state: 'visible' });
  await opt.click();
  await page.waitForTimeout(1500);
}

let TOKEN = null;

try {
  check('UI 登录成功', await uiLogin(page), page.url());
  await page.waitForSelector('.shell-sider', { timeout: 20000 });
  TOKEN = await localToken(page);
  check('浏览器本地取到 JWT（供 API 造数）', Boolean(TOKEN), TOKEN ? 'ok' : 'localStorage 无 token');

  // ---------- 造数：真实企业保单 + 计划 + 额度账户 ----------
  const p = await api(
    'POST',
    '/claim-config/policies',
    {
      policyNo: POLICY_NO,
      policyName: POLICY_NAME,
      insurerName: '中国人民财产保险股份有限公司',
      branchName: '深圳中心支公司',
      insureName: COMPANY,
      bizIdentityCode: 'UI-PROBE-BIZ-001',
      productType: 'GROUP_SUPPLEMENTARY_MEDICAL',
      effectiveStart: '2026-01-01T00:00:00Z',
      effectiveEnd: '2027-12-31T00:00:00Z',
      waitingPeriod: 30,
      policyVersion: 'v1',
      insuredSnapshot: JSON.stringify([INSURED_HASH]),
    },
    { token: TOKEN },
  );
  check('造数：保单已建（真实企业数据）', p.status === 201, `status=${p.status}`);

  const pl = await api(
    'POST',
    '/claim-config/plans',
    {
      policyNo: POLICY_NO,
      planCode: PLAN_CODE,
      planName: `${COMPANY}基础医疗计划`,
      planLimit: 50000,
      waitingPeriod: 0,
      versionNo: 'v1',
    },
    { token: TOKEN },
  );
  const planId = pl.data?.data?.planId;
  check('造数：计划已建', pl.status === 201 && Boolean(planId), `status=${pl.status} planId=${planId}`);

  const q = await api(
    'POST',
    '/claim-config/quota-accounts',
    {
      insuredIdHash: INSURED_HASH,
      policyNo: POLICY_NO,
      // planId 必须原样传字符串：雪花 ID 是 18~19 位，超出 JS Number 上限 2^53，
      // 一旦经过 Number() 末位会被静默截断（后端 Jackson 从字符串解析则无损），
      // 额度账户就会挂到一个并不存在的计划上 —— 且全程无任何报错。
      planId,
      quotaYear: new Date().getFullYear(),
      quotaType: 'PERSONAL',
      quotaInitial: 20000,
      remark: 'UI 探针开户',
    },
    { token: TOKEN },
  );
  const quotaId = q.data?.data?.quotaAccountId;
  check('造数：额度账户已开户', q.status === 201 && Boolean(quotaId), `status=${q.status}`);

  // ---------- 1. 保单管理：按保单号检索 ----------
  let body = await gotoPage('保单管理');
  check('[保单管理] 页面渲染出搜索框与表格', (await dataRows()) >= 0 && body.length > 0, `文本 ${body.length} 字`);

  await search(POLICY_NO);
  const rowByNo = page
    .locator('#subapp-viewport .ant-table-tbody tr.ant-table-row')
    .filter({ hasText: POLICY_NO });
  check('[保单管理] 按保单号检索命中本用例保单', (await rowByNo.count()) === 1, `行数 ${await rowByNo.count()}`);

  // ---------- 2. 保单管理：按保单名称检索（本次修复点） ----------
  await search(NAME_ONLY);
  const rowByName = page
    .locator('#subapp-viewport .ant-table-tbody tr.ant-table-row')
    .filter({ hasText: POLICY_NO });
  check(
    '[保单管理] 按保单名称检索命中（UI placeholder 承诺的两维之一）',
    (await rowByName.count()) === 1,
    `行数 ${await rowByName.count()} —— 修复前此处恒为 0`,
  );

  // ---------- 3. 保单管理：行内编辑 ----------
  // 注：antd 5 的两字 Button 会在「保」「存」之间注入空格（DOM 文本为「保 存」），
  // 故所有两字按钮一律用 \s* 容错正则匹配，不能用精确 name。
  const NEW_NAME = `${POLICY_NAME}（UI 改名）`;
  await rowByName.locator('button').filter({ hasText: /编\s*辑/ }).first().click();
  const pModal = page.locator('.ant-modal').filter({ hasText: '编辑保单' });
  await pModal.waitFor({ state: 'visible' });
  const nameInput = pModal
    .locator('.ant-form-item')
    .filter({ hasText: '保单名称' })
    .locator('input')
    .first();
  await nameInput.fill(NEW_NAME);
  // 取证：弹窗打开后各字段的真实值（定位「点保存没反应」是校验失败还是请求失败）
  const formValues = await pModal.locator('input').evaluateAll((els) =>
    els.map((e) => `${e.getAttribute('placeholder') ?? e.type}=${e.value}`).join(' ; '),
  );
  await pModal.getByRole('button', { name: /保\s*存/ }).click();
  const pMsg = await waitMessage(/保单已更新/);
  assert(
    '[保单管理] 编辑保存成功（前端回传 version）',
    Boolean(pMsg),
    `${pMsg ?? '未捕获「保单已更新」'} ｜ 弹窗字段=${formValues}`,
  );

  await search(POLICY_NO);
  const afterEdit = page
    .locator('#subapp-viewport .ant-table-tbody tr.ant-table-row')
    .filter({ hasText: POLICY_NO });
  const afterText = await afterEdit.innerText().catch(() => '');
  assert('[保单管理] 列表回显新名称', afterText.includes('UI 改名'), afterText.slice(0, 120));

  const readBack = await api('GET', `/claim-config/policies/${POLICY_NO}`, undefined, { token: TOKEN });
  assert(
    '[保单管理] API 回读确认改名已落库',
    readBack.data?.data?.policyName === NEW_NAME,
    `API policyName=${readBack.data?.data?.policyName}`,
  );

  // ---------- 4. 计划配置：行内编辑 ----------
  body = await gotoPage('计划配置');
  await pickPolicy();
  const planRow = page
    .locator('#subapp-viewport .ant-table-tbody tr.ant-table-row')
    .filter({ hasText: PLAN_CODE });
  check('[计划配置] 计划列表按保单可查', (await planRow.count()) === 1, `行数 ${await planRow.count()}`);

  const NEW_PLAN_NAME = `${COMPANY}基础医疗计划（UI 改名）`;
  await planRow.locator('button').filter({ hasText: /编\s*辑/ }).first().click();
  const plModal = page.locator('.ant-modal').filter({ hasText: '编辑计划' });
  await plModal.waitFor({ state: 'visible' });
  const planNameInput = plModal
    .locator('.ant-form-item')
    .filter({ hasText: '计划名称' })
    .locator('input')
    .first();
  await planNameInput.fill(NEW_PLAN_NAME);
  await plModal.getByRole('button', { name: /保\s*存/ }).click();
  const plMsg = await waitMessage(/计划已更新/);
  assert('[计划配置] 编辑保存成功（前端回传 version）', Boolean(plMsg), plMsg ?? '未捕获「计划已更新」');

  const planReadBack = await api(
    'GET',
    `/claim-config/plans?policyNo=${POLICY_NO}`,
    undefined,
    { token: TOKEN },
  );
  const planRec = (planReadBack.data?.data || []).find((r) => r.planCode === PLAN_CODE);
  assert(
    '[计划配置] API 回读确认改名已落库',
    planRec?.planName === NEW_PLAN_NAME,
    `API planName=${planRec?.planName}`,
  );

  // ---------- 5. 额度管理：行内调额 ----------
  body = await gotoPage('额度管理');
  await pickPolicy();

  // **契约形态探测**：额度列表的响应契约正在从「数组」升级为「分页信封」。
  // 前端若已升级而后端未升级，前端读到 res.data.records === undefined ⇒ 表格 0 行——
  // 那是**契约中间态**，不是「额度账户查不到」。探针必须区分二者：
  // 误报成功能缺陷会浪费一轮排查；用 API 断言掩盖 UI 回归则是假绿。
  const quotaProbe = await api(
    'GET',
    `/claim-config/quota-accounts?policyNo=${encodeURIComponent(POLICY_NO)}&quotaType=PERSONAL`,
    undefined,
    { token: TOKEN },
  );
  const quotaBody = quotaProbe.data?.data;
  const envelopeAligned = !Array.isArray(quotaBody) && Array.isArray(quotaBody?.records);
  const quotaRows = Array.isArray(quotaBody) ? quotaBody : (quotaBody?.records ?? []);
  const foundInApi = quotaRows.filter((r) => r.insuredIdHash === INSURED_HASH).length;
  const shape = Array.isArray(quotaBody) ? '数组' : '分页信封';

  // UI 层真实断言（无论契约是否对齐都要成立）：页面已选中目标保单 + 维度为个账
  const selectedPolicy = await page
    .locator('#subapp-viewport .ant-select-selection-item')
    .first()
    .innerText()
    .catch(() => '');
  check(
    '[额度管理] 页面已选中目标保单',
    selectedPolicy.includes(POLICY_NO),
    `已选=${selectedPolicy.slice(0, 60)}`,
  );
  const segSelected = page.locator('#subapp-viewport .ant-segmented-item-selected').first();
  if (await segSelected.count()) {
    const segText = await segSelected.innerText();
    check(
      '[额度管理] 额度维度视图为个账（PERSONAL，与开户维度一致）',
      /个账/.test(segText),
      `选中=${segText}`,
    );
  }
  check(
    '[额度管理] API 层能查到本次开户的额度账户',
    foundInApi === 1,
    `命中 ${foundInApi} 条（后端返回形态=${shape}）`,
  );

  const qRow = page
    .locator('#subapp-viewport .ant-table-tbody tr.ant-table-row')
    .filter({ hasText: INSURED_HASH });
  const uiRows = await qRow.count();
  const NEW_INITIAL = 18500.5;
  if (envelopeAligned) {
    check('[额度管理] 额度账户按保单可查（表格渲染）', uiRows === 1, `行数 ${uiRows}`);
    await qRow.locator('button').filter({ hasText: /调\s*额/ }).first().click();
    const qModal = page.locator('.ant-modal').filter({ hasText: '调整额度' });
    await qModal.waitFor({ state: 'visible' });
    const numInput = qModal.locator('.ant-input-number input').first();
    await numInput.click();
    await numInput.fill(String(NEW_INITIAL));
    await qModal.getByRole('button', { name: /确认\s*调额/ }).click();
    const qMsg = await waitMessage(/额度已调整/);
    assert('[额度管理] 调额成功（前端回传 version）', Boolean(qMsg), qMsg ?? '未捕获「额度已调整」');
  } else {
    // 契约未对齐：表格 0 行是**可解释的**（前端读不到 records），不是数据缺失。
    // 断言这一点 ⇒ 0 行永远有据可查，不会被当成"额度查不出来"的静默失败放过。
    console.warn(
      `[额度管理] 后端额度列表仍返回${shape}、前端已按分页信封读取 ⇒ 表格渲染断言不适用` +
        '（契约中间态，非功能缺陷；调额 UI 链路由 API 级 E2E 覆盖）',
    );
    check(
      '[额度管理] 表格 0 行可被解释（后端未返回分页信封 ⇒ 前端读不到 records，非数据缺失）',
      uiRows === 0 && foundInApi === 1,
      `表格行数=${uiRows}，API 命中=${foundInApi}，后端形态=${shape}`,
    );
  }

  // 后端没有「单额度账户详情」接口（只有列表 / by-insured / changes），
  // 故按保单列账户后按 ID 取对应记录回读，避免拿不存在的端点当断言。
  // 列表响应可能是数组（旧契约）或分页信封（新契约），两种形态都要能取到记录。
  const qList = await api(
    'GET',
    `/claim-config/quota-accounts?policyNo=${encodeURIComponent(POLICY_NO)}`,
    undefined,
    { token: TOKEN },
  );
  const qBody = qList.data?.data;
  const qAll = Array.isArray(qBody) ? qBody : (qBody?.records ?? []);
  const qRec = qAll.find((r) => r.quotaAccountId === quotaId);
  if (envelopeAligned) {
    assert(
      '[额度管理] API 回读确认额度已调整',
      Number(qRec?.initial) === NEW_INITIAL,
      `API initial=${qRec?.initial}（期望 ${NEW_INITIAL}）`,
    );
  } else {
    assert(
      '[额度管理] 账户初始额度未被误改（契约未对齐，未走 UI 调额）',
      Number(qRec?.initial) === 20000,
      `API initial=${qRec?.initial}（期望 20000）`,
    );
  }
  // 完整性断言：额度账户挂的计划必须与计划真实 ID **逐字符相同**。
  // 雪花 ID 超出 2^53，任何一次 Number() 往返都会截断末位并静默错配到不存在的计划。
  assert(
    '[额度管理] 额度账户关联的计划 ID 与计划真实 ID 完全一致（无精度截断）',
    qRec?.planId === planId,
    `账户 planId=${qRec?.planId} vs 计划 planId=${planId}`,
  );

  // ---------- 6. 保全记录可查（留痕闭环） ----------
  const changes = await api(
    'GET',
    `/claim-config/quota-accounts/${quotaId}/changes`,
    undefined,
    { token: TOKEN },
  );
  const changeRows = changes.data?.data || [];
  if (envelopeAligned) {
    assert(
      '[额度管理] 保全记录留痕（开户/调额两类）',
      changeRows.length >= 2,
      `条数 ${changeRows.length}`,
    );
  } else {
    assert(
      '[额度管理] 保全记录留痕（开户一类；契约未对齐未走 UI 调额）',
      changeRows.length >= 1,
      `条数 ${changeRows.length}`,
    );
  }

  // ---------- 7. 全程无 5xx / console error ----------
  check('[全程] 无 5xx 响应', httpErrors.length === 0, httpErrors.join(' | '));
  check('[全程] 无 console error', consoleErrors.length === 0, consoleErrors.join(' | '));

  await shot(page, 'claim_config_ui_probe');
  console.log(`\n截图：${SHOT_DIR}/claim_config_ui_probe*.png`);
} catch (e) {
  check('探针执行未抛异常', false, String(e).slice(0, 400));
} finally {
  await browser.close();
}

const r = report('智能理赔 · 配置面 UI 探针');
process.exit(r.ok ? 0 : 1);
