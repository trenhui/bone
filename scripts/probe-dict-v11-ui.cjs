#!/usr/bin/env node
/**
 * 字典打通 v1.1 UI 层联调探针（Playwright + 系统 Chrome，经 shell :3000 真实登录）。
 *
 * 覆盖（doc/design/lowcode/ui/界面设计器字典打通设计方案.md v1.1 + 联调实证修复）：
 *   P1 系统应用·字典管理页：三态状态列、弃用/恢复交互、非终态删除置灰、弃用前二次确认
 *   P2 引用方清单抽屉：绑定实体/字段、漂移徽标、跨应用深链（/metadata#/entities/{id}）
 *   P3 元数据应用·界面设计器字典面板：真源绑定展示、漂移徽标、刷新后 DEPRECATED 落「废弃中」
 *      （v1.1 联调实证修复：刷新成功必须回调宿主重取字段，否则面板停留旧快照）
 *
 * 前置（API 造数，失败即中止）：字典类型 + A/B/C 项、实体 + ENUM 字段（dict 绑定）、
 * 弃用 A 制造「快照=ACTIVE / 真源=DEPRECATED」漂移。
 *
 * 纪律：末尾统一断言汇总，失败 exit 1；截图落 /tmp/v11-ui-screens/。
 * 运行：NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules \
 *       ~/.workbuddy/binaries/node/versions/22.22.2-6/bin/node scripts/probe-dict-v11-ui.cjs
 */
const { chromium } = require('playwright');
const fs = require('fs');

const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const UI = 'http://localhost:3000';
const API = 'http://127.0.0.1:8888';
const SHOTS = '/tmp/v11-ui-screens';

const RUN = Date.now().toString(36);
const TYPE_CODE = `UI_V11_${RUN}`;
const ENTITY_CODE = `ui_v11_ent_${RUN}`;
const ENTITY_NAME = 'UI联调实体';

