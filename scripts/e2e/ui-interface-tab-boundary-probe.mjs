/**
 * 两层边界显式化探针（IA 收敛后版本）。
 *
 * 目标不变：验证「团队契约层 vs 个人偏好层」的边界在 UI 上依然清晰、且跨层入口不是死按钮。
 * 只是断言落点随 IA 收敛迁移（原断言对象「交付预览 Tab」已按决策摘除）：
 *   - 团队契约口径 → 界面设计器：契约层状态徽标在数据页对照卡片的 extra 区；
 *   - 个人覆盖口径 → 运行时数据页「交付口径对照」卡片：双口径摘要 + 个人层语义；
 *   - 跨层入口    → 卡片内「去界面设计器配团队列」必须真的跳 uischema（非死按钮）。
 *
 * 为什么保留本探针而不删：它守的是"两层边界不得静默混淆"这条产品不变量，
 * 与承载组件是谁无关——归位后换落点继续守。直接删掉等于丢掉这条回归。
 *
 * 登录走共享 client.cjs 的 USER/PWD（必须用 localhost，网关 CORS 只放行 localhost 源）。
 * 用法：node scripts/e2e/ui-interface-tab-boundary-probe.mjs
 */
import { UI_BASE as BASE, SHOT_DIR, loadPlaywright, makeChecker, USER, PWD } from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const ENTITY_ID = process.env.BONE_ENTITY_ID || '766546550728949760';

const { results, check, report } = makeChecker();

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(25000);
const consoleErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200));
});
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

const shot = async (n) => {
  const p = `${SHOT_DIR}/ui_boundary_${n}.png`;
  await page.screenshot({ path: p, fullPage: false }).catch(() => {});
  return p;
};
const bodyText = () => page.locator('body').innerText();

try {
  // 1. 登录
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"], input[placeholder*="密码"]', { timeout: 20000 });
  const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
  if (await page.locator(userSel).first().count()) {
    await page.locator(userSel).first().fill(USER);
  }
  const pwd = page.locator('input[type="password"]').first();
  await pwd.fill(PWD);
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

  // 2. 进运行时数据页（个人覆盖层归属地）
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=data`, { waitUntil: 'domcontentloaded' });
  let mounted = false;
  try {
    await page.waitForFunction(() => /交付口径对照/.test(document.body.innerText), { timeout: 40000 });
    mounted = true;
  } catch { mounted = false; }
  check('个人覆盖层挂载（「交付口径对照」卡片可见）', mounted, `url=${page.url()}`);
  await page.waitForTimeout(1500);
  await shot('1_scope_card');

  let body = await bodyText();

  // 3. 双口径摘要存在（契约层 vs 个人层同屏对照）
  check('双口径摘要：含「全员所见（契约）」', /全员所见（契约）/.test(body), body.slice(0, 120));
  check('双口径摘要：含「我的所见（含个人覆盖）」', /我的所见（含个人覆盖）/.test(body), body.slice(0, 120));
  // 两侧列数都必须显式给出（否则"对照"无从谈起）
  const cN = Number(/全员所见（契约）\s*(\d+)\s*列/.exec(body)?.[1] ?? 'NaN');
  const mN = Number(/我的所见（含个人覆盖）\s*(\d+)\s*列/.exec(body)?.[1] ?? 'NaN');
  check('双口径各自给出显式列数（可差分）', Number.isFinite(cN) && Number.isFinite(mN), `contract=${cN} mine=${mN}`);

  // 4. 团队契约层状态徽标（已发布 / 未配置 任一）
  check('团队契约层状态徽标渲染（已发布 / 未配置 任一）', /团队契约/.test(body), body.slice(0, 120));
  const contractState = /团队契约[\s\S]*已发布/.test(body) ? '已发布'
    : (/团队契约[\s\S]*未配置/.test(body) ? '未配置' : '未知');
  check('团队契约状态可判读（非"未知"）', contractState !== '未知', `state=${contractState}`);
  console.log(`  · 团队契约状态 = ${contractState}`);

  // 5. 个人层语义标识（仅自己可见）——用 PersonalViewBar 的常驻徽标
  const personalTag = page.locator('.ant-tag', { hasText: '个人偏好' }).first();
  const personalShown = (await personalTag.count()) > 0
    && /仅自己可见/.test(await personalTag.innerText().catch(() => ''));
  check('个人层徽标「个人偏好 · 仅自己可见」出现（仅自己可见语义）', personalShown, body.slice(0, 120));
  await shot('2_two_layers');

  // 6. 跨层入口：卡片内「去界面设计器配团队列」必须真的跳转（非死按钮）
  const designerCta = page.locator('button', { hasText: '去界面设计器配团队列' }).first();
  const ctaCount = await designerCta.count();
  check('跨层入口「去界面设计器配团队列」渲染', ctaCount === 1, `匹配数=${ctaCount}`);
  if (ctaCount === 1) {
    await designerCta.click();
    let toSchema = false;
    try {
      await page.waitForFunction(
        () => /[?&]tab=uischema/.test(location.hash + location.search) || location.href.includes('uischema'),
        { timeout: 12000 },
      );
      toSchema = true;
    } catch { toSchema = false; }
    check('点跨层入口跳转到界面设计器契约层（tab=uischema）', toSchema, `url=${page.url()}`);
    await page.waitForTimeout(2500);
    const designerBody = await bodyText();
    check('界面设计器契约层正常渲染', /界面设计器/.test(designerBody), designerBody.slice(0, 120));
    // 契约口径核对摘要在设计器内的「列表」面板；注意「列表」有两个同名入口
    // （外层视图切换器是 .ant-segmented-item，内层面板是 .ant-tabs-tab），
    // 必须用 role=tab 精确定位，否则点错到外层、面板根本没切过去。
    await page.locator('[role="tab"]', { hasText: '列表' }).last().click().catch(() => {});
    await page.waitForTimeout(1500);
    const listPanelBody = await bodyText();
    check('设计器承接契约口径核对「运行时最终显示 N 列」',
      /运行时最终显示\s*\d+\s*列/.test(listPanelBody), listPanelBody.slice(0, 120));
    await shot('3_designer_jump');
  }

  // 7. 无 console / page 异常
  check('全流程无 console / page 异常', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));

  const { ok } = report('两层边界探针汇总');
  await browser.close();
  process.exit(ok ? 0 : 1);
} catch (e) {
  console.error('探针异常：', e?.message || e);
  await shot('err').catch(() => {});
  await browser.close();
  process.exit(2);
}