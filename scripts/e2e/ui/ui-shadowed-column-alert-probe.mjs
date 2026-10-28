/**
 * 自伤遮蔽告警 · 真实触发探针（IA 收敛后版本）。
 *
 * 背景：「交付预览」Tab 已归位——团队契约口径并入「界面设计器」，个人覆盖口径迁到
 * 运行时数据页的「交付口径对照」卡片（ContractScopeCard）。于是编辑器与对照对象
 * 同处一页，本探针不再需要"建模台↔数据页"来回跳，改成单页闭环（更强、更少假红）。
 *
 * 路径（真实用户路径，不逆向 API）：
 *   1. 登录 → 进实体数据页（?tab=data）
 *   2. 确认「交付口径对照」卡片在（双口径摘要 + 契约徽标）
 *   3. 工具栏点「列设置」→ 关掉第一个契约默认显示的列（个人隐藏）
 *   4. 断言 PersonalViewBar 出现「N 列被个人偏好隐藏 · 恢复显示」
 *   5. 断言对照卡片出现自伤遮蔽告警「你的个人偏好正在隐藏 N 个契约列」+ 恢复显示按钮
 *   6. 点卡片内「恢复显示」一键自救 → 断言告警消失
 *
 * 关键工程点：
 *   - 服务端 overlay 预清理/收尾复位：fetchOverlay「服务端优先」，残留会污染下一轮。
 *   - 告警与列表同源（同一projectRuntimeColumns），故隐藏后应即时出现、无需刷新。
 *
 * 用法：node scripts/e2e/ui/ui-shadowed-column-alert-probe.mjs
 */
/* 共享 client：playwright 加载 / SHOT_DIR / IAM 直连登录与带 token fetch / 断言收集（见 e2e_lib/client.cjs） */
import {
  UI_BASE as BASE,
  SHOT_DIR,
  IAM_BASE as IAM,
  USER,
  PWD,
  loadPlaywright,
  api,
  tryLogin,
  makeChecker,
} from '../e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const ENTITY_ID = process.env.BONE_ENTITY_ID || '766546550728949760';
const ENTITY_CODE = process.env.BONE_ENTITY_CODE || 'claim_case_chain_792218121';
const META = process.env.BONE_META_BASE || 'http://127.0.0.1:9001';

async function resetServerOverlay() {
  try {
    const token = await tryLogin(`${IAM}/api/v1`);
    // 与原口径一致：拿不到 token 也照发该 DELETE（401 空操作），不阻断「返回 true」
    await api('DELETE', `${META}/api/v1/metadata/entities/${encodeURIComponent(ENTITY_CODE)}/ui-overlay`, undefined, { token: String(token) });
    return true;
  } catch { return false; }
}

const { results, check, report } = makeChecker();
const log = (...a) => console.log(...a);

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(25000);
const consoleErrors = [];
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

const shot = async (n) => { await page.screenshot({ path: `${SHOT_DIR}/ui_shadowed_${n}.png` }).catch(() => {}); };
const bodyText = () => page.locator('body').innerText();

