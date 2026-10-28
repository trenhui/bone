/**
 * IA 收敛探针：「交付预览」归位后的新落点。
 *
 * 本次 IA 变更（EntityDetail）：
 *   - 顶层 Tab 摘除「交付预览」，「界面设计器」承接团队契约的交付预览职责；
 *   - 个人覆盖口径（「我的所见 vs 契约」+ 自伤遮蔽告警）迁到运行时数据页 ContractScopeCard；
 *   - ?tab=ui 深链（PRD §0.6.6 模板起盘黄金路径）rewrite 到 ?tab=uischema，不落进Route "*"。
 *
 * 断言要点：
 *   1. ?tab=ui 不再挂交付预览，且 URL 被 rewrite 成 uischema（深链不落首页、不静默失效）；
 *   2. Tab 栏不再出现「交付预览」；
 *   3. 设计器里出现「运行时最终显示 N 列」契约核对摘要；
 *   4. 数据页出现「交付口径对照」卡片，含双口径摘要；
 *   5. 无 console / page 异常。
 *
 * 用法：node scripts/e2e/ui-delivery-preview-relocation-probe.mjs
 */
import { UI_BASE as BASE, SHOT_DIR, loadPlaywright, makeChecker } from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const ENTITY_ID = process.env.BONE_ENTITY_ID || '766546550728949760';

const { results, check, report } = makeChecker();

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(25000);
const consoleErrors = [];
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

const shot = async (n) => { await page.screenshot({ path: `${SHOT_DIR}/ui_relocate_${n}.png` }).catch(() => {}); };
const bodyText = () => page.locator('body').innerText();

try {
  // 登录
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
  await page.waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 });
  check('登录成功', true);

  // 1. ?tab=ui 深链 → 应 rewrite 到 uischema，不静默落首页、不挂交付预览
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=ui`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(4000);
  const url1 = page.url();
  check('?tab=ui 深链被 rewrite 到 ?tab=uischema（PRD 黄金路径不失效）',
    /tab=uischema/.test(url1), `url=${url1}`);

  // 2. Tab 栏不再有「交付预览」
  const body1 = await bodyText();
  const tabBarText = await page.locator('.ant-tabs-tab, [role="tab"]').allInnerTexts().catch(() => []);
  const tabBarAll = tabBarText.join(' | ');
  check('Tab 栏不再出现「交付预览」',
    !/交付预览/.test(tabBarAll), `tab栏=${tabBarAll.slice(0, 160)}`);
  check('Tab 栏仍有「界面设计器」与「数据」',
    /界面设计器/.test(tabBarAll) && /数据/.test(tabBarAll), `tab栏=${tabBarAll.slice(0, 160)}`);
  void body1;
  await shot('1_tabbar');

  // 3. 设计器承接契约口径交付预览：切到设计器内的「列表」面板，再断言核对摘要
  await page.locator('.ant-tabs-tab', { hasText: '界面设计器' }).first().click().catch(() => {});
  await page.waitForTimeout(2500);
  // 设计器内层面板是真 Tab（role=tab，标签含「列表」），而 Segmented 里也有同名「列表」项
  // （那是视图切换器：录入表单/详情视图/表单/详情/列表）——必须用 Tab 精确定位，
  // 否则 .first() 会点到外层视图切换器，左侧配置面板根本没切过去。
  await page.locator('[role="tab"]', { hasText: '列表' }).last().click().catch(() => {});
  await page.waitForTimeout(1500);
  let contractSummary = false;
  try {
    await page.waitForFunction(
      () => /运行时最终显示\s*\d+\s*列/.test(document.body.innerText),
      { timeout: 25000 },
    );
    contractSummary = true;
  } catch { contractSummary = false; }
  const designerBody = await bodyText();
  check('设计器出现契约口径核对「运行时最终显示 N 列（发布后全员一致）」',
    contractSummary, designerBody.slice(0, 160));
  await shot('2_designer_contract_scope');

  // 4. 数据页出现「交付口径对照」卡片（个人覆盖口径归位处）
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=data`, { waitUntil: 'domcontentloaded' });
  let scopeCard = false;
  try {
    await page.waitForFunction(
      () => /交付口径对照/.test(document.body.innerText),
      { timeout: 25000 },
    );
    scopeCard = true;
  } catch { scopeCard = false; }
  const dataBody = await bodyText();
  check('数据页出现「交付口径对照」卡片（个人覆盖口径归位）', scopeCard, dataBody.slice(0, 160));
  const hasBoth = /全员所见（契约）/.test(dataBody) && /我的所见（含个人覆盖）/.test(dataBody);
  check('对照卡片含双口径摘要（全员所见 / 我的所见）', hasBoth, dataBody.slice(0, 200));
  check('对照卡片提供去设计器配团队列的入口',
    /去界面设计器配团队列/.test(dataBody), '');
  await shot('3_data_scope_card');

  // 5. 无 console / page 异常
  check('全流程无 console / page 异常', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));

  const { ok } = report('交付预览归位探针汇总');
  await browser.close();
  process.exit(ok ? 0 : 1);
} catch (e) {
  console.error('探针异常：', e?.message || e);
  await shot('err').catch(() => {});
  await browser.close();
  process.exit(2);
}