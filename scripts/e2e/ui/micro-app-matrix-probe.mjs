/**
 * 微应用矩阵加载探针（micro-app-matrix-probe.mjs）
 *
 * 守的是 2026-10-29 用户实测反馈的那类问题：**点进某个业务域，整页只有「微应用加载失败」**
 * （截图实证：bone-commerce-app 入口 //localhost:3012 Failed to fetch）。
 *
 * 成因不是代码缺陷，而是**子应用 dev server 没起**——qiankun 拿不到入口脚本，
 * 容器 div 保持空白，只有控制台有线索，用户侧表现为「白屏 + 一句报错」。
 * `dev-frontend-matrix.sh --check` 只能验端口，验不了「shell 真的把它挂载出来了」，
 * 故本探针走真实链路：登录 shell → 逐个 goto 每个微应用路由 → 断言没有加载失败面板。
 *
 * 判据（每条都可机器验证，且「停掉任一 dev server 就转红」）：
 *   A. 无加载失败面板 —— `.subapp-error`（role=alert，MicroAppLoadError 组件）数量为 0
 *   B. 容器真有内容   —— 微应用挂载点文本非空（防止「没报错但也没渲染」的假绿）
 *   C. 无 5xx         —— 导航期间该域的请求没有服务端错误
 *
 * 用法：
 *   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
 *   node scripts/e2e/ui/micro-app-matrix-probe.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { UI_BASE as BASE, SHOT_DIR, loadPlaywright, makeChecker, api } from '../e2e_lib/client.cjs';
import { uiLogin, shot } from '../e2e_lib/ui.cjs';

const { chromium } = loadPlaywright();
const { check, report } = makeChecker();

const HERE = path.dirname(new URL(import.meta.url).pathname);
const SHELL_APP_TSX = path.resolve(HERE, '../../../bone-frontend/apps/bone-shell/src/App.tsx');

/**
 * 前端唯一真源：从 shell 的 App.tsx 解析微应用注册与路由前缀。
 * 不另写一份端口/路由表——写死的表会和源码漂移（本项目已因此出过两次事故）。
 */
function parseShellRegistry() {
  const src = fs.readFileSync(SHELL_APP_TSX, 'utf8');

  // 路由前缀：MICRO_APP_ROUTE_PREFIXES 数组里的 '/xxx'
  const prefixBlock = src.match(/MICRO_APP_ROUTE_PREFIXES\s*=\s*\[([\s\S]*?)\]\s*as const/);
  const prefixes = prefixBlock
    ? [...prefixBlock[1].matchAll(/'(\/[^']+)'/g)].map((m) => m[1])
    : [];

  // 微应用注册：name: 'bone-xxx-app' ... entry: ... || '//localhost:30xx'
  const apps = [];
  const re = /name:\s*'(bone-[a-z-]+)'[\s\S]{0,400}?entry:[^|]*\|\|\s*'\/\/localhost:(\d{4})'/g;
  let m;
  while ((m = re.exec(src))) apps.push({ app: m[1], port: Number(m[2]) });

  // app 名 → 路由前缀：bone-commerce-app → /commerce
  return {
    prefixes,
    apps: apps.map((a) => {
      const key = a.app.replace(/^bone-/, '').replace(/-app$/, '');
      return { ...a, route: `/${key}` };
    }),
  };
}

/** 挂载点文本：微应用容器优先，退化到 main（不同 shell 版本容器 class 不一致）。 */
async function mountedText(page) {
  return page.evaluate(() => {
    const sel = '#subapp-viewport, .subapp-viewport, #micro-app, .micro-app-container, main';
    const el = document.querySelector(sel) || document.body;
    return (el.innerText || '').trim();
  });
}

/**
 * 后端唯一真源：IAM 当前用户可见菜单里，每个域的第一条可导航路径。
 * 为什么不用 `#/` 直达：子应用的根路由未必有匹配（实测 masterdata / commerce 的 `#/` 是空白页），
 * 而用户走的永远是菜单里的真实路径 —— 用菜单第一项才复现得了真实链路。
 */
async function fetchEntryPaths() {
  const { data } = await api('GET', '/iam/menus/current');
  const root = data?.data ?? data;
  const found = new Map();
  const walk = (n) => {
    if (Array.isArray(n)) return n.forEach(walk);
    if (!n || typeof n !== 'object') return;
    const p = n.path || '';
    const m = p.match(/^\/([a-z-]+)#\/(.+)$/);
    if (m && !found.has(`/${m[1]}`)) found.set(`/${m[1]}`, p);
    (n.children || []).forEach(walk);
  };
  walk(root);
  return found;
}

(async () => {
  const { prefixes, apps: registry } = parseShellRegistry();
  check('shell 解析出微应用注册', registry.length > 0, `解析到 ${registry.length} 个：${registry.map((r) => r.app).join(', ')}`);

  // 前缀表漏登记 = 菜单被 isKnownMenuPath 整组静默滤掉（本项目已踩过：/claim 漏登记）
  const missing = registry.filter((r) => !prefixes.includes(r.route)).map((r) => r.route);
  check(
    '每个微应用的路由前缀都已在 MICRO_APP_ROUTE_PREFIXES 登记',
    missing.length === 0,
    missing.length ? `漏登记：${missing.join(', ')}` : `前缀表 ${prefixes.length} 项，注册 ${registry.length} 项`,
  );

  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 950 } });
  const page = await ctx.newPage();

  const serverErrors = [];
  page.on('response', (r) => {
    if (r.status() >= 500) serverErrors.push(`${r.status()} ${r.url()}`);
  });

  const loggedIn = await uiLogin(page, { route: `${BASE}/` });
  check('shell 登录成功', loggedIn, loggedIn ? '' : '未进入主界面，后续断言无意义');
  if (!loggedIn) {
    await browser.close();
    report('微应用矩阵探针');
    process.exit(1);
  }

  const entryPaths = await fetchEntryPaths();
  const noMenu = registry.filter((r) => !entryPaths.has(r.route)).map((r) => r.route);
  check(
    '每个已注册微应用都有至少一条可导航菜单（功能可达）',
    noMenu.length === 0,
    noMenu.length ? `无菜单入口：${noMenu.join(', ')}` : `${entryPaths.size} 个域有菜单入口`,
  );

  for (const r of registry) {
    serverErrors.length = 0;
    const target = `${BASE}${entryPaths.get(r.route) ?? `${r.route}#/`}`;
    await page.goto(target, { waitUntil: 'domcontentloaded' });
    // 微应用是异步挂载 + 自举请求，固定等待比 waitForSelector 更稳（容器选择器随版本变）
    await page.waitForTimeout(3500);
    await page.reload({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await page.waitForTimeout(2500);

    const errPanels = await page.locator('.subapp-error').count();
    const body = await mountedText(page);
    const hasContent = body.length > 10 && !body.includes('微应用加载失败');

    check(`[${r.app}] 无「微应用加载失败」面板（入口 :${r.port}）`, errPanels === 0, `实际面板数 ${errPanels}`);
    check(`[${r.app}] 挂载点渲染出内容`, hasContent, `容器文本 ${body.length} 字`);
    check(`[${r.app}] 无 5xx`, serverErrors.length === 0, serverErrors.slice(0, 2).join(' | '));

    await shot(page, `micro_app_matrix_${r.app}`);
  }

  await browser.close();
  const res = report('微应用矩阵探针');
  console.log(`\n截图：${SHOT_DIR}/micro_app_matrix_*.png`);
  process.exit(res.ok ? 0 : 1);
})();
