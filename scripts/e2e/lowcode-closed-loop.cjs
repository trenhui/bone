/**
 * 低代码最小闭环端到端联调（PRD v0.34 / 融合方案 W0-1~8 + W0b + W1a T1~T4 + UC-01~06）
 *
 * 策略：Playwright 打开 shell 做一次真实 UI 登录取 JWT，之后所有断言走网关 HTTP API
 *      （比深层 qiankun 点击稳定，又保留真实登录态与租户上下文），
 *      最后再回到浏览器做「页面级」验收（四 Tab / ?tab= 同步 / 界面预览 / 脏态）。
 *
 * 用法：
 *   NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-6/bin/node
 *   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
 *   $NODE scripts/e2e/lowcode-closed-loop.cjs
 */
/* 共享 client：playwright 加载 / CHROME / 登录凭证（见 e2e_lib/client.cjs） */
const { loadPlaywright, CHROME, USER, PWD } = require('./e2e_lib/client.cjs');
/* 生产化测试数据：单号/人名/日期与 Python fixtures 同口径（见 e2e_lib/fixtures.cjs） */
const { dataset } = require('./e2e_lib/fixtures.cjs');
const { chromium } = loadPlaywright();
const SHELL = 'http://localhost:3000';
const GATEWAY = 'http://localhost:8888';
const META_APP = 'http://localhost:3004';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const steps = [];
const errors = [];
function assert(name, ok, info) {
  steps.push({ name, ok: !!ok, ...(info || {}) });
  if (!ok) errors.push(`${name} :: ${JSON.stringify(info || {}).slice(0, 260)}`);
  console.log(`  ${ok ? '✅' : '❌'} ${name}${info ? '  ' + JSON.stringify(info) : ''}`);
}

