/**
 * 全模块 UI 巡检探针（模拟人工点击）：shell :3000 登录后，从 IAM 菜单树取**运行时真源**菜单清单，
 * 逐个导航 + 点击页面主操作按钮，验证每个模块前端页面：
 *   1. 路由落点未被壳层 `<Route path="*">` 静默送回首页（hash 保持）；
 *   2. 微应用真实渲染出内容（qiankun 容器非空，不是白屏）；
 *   3. 页面无错误边界 / 无 500 / 无致命 console 错误；
 *   4. 列表页主操作按钮（新建 / 新增 / 创建）可点开抽屉或弹窗（模拟真人建数据第一步）。
 *
 * 用法：node scripts/e2e/ui-all-menu-regression.mjs
 * 依赖：playwright（绝对路径导入托管 workspace 里的包）
 */
import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
const API = process.env.BONE_E2E_API || 'http://127.0.0.1:8888/api/v1';
const SHOT_DIR = '/Users/renhui.trh/wps/bone/logs';

const results = [];
const check = (name, cond, detail = '') => {
  results.push({ name, ok: !!cond, detail });
  console.log(`${cond ? '✓' : '✗'} ${name}${cond ? '' : '  ← ' + detail}`);
};

// 联调用种子账号凭据通过环境变量注入：BONE_E2E_USER / BONE_E2E_PWD。
// 本地 dev 缺省用户名 admin；口令一律走环境变量（源码不硬编码测试凭据，过 gitleaks 门禁）。
const E2E_USER = process.env.BONE_E2E_USER || 'admin';
const E2E_PWD = process.env.BONE_E2E_PWD;

