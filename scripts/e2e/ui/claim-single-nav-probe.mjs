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
 *                     的非 hidden 路由双向全等；多一条 = 幽灵菜单（点了空白），
 *                     少一条 = 功能不可达。hidden 路由（保单下钻页）豁免——它们
 *                     从对象列表深链进入，本就不该有菜单。
 *   C. 真实点击    —— 逐个点侧边栏菜单（不是 goto），断言 hash 落地、顶栏标题、页面标题、内容非空
 *   D. 无致命错误  —— 导航期间无 5xx、无 console error（第三方噪音除外）
 *   E. 常驻分组    —— 四个分组标题常驻可见、15 条菜单项默认全部可见可点、
 *                     DOM 序仍等于 routes.tsx 声明顺序、分组内无折叠 SubMenu（2026-11-13 新增）
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
  CHROME,
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

/**
 * 前端唯一真源：从 routes.tsx 解析 [{path, name, hidden}]（与页面组件一一对应）。
 * hidden 路由（如 /plan /liability）不进菜单，从对象列表深链进入（见 routes.tsx hidden 注释），
 * 不参与菜单↔路由全等断言。
 */
function parseRoutes() {
  const src = fs.readFileSync(ROUTES_TSX, 'utf8');
  const out = [];
  const re = /\{\s*path:\s*'([^']+)',\s*name:\s*'([^']+)'[^}]*\}/g;
  let m;
  while ((m = re.exec(src))) {
    out.push({ path: m[1], name: m[2], hidden: /hidden:\s*true/.test(m[0]) });
  }
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
// 条数**不硬编码**：routes.tsx 是唯一真源，在这里再写一个数字等于给真源拷一份副本，
// 每加一条路由都要同步改探针（2026-10-30 加 `/pre-exam` 后此处仍是 9 ⇒ 探针必红，
// 而当时路由表与菜单其实完全一致——报的是假失败）。下面 ghosts / unreachable 两条
// 双向全等已能推出「条数相等」，此处只守「解析成功」，防解析漂移把 0 条当成通过。
check('routes.tsx 解析到理赔路由（非空）', routes.length > 0, `实际 ${routes.length}`);
check('IAM 下发理赔菜单（非空）', menus.length > 0, `实际 ${menus.length}`);

const routeByPath = new Map(routes.map((r) => [r.path, r.name]));
const visibleRoutes = routes.filter((r) => !r.hidden);
const menuPaths = menus.map((m) => m.path.replace(CLAIM_MENU_PREFIX, ''));
const ghosts = menus.filter((m) => !routeByPath.has(m.path.replace(CLAIM_MENU_PREFIX, '')));
const unreachable = visibleRoutes.filter((r) => !menuPaths.includes(r.path));

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
  '菜单顺序 = routes.tsx 声明顺序（order_no 真实生效；hidden 路由不占菜单）',
  JSON.stringify(menuOrder) === JSON.stringify(visibleRoutes.map((r) => r.path)),
  `菜单序 ${menuOrder.join(' → ')}`,
);

if (ghosts.length || unreachable.length) {
  console.log('\n菜单/路由已漂移，跳过浏览器阶段（先修数据再谈 UI）。');
  const r = report('智能理赔单导航探针');
  process.exit(r.ok ? 0 : 1);
}

// ---------- 浏览器阶段 ----------
// 本机 Playwright 自带 chromium 下载不全且 1.64 不支持 macOS 13 ⇒ 退回系统 Chrome 通道；
// 与 micro-app-matrix-probe 同一套判据，避免两个探针走不同浏览器导致结论不可比。
const launchOpts = { headless: true };
if (fs.existsSync(CHROME)) launchOpts.executablePath = CHROME;
const browser = await chromium.launch(launchOpts);
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
      if (!e) return '';
      // innerText 对隐藏元素返回空串，会与「白屏」混淆 ⇒ 显式带上存在性与可见性，
      // 便于区分「容器没了 / 容器在但不可见 / 容器可见但真没内容」三种不同故障。
      const vis = !!e.offsetParent || e.getClientRects().length > 0;
      return `${e.innerText || ''}`.trim() + (vis ? '' : '\u0000HIDDEN');
    })
    .catch(() => '');

