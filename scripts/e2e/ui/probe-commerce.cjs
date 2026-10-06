// BONE 多渠道交易域 UI 巡检：真·点击每一个功能（含写操作闭环）
//
// 与 probe-routes.cjs 的区别：routes 探针只做「导航 + 断言有数据」，本探针覆盖
// 四张新页面的**写入口完整闭环**：开弹窗 → 填表 → 提交 → 断言成功提示与表格变化。
//
// 复用 scripts/e2e/ui/README.md 里踩过的坑：
//   ① 菜单名以运行时为准；② shell 的 commerce 是两级菜单（先展开「交易管理」）；
//   ③ Modal 与 Drawer 都认；④ 首屏给足 Vite 编译时间。
const { chromium } = require('playwright');
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const BASE = 'http://localhost:3000';
const OUT = '/tmp/bone-ui-commerce';
require('fs').mkdirSync(OUT, { recursive: true });

const R = [];
const ok = (name, pass, detail = '') => {
  R.push({ name, pass, detail });
  console.log(`  [${pass ? 'PASS' : 'FAIL'}] ${name}${detail && !pass ? ' — ' + detail : ''}`);
};

async function login(page) {
  await page.goto(`${BASE}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);
  for (const pwd of ['123456', 'admin123', 'Admin@123']) {
    const u = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
    if (!(await u.count()) || !(await u.isVisible().catch(() => false))) break;
    await u.fill('admin');
    await page.locator('input[type="password"], input[placeholder="密码"]').first().fill(pwd);
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await page.waitForTimeout(4000);
    if (await page.locator('.ant-layout-sider, aside').count()) {
      const tok = await page.evaluate(() => localStorage.getItem('token'));
      if (tok) return pwd;
    }
    await page.goto(`${BASE}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2500);
  }
  return null;
}

/** 展开父级 SubMenu 并点击子菜单项（幂等展开 + 重试） */
async function gotoMenu(page, parent, child) {
  await page.goto(`${BASE}/commerce`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3200);
  const sub = page.locator('.ant-menu-submenu').filter({ hasText: new RegExp(`^${parent}`) }).first();
  if (await sub.count()) {
    const exp = await sub.locator('.ant-menu-submenu-title').first().getAttribute('aria-expanded');
    if (exp !== 'true') {
      try { await sub.locator('.ant-menu-submenu-title').first().click({ timeout: 5000 }); } catch (e) {}
      await page.waitForTimeout(1200);
    }
  }
  const loc = page.locator('.ant-menu-item').filter({ hasText: new RegExp(`^${child}$`) }).first();
  if (!(await loc.count())) return { found: false };
  let clicked = false;
  for (let i = 1; i <= 3 && !clicked; i++) {
    try { await loc.click({ timeout: 4000 + i * 3500 }); clicked = true; } catch (e) { await page.waitForTimeout(1500); }
  }
  await page.waitForTimeout(3000);
  return { found: true, clicked, hash: await page.evaluate(() => location.hash || '') };
}

/** 按 Form.Item 的 label 文本定位并填写（Input / InputNumber / Select 都支持） */
async function fill(page, label, value, kind = 'input') {
  const item = page.locator('.ant-form-item').filter({ hasText: new RegExp(`^${label}`) }).first();
  if (!(await item.count())) throw new Error(`form item not found: ${label}`);
  if (kind === 'select') {
    await item.locator('.ant-select-selector').first().click();
    await page.waitForTimeout(700);
    await page.locator('.ant-select-item-option').filter({ hasText: new RegExp(`^${value}$`) }).first().click();
    await page.waitForTimeout(400);
    return;
  }
  // 收货地址等长文本用的是 Input.TextArea（没有 <input>），必须退化到 textarea
  const input = item.locator('input').first();
  if (await input.count()) { await input.fill(String(value)); return; }
  const ta = item.locator('textarea').first();
  if (await ta.count()) { await ta.fill(String(value)); return; }
  throw new Error(`no input/textarea in form item: ${label}`);
}

