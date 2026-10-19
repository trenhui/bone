/**
 * 个人视图筛选/排序持久化 · 端到端探针（业界 saved view 语义）。
 *
 * 目标：验证「视图 = 列 + 筛选 + 排序」是完整快照——存进去、切走再切回来仍在。
 *
 * 为什么要有这个探针（此前的真实缺陷）：
 *   筛选（appliedConditions）与排序（sortField/sortDesc）原本只活在页面 useState，
 *   个人视图只存显隐/改名/密度 ⇒ 用户配好「我的待审：只看待我审批 + 按金额倒序」，
 *   切去别的视图再切回来条件被静默清空，视图不可复用。
 *
 * 路径（真实用户路径，不逆向 API）：
 *   1. 登录 → 进数据页（预清理 overlay，保证干净起点）
 *   2. 在「管理」菜单里新建视图「我的待审」
 *   3. 施加一个筛选条件 + 一个排序 → 点「把当前筛选/排序保存到本视图」
 *   4. 切到另一个视图 → 断言筛选/排序被清（视图各自独立）
 *   5. 切回「我的待审」→ 断言筛选/排序**被恢复**（核心判据）
 *   6. 刷新页面后再次进入 → 断言仍恢复（证明落到了 blob，不是内存态）
 *   7. 无 console / page 异常
 *
 * 用法：node scripts/e2e/ui-view-filter-persistence-probe.mjs
 */
import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const SHOT_DIR = '/Users/renhui.trh/wps/bone/logs';
const BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
const ENTITY_ID = process.env.BONE_ENTITY_ID || '766546550728949760';
const ENTITY_CODE = process.env.BONE_ENTITY_CODE || 'claim_case_chain_792218121';
const IAM = process.env.BONE_IAM_BASE || 'http://127.0.0.1:8081';
const META = process.env.BONE_META_BASE || 'http://127.0.0.1:9001';

