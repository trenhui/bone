/**
 * 智能理赔「单导航」回归探针（claim-single-nav-probe.mjs）
 *
 * 守的是 2026-10-28 用户实测反馈的那一类缺陷：**同一个域出现两层菜单，且两层子项还不一样**
 * （shell 侧边栏 = IAM 菜单 8 条，微应用自绘 Sider = 9 条，名称三处漂移、`后督抽检` 只在子应用侧存在）。
 *
 * 判据（每条都可机器验证，且都是「删掉修复就转红」的形态）：
 *   A. 导航唯一    —— 页面里 `.shell-sider` 恰好 1 个；qiankun 容器内 `.ant-layout-sider` 为 0
 *                     （子应用重新自绘侧边栏 = 双层菜单复发）
 *   B. 菜单↔路由   —— IAM 下发菜单（路径集合/名称/顺序）与 `bone-claim-app/src/routes.tsx`
 *                     双向全等；多一条 = 幽灵菜单（点了空白），少一条 = 功能不可达
 *   C. 真实点击    —— 逐个点侧边栏菜单（不是 goto），断言 hash 落地、顶栏标题、页面标题、内容非空
 *   D. 无致命错误  —— 导航期间无 5xx、无 console error（第三方噪音除外）
 *
 * 为什么 C 用「点击」而不是 goto：goto 绕过了 shell 的菜单点击链路（handleMenuClick → navigate）。
 * 用户报的缺陷正是「菜单里的子项和页面不是一回事」，只有点菜单才能复现。
 *
 * 用法：
 *   NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-3/bin/node
 *   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
 *   $NODE scripts/e2e/ui/claim-single-nav-probe.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import {
  UI_BASE as BASE,
  SHOT_DIR,
  loadPlaywright,
  makeChecker,
  api,
} from '../e2e_lib/client.cjs';
import { uiLogin, shot } from '../e2e_lib/ui.cjs';

const { chromium } = loadPlaywright();
const { check, report } = makeChecker();

const CLAIM_MENU_PREFIX = '/claim#';
const ROUTES_TSX = path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  '../../../bone-frontend/apps/bone-claim-app/src/routes.tsx',
);

/** 前端唯一真源：从 routes.tsx 解析 [{path, name}]（与页面组件一一对应）。 */
function parseRoutes() {
  const src = fs.readFileSync(ROUTES_TSX, 'utf8');
  const out = [];
  const re = /\{\s*path:\s*'([^']+)',\s*name:\s*'([^']+)'/g;
  let m;
  while ((m = re.exec(src))) out.push({ path: m[1], name: m[2] });
  return out;
}

/** 后端唯一真源：IAM 当前用户可见菜单里，挂在「智能理赔」下的可导航叶子。 */
async function fetchClaimMenus() {
  const { data } = await api('GET', '/iam/menus/current');
  const root = data?.data ?? data;
  const leaves = [];
  const walk = (n) => {
    if (Array.isArray(n)) return n.forEach(walk);
    if (!n || typeof n !== 'object') return;
    const p = n.path || '';
    if (p.startsWith(CLAIM_MENU_PREFIX)) leaves.push({ path: p, name: n.name });
    (n.children || []).forEach(walk);
  };
  walk(root);
  return leaves;
}

const isNoise = (e) =>
  /favicon|Failed to load resource:|\[antd[^\]]*\]|deprecated|ResizeObserver loop|Warning: |single-spa minified message #31/i.test(
    e,
  );

const routes = parseRoutes();
const menus = await fetchClaimMenus();

console.log(`前端路由（routes.tsx）：${routes.length} 条`);
console.log(`后端菜单（iam/current）：${menus.length} 条\n`);

// ---------- B. 菜单 ↔ 路由 双向一致性（无需浏览器，先判，失败即中止） ----------
check('routes.tsx 解析到 9 条理赔路由', routes.length === 9, `实际 ${routes.length}`);
check('IAM 下发 9 条理赔菜单', menus.length === 9, `实际 ${menus.length}`);

const routeByPath = new Map(routes.map((r) => [r.path, r.name]));
const menuPaths = menus.map((m) => m.path.replace(CLAIM_MENU_PREFIX, ''));
const ghosts = menus.filter((m) => !routeByPath.has(m.path.replace(CLAIM_MENU_PREFIX, '')));
const unreachable = routes.filter((r) => !menuPaths.includes(r.path));

check(
  '无幽灵菜单（菜单项都有落地页面）',
  ghosts.length === 0,
  ghosts.map((g) => `${g.name}(${g.path})`).join(', '),
);
check(
  '无功能不可达（每条路由都有菜单入口）',
  unreachable.length === 0,
  unreachable.map((r) => `${r.name}(${r.path})`).join(', '),
);

const nameDrift = menus
  .map((m) => ({ menu: m, route: m.path.replace(CLAIM_MENU_PREFIX, '') }))
  .filter((x) => routeByPath.has(x.route) && routeByPath.get(x.route) !== x.menu.name)
  .map((x) => `菜单「${x.menu.name}」≠ 页面「${routeByPath.get(x.route)}」`);
check('菜单名与页面名逐字一致', nameDrift.length === 0, nameDrift.join('; '));

const menuOrder = menuPaths.filter((p) => routeByPath.has(p));
check(
  '菜单顺序 = routes.tsx 声明顺序（order_no 真实生效）',
  JSON.stringify(menuOrder) === JSON.stringify(routes.map((r) => r.path)),
  `菜单序 ${menuOrder.join(' → ')}`,
);