/** 等待 antd message 出现并返回文案（success / error 都收） */
async function waitMsg(page, timeout = 12000) {
  try {
    await page.locator('.ant-message-notice-content').first().waitFor({ state: 'visible', timeout });
    await page.waitForTimeout(400);
    const txt = await page.locator('.ant-message-notice-content').first().innerText();
    const cls = await page.locator('.ant-message-notice').first().getAttribute('class').catch(() => '');
    return { text: txt.replace(/\s+/g, ' ').trim(), error: /error/.test(cls || '') };
  } catch (e) {
    return null;
  }
}
async function clearMsg(page) {
  await page.waitForTimeout(2600); // antd message 默认 3s 自动消失
}

async function openModal(page, btnText, scope = 'page') {
  const root = scope === 'page' ? page : page.locator('.ant-modal-content').first();
  await page.locator('button').filter({ hasText: new RegExp(`^${btnText}$`) }).first().click();
  await page.waitForTimeout(1200);
}

async function submitModal(page, btnText) {
  // 只用 `[type=submit]` 容易被 loading/禁用态干扰；只用文案又会被 antd 的
  // `<span>` 包裹与多弹窗残留坑到。故：先按「可见弹窗 + 文案」，再退化到 type=submit。
  const byText = page.locator('.ant-modal-content:visible button').filter({ hasText: new RegExp(`^\\s*${btnText}\\s*$`) }).first();
  if (await byText.count()) { await byText.click({ timeout: 8000 }); return; }
  const byType = page.locator('.ant-modal-content:visible button[type="submit"]').first();
  if (await byType.count()) { await byType.click({ timeout: 8000 }); return; }
  const all = await page.locator('.ant-modal-content button').allInnerTexts().catch(() => []);
  throw new Error(`submit button not found: ${btnText}; candidates=${JSON.stringify(all)}`);
}

// 支付闭环里的 3 条 FAIL 曾被误判为产品缺陷，实际是**探针挑中的订单早已 PAID**——
// 重复发起支付被服务端以 BP_ORDER_STATUS_CONFLICT 正确拒绝。故支付前必须现造一笔
// CREATED 状态的新单（走渠道拉单，落单即 CREATED），而不是复用列表第一行。
/** 造一笔**站内**订单（CREATED 待支付），供支付闭环测试使用。
 *
 * ⚠️ 2026-10-06 修正（两处，都是探针错而非产品错）：
 * 1) 原先造的是**渠道单**（走 channel-orders/pull）。渠道订单的钱已在淘宝/京东侧收妥，
 *    2026-10-06 起拉单即标记 PAID（Order.markChannelPaid），站内支付正确地拒绝它：
 *    BP_ORDER_STATUS_CONFLICT: PAID。拿"钱已收的渠道单"测"站内待支付"是选错数据。
 * 2) `productId` 实际走的是**商品编码**（MasterDataAclAdapter.findPublishedProduct 按 code 查
 *    `MD_PRODUCT` 的 PUBLISHED 记录），不是数值主键。用 900001 会得
 *    BP_ORDER_PRODUCT_NOT_PUBLISHED。这里用已发布商品编码 1001（无线鼠标）。
 */
async function newOrderId(page) {
  return page.evaluate(async () => {
    const token = localStorage.getItem('token') || '';
    const r = await fetch('/api/v1/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Tenant-Id': '0', Authorization: 'Bearer ' + token },
      body: JSON.stringify({
        customerId: 0,
        items: [{ productId: '1001', productName: '无线鼠标', quantity: 1, unitPrice: 99 }],
      }),
    });
    const j = await r.json().catch(() => null);
    if (!j || !j.data) return null;
    // 返回体是 {id: ...} 对象而非裸ID —— 取值失败会让下游正则 /\d{6,}/ 判空，
    // 表现为「订单号不可用: [object Object]」（本轮踩过）。
    const d = j.data;
    return (typeof d === 'object' && d !== null) ? d.id : d;
  });
}

