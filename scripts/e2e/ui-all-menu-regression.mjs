/**
 * 全模块 UI 巡检探针（模拟人工点击）：shell :3000 登录后，从 IAM 菜单树取**运行时真源**菜单清单，
 * 逐个导航 + 点击页面主操作按钮，验证每个模块前端页面：
 *   1. 路由落点未被壳层 `<Route path="*">` 静默送回首页（hash 保持）；
 *   2. 微应用真实渲染出内容（qiankun 容器非空，不是白屏）；
 *   3. 页面无错误边界 / 无 500 / 无致命 console 错误；
 *   4. 列表页主操作按钮（新建 / 新增 / 创建）可点开抽屉或弹窗（模拟真人建数据第一步）。
 *
 * 用法：node scripts/e2e/ui-all-menu-regression.mjs
 * 依赖：playwright（经共享 client 的 loadPlaywright 加载，见 e2e_lib/client.cjs）
 */
import {
  UI_BASE as BASE,
  SHOT_DIR,
  USER as E2E_USER,
  PWD as E2E_PWD,
  loadPlaywright,
  makeChecker,
  api,
} from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const { results, check, report } = makeChecker();

// 账号凭据缺省值约定（BONE_E2E_USER / BONE_E2E_PWD 注入、口令必须带缺省值）见 e2e_lib/client.cjs。

// ---------- 菜单真源：IAM menus/tree（不是硬编码，避免与运行时漂移） ----------
async function fetchMenus() {
  const { data: mj } = await api('GET', '/iam/menus/tree');
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

/**
 * 等微应用内容**稳定**（连续两次采样文本一致）再动手点按钮。
 * ★ 没有这一步会踩「点了上一页的按钮」：qiankun 切子应用时 #subapp-viewport 里
 *   可能还残留上一个页面（或壳层工作台）的 DOM —— 只判「长度 > 20」会提前放行，
 *   于是点到了旧页面的「新建」，随后子应用挂载把该节点替换掉 →
 *   innerText() 抛异常、断言文案退化成「?」，看起来像死按钮，实际是探针抢跑。
 */
const waitForStable = async (ms = 8000) => {
  const deadline = Date.now() + ms;
  let prev = '';
  while (Date.now() < deadline) {
    const cur = await page
      .evaluate(() => {
        const e = document.querySelector('#subapp-viewport');
        return e ? (e.innerText || '').trim() : '';
      })
      .catch(() => '');
    if (cur && cur === prev) return true;
    prev = cur;
    await page.waitForTimeout(500);
  }
  return false;
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
      // 等子应用挂载稳定，避免点到上一个页面残留 DOM 里的按钮（见 waitForStable 注释）
      await waitForStable(8000);

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
      // 只在微应用容器内找按钮：壳层顶栏/侧栏不参与，避免点到壳层自己的控件
      const scopeRoot = isShellHome ? page.locator('body') : page.locator('#subapp-viewport');
      const primary = scopeRoot
        .locator('button:visible')
        .filter({ hasText: /^(新建|新增|创建|发起|登记|录入)/ })
        .first();
      if (await primary.count()) {
        if (await primary.isDisabled().catch(() => false)) {
          results.push({ name: `${label} 主操作按钮按上下文禁用（跳过点击）`, ok: true });
        } else {
          // ★ 先取文案再点击：点击后节点可能被子应用重挂载替换，
          //   那时 innerText() 会抛异常，失败文案退化成「?」，没法定位
          const btnText = (await primary.innerText().catch(() => '')).trim().slice(0, 20);
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
            check(
              `${label} 主操作按钮「${btnText || '(空文案)'}」点击有反应`,
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
  const { pass } = report('全模块 UI 巡检汇总');
  const bad = results.filter((r) => !r.ok);
  if (bad.length) {
    console.log('\n失败明细：');
    bad.forEach((b) => console.log('  ✗ ' + b.name + '  ← ' + b.detail));
  }
  await browser.close();
  process.exit(pass === results.length ? 0 : 1);
}