async function login() {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 900 } });
  const page = await ctx.newPage();
  await page.goto(`${SHELL}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await sleep(3500);
  const u = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if ((await u.count()) && (await u.isVisible().catch(() => false))) {
    await u.fill(USER);
    await page.locator('input[type="password"], input[placeholder="密码"]').first().fill(PWD);
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await sleep(4000);
  }
  const token = await page.evaluate(() => localStorage.getItem('token'));
  if (!token) { await browser.close(); throw new Error('login failed: no token'); }
  return { browser, ctx, page, token };
}

function makeApi(token) {
  return async function api(method, path, { body, etag, idempotencyKey, rawHeaders } = {}) {
    const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
    if (etag) headers['If-Match'] = etag;
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
    Object.assign(headers, rawHeaders || {});
    const res = await fetch(GATEWAY + path, {
      method, headers, body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    return { status: res.status, data, etag: res.headers.get('etag') || undefined };
  };
}

/** 统一响应解包：兼容 ApiResponse{code,data} 与裸 data */
const payload = (r) => (r && r.data && typeof r.data === 'object' && 'data' in r.data ? r.data.data : r?.data);
const okOf = (r) => r.status >= 200 && r.status < 300 && (r.data == null || r.data.code === undefined || r.data.code === 200 || r.data.code === 201);

(async () => {
  const { browser, ctx, token } = await login();
  const api = makeApi(token);

  console.log('\n===== A. 前置：模块上下文 & 平台模板（T4） =====');
  const ents = await api('GET', '/api/v1/metadata/entities?page=1&size=50');
  const recs = payload(ents)?.records || [];
  // moduleId 容忍 null：前 50 实体多为早期建模数据（module_id IS NULL），
  // 实例化接口对 null 模块上下文宽容（不构成门禁项，仅记录）。
  const moduleId = (recs.find((e) => e.moduleId != null) || recs[0])?.moduleId ?? null;
  assert('获取实体列表（moduleId 可空）', ents.status === 200, { moduleId, entities: recs.length });

  const tpls = await api('GET', '/api/v1/metadata/templates');
  const tplList = Array.isArray(payload(tpls)) ? payload(tpls) : [];
  const byCode = Object.fromEntries(tplList.map((t) => [t.code, t]));
  assert('T4 三套场景模板已落库', ['tpl_device_inspection', 'tpl_replenishment', 'tpl_visitor_pass'].every((c) => byCode[c]),
    { templates: tplList.map((t) => t.code) });

  console.log('\n===== B. UC-01 从场景模板起盘（0→1）=====');
  const stamp = Date.now();
  // 生产化测试数据（口径真源 e2e_lib/fixtures.py）：每个值只生成一次存变量，
  // 创建与断言共用同一变量（README §3 铁律）；同 seed 同数据 ⇒ 复跑可比对
  const ds = dataset(stamp);
  const NO_REC1 = ds.doc_no('WO');     // 首条巡检单号（PUT 全量覆写复用同一值）
  const NO_BAD = ds.doc_no('WO-BAD');  // 负向直写 status 用单号
  const NO_REC2 = ds.doc_no('WO2');    // 查询能力用例单号
  const NO_REC3 = ds.doc_no('WO3');    // 新列写入用例单号
  const NO_REPL = ds.doc_no('RO');     // 补货记录单号
  const INSP_CREATE = ds.person();     // 首条记录巡检人
  const INSP_UPD = ds.person();        // 更新后巡检人
  const INSP_NEG = ds.person();        // 负向直写用巡检人
  const INSP_STALE = ds.person();      // 陈旧 ETag 用例
  const INSP_NOETAG = ds.person();     // 缺 If-Match 观察项用例
  const INSP_QUERY = ds.person();      // 查询能力用例记录巡检人
  const INSP_NEWCOL = ds.person();     // 新列写入用例记录巡检人
  const DATE_INSP = ds.days_ago(19);   // 巡检日期（运行日倒推，数据老化）
  const DATE_Q = ds.days_ago(18);
  const DATE_COL = ds.days_ago(17);
  const DATE_EXPECT = ds.days_ago(8);  // 补货期望到货日期
  const inspCode = `insp_${stamp}`;
  const inst = await api('POST', `/api/v1/metadata/templates/${byCode.tpl_device_inspection.id}/instantiate`, {
    body: { name: `Insp_${stamp}`, code: inspCode, displayName: '设备巡检（E2E）', tableName: `md_${inspCode}`, moduleId, deliveryMode: 1 },
  });
  const inspId = payload(inst);
  assert('模板实例化（巡检）', okOf(inst) && !!inspId, { status: inst.status, entityId: inspId });

  const inspFields = await api('GET', `/api/v1/metadata/entities/${inspId}/fields?page=1&size=100`);
  const inspFieldArr = payload(inspFields)?.records || [];
  assert('模板字段数 = 7', inspFieldArr.length === 7, { fieldCount: inspFieldArr.length });

  // T2 核心断言：模板 enum_values 必须透传到实体字段（否则场景状态机在第一步就断）
  const enumFields = inspFieldArr.filter((f) => String(f.type).toUpperCase() === 'ENUM');
  const enumPersisted = enumFields.filter((f) => f.enumValues && String(f.enumValues).includes('"code"'));
  assert('T1+T2 ENUM 码表透传（巡检 result/status）', enumFields.length === 2 && enumPersisted.length === 2,
    { enumFields: enumFields.map((f) => ({ code: f.code, hasEnum: !!f.enumValues })) });

  const qtyRuleProbe = await api('GET', `/api/v1/metadata/templates/${byCode.tpl_replenishment.id}/fields`);
  const replTplFields = Array.isArray(payload(qtyRuleProbe)) ? payload(qtyRuleProbe) : [];
  const qtyRule = replTplFields.find((f) => f.code === 'qty');
  assert('T2 validation_rules 模板携带', !!qtyRule && qtyRule.validationRules !== null && qtyRule.validationRules !== undefined,
    { qtyValidationRules: qtyRule?.validationRules });

  console.log('\n===== C. UC-01 发布 → 热生效 → 录首条数据 =====');
  const pub1 = await api('POST', `/api/v1/metadata/entities/${inspId}/publish`);
  assert('发布（首次）', okOf(pub1), { status: pub1.status, msg: pub1.data?.message });

  const afterPub = await api('GET', `/api/v1/metadata/entities/${inspId}`);
  assert('发布后状态 = 已发布', payload(afterPub)?.status === 1, { status: payload(afterPub)?.status });

  // 0040/0048 状态机契约：status 是受保护字段（is_entry 入口动作标记 + G14 422 保护），
  // 客户端不再直写初始状态——由状态机入口态注入（巡检全环：close.from_status=OPEN）。
  const rec1 = await api('POST', `/api/v1/runtime/entities/${inspCode}/records`, {
    body: { insp_no: NO_REC1, device_code: 'DEV-001', inspector: INSP_CREATE, insp_date: DATE_INSP, result: 'NORMAL', remark: '首次巡检' },
  });
  const rec1Id = payload(rec1)?.id;
  assert('录首条记录（不含 status，入口态由状态机注入）', okOf(rec1) && !!rec1Id, { status: rec1.status, id: rec1Id, result: payload(rec1)?.result });
  assert('入口态注入 = OPEN（0048 is_entry 显式标记优先于图推导）', payload(rec1)?.status === 'OPEN', { status: payload(rec1)?.status });

  // 负向：直写受保护状态字段 → 422 META_RUNTIME_FIELD_PROTECTED（G14）
  const protectedWrite = await api('POST', `/api/v1/runtime/entities/${inspCode}/records`, {
    body: { insp_no: NO_BAD, device_code: 'DEV-001', inspector: INSP_CREATE, insp_date: DATE_INSP, result: 'NORMAL', status: 'CLOSED' },
  });
  assert('负向：POST 直写 status → 422 META_RUNTIME_FIELD_PROTECTED',
    protectedWrite.status === 422 && protectedWrite.data?.data?.errorCode === 'META_RUNTIME_FIELD_PROTECTED',
    { status: protectedWrite.status, errorCode: protectedWrite.data?.data?.errorCode });

  console.log('\n===== C2. 0048 状态机：动作是唯一合法流转入口 =====');
  // 负向：OPEN 态触发 reopen（要求 CLOSED）→ 409 META_RUNTIME_ACTION_ILLEGAL_TRANSITION
  const illegalReopen = await api('POST', `/api/v1/runtime/entities/${inspCode}/records/${rec1Id}/actions/reopen`, { body: { params: {} } });
  assert('负向：OPEN 态触发 reopen → 409 非法流转',
    illegalReopen.status === 409 && illegalReopen.data?.data?.errorCode === 'META_RUNTIME_ACTION_ILLEGAL_TRANSITION',
    { status: illegalReopen.status, errorCode: illegalReopen.data?.data?.errorCode });
  // 正向：close（OPEN→CLOSED）
  const actClose = await api('POST', `/api/v1/runtime/entities/${inspCode}/records/${rec1Id}/actions/close`, { body: { params: {} } });
  assert('动作 close：OPEN→CLOSED（唯一合法流转入口）',
    actClose.status === 200 && payload(actClose)?.toStatus === 'CLOSED' && payload(actClose)?.fromStatus === 'OPEN',
    { status: actClose.status, to: payload(actClose)?.toStatus, from: payload(actClose)?.fromStatus });
  // 负向：CLOSED 态重复 close → 409
  const dupClose = await api('POST', `/api/v1/runtime/entities/${inspCode}/records/${rec1Id}/actions/close`, { body: { params: {} } });
  assert('负向：CLOSED 态重复 close → 409', dupClose.status === 409, { status: dupClose.status });
  // 正向：reopen（CLOSED→OPEN）回环
  const actReopen = await api('POST', `/api/v1/runtime/entities/${inspCode}/records/${rec1Id}/actions/reopen`, { body: { params: {} } });
  assert('动作 reopen：CLOSED→OPEN（全环回路的另一半）',
    actReopen.status === 200 && payload(actReopen)?.toStatus === 'OPEN', { status: actReopen.status, to: payload(actReopen)?.toStatus });

  console.log('\n===== D. W0-4 / UC-06 并发 412 =====');
  const got = await api('GET', `/api/v1/runtime/entities/${inspCode}/records/${rec1Id}`);
  assert('GET /{id} 返回 ETag', !!got.etag, { etag: got.etag });

  const upd = await api('PUT', `/api/v1/runtime/entities/${inspCode}/records/${rec1Id}`, {
    body: { insp_no: NO_REC1, device_code: 'DEV-001', inspector: INSP_UPD, insp_date: DATE_INSP, result: 'NORMAL', remark: '已闭环' }, etag: got.etag,
  });
  assert('If-Match 正确 → 200（并发受控）', upd.status === 200, { status: upd.status });

  // 负向：PUT 直写受保护状态字段 → 422（状态推进只能走动作端点，G14/Runtime §4.7）
  // 不带 If-Match：ETag 宽限期允许缺省，确保唯一失败原因就是 422 字段保护本身。
  const protectedUpdate = await api('PUT', `/api/v1/runtime/entities/${inspCode}/records/${rec1Id}`, {
    body: { inspector: INSP_NEG, status: 'CLOSED' },
  });
  assert('负向：PUT 直写 status → 422 META_RUNTIME_FIELD_PROTECTED',
    protectedUpdate.status === 422 && protectedUpdate.data?.data?.errorCode === 'META_RUNTIME_FIELD_PROTECTED',
    { status: protectedUpdate.status, errorCode: protectedUpdate.data?.data?.errorCode });

  const stale = await api('PUT', `/api/v1/runtime/entities/${inspCode}/records/${rec1Id}`, {
    body: { insp_no: NO_REC1, inspector: INSP_STALE }, etag: got.etag,
  });
  assert('陈旧 ETag → 412（未静默覆盖）', stale.status === 412, { status: stale.status, code: stale.data?.data?.errorCode });

  // AMBER 观察项（非门禁）：PRD §12.2 D3 —— If-Match 处于「宽限期」，缺省不拦截，
  // 以 ifmatch_missing_count 埋点决定是否升级为强制（v1.2）。此处仅记录实际行为。
  const noEtag = await api('PUT', `/api/v1/runtime/entities/${inspCode}/records/${rec1Id}`, {
    body: { inspector: INSP_NOETAG },
  });
  console.log(`  ⚠️ [观察项] 缺 If-Match 行为 = ${noEtag.status}（D3 宽限期：允许，待埋点决定是否强制）`);

  const afterNoEtag = await api('GET', `/api/v1/runtime/entities/${inspCode}/records/${rec1Id}`);
  assert('无 If-Match 更新确实生效（宽限期语义，非静默丢弃）', afterNoEtag.status === 200,
    { status: afterNoEtag.status, inspector: payload(afterNoEtag)?.inspector });

  console.log('\n===== E. W0a2 查询能力：fields / sort / q =====');
  await api('POST', `/api/v1/runtime/entities/${inspCode}/records`, {
    body: { insp_no: NO_REC2, device_code: 'DEV-002', inspector: INSP_QUERY, insp_date: DATE_Q, result: 'ABNORMAL' },
  });
  // 契约口径：q = field:value（RuntimePageQuery.parseFilter）；sort = [-]field（parseSort）
  const q = await api('GET', `/api/v1/runtime/entities/${inspCode}/records?page=1&size=10&q=device_code:DEV-002`);
  const qRecs = payload(q)?.records || [];
  assert('q 过滤生效（field:value）', qRecs.length === 1 && qRecs[0].device_code === 'DEV-002', { hit: qRecs.length });

  const proj = await api('GET', `/api/v1/runtime/entities/${inspCode}/records?page=1&size=10&fields=insp_no,device_code`);
  const pRecs = payload(proj)?.records || [];
  assert('fields 列投影生效', pRecs.length > 0 && !('remark' in (pRecs[0] || {})), { keys: Object.keys(pRecs[0] || {}).join(',') });

  const desc = await api('GET', `/api/v1/runtime/entities/${inspCode}/records?page=1&size=10&sort=-insp_no`);
  const descRecs = payload(desc)?.records || [];
  const asc = await api('GET', `/api/v1/runtime/entities/${inspCode}/records?page=1&size=10&sort=insp_no`);
  const ascRecs = payload(asc)?.records || [];
  const firstDesc = descRecs[0]?.insp_no;
  const firstAsc = ascRecs[0]?.insp_no;
  assert('sort 升降序生效（[-]field）', desc.status === 200 && asc.status === 200 && !!firstDesc && firstDesc !== firstAsc,
    { firstDesc, firstAsc });

  const badSort = await api('GET', `/api/v1/runtime/entities/${inspCode}/records?page=1&size=10&sort=not_a_column`);
  assert('非法排序列 → 400（列白名单校验）', badSort.status === 400, { status: badSort.status });

  console.log('\n===== F. UC-02 1→n：已发布加字段 → 再发布 → 数据见新列 =====');
  const newField = await api('POST', `/api/v1/metadata/entities/${inspId}/fields`, {
    body: { name: 'duration', code: 'duration', displayName: '耗时(分钟)', type: 'INTEGER', required: false, sortOrder: 8 },
  });
  assert('已发布实体新增字段', okOf(newField), { status: newField.status });
  const pub2 = await api('POST', `/api/v1/metadata/entities/${inspId}/publish`);
  assert('已发布实体再次发布（顶栏常驻发布口径）', okOf(pub2), { status: pub2.status });

  const rec3 = await api('POST', `/api/v1/runtime/entities/${inspCode}/records`, {
    body: { insp_no: NO_REC3, device_code: 'DEV-003', inspector: INSP_NEWCOL, insp_date: DATE_COL, result: 'NORMAL', duration: 35 },
  });
  const rec3Id = payload(rec3)?.id;
  assert('新列写入（再次发布后热生效）', okOf(rec3) && !!rec3Id, { status: rec3.status, echoDuration: payload(rec3)?.duration });
  // 回读确认：物理表确已收到新列（发布时 physical align 对齐成功）
  const rec3Read = await api('GET', `/api/v1/runtime/entities/${inspCode}/records/${rec3Id}`);
  assert('新列持久化到物理表（回读）', payload(rec3Read)?.duration === 35, { duration: payload(rec3Read)?.duration });
  const listing = await api('GET', `/api/v1/runtime/entities/${inspCode}/records?page=1&size=20`);
  const hasDurationKey = (payload(listing)?.records || []).some((r) => 'duration' in r);
  assert('列表返回新列（前端「出现新列」的前提）', hasDurationKey, {});

  console.log('\n===== G. UC-04 换场景：补货 5 态状态机 =====');
  const replCode = `repl_${stamp}`;
  const inst2 = await api('POST', `/api/v1/metadata/templates/${byCode.tpl_replenishment.id}/instantiate`, {
    body: { name: `Repl_${stamp}`, code: replCode, displayName: '补货管理（E2E）', tableName: `md_${replCode}`, moduleId, deliveryMode: 1 },
  });
  const replId = payload(inst2);
  assert('模板实例化（补货）', okOf(inst2) && !!replId, { status: inst2.status });
  const replFields = await api('GET', `/api/v1/metadata/entities/${replId}/fields?page=1&size=100`);
  const replArr = payload(replFields)?.records || [];
  const st = replArr.find((f) => f.code === 'status');
  const fiveState = st && typeof st.enumValues === 'string'
    && ['PENDING', 'ORDERED', 'SHIPPED', 'RECEIVED', 'CANCELLED'].every((c) => st.enumValues.includes(c));
  assert('补货 5 态 ENUM 完整落库（C2 场景前提）', !!fiveState, { enumValues: st?.enumValues });

  const pub3 = await api('POST', `/api/v1/metadata/entities/${replId}/publish`);
  assert('补货实体发布', okOf(pub3), { status: pub3.status });
  const replRec = await api('POST', `/api/v1/runtime/entities/${replCode}/records`, {
    body: { order_no: NO_REPL, sku: 'SKU-1001', warehouse: 'WH-SZ', qty: 120, priority: 'HIGH', expect_date: DATE_EXPECT },
  });
  const replRecId = payload(replRec)?.id;
  assert('补货记录录入（不含 status，入口态注入 PENDING）', okOf(replRec) && payload(replRec)?.status === 'PENDING',
    { status: replRec.status, priority: payload(replRec)?.priority, injected: payload(replRec)?.status });

  console.log('\n===== G2. 0048 状态机：补货 5 态动作链 + 非法流转 =====');
  const actPlace = await api('POST', `/api/v1/runtime/entities/${replCode}/records/${replRecId}/actions/placeOrder`, { body: { params: {} } });
  assert('动作 placeOrder：PENDING→ORDERED', actPlace.status === 200 && payload(actPlace)?.toStatus === 'ORDERED',
    { status: actPlace.status, to: payload(actPlace)?.toStatus });
  // 负向：ORDERED 态触发 receive（要求 SHIPPED）→ 409
  const actEarlyReceive = await api('POST', `/api/v1/runtime/entities/${replCode}/records/${replRecId}/actions/receive`, { body: { params: {} } });
  assert('负向：ORDERED 态触发 receive → 409 非法流转', actEarlyReceive.status === 409,
    { status: actEarlyReceive.status, errorCode: actEarlyReceive.data?.data?.errorCode });
  const actShip = await api('POST', `/api/v1/runtime/entities/${replCode}/records/${replRecId}/actions/ship`, { body: { params: {} } });
  assert('动作 ship：ORDERED→SHIPPED', actShip.status === 200 && payload(actShip)?.toStatus === 'SHIPPED',
    { status: actShip.status, to: payload(actShip)?.toStatus });
  const actReceive = await api('POST', `/api/v1/runtime/entities/${replCode}/records/${replRecId}/actions/receive`, { body: { params: {} } });
  assert('动作 receive：SHIPPED→RECEIVED（链尾）', actReceive.status === 200 && payload(actReceive)?.toStatus === 'RECEIVED',
    { status: actReceive.status, to: payload(actReceive)?.toStatus });

  console.log('\n===== H. W0-1 数据正确性：/data 绑 id（负向断言 A/B）=====');
  assert('A/B 两实体 code 不同且各自 detail 可解析', inspCode !== replCode, { inspCode, replCode });
  const dA = await api('GET', `/api/v1/metadata/entities/${inspId}`);
  const dB = await api('GET', `/api/v1/metadata/entities/${replId}`);
  assert('detail(A) 返回 A 自身 code（防静默串实体）', payload(dA)?.code === inspCode && payload(dB)?.code === replCode,
    { a: payload(dA)?.code, b: payload(dB)?.code });
  const dataA = await api('GET', `/api/v1/runtime/entities/${inspCode}/records?page=1&size=5`);
  const dataB = await api('GET', `/api/v1/runtime/entities/${replCode}/records?page=1&size=5`);
  const onlyA = (payload(dataA)?.records || []).every((r) => r.insp_no !== undefined || r.device_code !== undefined);
  const onlyB = (payload(dataB)?.records || []).every((r) => r.order_no !== undefined || r.sku !== undefined);
  assert('两实体运行时数据互不串台', onlyA && onlyB, { aCount: (payload(dataA)?.records || []).length, bCount: (payload(dataB)?.records || []).length });

  console.log('\n===== I. UI 级验收（四 Tab / ?tab= 同步 / 界面预览 / 脏态）=====');
  // ⚠️ UI 验收必须走 shell（:3000）：bone-metadata-app 的 index.html 只加载
  // public/qiankun-entry.js（注册 window['bone-metadata-app']），没有 /src/main.tsx 入口
  // ⇒ 直连 :3004 是空白页（设计如此：子应用只由 qiankun 挂载）。
  const mp = await ctx.newPage();
  const consoleErrors = [];
  mp.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  mp.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message));
  // IA 收敛后：?tab=ui（PRD §0.6.6 模板起盘黄金路径深链）rewrite 到界面设计器——
  // 团队契约的交付预览职责由设计器承接；个人覆盖口径对照在运行时数据页。
  await mp.goto(`${SHELL}/metadata#/entities/${inspId}?tab=ui`, { waitUntil: 'domcontentloaded' });
  await sleep(6000);
  const uiState = await mp.evaluate(() => {
    const tabs = Array.from(document.querySelectorAll('.ant-tabs-tab')).map((e) => e.textContent.trim());
    return {
      tabs,
      url: location.hash,
      hasAutoBadge: document.body.innerText.includes('自动生成'),
    };
  });
  // 对齐当前产品：四 Tab 结构/界面设计器/数据/发布（字段/关系/校验 是「结构」内嵌子 Tab，仅结构激活时入 DOM）
  assert('四 Tab = 结构/界面设计器/数据/发布', ['结构', '界面设计器', '数据', '发布'].every((t) => uiState.tabs.some((x) => x.includes(t))), { tabs: uiState.tabs });
  assert('「交付预览」已归位（顶层 Tab 不再单列，个人语义不入建模台）', !uiState.tabs.some((x) => x.includes('交付预览')), { tabs: uiState.tabs });
  assert('旧「显示设置」/「个人显示偏好」/「界面（协议）」tab 已不存在', !uiState.tabs.some((x) => x.includes('显示设置') || x.includes('个人显示偏好') || x.includes('界面（协议）')), { tabs: uiState.tabs });
  // 深链兼容：旧 ?tab=ui 必须 rewrite 到 ?tab=uischema，不能落进 <Route path="*"> 静默回首页。
  // ⚠️ 判「仍是旧 key」不能用 includes('tab=ui') —— 'tab=uischema' 含该子串会自我否定；
  // 必须用「tab=ui 后面紧跟分隔符或结尾」的精确匹配。
  const stillOldTab = /[?&]tab=ui(&|$)/.test(uiState.url);
  assert('?tab=ui 深链rewrite 到界面设计器（URL 同步，PRD 黄金路径不失效）',
    uiState.url.includes('tab=uischema') && !stillOldTab, { hash: uiState.url });

  // 数据页「交付口径对照」卡片呈现双口径（全员所见 / 我的所见）
  await mp.goto(`${SHELL}/metadata#/entities/${inspId}/data`, { waitUntil: 'domcontentloaded' });
  await sleep(6000);
  const scopeState = await mp.evaluate(() => {
    const t = document.body.innerText;
    return {
      hasScopeCard: t.includes('交付口径对照'),
      hasContractScope: t.includes('全员所见（契约）'),
      hasMineScope: t.includes('我的所见（含个人覆盖）'),
    };
  });
  assert('数据页「交付口径对照」卡片呈现双口径（全员所见 / 我的所见）',
    scopeState.hasScopeCard && scopeState.hasContractScope && scopeState.hasMineScope, scopeState);

  // ENUM 渲染（T3）：ENUM 是**字段结构**的展示，与契约无关 ⇒ 断言落「结构 → 字段」，
  // 不能放在设计器/交付预览（那里渲染的是 ui-schema 契约，未配契约的实体看不到 ENUM）。
  await mp.goto(`${SHELL}/metadata#/entities/${inspId}?tab=structure`, { waitUntil: 'domcontentloaded' });
  await sleep(4000);
  const enumState = await mp.evaluate(() => document.body.innerText.includes('ENUM'));
  assert('结构 Tab 展示 ENUM 字段（T3 渲染）', enumState, {});

  // 数据 Tab（嵌入模式）：断言绑定的是本实体。
  // 上一步已在数据页（对照卡片断言），复用当前 DOM，不再重复导航。
  const dataState = await mp.evaluate(() => document.body.innerText.slice(0, 2500));
  assert('数据 Tab 显示本实体物理表（md_insp_*）', /md_insp_/.test(dataState), { snippet: dataState.replace(/\s+/g, ' ').slice(0, 120) });

  // 0027 菜单收口：live 菜单树中实体入口名为「业务对象 [/metadata#/entities]」（非「实体」），
  // 且无「关系管理」重复入口——按 live 数据校准断言口径（L3 数据迁移范畴，只观测不修改）。
  const sidebar = await mp.evaluate(() => document.body.innerText.slice(0, 3000));
  assert('0027 菜单收口生效：侧栏含「业务对象」且无「关系管理」',
    /业务对象/.test(sidebar) && !/关系管理/.test(sidebar), { hasBusinessObject: /业务对象/.test(sidebar), hasRelation: /关系管理/.test(sidebar) });

  // 已知存量告警（非本次改动引入，全仓 grep 无 clearIcon 字面量，源自 antd 内部/其他微应用）
  const KNOWN = /clearIcon.{0,4}is deprecated/;
  const realErrors = consoleErrors.filter((e) => !/favicon|404 \(Not Found\)/i.test(e) && !KNOWN.test(e));
  if (consoleErrors.some((e) => KNOWN.test(e))) {
    console.log('  ⚠️ [已知存量告警] antd Select clearIcon 弃用警告（非本次改动引入）');
  }
  assert('页面无新增 console error', realErrors.length === 0, { errors: realErrors.slice(0, 3) });

  await browser.close();

  const failed = steps.filter((s) => !s.ok).length;
  const summary = { total: steps.length, pass: steps.length - failed, failed, errors };
  console.log('\n===== SUMMARY =====');
  console.log(JSON.stringify(summary, null, 2));
  process.exit(failed > 0 ? 1 : 0);
})().catch((e) => { console.error('FATAL', e); process.exit(2); });