/** 支付页只展示 payUrl（mock 链接里没有 paymentId），故 paymentId 必须从
 *  initiate 的响应体取；为不干扰 UI 那次发起，这里另造一笔新单。 */
async function newPaymentId(page) {
  return page.evaluate(async () => {
    const token = localStorage.getItem('token') || '';
    const post = async (path, body) => {
      const r = await fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Tenant-Id': '0', Authorization: 'Bearer ' + token },
        body: JSON.stringify(body),
      });
      return r.json().catch(() => null);
    };
    // ⚠️ 2026-10-06 修正：原先这里造**渠道单**再去发起站内支付，必然被
    // 「BP_ORDER_STATUS_CONFLICT: PAID」拒绝（渠道单钱已在渠道收妥）⇒ paymentId 永远取不到，
    // 连带「查询支付单有详情」长期假失败。改为造**站内**单（与 newOrderId 同口径）。
    const o = await post('/api/v1/orders', {
      customerId: 0,
      items: [{ productId: '1001', productName: '无线鼠标', quantity: 1, unitPrice: 99 }],
    });
    const raw = o && o.data;
    const oid = (raw && typeof raw === 'object') ? raw.id : raw;
    if (!oid) return null;
    const p = await post('/api/v1/payments/initiate', { orderId: String(oid) });
    return (p && p.data && p.data.paymentId) ? String(p.data.paymentId) : null;
  });
}