if (ghosts.length || unreachable.length) {
  console.log('\n菜单/路由已漂移，跳过浏览器阶段（先修数据再谈 UI）。');
  const r = report('智能理赔单导航探针');
  process.exit(r.ok ? 0 : 1);
}

// ---------- 浏览器阶段 ----------
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(20000);

let consoleErrors = [];
let httpErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error' && !isNoise(m.text())) consoleErrors.push(m.text().slice(0, 200));
});
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));
page.on('response', (r) => {
  if (r.status() >= 500) httpErrors.push(`${r.status()} ${r.url().slice(0, 120)}`);
});

const viewportText = () =>
  page
    .evaluate(() => {
      const e = document.querySelector('#subapp-viewport');
      return e ? (e.innerText || '').trim() : '';
    })
    .catch(() => '');

/** 等微应用内容稳定（连续两次采样一致），避免读到上一个页面残留 DOM。 */
async function waitStable(ms = 12000) {
  const deadline = Date.now() + ms;
  let prev = '';
  while (Date.now() < deadline) {
    const cur = await viewportText();
    if (cur.length > 10 && cur === prev) return cur;
    prev = cur;
    await page.waitForTimeout(400);
  }
  return prev;
}

let grouped = false;
const expandGroup = async () => {
  if (grouped) return;
  const title = page
    .locator('.shell-sider .ant-menu-submenu-title')
    .filter({ hasText: '智能理赔' })
    .first();
  await title.waitFor({ state: 'visible' });
  const opened = await title.evaluate((el) =>
    (el.parentElement?.className || '').includes('ant-menu-submenu-open'),
  );
  if (!opened) await title.click();
  grouped = true;
};

try {
  check('UI 登录成功', await uiLogin(page), page.url());
  // 进入理赔域：点分组（accent 展开）→ 点第一个子项，模拟真实用户
  await page.waitForSelector('.shell-sider', { timeout: 20000 });
  await expandGroup();
  await page
    .locator('.shell-sider .ant-menu-item')
    .filter({ hasText: routes[0].name })
    .first()
    .click();
  await waitStable();

  // ---------- A. 单导航 ----------
  const siderCount = await page.locator('.shell-sider').count();
  const innerSider = await page.locator('#subapp-viewport .ant-layout-sider').count();
  const innerMenuRoot = await page.locator('#subapp-viewport .ant-menu-root').count();
  check('shell 侧边栏恰好 1 个', siderCount === 1, `实际 ${siderCount}`);
  check('微应用内无自绘侧边栏（否则双层菜单复发）', innerSider === 0, `实际 ${innerSider}`);
  check('微应用内无自绘菜单（.ant-menu-root）', innerMenuRoot === 0, `实际 ${innerMenuRoot}`);
  check(
    '页面不再出现「独立调试」导航条',
    !(await viewportText()).includes('独立调试'),
  );

  // ---------- C. 逐个点击菜单 ----------
  for (const r of routes) {
    consoleErrors = [];
    httpErrors = [];
    const item = page.locator('.shell-sider .ant-menu-item').filter({ hasText: r.name }).first();
    const visible = await item.isVisible().catch(() => false);
    check(`[${r.name}] 菜单可见`, visible);
    if (!visible) continue;

    await item.click();
    const body = await waitStable();

    const hash = await page.evaluate(() => location.hash);
    check(`[${r.name}] hash 落地 ${r.path}`, hash === `#${r.path}`, `实际 ${hash}`);

    const headerTitle = (await page.locator('.shell-header .page-title').first().innerText().catch(() => '')).trim();
    check(`[${r.name}] 顶栏标题 = 菜单名`, headerTitle === r.name, `实际「${headerTitle}」`);

    const pageTitle = (
      await page
        .locator('#subapp-viewport h4.ant-typography')
        .first()
        .innerText()
        .catch(() => '')
    ).trim();
    check(`[${r.name}] 页面标题 = 菜单名`, pageTitle === r.name, `实际「${pageTitle}」`);

    // 内容判据用「结构」而不是「文本长度」：理赔进度页是查询驱动，初始态只有标题 + 查询框
    // （innerText 仅个位数），按长度判会把正常空态误判成白屏。
    const structured = await page
      .locator(
        '#subapp-viewport .ant-card, #subapp-viewport .ant-table, #subapp-viewport .ant-form, #subapp-viewport .ant-empty, #subapp-viewport .ant-steps, #subapp-viewport .ant-descriptions',
      )
      .count();
    check(`[${r.name}] 页面渲染出内容`, structured > 0, `容器文本 ${body.length} 字 / 结构块 ${structured} 个`);
    check(`[${r.name}] 无 5xx`, httpErrors.length === 0, httpErrors.join(' | '));
    check(`[${r.name}] 无 console error`, consoleErrors.length === 0, consoleErrors.join(' | '));
  }

  await shot(page, 'claim_single_nav');
  // 页面标题与菜单名一一对应的实拍证据（人工复核用）
  await page
    .locator('.shell-sider .ant-menu-item')
    .filter({ hasText: routes[6].name })
    .first()
    .click();
  await waitStable();
  await shot(page, 'claim_single_nav_inspection');
  console.log(`\n截图：${SHOT_DIR}/claim_single_nav*.png`);
} catch (e) {
  check('探针执行未抛异常', false, String(e).slice(0, 300));
} finally {
  await browser.close();
}

const r = report('智能理赔单导航探针');
process.exit(r.ok ? 0 : 1);