/**
 * 等微应用内容稳定（连续两次采样一致），避免读到上一个页面残留 DOM。
 *
 * 默认窗口 25s（原 12s）：首屏进子应用时要过 qiankun 拉 entry + 执行 bundle + 首屏接口，
 * 本机实测首次点击后 12s 内仍可能是空容器，12s 会把「加载慢」误判成「白屏」。
 * 超窗时打印实测文本长度，保留「真白屏」的可观测性。
 */
async function waitStable(ms = 25000) {
  const deadline = Date.now() + ms;
  let prev = '';
  while (Date.now() < deadline) {
    const cur = await viewportText();
    if (cur.length > 10 && cur === prev) return cur;
    prev = cur;
    await page.waitForTimeout(400);
  }
  // 只在「完全无文本」时告警：查询驱动页（理赔进度）初始态只有标题+输入框，本就 <10 字，
  // 按长度告警会把正常空态刷成噪音，掩盖真正的白屏。
  if (prev.length === 0) {
    const diag = await page
      .evaluate(() => {
        const e = document.querySelector('#subapp-viewport');
        return {
          has: !!e,
          childCount: e ? e.children.length : -1,
          htmlLen: e ? e.innerHTML.length : -1,
          hash: location.hash,
          url: location.href,
        };
      })
      .catch(() => null);
    console.log(`  ⚠ 等待 ${ms}ms 后容器仍为空（${prev.length} 字），按白屏判定`, JSON.stringify(diag));
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

  // ---------- E. 四空间常驻分组（本 change 新增，计数用精确值） ----------
  // 判据与「常驻可见、不折叠、不重排」一一对应；折叠/隐藏/搬动任一种缺陷都会转红。
  const GROUP_TITLES = ['作业', '查询', '配置', '治理'];
  const groupTitles = (await page.locator('.shell-sider .ant-menu-item-group-title').allInnerTexts())
    .map((t) => t.trim());
  const claimGroups = groupTitles.filter((t) => GROUP_TITLES.includes(t));
  check(
    '侧栏四个分组标题常驻可见且顺序为 作业/查询/配置/治理',
    JSON.stringify(claimGroups) === JSON.stringify(GROUP_TITLES),
    `实际 ${groupTitles.join(' | ')}`,
  );
  check('分组标题精确 4 个（无第三层导航标题）', claimGroups.length === 4, `实际 ${claimGroups.length}`);

  let visibleCount = 0;
  for (const r of visibleRoutes) {
    const item = page.locator('.shell-sider .ant-menu-item').filter({ hasText: r.name }).first();
    if (await item.isVisible().catch(() => false)) visibleCount += 1;
  }
  check(
    '分组后 15 条菜单项默认全部可见（无需任何展开动作）',
    visibleCount === visibleRoutes.length,
    `实际 ${visibleCount}/${visibleRoutes.length}`,
  );

  const domOrder = (await page.locator('.shell-sider .ant-menu-item').allInnerTexts())
    .map((t) => t.trim())
    .filter((t) => visibleRoutes.some((r) => r.name === t));
  check(
    '分组未改变菜单顺序（DOM 序 = routes.tsx 声明序）',
    JSON.stringify(domOrder) === JSON.stringify(visibleRoutes.map((r) => r.name)),
    `DOM 序 ${domOrder.join(' → ')}`,
  );

  const groupContainers = await page.locator('.shell-sider .ant-menu-item-group').count();
  const nestedSubmenus = await page.locator('.shell-sider .ant-menu-item-group .ant-menu-submenu').count();
  check(
    '分组内没有折叠 SubMenu（不引入第三层导航）',
    groupContainers === 4 && nestedSubmenus === 0,
    `group 容器 ${groupContainers} / 嵌套折叠 ${nestedSubmenus}`,
  );
  const groupedClickable = await page.locator('.shell-sider .ant-menu-item-group .ant-menu-item').count();
  check(
    '分组内菜单仍是可直接点击的 .ant-menu-item（15 条）',
    groupedClickable === visibleRoutes.length,
    `实际 ${groupedClickable}`,
  );
  await page
    .locator('.shell-sider .ant-menu-item')
    .filter({ hasText: visibleRoutes[0].name })
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
  // 只点可见路由（= 侧栏菜单，数据阶段已断言全等）：hidden 下钻页不占菜单，
  // 由对象列表「配置」按钮深链进入，菜单里点不到是正确形态。
  for (const r of visibleRoutes) {
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
    .filter({ hasText: visibleRoutes.find((r) => r.path === '/inspection').name })
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