// ---------- 菜单真源：IAM menus/tree（不是硬编码，避免与运行时漂移） ----------
async function fetchMenus() {
  const lr = await fetch(`${API}/iam/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: E2E_USER, password: E2E_PWD }),
  });
  const lj = await lr.json();
  const token = lj?.data?.token;
  if (!token) throw new Error('登录取 token 失败: ' + JSON.stringify(lj).slice(0, 200));
  const mr = await fetch(`${API}/iam/menus/tree`, { headers: { Authorization: 'Bearer ' + token, 'X-Tenant-Id': '0' } });
  const mj = await mr.json();
  const root = mj?.data ?? mj;
  const out = [];
  const walk = (n) => {
    if (Array.isArray(n)) return n.forEach(walk);
    if (!n || typeof n !== 'object') return;
    if (n.path) out.push({ path: n.path, name: n.name });
    (n.children || []).forEach(walk);
  };
  walk(root);
  return out;
}

const menus = await fetchMenus();
console.log(`菜单真源：共 ${menus.length} 个可导航菜单\n`);

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(20000);
let consoleErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200));
});
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

const shot = async (n) => {
  await page.screenshot({ path: `${SHOT_DIR}/ui_menu_${n}.png`, fullPage: false }).catch(() => {});
};

// antd 的 deprecation 警告走 console.error，属第三方库噪声；跨域/资源加载噪声一并剔除
const isNoise = (e) =>
  /favicon|Failed to load resource:|\[antd[^\]]*\]|deprecated|ResizeObserver loop|Warning: /i.test(e);

/**
 * single-spa #31 = 「lifecycle 未在 N ms 内 settle」的默认 mount 超时（默认 3000ms，dieOnTimeout=false）。
 * vite dev 下**首次**进入某微应用要现场转换/下发上百个 ESM 模块，mount 常超 3s —— 应用最终仍会挂上，
 * 属开发环境冷启动噪声，非产品缺陷（生产为预构建产物，不触发）。
 * 不静默丢弃：单独计数并在汇总里显式报出，仍与「致命错误」分列，避免掩盖真实异常。
 */
const isDevColdStartTimeout = (e) => /single-spa minified message #31/.test(e);
let devColdStartHits = [];

/**
 * 轮询等待微应用真实渲染出内容（qiankun mount 在 dev 冷启动可能 >3s，不能固定 sleep）。
 * 只看 qiankun 容器 #subapp-viewport，不量 body —— body 里有一大段壳层侧边菜单文案，
 * 会把「微应用根本没挂上」和「页面内容本来就短（如扩展点目录只有标题 + No data）」混为一谈。
 */
const viewportLen = () =>
  page.evaluate(() => {
    const e = document.querySelector('#subapp-viewport');
    if (!e) return -1;
    return (e.innerText || '').trim().length;
  });

const waitForContent = async (ms = 15000) => {
  const deadline = Date.now() + ms;
  let len = -1;
  while (Date.now() < deadline) {
    len = await viewportLen();
    if (len > 20) return len;
    await page.waitForTimeout(600);
  }
  return len;
};

try {
  // ---------- 登录 ----------
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"]', { timeout: 20000 });
  const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
  if (await page.locator(userSel).first().count()) await page.locator(userSel).first().fill(E2E_USER);
  const pwd = page.locator('input[type="password"]').first();
  await pwd.fill(E2E_PWD);
  await pwd.press('Enter');
  await page.waitForTimeout(1500);
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('button').filter({ hasText: /登\s*录/ }).first().click().catch(() => {});
    await page.waitForTimeout(3000);
  }
  let loggedIn = false;
  try {
    await page.waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 });
    loggedIn = true;
  } catch {}
  check('UI 登录成功（登录表单消失）', loggedIn, `url=${page.url()}`);
  if (!loggedIn) throw new Error('登录失败，后续巡检无意义');

  let broken = 0;
  let clicked = 0;

  for (const m of menus) {
    const label = `${m.name}（${m.path}）`;
    consoleErrors = [];
    const expectHash = m.path.includes('#') ? '#' + m.path.split('#')[1] : '';
    try {
      await page.goto(`${BASE}${m.path}`, { waitUntil: 'domcontentloaded' });
      const bodyLen = await waitForContent(12000);

      // 1) 路由落点保持（没被 <Route path="*"> 静默送回首页）
      const curHash = await page.evaluate(() => location.hash);
      const landed = expectHash ? curHash === expectHash : true;
      if (!landed) check(`${label} 路由落点保持未被送回首页`, false, `expected=${expectHash} actual=${curHash}`);
      else results.push({ name: `${label} 路由落点保持`, ok: true });

      // 2) 微应用真实渲染（qiankun 容器非空）
      const body = await page.locator('body').innerText();
      // `/` 是壳层自带仪表盘，不走 qiankun，本来就没有 #subapp-viewport，改量壳层正文
      const isShellHome = !m.path.includes('#');
      const rendered = isShellHome ? body.trim().length > 120 : bodyLen > 20;
      if (!rendered) check(`${label} 微应用渲染出内容`, false, `#subapp-viewport len=${bodyLen} body=${body.slice(0, 120)}`);
      else results.push({ name: `${label} 微应用渲染出内容`, ok: true });

      // 3) 无错误边界 / 500
      const errUi = /页面出错了|Something went wrong|应用加载失败|加载失败|500\s*Internal/i.test(body);
      if (errUi) check(`${label} 无错误边界 / 加载失败提示`, false, body.slice(0, 200));
      else results.push({ name: `${label} 无错误边界`, ok: true });

      // 4) 无致命 console 错误（dev 冷启动 mount 超时单列，不计入致命）
      devColdStartHits.push(...consoleErrors.filter(isDevColdStartTimeout).map(() => m.path));
      const fatal = consoleErrors.filter((e) => !isNoise(e) && !isDevColdStartTimeout(e));
      if (fatal.length) check(`${label} 无致命 console 错误`, false, fatal.slice(0, 2).join(' | '));
      else results.push({ name: `${label} 无致命 console 错误`, ok: true });

      // 5) 点击主操作按钮（新建 / 新增 / 创建 / 发起 …），模拟真人操作第一步
      //    注意两类「不是缺陷」的情形，早期版本把它们误判成失败：
      //      a) 按钮按上下文禁用（如业务数据页未选实体前「新建记录」disabled）——跳过，不算失败；
      //      b) 按钮是行内表单的 submit（如支付管理「发起支付」需先填订单号）——
      //         点击后应出现校验提示，这同样证明按钮已接线，只是入参未齐。
      // 只点**可见**按钮：页面里常存在隐藏的模板/抽屉按钮，点到它会静默超时，
      // 被误判成「死按钮」（日志管理就踩过这个坑）。
      const primary = page
        .locator('button:visible')
        .filter({ hasText: /^(新建|新增|创建|发起|登记|录入)/ })
        .first();
      if (await primary.count()) {
        if (await primary.isDisabled().catch(() => false)) {
          results.push({ name: `${label} 主操作按钮按上下文禁用（跳过点击）`, ok: true });
        } else {
          const urlBefore = page.url();
          await primary.click({ timeout: 6000 }).catch(() => {});
          await page.waitForTimeout(2200);
          const modalOpen =
            (await page.locator('.ant-modal:not(.ant-modal-hidden), .ant-drawer:not(.ant-drawer-hidden)').count()) > 0;
          const urlChanged = page.url() !== urlBefore;
          const toast = await page.locator('.ant-message').count().catch(() => 0);
          const validation = await page
            .locator('.ant-form-item-explain-error, .ant-alert')
            .count()
            .catch(() => 0);
          if (modalOpen || urlChanged) {
            clicked++;
            results.push({ name: `${label} 主操作按钮打开抽屉/弹窗/跳转`, ok: true });
            await page.keyboard.press('Escape').catch(() => {});
            await page.waitForTimeout(500);
            const cancel = page
              .locator('.ant-modal:not(.ant-modal-hidden) button, .ant-drawer:not(.ant-drawer-hidden) button')
              .filter({ hasText: /^(取消|取\s*消|关\s*闭)/ })
              .first();
            if (await cancel.count()) await cancel.click({ timeout: 4000 }).catch(() => {});
            await page.waitForTimeout(500);
          } else if (validation > 0 || toast > 0) {
            clicked++;
            results.push({ name: `${label} 主操作按钮有反馈（表单校验/toast，需补全入参）`, ok: true });
          } else {
            const btnText = (await primary.innerText().catch(() => '?')).trim().slice(0, 20);
            check(
              `${label} 主操作按钮「${btnText}」点击有反应`,
              false,
              '点击后无弹窗、无跳转、无校验/提示 → 疑似死按钮'
            );
          }
        }
      }

      if (!landed || !rendered || errUi || fatal.length) {
        broken++;
        await shot(String(m.path).replace(/[^a-z0-9]/gi, '_'));
      }
    } catch (e) {
      broken++;
      check(`${label} 巡检未抛异常`, false, e.message.slice(0, 160));
    }
  }

  check(`全菜单巡检：${menus.length} 个菜单全部可渲染且无致命错误`, broken === 0, `异常 ${broken} 个`);
  check(`主操作按钮点击闭环：共触发 ${clicked} 个页面`, clicked > 0, 'clicked=' + clicked);
  if (devColdStartHits.length) {
    console.log(
      `\n[已知环境噪声] vite dev 冷启动触发 single-spa mount 超时 ${devColdStartHits.length} 次（应用随后仍挂载成功，` +
        `非致命）：${[...new Set(devColdStartHits)].join(', ')}`
    );
  }
} catch (e) {
  check('全菜单巡检执行未抛异常', false, e.message.slice(0, 240));
} finally {
  const pass = results.filter((r) => r.ok).length;
  console.log(`\n全模块 UI 巡检汇总：通过 ${pass} / ${results.length}`);
  const bad = results.filter((r) => !r.ok);
  if (bad.length) {
    console.log('\n失败明细：');
    bad.forEach((b) => console.log('  ✗ ' + b.name + '  ← ' + b.detail));
  }
  await browser.close();
  process.exit(pass === results.length ? 0 : 1);
}
