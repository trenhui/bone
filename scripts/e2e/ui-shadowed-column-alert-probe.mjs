/**
 * 「个人偏好隐藏契约列」自伤遮蔽告警 · 真实触发探针。
 *
 * 目标：验证 InterfaceTab（交付预览 Tab）第 225 行的告警在真实数据上能生效——
 * 即「用户运行时把某契约默认显示的列个人隐藏后，交付预览能看到『配了不生效』告警」，
 * 而不是一个永不触发的摆设。
 *
 * 路径（真实用户路径，不逆向 API）：
 *   1. 登录 → 进实体详情 ?tab=ui（交付预览）
 *   2. 点跨层 CTA「去数据页配个人显示」→ 跳 ?tab=data（个人覆盖编辑器所在）
 *   3. 数据页工具栏点「列设置」弹层 → 关掉第一个契约默认显示的列开关（个人隐藏）
 *   4. 断言 PersonalViewBar 出现「N 列被个人偏好隐藏 · 恢复显示」（数据页侧自救入口）
 *   5. 回 ?tab=ui → 断言 InterfaceTab 告警「你的个人偏好正在隐藏 N 个契约列」出现
 *   6. 清理：回数据页点「恢复显示」恢复被隐藏的契约列 → 回 ?tab=ui 断言告警消失
 *
 * 复用 ui-interface-tab-boundary-probe.mjs 的登录流程（localhost 源，网关 CORS 只放行 localhost）。
 * 用法：node scripts/e2e/ui-shadowed-column-alert-probe.mjs
 */
import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const SHOT_DIR = '/Users/renhui.trh/wps/bone/logs';
const BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
const ENTITY_ID = process.env.BONE_ENTITY_ID || '766546550728949760';
const ENTITY_CODE = process.env.BONE_ENTITY_CODE || 'claim_case_chain_792218121';
const IAM = process.env.BONE_IAM_BASE || 'http://127.0.0.1:8081';
const META = process.env.BONE_META_BASE || 'http://127.0.0.1:9001';

/**
 * 服务端 overlay 预清理（诊断实测的关键：起始残留会让断言读到上一轮的覆盖）。
 * fetchOverlay 是「服务端优先」——服务端一旦有 blob 就直接采用、忽略本地缓存，
 * 因此探针必须从「服务端从未配置」的干净态起步，否则测的是上一轮的残留。
 */
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
  } catch {
    return false;
  }
}

const results = [];
const log = (...a) => console.log(...a);
const check = (name, cond, detail = '') => {
  results.push({ name, ok: !!cond, detail });
  console.log(`${cond ? '✓' : '✗'} ${name}${cond ? '' : '  ← ' + detail}`);
};

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(25000);
const consoleErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200));
});
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

const shot = async (n) => {
  const p = `${SHOT_DIR}/ui_shadowed_${n}.png`;
  await page.screenshot({ path: p, fullPage: false }).catch(() => {});
  return p;
};
const bodyText = () => page.locator('body').innerText();
// 切换双口径预览 Segmented（全员所见 / 我的所见）。告警仅在「我的所见」口径显示。
const clickSeg = async (text) => {
  await page.locator('.ant-segmented-item', { hasText: text }).first().click();
  await page.waitForTimeout(600);
};

