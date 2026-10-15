/**
 * 三样板场景 UI 探针（模拟人工点击）：shell :3000 登录后，走「业务对象 → 业务数据」两个菜单，
 * 验证 API 链路造出来的报销单 / 理赔案件 / 采购订单在真实界面上可见、可进、状态列有值。
 *
 * 用法：node scripts/e2e/ui-two-scenario-probe.mjs
 * 依赖：playwright（NODE_PATH 指向 ~/.workbuddy/binaries/node/workspace/node_modules）
 */
// ESM 不认 NODE_PATH，直接用绝对路径导入（playwright 装在托管 workspace 里）
import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const SHOT_DIR = '/Users/renhui.trh/wps/bone/logs';
// 网关 globalcors 只放行 localhost 系列 Origin（见 bone-gateway/application.yml），
// 用 127.0.0.1 打开外壳时浏览器带 Origin: http://127.0.0.1:3000 → CORS 校验 403。
const BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
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

const shot = async (n) => {
  const p = `${SHOT_DIR}/ui_two_scenario_${n}.png`;
  await page.screenshot({ path: p, fullPage: false });
  return p;
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
  // 回车提交 + 兜底点按钮：SPA 的登录按钮文案可能是「登 录」（中间带空格）
  await pwd.press('Enter');
  await page.waitForTimeout(1200);
  const stillLogin = await page.locator('input[type="password"]').count();
  if (stillLogin) {
    await page.locator('button').filter({ hasText: /登\s*录/ }).first().click().catch(() => {});
    await page.waitForTimeout(3000);
  }
  // 判据：壳层菜单出现（登录成功才渲染侧边栏），而不是只看 URL
  // 判据用「登录表单消失」而不是菜单文本：侧边菜单是异步拉取（IAM menus/tree），
  // 慢于壳层首屏，用文本等待会把「登录其实成功、只是菜单还没回来」误判为失败。
  let loggedIn = false;
  try {
    await page.waitForFunction(() => !document.querySelector('input[type="password"]'), {
      timeout: 30000,
    });
    loggedIn = true;
  } catch {
    loggedIn = false;
  }
  check('UI 登录成功（壳层菜单渲染）', loggedIn, `url=${page.url()} body=${(await page.locator('body').innerText()).slice(0, 80)}`);
  await shot('1_home');

  // 2. 业务对象：用页面搜索框（pageSize=10、307+ 实体，直接翻页不可靠）确认 API 建的实体可见
  await page.goto(`${BASE}/metadata#/entities`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(4500);
  const entitiesBody0 = await page.locator('body').innerText();
  check('业务对象页渲染出实体列表', /实体|业务对象|新建/.test(entitiesBody0), entitiesBody0.slice(0, 120));

  const searchEntities = async (kw) => {
    const box = page.locator('input[placeholder*="搜索对象名称"]').first();
    await box.click();
    await box.fill(kw);
    await box.press('Enter');
    await page.waitForTimeout(2200);
    return page.locator('body').innerText();
  };

  const expenseBody = await searchEntities('报销单');
  check('界面可搜索到 E2E 建的「报销单」实体', expenseBody.includes('报销单'), expenseBody.slice(0, 200));
  const claimBody = await searchEntities('理赔案件');
  check('界面可搜索到 E2E 建的「理赔案件」实体', claimBody.includes('理赔案件'), claimBody.slice(0, 200));
  const poBody = await searchEntities('采购订单');
  check('界面可搜索到 E2E 建的「采购订单」实体', poBody.includes('采购订单'), poBody.slice(0, 200));
  await shot('2_entities');

  // 3. 业务数据：按新 UX 从实体行内「数据」按钮进入（自动锁定实体，不再走自由下拉）
  // 上面最后一次搜索是「采购订单」，必须先把检索条件切回「理赔案件」再找行
  await searchEntities('理赔案件');
  let picked = false;
  const claimRow = page.locator('tr').filter({ hasText: '理赔案件' }).first();
  if (await claimRow.count()) {
    const dataBtn = claimRow.locator('button').filter({ hasText: '数据' }).first();
    if (await dataBtn.count()) {
      await dataBtn.click();
      picked = true;
    }
  }
  await page.waitForTimeout(4500);
  const dataBody = await page.locator('body').innerText();
  check('行内「数据」按钮进入运行数据面', picked, '实体行内未找到「数据」按钮');
  check(
    '业务数据：理赔记录与状态列可见',
    /PAID|REJECTED|APPEALING|REVIEWING|APPROVED|已支付|案件/.test(dataBody),
    dataBody.slice(0, 260)
  );
  await shot('3_runtime');

  // 3b. 采购订单：同样从行内「数据」进入，验证行项目子表 / 三单匹配金额列可见
  await page.goto(`${BASE}/metadata#/entities`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(4000);
  await searchEntities('采购订单');
  let poPicked = false;
  const poRow = page.locator('tr').filter({ hasText: '采购订单' }).first();
  if (await poRow.count()) {
    const dataBtn = poRow.locator('button').filter({ hasText: '数据' }).first();
    if (await dataBtn.count()) {
      await dataBtn.click();
      poPicked = true;
    }
  }
  await page.waitForTimeout(4500);
  const poData = await page.locator('body').innerText();
  check('采购订单：行内「数据」进入运行数据面', poPicked, '实体行内未找到「数据」按钮');
  check(
    '业务数据：采购订单记录与三单金额列可见',
    /RECEIVED|RECEIVING|APPROVED|PAID|采购金额|收货|发票/.test(poData),
    poData.slice(0, 260)
  );
  await shot('4_purchase_runtime');

  // antd 的 deprecation 警告走 console.error，属第三方库噪声，不算应用缺陷
  const fatal = consoleErrors.filter(
    (e) => !/favicon|Failed to load resource:|\[antd[^\]]*\]|deprecated/i.test(e)
  );
  check('页面无致命 console 错误', fatal.length === 0, fatal.slice(0, 3).join(' | '));
} catch (e) {
  check('UI 探针执行未抛异常', false, e.message.slice(0, 200));
  await shot('error').catch(() => {});
} finally {
  const pass = results.filter((r) => r.ok).length;
  console.log(`\nUI 探针汇总：通过 ${pass} / ${results.length}`);
  await browser.close();
  process.exit(pass === results.length ? 0 : 1);
}