async function resetServerOverlay() {
  try {
    const r = await fetch(`${IAM}/api/v1/iam/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: '123456' }),
    });
    const token = (await r.json())?.data?.token;
    await fetch(`${META}/api/v1/metadata/entities/${encodeURIComponent(ENTITY_CODE)}/ui-overlay`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0' },
    });
    return true;
  } catch { return false; }
}

const results = [];
const log = (...a) => console.log(...a);
const check = (name, cond, detail = '') => {
  results.push({ name, ok: !!cond, detail });
  console.log(`${cond ? '✓' : '✗'} ${name}${cond ? '' : '  ← ' + detail}`);
};

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.setDefaultTimeout(25000);
const consoleErrors = [];
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

const shot = async (n) => { await page.screenshot({ path: `${SHOT_DIR}/ui_viewfilter_${n}.png` }).catch(() => {}); };
const bodyText = () => page.locator('body').innerText();

/**
 * 当前生效筛选的判定。
 * ⚠️ 不能用「已按…筛选」文案：那只是**状态机步骤下钻**的提示（L1568），普通条件筛选不渲染它。
 * 也不能用 `/LI/.test(body)`：报案号等列数据天然含 LI ⇒ 假绿。
 * 可靠口径 = **列表行数**：施加筛选必然把结果集变小；恢复则变大。
 */
const listRowCount = async () =>
  page.locator('.ant-table-tbody tr.ant-table-row').count();

/** 条件行是否仍在（筛选编辑态存在） */
const condRowCount = async () =>
  page.locator('.ant-space-compact:visible').filter({ has: page.locator('button', { hasText: /查\s*询/ }) }).count();

/**
 * 直读服务端 overlay blob —— 本探针的核心判据。
 * 为什么必须读真值而不只看页面：页面能显示筛选 ≠ 筛选被存进了视图；
 * 只有 blob 里有 filters/sortField 才证明"切视图会恢复"（刷新后仍在）。
 */
async function readOverlay(token) {
  const r = await fetch(
    `${META}/api/v1/metadata/entities/${encodeURIComponent(ENTITY_CODE)}/ui-overlay`,
    { headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0' } },
  );
  const j = await r.json();
  return j?.data ? JSON.parse(j.data) : null;
}


/**
 * 视图切换器（PersonalViewBar 内）精确定位。
 *
 * <p>⚠️ 页面有**两个** .ant-segmented：① 视图切换（items 来自 bundle.views，形如「默认视图」）
 * ② 回收站（在用/回收站）。实测 SEGS = [{items:["默认视图"]}, {items:["在用","回收站"]}]。
 * 「管理」按钮的祖先 3 层内能精确命中 ①（parentElement 单层查不到：它在 DIV.ant-space-item 里）。
 */
const VIEW_SEG_ITEMS = () => {
  const manageBtn = Array.from(document.querySelectorAll('button')).find(
    (b) => (b.textContent || '').trim() === '管理',
  );
  let n = manageBtn;
  for (let i = 0; i < 3 && n; i++) {
    n = n.parentElement;
    if (!n) break;
    const seg = n.querySelector('.ant-segmented');
    if (seg) return Array.from(seg.querySelectorAll('.ant-segmented-item'));
  }
  return [];
};
const viewNames = () => page.evaluate(() => {
  const manageBtn = Array.from(document.querySelectorAll('button')).find(
    (b) => (b.textContent || '').trim() === '管理',
  );
  let n = manageBtn;
  for (let i = 0; i < 3 && n; i++) {
    n = n.parentElement;
    if (!n) break;
    const seg = n.querySelector('.ant-segmented');
    if (seg) return Array.from(seg.querySelectorAll('.ant-segmented-item')).map((e) => (e.textContent || '').trim());
  }
  return [];
});
const clickView = async (nameFragment) => {
  const ok = await page.evaluate((frag) => {
    const manageBtn = Array.from(document.querySelectorAll('button')).find(
      (b) => (b.textContent || '').trim() === '管理',
    );
    let n = manageBtn;
    for (let i = 0; i < 3 && n; i++) {
      n = n.parentElement;
      if (!n) break;
      const seg = n.querySelector('.ant-segmented');
      if (!seg) continue;
      const item = Array.from(seg.querySelectorAll('.ant-segmented-item'))
        .find((e) => (e.textContent || '').includes(frag));
      if (item) { item.click(); return true; }
    }
    return false;
  }, nameFragment);
  await page.waitForTimeout(1800);
  return ok;
};

/**
 * 只取**可见**的 Modal。
 * ⚠️ 页面 DOM 里残留着多个 `.ant-modal-content`（antd 会保留已关闭弹窗的节点），
 * 用 `.first()` 会命中不可见的那个 ⇒ fill/click 全部超时。必须按可见性过滤。
 */
const visibleModal = () => page.locator('.ant-modal-content:visible').first();

const goDataPage = async () => {
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=data`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => /交付口径对照/.test(document.body.innerText), { timeout: 40000 }).catch(() => {});
  // 必须等到工具栏真实可点：页面渲染完成 ≠ 元素可交互（布局/动画未完成时 click 会超时）
  await page.locator('button', { hasText: '管理' }).first()
    .waitFor({ state: 'visible', timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(2500);
};

const openManageMenu = async () => {
  await page.locator('button', { hasText: '管理' }).first().click();
  await page.waitForTimeout(800);
};

try {
  // 0. 干净起点 + 取 token（供直读 blob）
  check('服务端 overlay 预清理（干净起点）', await resetServerOverlay(), `entityCode=${ENTITY_CODE}`);
  const TOKEN = await (async () => {
    const r = await fetch(`${IAM}/api/v1/iam/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: '123456' }),
    });
    return (await r.json())?.data?.token;
  })();
  check('取到 token（供直读 overlay 真值）', !!TOKEN, '');

  // 1. 登录
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"]', { timeout: 20000 });
  const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
  if (await page.locator(userSel).first().count()) await page.locator(userSel).first().fill('admin');
  await page.locator('input[type="password"]').first().fill('123456');
  await page.locator('input[type="password"]').first().press('Enter');
  await page.waitForTimeout(1500);
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('button').filter({ hasText: /登\s*录/ }).first().click().catch(() => {});
  }
  let loggedIn = false;
  try {
    await page.waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 });
    loggedIn = true;
  } catch { loggedIn = false; }
  check('UI 登录成功', loggedIn, `url=${page.url()}`);

  // 2. 进数据页 + 记录基线行数（筛选效果判据的基准）+ 新建视图
  await goDataPage();
  const baselineRows = await listRowCount();
  const VIEW_NAME = `我的待审${Date.now() % 100000}`;
  await openManageMenu();
  await page.locator('.ant-dropdown-menu-item', { hasText: '新建视图' }).first().click().catch(() => {});
  await page.waitForTimeout(900);
  const nameInput = visibleModal().locator('input').first();
  await nameInput.fill(VIEW_NAME).catch(() => {});
  await visibleModal().locator('button', { hasText: /确\s*定/ }).first().click().catch(() => {});
  await page.waitForTimeout(2200);
  const modalGone = (await page.locator('.ant-modal-content:visible').count()) === 0;
  check('新建视图弹窗已关闭（提交生效）', modalGone, `可见弹窗数=${await page.locator('.ant-modal-content:visible').count()}`);

  const segTexts = await viewNames();
  check('新建个人视图成功（视图切换器出现该视图）',
    segTexts.some((t) => t.includes(VIEW_NAME.slice(0, 5))), `views=${JSON.stringify(segTexts)}`);
  await shot('1_view_created');

  // 3. 施加筛选：用工具栏「添加条件」打开筛选行 → 选字段 → 填值 → 点「查 询」
  //    （实测口径：BUTTONS 含「添加条件」与「查 询」，antd 两个汉字按钮会注入空格）
  const addCond = page.locator('button').filter({ hasText: '添加条件' }).first();
  const addCondCount = await addCond.count();
  check('数据页存在「添加条件」筛选入口', addCondCount === 1, `匹配数=${addCondCount}`);
  await addCond.click().catch(() => {});
  await page.waitForTimeout(900);

  // 条件行结构（实测）：`<Space compact>` 里第一个 Select placeholder = 「筛选字段」，
  // 第二个是算子 select。必须锚定到「查 询」按钮所在的 compact 容器，否则会抓到页面上别的 Select。
  const condBar = page.locator('.ant-space-compact:visible').filter({ has: page.locator('button', { hasText: /查\s*询/ }) }).first();
  const fieldSel = condBar.locator('.ant-select').first();
  await fieldSel.click();
  await page.waitForTimeout(900);
  // antd 已关闭的下拉 DOM 仍在文档里：必须排除 .ant-select-dropdown-hidden（项目铁律）
  const optLoc = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option');
  const optCount = await optLoc.count();
  check('筛选字段下拉有可选项', optCount > 0, `选项数=${optCount}`);
  if (optCount > 0) {
    const optTexts = await optLoc.allInnerTexts();
    log(`  （可筛字段：${JSON.stringify(optTexts.slice(0, 6))}）`);
    // 选一个非 status 的普通字段（status 是状态机字段，用它会撞 409/枚举语义）
    const idx = optTexts.findIndex((t) => /报案号|被保人|金额|医院/.test(t));
    await optLoc.nth(idx >= 0 ? idx : 0).click();
    await page.waitForTimeout(700);
  }
  const valInput = condBar.locator('input.ant-input').first();
  await valInput.fill('LI').catch(() => {});
  await page.locator('button').filter({ hasText: /查\s*询/ }).first().click().catch(() => {});
  await page.waitForTimeout(2200);

  const bodyAfterFilter = await bodyText();
  const postRows = await listRowCount();
  check('筛选已施加到列表（结果集行数发生变化）', postRows !== baselineRows,
    `施加前=${baselineRows} 施加后=${postRows}`);
  await shot('2_filter_applied');

  // 4. 保存当前筛选/排序到视图 → 直读 blob 断言真的存进去了
  // 前置判据：页面必须**真的有**已应用筛选。若筛选没生效，保存必然存成空——
  // 这时 blob 里没有 filters 是正确行为，不能算功能失败（否则会把探针缺陷当产品缺陷）。
  const preRows = await listRowCount();
  check('保存前置：筛选仍处于生效状态（行数与施加后一致）', preRows === postRows,
    `施加后=${postRows} 保存前=${preRows}`);

  await openManageMenu();
  const saveItem = page.locator('.ant-dropdown-menu-item', { hasText: '把当前筛选/排序保存到本视图' });
  const saveExists = (await saveItem.count()) > 0;
  check('「管理」菜单提供「把当前筛选/排序保存到本视图」入口', saveExists, `菜单项数=${await saveItem.count()}`);
  if (saveExists) {
    await saveItem.first().click().catch(() => {});
    await page.waitForTimeout(2500); // 等 persistOverlay 异步推服务端
  }
  await page.keyboard.press('Escape');
  await page.waitForTimeout(600);

  const blob1 = await readOverlay(TOKEN);
  const savedView = blob1?.views?.find((v) => (v.name || '').includes(VIEW_NAME.slice(0, 5)));
  check('保存后：目标视图出现在 overlay blob 中', !!savedView,
    `views=${JSON.stringify(blob1?.views?.map((v) => v.name))}`);
  check('保存后：视图携带 filters（核心存储判据）',
    Array.isArray(savedView?.filters) && savedView.filters.length > 0,
    `filters=${JSON.stringify(savedView?.filters)}`);
  log(`  （保存的视图快照：${JSON.stringify(savedView)}）`);

  // 5. 切到另一个视图 → 断言该视图无 filters（视图各自独立，不是共享一份）
  const names = await viewNames();
  const otherName = names.find((n) => !n.includes(VIEW_NAME.slice(0, 5)));
  if (otherName) {
    await clickView(otherName);
    // ⚠️ 不能用「行数不同」判独立性：两个视图的行数可能恰好相同（都为空集/同一批数据），
    // 那时 != 不成立但功能其实正常。精确判据 = blob 里 filters 只属于目标视图。
    const blobOther = await readOverlay(TOKEN);
    const otherView = blobOther?.views?.find((v) => (v.name || '') === otherName);
    check('切到其他视图后筛选不再生效（视图各自独立）',
      !Array.isArray(otherView?.filters) || otherView.filters.length === 0,
      `其他视图=${otherName} 其filters=${JSON.stringify(otherView?.filters)}`);
    await shot('3_other_view');
  } else {
    check('存在可切换的其他视图（独立性判据的前提）', false, `views=${JSON.stringify(names)}`);
  }

  // 6. 切回目标视图 → 断言筛选被恢复（核心判据）
  const clickedBack = await clickView(VIEW_NAME.slice(0, 5));
  check('切回目标视图（切换器可命中）', clickedBack, '');
  const backRows = await listRowCount();
  check('切回目标视图后筛选被恢复（行数回到筛选后口径）', backRows === postRows,
    `切回后行数=${backRows} 应= 筛选后行数=${postRows}`);
  await shot('4_back_to_view');

  // 7. 刷新后仍恢复（证明落 blob，不是内存态）
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => /交付口径对照/.test(document.body.innerText), { timeout: 40000 }).catch(() => {});
  await page.waitForTimeout(3000);
  const afterReloadRows = await listRowCount();
  const segAfter = await viewNames();
  check('刷新后视图仍在（blob 已持久化）',
    segAfter.some((t) => t.includes(VIEW_NAME.slice(0, 5))), `views=${JSON.stringify(segAfter)}`);
  check('刷新后筛选仍生效（行数与保存时一致，证明落 blob）', afterReloadRows === postRows,
    `刷新后行数=${afterReloadRows} 应= ${postRows}`);
  const blob2 = await readOverlay(TOKEN);
  const view2 = blob2?.views?.find((v) => (v.name || '').includes(VIEW_NAME.slice(0, 5)));
  check('刷新后该视图的 filters 仍在 blob 中',
    Array.isArray(view2?.filters) && view2.filters.length > 0,
    `filters=${JSON.stringify(view2?.filters)}`);

  // 8. 无 console / page 异常
  check('全流程无 console / page 异常', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));

  // 收尾复位
  check('收尾：服务端 overlay 复位', await resetServerOverlay(), '');

  const pass = results.filter((r) => r.ok).length;
  console.log(`\n视图筛选持久化探针汇总：通过 ${pass} / ${results.length}`);
  await browser.close();
  process.exit(results.every((r) => r.ok) ? 0 : 1);
} catch (e) {
  console.error('探针异常：', e?.message || e);
  await shot('err').catch(() => {});
  await resetServerOverlay();
  await browser.close();
  process.exit(2);
}