try {
  // 0. 服务端 overlay 预清理——保证从「从未配置」干净态起步（否则测到上一轮残留）
  const preCleaned = await resetServerOverlay();
  check('服务端 overlay 预清理（干净起点）', preCleaned, `entityCode=${ENTITY_CODE}`);

  // 1. 登录
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"], input[placeholder*="密码"]', { timeout: 20000 });
  const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
  if (await page.locator(userSel).first().count()) {
    await page.locator(userSel).first().fill('admin');
  }
  const pwd = page.locator('input[type="password"]').first();
  await pwd.fill('123456');
  await pwd.press('Enter');
  await page.waitForTimeout(1200);
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('button').filter({ hasText: /登\s*录/ }).first().click().catch(() => {});
    await page.waitForTimeout(3000);
  }
  let loggedIn = false;
  try {
    await page.waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 });
    loggedIn = true;
  } catch { loggedIn = false; }
  check('UI 登录成功（壳层菜单渲染）', loggedIn, `url=${page.url()}`);

  // 2. 进交付预览 ?tab=ui，点「去数据页配个人显示」跳数据页
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=ui`, { waitUntil: 'domcontentloaded' });
  let ifaceMounted = false;
  try {
    await page.locator('button').filter({ hasText: '去数据页' }).first()
      .waitFor({ state: 'visible', timeout: 40000 });
    ifaceMounted = true;
  } catch { ifaceMounted = false; }
  check('交付预览层挂载（去数据页 CTA 可见）', ifaceMounted, `url=${page.url()}`);

  const dataCta = page.locator('button').filter({ hasText: '去数据页' }).first();
  await dataCta.click();
  let toData = false;
  try {
    await page.waitForFunction(
      () => /[?&]tab=data/.test(location.hash + location.search) || location.href.includes('tab=data'),
      { timeout: 12000 },
    );
    toData = true;
  } catch { toData = false; }
  check('点 CTA 跳转到数据页（个人覆盖编辑器所在，tab=data）', toData, `url=${page.url()}`);
  await page.waitForTimeout(2500);
  await shot('1_data_page');

  // 3. 数据页工具栏「列设置」弹层
  const colSettingsBtn = page.locator('button').filter({ hasText: '列设置' }).first();
  const colSettingsCount = await colSettingsBtn.count();
  check('数据页工具栏「列设置」按钮渲染', colSettingsCount === 1, `匹配数=${colSettingsCount}`);
  if (colSettingsCount !== 1) throw new Error('未找到列设置按钮，终止');

  await colSettingsBtn.click();
  let popoverOpen = false;
  try {
    await page.locator('.col-settings-row').first().waitFor({ state: 'visible', timeout: 15000 });
    popoverOpen = true;
  } catch { popoverOpen = false; }
  check('列设置弹层打开（含契约列行）', popoverOpen, '未出现 .col-settings-row');
  await page.waitForTimeout(800);
  await shot('2_col_settings');

  // 4. 关掉第一个「可个人切换」（非契约禁用）的列开关 —— 个人隐藏 → 应进 shadowedColumns
  const switchLoc = page.locator('.col-settings-row')
    .filter({ hasNot: page.locator('.ant-switch-disabled') })
    .first();
  const switchCount = await switchLoc.count();
  check('存在可个人切换的列开关（非契约禁用列）', switchCount >= 1, `可切换行数=${switchCount}`);
  if (switchCount < 1) throw new Error('无可用列开关，终止');
  const targetCol = (await switchLoc.innerText().catch(() => '')).replace(/\s+/g, ' ').trim().slice(0, 30);
  await switchLoc.locator('.col-settings-switch').first().click();
  await page.waitForTimeout(3000); // 等 persistOverlay 异步推服务端
  log(`（已个人隐藏列：${targetCol}）`);

  // 5. 数据页侧自救入口出现
  let dataSelfHelp = false;
  try {
    await page.waitForFunction(() => /列被个人偏好隐藏/.test(document.body.innerText), { timeout: 10000 });
    dataSelfHelp = true;
  } catch { dataSelfHelp = false; }
  const dataBody = await bodyText();
  check('数据页 PersonalViewBar 出现「N 列被个人偏好隐藏 · 恢复显示」', dataSelfHelp, dataBody.slice(0, 140));
  await shot('3_data_selfhelp');

  // 6. 回交付预览，断言告警
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=ui`, { waitUntil: 'domcontentloaded' });
  let ifaceBack = false;
  try {
    await page.locator('button').filter({ hasText: /团队列|界面设计器/ }).first()
      .waitFor({ state: 'visible', timeout: 40000 });
    ifaceBack = true;
  } catch { ifaceBack = false; }
  check('交付预览层重新挂载', ifaceBack, `url=${page.url()}`);
  // 等契约层（contractListView）hydrate 完成——否则 shadowedColumns 因 listView 未就绪而暂为空，告警不显示
  let contractReady = false;
  try {
    await page.waitForFunction(
      () => /列集合、列序、列宽、固定列与列标题由「界面设计器」契约定义/.test(document.body.innerText),
      { timeout: 20000 },
    );
    contractReady = true;
  } catch { contractReady = false; }
  check('交付预览契约层 hydrate 完成（列集合 info alert 出现）', contractReady, '');
  // 告警仅在「我的所见（含个人覆盖）」口径显示：先切 scope=mine 再断言。
  // 前置判别「个人层徽标」：它只依赖 scope，不依赖 overlay 数据——用于区分
  // 「scope 没切到 mine（探针假阴性）」与「切到了但 shadowed 真为空（产品问题）」。
  await clickSeg('我的所见');
  let mineTagShown = false;
  try {
    await page.waitForFunction(
      () => /个人偏好\s*·\s*仅自己可见/.test(document.body.innerText),
      { timeout: 8000 },
    );
    mineTagShown = true;
  } catch { mineTagShown = false; }
  check('已切到「我的所见」口径（个人层徽标出现）', mineTagShown, 'scope 未切换成功则后续告警断言无意义');
  let alertShown = false;
  try {
    await page.waitForFunction(
      () => /你的个人偏好正在隐藏\s*\d+\s*个契约列/.test(document.body.innerText),
      { timeout: 20000 },
    );
    alertShown = true;
  } catch { alertShown = false; }
  const ifaceBody = await bodyText();
  check('交付预览告警「你的个人偏好正在隐藏 N 个契约列」真实出现（我的所见口径）', alertShown, ifaceBody.slice(0, 160));
  await shot('4_iface_alert');

  // 7. 清理：回数据页点「恢复显示」恢复被隐藏的契约列
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=data`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2500);
  const recoverBtn = page.locator('button').filter({ hasText: /恢复显示|列被个人偏好隐藏/ }).first();
  const recoverCount = await recoverBtn.count();
  check('清理入口「恢复显示」按钮存在', recoverCount === 1, `匹配数=${recoverCount}`);
  if (recoverCount === 1) {
    await recoverBtn.click();
    await page.waitForTimeout(2500); // 等 persistOverlay 异步推服务端
  }
  // 若「恢复显示」按钮已消失（恢复完成），也视为清理成功

  // 8. 回交付预览，断言告警已消失（同样切「我的所见」口径，避免「默认 contract 口径本就不显示」造成的假阴性）
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=ui`, { waitUntil: 'domcontentloaded' });
  let ifaceBack2 = false;
  try {
    await page.locator('button').filter({ hasText: /团队列|界面设计器/ }).first()
      .waitFor({ state: 'visible', timeout: 40000 });
    ifaceBack2 = true;
  } catch { ifaceBack2 = false; }
  check('清理后交付预览层重新挂载', ifaceBack2, `url=${page.url()}`);
  // 同样先等契约层 hydrate，再切 mine 口径断言告警消失（避免「contract 口径本不显示」造成的假阴性）
  let contractReady2 = false;
  try {
    await page.waitForFunction(
      () => /列集合、列序、列宽、固定列与列标题由「界面设计器」契约定义/.test(document.body.innerText),
      { timeout: 20000 },
    );
    contractReady2 = true;
  } catch { contractReady2 = false; }
  check('清理后交付预览契约层 hydrate 完成', contractReady2, '');
  await clickSeg('我的所见');
  await page.waitForTimeout(1500);
  const ifaceBody2 = await bodyText();
  const alertGone = !/你的个人偏好正在隐藏\s*\d+\s*个契约列/.test(ifaceBody2);
  check('清理后告警消失（恢复显示不污染团队契约）', alertGone, ifaceBody2.slice(0, 160));
  await shot('5_iface_recovered');

  // 8.5 UI 自救只把 shadowed 列写回 visible:true（语义等价但留残余键），再服务端 DELETE 一次
  //     —— 保证探针可重复运行（幂等），下轮从干净态起步。
  const postClean = await resetServerOverlay();
  check('收尾：服务端 overlay 复位（探针幂等可重跑）', postClean, '');

  // 9. 无 console / page 异常
  check('全流程无 console / page 异常', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));

  const pass = results.filter((r) => r.ok).length;
  console.log(`\n自伤遮蔽告警探针汇总：通过 ${pass} / ${results.length}`);
  await browser.close();
  process.exit(results.every((r) => r.ok) ? 0 : 1);
} catch (e) {
  console.error('探针异常：', e?.message || e);
  await shot('err').catch(() => {});
  await browser.close();
  process.exit(2);
}