const rowCount = (page) => page.locator('tbody tr.ant-table-row').count();
const modalOpen = async (page) =>
  (await page.locator('.ant-modal-content:visible').count()) > 0 ||
  (await page.locator('.ant-drawer-content:visible').count()) > 0;

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const page = await (await browser.newContext({ viewport: { width: 1680, height: 1000 } })).newPage();
  const errors = [], failed = [];
  let cur = '';
  page.on('pageerror', e => errors.push({ cur, kind: 'pageerror', text: String(e.message).slice(0, 200) }));
  page.on('console', m => { if (m.type() === 'error') errors.push({ cur, kind: 'console', text: m.text().slice(0, 200) }); });
  page.on('response', r => { if (r.status() >= 400) failed.push({ cur, url: r.url().replace(BASE, '').slice(0, 120), status: r.status() }); });

  console.log('=== BONE 多渠道交易域 UI 巡检 ===');
  const pwd = await login(page);
  ok('登录 shell', !!pwd, '未能取得 token');
  if (!pwd) { await browser.close(); console.log(JSON.stringify({ R, errors, failed }, null, 1)); process.exit(1); }

  // ---------- 0. 侧边栏真实菜单清单（唯一真源，静态 grep 不可信） ----------
  await page.goto(`${BASE}/commerce`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);
  const sidebar = await page.evaluate(() => {
    const subs = [...document.querySelectorAll('.ant-menu-submenu-title')].map(e => e.innerText.trim());
    const items = [...document.querySelectorAll('.ant-menu-item')].map(e => e.innerText.trim());
    return { subs, items };
  });
  console.log('  sidebar subs:', JSON.stringify(sidebar.subs));
  console.log('  sidebar items:', JSON.stringify(sidebar.items.slice(0, 40)));
  await page.screenshot({ path: `${OUT}/00-sidebar.png` });

  const need = ['订单管理', '渠道管理', '商品上架', '库存管理', '发货物流'];
  const missing = need.filter(n => !sidebar.items.includes(n) && !sidebar.subs.includes(n));
  ok('侧边栏含 5 个交易菜单', missing.length === 0, '缺失: ' + missing.join('/'));

  // ---------- 1. 渠道管理 ----------
  try {
  cur = '渠道管理';
  let nav = await gotoMenu(page, '交易管理', '渠道管理');
  ok('导航→渠道管理', nav.found && /channels/.test(nav.hash || ''), JSON.stringify(nav));
  let n0 = await rowCount(page);
  ok('渠道列表有数据', n0 >= 4, `rows=${n0}`);
  await page.screenshot({ path: `${OUT}/01-channels.png` });

  // 1a. 渠道拉单（写）
  cur = '渠道拉单';
  await openModal(page, '渠道拉单');
  ok('渠道拉单弹窗打开', await modalOpen(page));
  const orderNo = 'UI' + Date.now();
  await fill(page, '渠道', '淘宝', 'select');
  await fill(page, '渠道原始订单号', orderNo);
  await fill(page, '渠道买家昵称', 'UI巡检买家');
  await fill(page, '内部商品ID', '900001');
  await page.waitForTimeout(500);
  await submitModal(page, '拉取并落单');
  let m = await waitMsg(page, 15000);
  ok('拉取并落单成功', !!m && !m.error, m ? m.text : 'no message');
  await clearMsg(page);
  await page.screenshot({ path: `${OUT}/02-pull.png` });

  // 1b. 停用 / 启用（状态开关 + Popconfirm）
  cur = '渠道启停';
  const toggleBtn = page.locator('tbody tr.ant-table-row').first().locator('button').filter({ hasText: /^(停用|启用)$/ }).first();
  const before = await toggleBtn.innerText().catch(() => '');
  await toggleBtn.click();
  await page.waitForTimeout(900);
  const confirmBtn = page.locator('.ant-popconfirm-buttons button.ant-btn-primary').first();
  if (await confirmBtn.count()) {
    await confirmBtn.click();
    let mm = await waitMsg(page, 12000) || { text: '(no msg)' };
    await clearMsg(page);
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3200);
    const after = await page.locator('tbody tr.ant-table-row').first().locator('button').filter({ hasText: /^(停用|启用)$/ }).first().innerText().catch(() => '');
    ok('渠道停用切换生效', after !== before, `${before} → ${after} (${mm.text})`);
    // 复原
    await page.locator('tbody tr.ant-table-row').first().locator('button').filter({ hasText: /^(停用|启用)$/ }).first().click();
    await page.waitForTimeout(900);
    const c2 = page.locator('.ant-popconfirm-buttons button.ant-btn-primary').first();
    if (await c2.count()) { await c2.click(); await clearMsg(page); }
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3200);
    await gotoMenu(page, '交易管理', '渠道管理');
  }
  await page.screenshot({ path: `${OUT}/03-channel-toggle.png` });

  } catch (e) { ok('渠道管理分节未崩溃', false, String(e.message).slice(0, 160)); }
  // ---------- 2. 商品上架 ----------
  try {
  cur = '商品上架';
  await gotoMenu(page, '交易管理', '商品上架');
  ok('导航→商品上架', /channel-products/.test(await page.evaluate(() => location.hash)), '');
  let p0 = await rowCount(page);
  ok('渠道商品列表有数据', p0 > 0, `rows=${p0}`);
  cur = '提交上架';
  await openModal(page, '商品上架');
  ok('商品上架弹窗打开', await modalOpen(page));
  await fill(page, '渠道', '京东', 'select');
  await fill(page, '内部商品ID', '900001');
  await fill(page, '商品名称', 'UI巡检-京东商品');
  await fill(page, '挂牌价', '88.5');
  await page.waitForTimeout(400);
  await submitModal(page, '提交上架');
  m = await waitMsg(page, 15000);
  ok('提交上架成功', !!m && !m.error, m ? m.text : 'no message');
  await clearMsg(page);
  await page.screenshot({ path: `${OUT}/04-listing.png` });

  // 2b. 同步库存 / 下架
  cur = '同步库存';
  const syncBtn = page.locator('tbody tr.ant-table-row').first().locator('button').filter({ hasText: /^同步库存$/ }).first();
  if (await syncBtn.count()) {
    await syncBtn.click();
    m = await waitMsg(page, 12000);
    ok('同步库存成功', !!m && !m.error, m ? m.text : 'no message');
    await clearMsg(page);
  } else ok('同步库存成功', false, '按钮未找到');

  cur = '下架';
  const delistBtn = page.locator('tbody tr.ant-table-row').filter({ hasText: '已上架' }).first()
    .locator('button').filter({ hasText: /^下架$/ }).first();
  if (await delistBtn.count()) {
    await delistBtn.click();
    await page.waitForTimeout(900);
    const c = page.locator('.ant-popconfirm-buttons button.ant-btn-primary').first();
    if (await c.count()) await c.click();
    m = await waitMsg(page, 12000);
    ok('商品下架成功', !!m && !m.error, m ? m.text : 'no message');
    await clearMsg(page);
  } else ok('商品下架成功', false, '无 ONLINE 行可下架');
  await page.screenshot({ path: `${OUT}/05-delist.png` });

  } catch (e) { ok('商品上架分节未崩溃', false, String(e.message).slice(0, 160)); }
  // ---------- 3. 库存管理 ----------
  try {
  cur = '库存管理';
  await gotoMenu(page, '交易管理', '库存管理');
  ok('导航→库存管理', /inventories/.test(await page.evaluate(() => location.hash)), '');
  ok('库存列表有数据', (await rowCount(page)) > 0, '');
  cur = '入库';
  await openModal(page, '入库');
  ok('入库弹窗打开', await modalOpen(page));
  await fill(page, '商品ID', '900001');
  await fill(page, '仓库编码', 'DEFAULT');
  await fill(page, '数量', '10');
  await submitModal(page, '提交');
  m = await waitMsg(page, 15000);
  ok('入库提交成功', !!m && !m.error, m ? m.text : 'no message');
  await clearMsg(page);
  await page.screenshot({ path: `${OUT}/06-inventory.png` });

  } catch (e) { ok('库存管理分节未崩溃', false, String(e.message).slice(0, 160)); }

  // ---------- 4b. 买家映射（2026-10-06 补测） ----------
  // 补测原因：侧边栏 9 个交易菜单，本探针原先只覆盖 6 个，
  // 恰好漏掉「买家映射」与「广播任务」这两个 2026-10-05 新增的功能页
  // —— 「菜单可见」不等于「功能可用」，必须逐个点进去。
  try {
  cur = '买家映射';
  await gotoMenu(page, '交易管理', '买家映射');
  const hBuyer = await page.evaluate(() => location.hash);
  ok('导航→买家映射', /channel-buyers/.test(hBuyer), `hash=${hBuyer}`);
  await page.waitForTimeout(1500);
  const rowsOrEmpty = await rowCount(page);
  const emptyHint = await page.locator('.ant-empty').count();
  ok(
    '买家映射页正常渲染（列表或空态）',
    rowsOrEmpty > 0 || emptyHint > 0,
    `rows=${rowsOrEmpty} emptyHint=${emptyHint}`,
  );
  // 绑定入口：先拉一笔带买家ID 的渠道单，页面上才可能有可绑定的候选
  const shadow = await page.evaluate(async () => {
    const token = localStorage.getItem('token') || '';
    const r = await fetch('/api/v1/channel-orders/pull', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Tenant-Id': '0', Authorization: 'Bearer ' + token },
      body: JSON.stringify({
        channelCode: 'TAOBAO', channelOrderNo: 'UIBUYER' + Date.now(),
        buyerId: 'UIBUYER' + (Date.now() % 100000), buyerNick: 'UI买家映射',
        receiverName: 'UI', receiverPhone: '13800000000', receiverAddress: 'UI',
        lines: [{ outerSkuId: 'TB900001', title: 'UI买家商品', quantity: 1, unitPrice: 66 }],
      }),
    });
    return r.status;
  });
  ok('带买家ID 的渠道单拉取成功（影子映射前提）', shadow === 200, `status=${shadow}`);
  // 影子映射由 ChannelBuyerObservedEventHandler 在**AFTER_COMMIT + REQUIRES_NEW** 写入，
  // 属最终一致：拉单返回时记录还没落库。必须轮询等它出现，
  // 否则会把它误判成"影子映射没生成"（本轮第一次跑就踩了）。
  let shadowReady = false;
  for (let i = 0; i < 12; i++) {
    const n = await page.evaluate(async () => {
      const token = localStorage.getItem('token') || '';
      const r = await fetch('/api/v1/channel-buyers?page=1&size=10', {
        headers: { 'X-Tenant-Id': '0', Authorization: 'Bearer ' + token },
      });
      const j = await r.json().catch(() => null);
      return ((j && j.data && j.data.records) || []).length;
    });
    if (n > 0) { shadowReady = true; break; }
    await page.waitForTimeout(1000);
  }
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);
  ok('影子映射已入库（买家映射列表可见数据）', shadowReady && (await rowCount(page)) > 0, `apiRows=${shadowReady}`);
  await page.screenshot({ path: `${OUT}/13-channel-buyers.png` });
  } catch (e) { ok('买家映射分节未崩溃', false, String(e.message).slice(0, 160)); }

  // ---------- 4c. 广播任务（2026-10-06 补测） ----------
  try {
  cur = '广播任务';
  await gotoMenu(page, '交易管理', '广播任务');
  const hTask = await page.evaluate(() => location.hash);
  ok('导航→广播任务', /broadcast-tasks/.test(hTask), `hash=${hTask}`);
  await page.waitForTimeout(1500);
  ok('广播任务页正常渲染（列表或空态）', (await rowCount(page)) > 0 || (await page.locator('.ant-empty').count()) > 0, '');
  await page.screenshot({ path: `${OUT}/14-broadcast-tasks.png` });
  } catch (e) { ok('广播任务分节未崩溃', false, String(e.message).slice(0, 160)); }

  // ---------- 4. 发货物流 ----------
  try {
  cur = '发货物流';
  await gotoMenu(page, '交易管理', '发货物流');
  ok('导航→发货物流', /shipments/.test(await page.evaluate(() => location.hash)), '');
  ok('发货单列表有数据', (await rowCount(page)) > 0, '');

  // 取一个真实订单号用于新建发货单
  await gotoMenu(page, '交易管理', '订单管理');
  await page.waitForTimeout(1500);
  const orderId = await page.locator('tbody tr.ant-table-row').first().locator('td').first().innerText().catch(() => '');
  console.log('  orderId sample =', JSON.stringify(orderId));

  await gotoMenu(page, '交易管理', '发货物流');
  cur = '新建发货单';
  if (orderId && /\d{6,}/.test(orderId)) {
    await openModal(page, '新建发货单');
    ok('新建发货单弹窗打开', await modalOpen(page));
    await fill(page, '订单ID', orderId.trim());
    await fill(page, '渠道', '淘宝', 'select');
    await fill(page, '收货人', 'UI巡检收货人');
    await fill(page, '收货电话', '13800000000');
    await fill(page, '收货地址', 'UI巡检地址-深圳市南山区');
    await submitModal(page, '创建');
    m = await waitMsg(page, 15000);
    ok('新建发货单成功', !!m && !m.error, m ? m.text : 'no message');
    await clearMsg(page);
  } else ok('新建发货单成功', false, `拿不到订单ID: ${orderId}`);
  await page.screenshot({ path: `${OUT}/07-shipment-create.png` });

  // 4b. 发货 → 回传渠道
  cur = '发货';
  const shipBtn = page.locator('tbody tr.ant-table-row').filter({ hasText: '待发货' }).first()
    .locator('button').filter({ hasText: /^发货$/ }).first();
  if (await shipBtn.count()) {
    await shipBtn.click();
    await page.waitForTimeout(1200);
    ok('发货弹窗打开', await modalOpen(page));
    await fill(page, '运单号', 'SF' + Date.now());
    await page.waitForTimeout(400);
    await submitModal(page, '确认发货');
    m = await waitMsg(page, 15000);
    ok('发货并回传渠道成功', !!m && !m.error, m ? m.text : 'no message');
    await clearMsg(page);
  } else ok('发货并回传渠道成功', false, '无 CREATED 行');
  await page.screenshot({ path: `${OUT}/08-ship.png` });

  // 4c. 轨迹 Drawer
  cur = '轨迹';
  const traceBtn = page.locator('tbody tr.ant-table-row').first().locator('button').filter({ hasText: /^轨迹$/ }).first();
  if (await traceBtn.count()) {
    await traceBtn.click();
    await page.waitForTimeout(2000);
    const drawerRows = await page.locator('.ant-drawer-content .ant-timeline-item, .ant-drawer-content tbody tr.ant-table-row').count();
    ok('物流轨迹抽屉有内容', drawerRows > 0, `items=${drawerRows}`);
    await page.screenshot({ path: `${OUT}/09-trace.png` });
    await page.locator('.ant-drawer-close').first().click().catch(() => {});
    await page.waitForTimeout(800);
  } else ok('物流轨迹抽屉有内容', false, '轨迹按钮未找到');

  // 4d. 签收
  cur = '签收';
  const signBtn = page.locator('tbody tr.ant-table-row').filter({ hasText: /已发货|运输中/ }).first()
    .locator('button').filter({ hasText: /^签收$/ }).first();
  if (await signBtn.count()) {
    await signBtn.click();
    await page.waitForTimeout(900);
    const c = page.locator('.ant-popconfirm-buttons button.ant-btn-primary').first();
    if (await c.count()) await c.click();
    m = await waitMsg(page, 12000);
    ok('签收成功', !!m && !m.error, m ? m.text : 'no message');
    await clearMsg(page);
  } else ok('签收成功', false, '无待签收行');
  await page.screenshot({ path: `${OUT}/10-sign.png` });

  } catch (e) { ok('发货物流分节未崩溃', false, String(e.message).slice(0, 160)); }
  // ---------- 5. 订单/支付（既有页面回归） ----------
  try {
  for (const [name, hash] of [['订单管理', 'orders'], ['支付管理', 'payments']]) {
    cur = name;
    await gotoMenu(page, '交易管理', name);
    const h = await page.evaluate(() => location.hash);
    // 判据按页面**形态**分流：订单管理是列表页，支付管理是「发起/查询」表单页
    // （它本就没有列表接口，早先按 rows>0 判是探针判据错误，不是产品缺陷）。
    if (hash === 'orders') {
      const rows = await rowCount(page);
      ok('订单管理 可访问且有数据', h.includes(hash) && rows > 0, `hash=${h} rows=${rows}`);
    } else {
      const inputs = await page.locator('input.ant-input').count();
      ok('支付管理 渲染为表单页', h.includes(hash) && inputs >= 2, `hash=${h} inputs=${inputs}`);
    }
    await page.screenshot({ path: `${OUT}/11-${hash}.png` });
  }

  // 5b. 支付闭环：发起支付 → 拿到支付链接 → 查询支付单详情
  cur = '发起支付';
  await gotoMenu(page, '交易管理', '支付管理');
  const payOrderId = String(await newOrderId(page) || '').trim();
  console.log('  fresh order for payment =', JSON.stringify(payOrderId));
  const orderInput = page.locator('input[placeholder^="订单号"]').first();
  if (await orderInput.count() && /\d{6,}/.test(payOrderId)) {
    await orderInput.fill(payOrderId);
    await page.locator('button').filter({ hasText: /^发起支付$/ }).first().click();
    m = await waitMsg(page, 15000);
    ok('发起支付成功', !!m && !m.error, m ? m.text : 'no message');
    await clearMsg(page);
    const payLink = await page.locator('a[target="_blank"]').first().getAttribute('href').catch(() => null);
    ok('返回支付链接', !!payLink, String(payLink));
    const pid = await newPaymentId(page);
    console.log('  paymentId for query =', JSON.stringify(pid));
    if (pid) {
      cur = '查询支付单';
      await page.locator('input[placeholder="支付单号"]').first().fill(pid);
      await page.locator('button').filter({ hasText: /^查询$/ }).first().click();
      await page.waitForTimeout(3000);
      const hasDetail = await page.locator('.ant-descriptions, .ant-card:has-text("支付")').count();
      ok('查询支付单有详情', hasDetail > 0, `nodes=${hasDetail}`);
    } else ok('查询支付单有详情', false, `未取到 paymentId（payLink=${payLink}）`);
  } else ok('发起支付成功', false, `订单号不可用: ${payOrderId}`);
  await page.screenshot({ path: `${OUT}/12-payment.png` });

  // 5c. 负向：渠道订单（钱已在淘宝侧收妥、落单即 PAID）**不得**再发起站内支付。
  // 这条锁的是 2026-10-06 的语义修正：渠道单不再停留CREATED。
  // 若哪天它又能发起站内支付，说明 markChannelPaid 的改动被回退了（会导致重复收款）。
  cur = '渠道单支付负向';
  const channelPay = await page.evaluate(async () => {
    const token = localStorage.getItem('token') || '';
    const post = async (path, body) => {
      const r = await fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Tenant-Id': '0', Authorization: 'Bearer ' + token },
        body: JSON.stringify(body),
      });
      return { status: r.status, body: await r.json().catch(() => null) };
    };
    const pulled = await post('/api/v1/channel-orders/pull', {
      channelCode: 'TAOBAO', channelOrderNo: 'UINEG' + Date.now(),
      buyerNick: 'UI负向买家', receiverName: 'UI', receiverPhone: '13800000000', receiverAddress: 'UI',
      lines: [{ outerSkuId: 'TB900001', title: 'UI负向商品', quantity: 1, unitPrice: 99 }],
    });
    const oid = pulled.body && pulled.body.data;
    if (!oid) return { skipped: true, reason: `拉单失败 status=${pulled.status}` };
    const paid = await post('/api/v1/payments/initiate', { orderId: oid });
    const code = paid.body && paid.body.code;
    const msg = String((paid.body && paid.body.message) || '');
    return { skipped: false, status: paid.status, code, msg, rejected: paid.status === 409 || msg.includes('ORDER_STATUS_CONFLICT') };
  });
  ok(
    '渠道订单被拒绝站内支付（不会重复收款）',
    channelPay.skipped ? false : !!channelPay.rejected,
    channelPay.skipped ? channelPay.reason : `status=${channelPay.status} code=${channelPay.code} msg=${channelPay.msg}`,
  );

  } catch (e) { ok('订单/支付回归分节未崩溃', false, String(e.message).slice(0, 160)); }
  await browser.close();
  const pass = R.filter(r => r.pass).length;
  console.log(`\n=== ${pass}/${R.length} PASS ===`);
  // 只保留本轮真实错误（排除 qiankun 沙箱与 probe 时序噪声）
  const noise = /useForm is not connected|Warning:|favicon|ResizeObserver/;
  const realErrors = errors.filter(e => !noise.test(e.text));
  console.log('console/page errors:', JSON.stringify(realErrors.slice(0, 12), null, 1));
  console.log('failed requests:', JSON.stringify(failed.slice(0, 12), null, 1));
  console.log(JSON.stringify({ summary: { pass, total: R.length }, fails: R.filter(r => !r.pass) }));
})();