const passed = [];
const failed = [];
const check = (name, cond, detail = '') => {
  (cond ? passed : failed).push([name, detail]);
  console.log(`  [${cond ? 'PASS' : 'FAIL'}] ${name}${!cond && detail ? ` — ${detail}` : ''}`);
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// 网关对上游的熔断器在瞬断后会 OPEN ~85s（记忆：熔断冷却）。api() 内置对 5xx 的指数退避重试，
// 避免一次性 503 直接中断整条联调（探针纪律：前置失败即中止 ≠ 被熔断误杀）。
async function api(method, path, body, token, _attempt = 0) {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const status = res.status;
  const payload = await res.json().catch(() => ({}));
  if ([502, 503, 504].includes(status) && _attempt < 4) {
    const backoff = [15, 30, 60, 90][_attempt] || 90;
    console.log(`  [retry] ${method} ${path} => ${status}（熔断/瞬断，退避 ${backoff}s 后重试 ${_attempt + 1}/4）`);
    await sleep(backoff * 1000);
    return api(method, path, body, token, _attempt + 1);
  }
  return { status, payload };
}

// 造数前先轮询非变更端点，等网关熔断窗口冷却关闭（至多 150s），避免首调即被 OPEN 熔断拦截。
// 用 dict-bindings（只读、未知 typeCode 返回空列表、无分页校验）作探针，规避 entities 列表的 page>=1 校验。
async function waitGatewayCooldown(token) {
  for (let i = 0; i < 15; i++) {
    const r = await api('GET', '/api/v1/metadata/fields/dict-bindings?typeCode=__cooldown__', undefined, token);
    if (r.status !== 503 && r.status !== 502) { console.log(`[cooldown] 网关就绪（第 ${i} 次探测 status=${r.status}）`); return; }
    console.log(`[cooldown] 网关仍熔断（status=${r.status}），等 10s…`);
    await sleep(10000);
  }
  console.log('[cooldown] 超时仍熔断，尝试继续（可能首调即失败）');
}
const extractId = (p) => {
  const d = p && p.data;
  return d && typeof d === 'object' ? (d.id || d.fieldId || d.entityId) : d;
};
const fatal = (msg, extra) => {
  console.log(`FATAL: ${msg}${extra ? ' ' + JSON.stringify(extra) : ''}`);
  process.exit(1);
};

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  console.log(`[setup] RUN=${RUN} TYPE_CODE=${TYPE_CODE} ENTITY_CODE=${ENTITY_CODE}`);

  // ---------- 前置：API 造数（失败即中止，禁假绿） ----------
  const login = await api('POST', '/api/v1/iam/login', { username: 'admin', password: '123456' });
  const token = (login.payload.data || {}).token;
  if (!token) fatal('登录失败', login.payload);

  // 等网关熔断窗口冷却（防首调即被 OPEN 熔断误杀）
  await waitGatewayCooldown(token);

  let r = await api('POST', '/api/v1/system/dict/types',
    { code: TYPE_CODE, name: 'UI字典联调v1.1', category: 'LIST', valueType: 'STRING', status: 1, sort: 0 }, token);
  if (r.status !== 200) fatal('创建字典类型失败', r.payload);
  const itemIds = {};
  for (const c of ['A', 'B', 'C']) {
    r = await api('POST', '/api/v1/system/dict/items',
      { typeCode: TYPE_CODE, code: c, label: `标签${c}`, value: c, status: 1, sort: c.charCodeAt(0) - 64 }, token);
    if (r.status !== 200) fatal(`创建字典项 ${c} 失败`, r.payload);
    itemIds[c] = extractId(r.payload);
  }
  r = await api('POST', '/api/v1/metadata/entities',
    { name: ENTITY_NAME, code: ENTITY_CODE, displayName: ENTITY_NAME, tableName: 't_' + ENTITY_CODE, type: 1, deliveryMode: 1 }, token);
  if (![200, 201].includes(r.status)) fatal('创建实体失败', r.payload);
  const entityId = extractId(r.payload);
  r = await api('POST', `/api/v1/metadata/entities/${entityId}/fields`, {
    name: '标签', code: 'label', displayName: '标签', type: 'ENUM',
    enumSource: JSON.stringify({ type: 'dict', mode: 'snapshot', ref: { typeCode: TYPE_CODE } }),
  }, token);
  if (![200, 201].includes(r.status)) fatal('创建字段失败', r.payload);
  const fieldId = extractId(r.payload);
  r = await api('PUT', `/api/v1/system/dict/items/${itemIds.A}/deprecate`, {}, token);
  if (r.status !== 200) fatal('弃用 A 失败', r.payload);
  console.log(`[setup] 就绪：entityId=${entityId} fieldId=${fieldId}（A 已弃用，快照未刷新 ⇒ 漂移待验证）`);

  // ---------- 浏览器 ----------
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const page = await browser.newPage({ viewport: { width: 1600, height: 950 } });
  // 抓 dict-bindings 真实请求 URL + 响应，定位 P2 空清单根因
  page.on('response', async (resp) => {
    const u = resp.url();
    if (u.includes('dict-bindings')) {
      try { const b = await resp.text(); console.log(`[net] dict-bindings ${resp.status()} ${u}\n      body=${b.slice(0, 300)}`); }
      catch { console.log(`[net] dict-bindings ${resp.status()} ${u}`); }
    }
  });
  const consoleErrors = [];
  page.on('pageerror', (e) => consoleErrors.push(`pageerror: ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(`console: ${m.text().slice(0, 200)}`); });
  const shot = (name) => page.screenshot({ path: `${SHOTS}/${RUN}-${name}.png` }).catch(() => {});

  // shell 真实登录（qiankun 子应用脱离 shell 无法渲染）
  await page.goto(`${UI}/system#/dict`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);
  const userInput = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if (await userInput.count() && await userInput.isVisible().catch(() => false)) {
    await userInput.fill('admin');
    await page.locator('input[type="password"]').first().fill('123456');
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await page.waitForTimeout(3000);
  }
  // 同 URL（含 hash）goto 不触发真 reload ⇒ 经 about:blank 中转保证以已登录态全新加载
  await page.goto('about:blank');
  await page.goto(`${UI}/system#/dict`, { waitUntil: 'domcontentloaded' });
  const searchBox = page.locator('input[placeholder="搜索编码 / 名称"]');
  try {
    await searchBox.waitFor({ state: 'visible', timeout: 25000 });
  } catch { /* 落入下方断言报告 */ }
  await page.waitForTimeout(1500);
  await shot('01-dict-page');

  // ---------- P1 字典管理页：三态交互 ----------
  console.log('\n=== P1 系统应用 · 字典管理页（三态生命周期） ===');
  const pageReady = await searchBox.count() > 0;
  check('P1.0 字典管理页渲染（经 shell 登录后可直达）', pageReady, page.url());
  if (pageReady) {
    await searchBox.fill(TYPE_CODE);
    await page.keyboard.press('Enter');
    await page.waitForTimeout(2000);
    const typeRow = page.locator('tr.ant-table-row', { hasText: TYPE_CODE }).first();
    const typeRowVisible = await typeRow.count() > 0;
    check('P1.1 字典类型可检索（唯一编码命中）', typeRowVisible);
    if (typeRowVisible) {
      await typeRow.click();
      await page.waitForTimeout(2500);
      // 字典项表首列为 code（无选择列/展开列），按行首单元格精确定位
      const itemRow = (code) => page.locator(`tr.ant-table-row:has(td.ant-table-cell:first-child:text-is("${code}"))`);
      const aRow = itemRow('A');
      const bRow = itemRow('B');
      const cRow = itemRow('C');
      check('P1.2 字典项表渲染 A/B/C', await aRow.count() > 0 && await bRow.count() > 0 && await cRow.count() > 0);
      // A 经 API 预弃用：状态列应为「废弃中」Tag（服务端状态驱动 UI，非本地臆造）
      check('P1.3 预弃用项 A 渲染「废弃中」Tag（状态列反映服务端三态）',
        await aRow.locator('.ant-tag', { hasText: '废弃中' }).count() > 0);
      // C 仍 ACTIVE：渲染启停 Switch
      check('P1.4 ACTIVE 项渲染启停 Switch', await cRow.locator('.ant-switch').count() > 0);
      // 负向：非终态删除置灰（破坏性变更必须先过渡，§4.4 双保险的 UI 侧）
      const delBtn = cRow.locator('button.ant-btn', { hasText: /删\s*除/ });
      check('P1.5 非终态项「删除」按钮置灰（UI 侧强制过渡）',
        await delBtn.count() > 0 && (await delBtn.first().isDisabled()));

      // 弃用交互：B（ACTIVE → DEPRECATED），含破坏语义二次确认
      const depBtn = bRow.locator('button.ant-btn', { hasText: /弃\s*用/ });
      check('P1.6 ACTIVE 项出现「弃用」入口', await depBtn.count() > 0);
      if (await depBtn.count() > 0) {
        await depBtn.first().click();
        await page.waitForTimeout(600);
        const okBtn = page.locator('.ant-modal-confirm .ant-btn-primary', { hasText: /弃\s*用/ });
        check('P1.7 弃用前二次确认弹窗（破坏语义明示）', await okBtn.count() > 0);
        if (await okBtn.count() > 0) {
          await okBtn.first().click();
          try {
            await bRow.locator('.ant-tag', { hasText: '废弃中' }).waitFor({ state: 'visible', timeout: 10000 });
            check('P1.8 弃用后行内出现「废弃中」Tag', true);
          } catch { check('P1.8 弃用后行内出现「废弃中」Tag', false, 'timeout'); }
          check('P1.9 弃用后行内出现「恢复」入口',
            await bRow.locator('button.ant-btn', { hasText: /恢\s*复/ }).count() > 0);
          await shot('02-deprecated');
          // 恢复交互（DEPRECATED → ACTIVE）
          const resBtn = bRow.locator('button.ant-btn', { hasText: /恢\s*复/ });
          if (await resBtn.count() > 0) {
            await resBtn.first().click();
            try {
              await bRow.locator('.ant-switch').waitFor({ state: 'visible', timeout: 10000 });
              check('P1.10 恢复后回到 ACTIVE（Switch 归来）', true);
            } catch { check('P1.10 恢复后回到 ACTIVE（Switch 归来）', false, 'timeout'); }
            check('P1.11 恢复后「废弃中」Tag 消失',
              await bRow.locator('.ant-tag', { hasText: '废弃中' }).count() === 0);
          }
        }
      }
      await shot('03-restored');

      // ---------- P2 引用方抽屉 ----------
      console.log('\n=== P2 系统应用 · 引用方清单抽屉（变更影响预览） ===');
      const bindBtn = page.locator('button.ant-btn', { hasText: '被引用' });
      check('P2.0 「被引用」入口存在', await bindBtn.count() > 0);
      if (await bindBtn.count() > 0) {
        await bindBtn.first().click();
        const drawer = page.locator('.ant-drawer-content').last();
        try {
          await drawer.waitFor({ state: 'visible', timeout: 8000 });
          check('P2.1 引用方抽屉打开', true);
        } catch { check('P2.1 引用方抽屉打开', false, 'timeout'); }
        // getBindings 异步加载：抽屉 visible 时表格尚未渲染（空态），须等绑定行落表再断言，否则误报空清单
        const openEntityBtn = drawer.locator('button.ant-btn', { hasText: '打开实体' });
        try {
          await openEntityBtn.first().waitFor({ state: 'visible', timeout: 10000 });
          check('P2.x 绑定行已渲染（异步 getBindings 落表）', true);
        } catch { check('P2.x 绑定行已渲染（异步 getBindings 落表）', false, 'timeout'); }
        const dtext = (await drawer.count()) ? await drawer.first().innerText() : '';
        check('P2.2 抽屉列出绑定实体名', dtext.includes(ENTITY_NAME), dtext.slice(0, 150));
        check('P2.3 抽屉列出绑定字段（label）', dtext.includes('label'));
        try {
          await drawer.locator('.ant-tag', { hasText: '已漂移' }).waitFor({ state: 'visible', timeout: 8000 });
          check('P2.4 漂移徽标「已漂移」（真源已变更待刷新）', true);
        } catch { check('P2.4 漂移徽标「已漂移」（真源已变更待刷新）', false, 'timeout'); }
        await shot('04-bindings-drawer');
        // 深链：跨应用整页指派到 metadata 实体详情
        const openEntity = drawer.locator('button.ant-btn', { hasText: '打开实体' });
        check('P2.5 抽屉提供「打开实体」深链', await openEntity.count() > 0);
        if (await openEntity.count() > 0) {
          await openEntity.first().click();
          try {
            await page.waitForURL(new RegExp(`/metadata#/entities/${entityId}`), { timeout: 20000 });
            check('P2.6 深链跳转 metadata 实体详情（跨应用导航）', true);
          } catch { check('P2.6 深链跳转 metadata 实体详情（跨应用导航）', false, page.url()); }
          try {
            await page.locator(`text=${ENTITY_NAME}`).first().waitFor({ state: 'visible', timeout: 20000 });
            check('P2.7 实体详情页渲染（实体名可见）', true);
          } catch { check('P2.7 实体详情页渲染（实体名可见）', false, page.url()); }
          await shot('05-entity-detail');
        }
      }
    }
  }

  // ---------- P3 metadata 应用：界面设计器 · 字典面板 ----------
  console.log('\n=== P3 元数据应用 · 界面设计器字典面板（渲染侧三态 + 漂移徽标） ===');
  const designerTab = page.locator('.ant-tabs-tab', { hasText: '界面设计器' });
  check('P3.0 实体详情含「界面设计器」页签', await designerTab.count() > 0);
  if (await designerTab.count() > 0) {
    await designerTab.first().click();
    await page.waitForTimeout(2500);
    const dictTab = page.locator('.ant-tabs-tab', { hasText: '字典' });
    check('P3.1 设计器含「字典」子页签', await dictTab.count() > 0);
    if (await dictTab.count() > 0) {
      await dictTab.first().click();
      // 面板挂载即拉 diff：等漂移徽标出现再取文本（避免读到加载中态）
      const driftTag = page.locator('.ant-tag', { hasText: '已漂移' }).last();
      try {
        await driftTag.waitFor({ state: 'visible', timeout: 12000 });
        check('P3.2 快照旧态时显示「已漂移」徽标（含状态变迁维度）', true);
      } catch { check('P3.2 快照旧态时显示「已漂移」徽标（含状态变迁维度）', false, 'timeout'); }
      const beforeText = await page.locator('body').innerText();
      check('P3.3 字典面板展示绑定真源（系统字典 · typeCode）', beforeText.includes(`系统字典 · ${TYPE_CODE}`));
      check('P3.4 负向：快照未刷新前无「废弃中」（标签非恒显）', !beforeText.includes('废弃中'));
      const tsBefore = (beforeText.match(/物化于 ([0-9T: \-]+)/) || [])[1] || '';
      check('P3.5 面板展示物化时间（快照 provenance）', tsBefore.length > 0, `ts=${tsBefore}`);
      // 刷新快照 → 回调宿主重取字段（v1.1 联调实证修复）→ DEPRECATED 落「废弃中」、漂移消失
      const refreshBtn = page.locator('button[aria-label="刷新 label 的字典快照"]');
      check('P3.6 面板提供快照刷新入口', await refreshBtn.count() > 0);
      if (await refreshBtn.count() > 0) {
        await refreshBtn.first().click();
        try {
          await page.locator('.ant-tag', { hasText: '废弃中' }).last().waitFor({ state: 'visible', timeout: 15000 });
          check('P3.7 刷新后 DEPRECATED 项落「废弃中」标签（修复后面板跟上服务端）', true);
        } catch { check('P3.7 刷新后 DEPRECATED 项落「废弃中」标签（修复后面板跟上服务端）', false, 'timeout'); }
        await page.waitForTimeout(1500);
        const afterText = await page.locator('body').innerText();
        check('P3.8 刷新后漂移徽标消失（快照追上真源）', !afterText.includes('已漂移'));
        const tsAfter = (afterText.match(/物化于 ([0-9T: \-]+)/) || [])[1] || '';
        check('P3.9 刷新后物化时间更新（面板非旧快照）', tsAfter.length > 0 && tsAfter !== tsBefore, `before=${tsBefore} after=${tsAfter}`);
        await shot('06-dict-panel');
      }
    }
  }

  // ---------- 汇总 ----------
  const benign = consoleErrors.filter((e) => /favicon|Failed to load resource/i.test(e));
  const real = consoleErrors.filter((e) => !/favicon|Failed to load resource/i.test(e));
  console.log(`\n=== 汇总：${passed.length} passed, ${failed.length} failed ===`);
  for (const [n, d] of failed) console.log(`  FAIL: ${n} — ${d}`);
  console.log(`console errors: ${real.length} real, ${benign.length} benign(favicon/404)`);
  for (const e of real.slice(0, 10)) console.log(`  ${e}`);
  console.log(`screenshots: ${SHOTS}/${RUN}-*.png`);
  await browser.close();
  process.exit(failed.length || real.length ? 1 : 0);
})().catch((e) => { console.log('FATAL:', e); process.exit(1); });