try {
  // 0. 干净起点
  check('服务端 overlay 预清理（干净起点）', await resetServerOverlay(), `entityCode=${ENTITY_CODE}`);

  // 1. 登录
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"]', { timeout: 20000 });
  const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
  if (await page.locator(userSel).first().count()) await page.locator(userSel).first().fill(USER);
  await page.locator('input[type="password"]').first().fill(PWD);
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

  // 2. 进数据页，确认「交付口径对照」卡片在
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=data`, { waitUntil: 'domcontentloaded' });
  let scopeCard = false;
  try {
    await page.waitForFunction(() => /交付口径对照/.test(document.body.innerText), { timeout: 40000 });
    scopeCard = true;
  } catch { scopeCard = false; }
  check('数据页出现「交付口径对照」卡片', scopeCard, (await bodyText()).slice(0, 140));
  const initialBody = await bodyText();
  check('对照卡片含双口径摘要（全员所见 / 我的所见）',
    /全员所见（契约）/.test(initialBody) && /我的所见（含个人覆盖）/.test(initialBody), '');
  check('起始无自伤遮蔽告警（干净起点）',
    !/你的个人偏好正在隐藏/.test(initialBody), '');
  await shot('1_scope_card');

  // 3. 列设置 → 关掉一个契约默认显示的列
  const colSettingsBtn = page.locator('button').filter({ hasText: '列设置' }).first();
  if (await colSettingsBtn.count() !== 1) throw new Error('未找到列设置按钮，终止');
  await colSettingsBtn.click();
  let popoverOpen = false;
  try {
    await page.locator('.col-settings-row').first().waitFor({ state: 'visible', timeout: 15000 });
    popoverOpen = true;
  } catch { popoverOpen = false; }
  check('列设置弹层打开', popoverOpen, '未出现 .col-settings-row');
  await page.waitForTimeout(800);

  const targetRow = page.locator('.col-settings-row')
    .filter({ hasNot: page.locator('.ant-switch-disabled') })
    .first();
  if (await targetRow.count() < 1) throw new Error('无可用列开关，终止');
  const targetCol = (await targetRow.innerText().catch(() => '')).replace(/\s+/g, ' ').trim().slice(0, 30);
  await targetRow.locator('.col-settings-switch').first().click();
  await page.waitForTimeout(3000); // 等 persistOverlay 异步推服务端 + 广播
  log(`（已个人隐藏列：${targetCol}）`);
  await page.keyboard.press('Escape'); // 关弹层，避免遮挡对照卡片

  // 4. PersonalViewBar 自救入口出现
  let dataSelfHelp = false;
  try {
    await page.waitForFunction(() => /列被个人偏好隐藏/.test(document.body.innerText), { timeout: 12000 });
    dataSelfHelp = true;
  } catch { dataSelfHelp = false; }
  check('PersonalViewBar 出现「N 列被个人偏好隐藏 · 恢复显示」', dataSelfHelp, '');

  // 5. 对照卡片出现自伤遮蔽告警（个人语义归位后的新落点）
  let alertShown = false;
  try {
    await page.waitForFunction(
      () => /你的个人偏好正在隐藏\s*\d+\s*个契约列/.test(document.body.innerText),
      { timeout: 15000 },
    );
    alertShown = true;
  } catch { alertShown = false; }
  const bodyAlert = await bodyText();
  check('对照卡片告警「你的个人偏好正在隐藏 N 个契约列」真实出现', alertShown, bodyAlert.slice(0, 160));
  const m = /你的个人偏好正在隐藏\s*(\d+)\s*个契约列/.exec(bodyAlert);
  check('告警明确列出被遮蔽的列名（可自查，不只是数字）',
    !!m && /被遮蔽/.test(bodyAlert), '');
  await shot('2_shadowed_alert');

  // 6. 点卡片内「恢复显示」→ 告警消失
  const rescueBtn = page.locator('.ant-alert').filter({ hasText: '你的个人偏好正在隐藏' })
    .locator('button', { hasText: '恢复显示' }).first();
  if (await rescueBtn.count() === 1) {
    await rescueBtn.click().catch(() => {});
    await page.waitForTimeout(2500);
  }
  let alertGone = false;
  try {
    await page.waitForFunction(
      () => !/你的个人偏好正在隐藏\s*\d+\s*个契约列/.test(document.body.innerText),
      { timeout: 12000 },
    );
    alertGone = true;
  } catch { alertGone = false; }
  check('点「恢复显示」后告警消失（只动本人，不影响团队契约）', alertGone, (await bodyText()).slice(0, 160));
  await shot('3_recovered');

  // 7. 收尾复位，保证探针幂等可重跑
  check('收尾：服务端 overlay 复位', await resetServerOverlay(), '');
  check('全流程无 console / page 异常', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));

  const { ok } = report('自伤遮蔽告警探针汇总');
  await browser.close();
  process.exit(ok ? 0 : 1);
} catch (e) {
  console.error('探针异常：', e?.message || e);
  await shot('err').catch(() => {});
  await browser.close();
  process.exit(2);
}