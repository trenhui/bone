/**
 * 交付预览（列表显示）· 两层边界显式化探针。
 *
 * 目标（适配重写后的「交付预览 Tab（建模期 · 只读）」设计）：验证两层边界在只读预览里
 * 依然清晰、且跨层 CTA 不是死按钮：
 *   1. 双口径预览切换「全员所见（契约）」/「我的所见（含个人覆盖）」可用；
 *   2. 团队契约层状态徽标「团队契约 · 已发布 / 未配置」渲染；
 *   3. 切到「我的所见」后个人层徽标「个人偏好 · 仅自己可见」出现（仅自己可见语义）；
 *   4. 跨层 CTA「团队列 / 契约 → 界面设计器」渲染且点击跳到 uischema 契约层；
 *   5. 跨层 CTA「去数据页配个人显示」渲染且点击跳到 data 页（个人覆盖编辑器所在）。
 *
 * 复刻 ui-two-scenario-probe.mjs 的登录流程（必须用 localhost，网关 CORS 只放行 localhost 源）。
 * 进用户原始 URL 的实体（766546550728949760，已知 runtime 交付、能渲染交付预览）。
 *
 * 用法：node scripts/e2e/ui-interface-tab-boundary-probe.mjs
 */
import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const SHOT_DIR = '/Users/renhui.trh/wps/bone/logs';
const BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
const ENTITY_ID = process.env.BONE_ENTITY_ID || '766546550728949760';

const results = [];
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
  const p = `${SHOT_DIR}/ui_iface_tab_${n}.png`;
  await page.screenshot({ path: p, fullPage: false });
  return p;
};
const bodyText = () => page.locator('body').innerText();
const clickSeg = async (text) => {
  await page.locator('.ant-segmented-item', { hasText: text }).first().click();
  await page.waitForTimeout(400);
};

try {
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

  // 2. 进实体详情 ?tab=ui（交付预览）
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=ui`, { waitUntil: 'domcontentloaded' });
  // 以「团队列 / 契约 → 界面设计器」CTA 出现为可靠挂载信号
  let mounted = false;
  try {
    await page.locator('button').filter({ hasText: /团队列|界面设计器/ }).first()
      .waitFor({ state: 'visible', timeout: 40000 });
    mounted = true;
  } catch { mounted = false; }
  check('交付预览层挂载（跨层 CTA 可见）', mounted, `url=${page.url()}`);
  await page.waitForTimeout(1500);
  await shot('1_iface_tab');

  let body = await bodyText();

  // 3. 双口径预览切换可用
  const segItems = await page.locator('.ant-segmented-item').allInnerTexts();
  check('双口径预览：含「全员所见（契约）」', segItems.some((t) => t.includes('全员所见')), segItems.join(' | '));
  check('双口径预览：含「我的所见（含个人覆盖）」', segItems.some((t) => t.includes('我的所见')), segItems.join(' | '));

  // 4. 团队契约层状态徽标
  check('团队契约层状态徽标渲染（已发布 / 未配置 任一）', /团队契约/.test(body), body.slice(0, 120));
  const contractState = /团队契约[\s\S]*已发布/.test(body) ? '已发布'
    : (/团队契约[\s\S]*未配置/.test(body) ? '未配置' : '未知');
  console.log(`  · 团队契约状态 = ${contractState}`);

  // 5. 切到「我的所见」→ 个人层徽标出现
  await clickSeg('我的所见');
  let personalShown = false;
  try {
    await page.waitForFunction(() => document.body.innerText.includes('个人偏好'), { timeout: 10000 });
    personalShown = true;
  } catch { personalShown = false; }
  body = await bodyText();
  check('切「我的所见」后个人层徽标「个人偏好 · 仅自己可见」出现', personalShown, body.slice(0, 120));
  await shot('2_mine_scope');

  // 6. 跨层 CTA：去数据页配个人显示（个人覆盖编辑器所在）
  const dataCta = page.locator('button').filter({ hasText: '去数据页' }).first();
  const dataCtaCount = await dataCta.count();
  check('跨层 CTA「去数据页配个人显示」渲染', dataCtaCount === 1, `匹配数=${dataCtaCount}`);
  if (dataCtaCount === 1) {
    await dataCta.click();
    let toData = false;
    try {
      await page.waitForFunction(() => /[?&]tab=data/.test(location.hash + location.search) || location.href.includes('tab=data'), { timeout: 12000 });
      toData = true;
    } catch { toData = false; }
    check('点 CTA 跳转到数据页（个人覆盖编辑器所在，tab=data）', toData, `url=${page.url()}`);
    await page.waitForTimeout(2000);
    await shot('3_data_jump');
  }

  // 7. 跨层 CTA：团队列 / 契约 → 界面设计器（需先回交付预览）
  await page.goto(`${BASE}/metadata#/entities/${ENTITY_ID}?tab=ui`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2500);
  const designerCta = page.locator('button').filter({ hasText: /团队列|界面设计器/ }).first();
  const designerCtaCount = await designerCta.count();
  check('跨层 CTA「团队列 / 契约 → 界面设计器」渲染', designerCtaCount === 1, `匹配数=${designerCtaCount}`);
  if (designerCtaCount === 1) {
    await designerCta.click();
    let toSchema = false;
    try {
      await page.waitForFunction(() => /[?&]tab=uischema/.test(location.hash + location.search) || location.href.includes('uischema'), { timeout: 12000 });
      toSchema = true;
    } catch { toSchema = false; }
    check('点 CTA 跳转到界面设计器契约层（tab=uischema）', toSchema, `url=${page.url()}`);
    await page.waitForTimeout(2500);
    const designerBody = await bodyText();
    check('界面设计器契约层正常渲染', /界面设计器/.test(designerBody), designerBody.slice(0, 120));
    await shot('4_designer_jump');
  }

  // 8. 无 console / page 异常
  check('全流程无 console / page 异常', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));

  const pass = results.filter((r) => r.ok).length;
  console.log(`\n交付预览边界探针汇总：通过 ${pass} / ${results.length}`);
  await browser.close();
  process.exit(results.every((r) => r.ok) ? 0 : 1);
} catch (e) {
  console.error('探针异常：', e?.message || e);
  await shot('err').catch(() => {});
  await browser.close();
  process.exit(2);
}
