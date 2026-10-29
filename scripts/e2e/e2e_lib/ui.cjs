/**
 * 共享 UI 交互层（e2e_lib/ui.cjs）—— UI 探针的 Page Object 精简版。
 *
 * 解决项：此前浏览器登录流程在十几个 UI 探针里逐字复制（ui-app-to-data-probe.mjs
 * 一处、ui-nine-module-deep-probe.mjs 一处、ui-designer-probe.mjs 一个文件内竟有 6 处
 * `async function uiLogin()`），选择器与等待逻辑各自漂移，改一处要满地改。
 *
 * 本模块把这份同构 UI 样板收敛为共享助手，所有 .mjs / .cjs UI 探针统一 import/require：
 *   // .mjs（ESM 命名导入，cjs-module-lexer 可静态识别，同 client.cjs）
 *   import { uiLogin, shot, waitAppReady } from './e2e_lib/ui.cjs';
 *   // .cjs（CommonJS 解构）
 *   const { uiLogin, shot, waitAppReady } = require('../e2e_lib/ui.cjs');
 *
 * 纪律（README 已登记）：**新增或重构 UI 探针禁止在探针内重抄登录选择器**，
 * 一律走本模块的 uiLogin；导航/截图/就绪判定也优先复用本层，避免两族分裂。
 *
 * 凭据与地址全部来自 client.cjs（读 BONE_E2E_* 或默认值），本模块零硬编码。
 * 本模块被 require/import 时不产生任何输出与网络请求。
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { UI_BASE, USER, PWD, SHOT_DIR } = require('./client.cjs');

// 登录页字段选择器（跨探针统一的单一口径；改一处即全量生效）
// 用户名口径是各历史探针的**并集**：shell 登录框文案出现过「用户名/用户/账号」三种，
// 只认其中一种会在另一种页面上静默漏填（填了密码没填账号 ⇒ 登录失败却报「选择器超时」，
// 归因成本极高）。并集不会误伤：uiLogin 先等密码框出现才动作，登录页外不会被调用。
const USER_SEL =
  'input[placeholder*="用户"], input[placeholder*="账号"], input[name="username"], input#username';
const PASS_SEL = 'input[type="password"]';
const PWD_PH_SEL = 'input[placeholder*="密码"]';

/**
 * 浏览器表单登录（qiankun shell），成功返回 true。
 *
 * 行为等价原各探针内联的 uiLogin（以 ui-app-to-data-probe.mjs 的稳健版为蓝本）：
 * 填 USER/PWD → 回车提交 → 若密码框仍在（未自动跳转发请求）兜底点「登录」按钮
 * → 等密码框从 DOM 消失作为登录成功信号。
 *
 * @param {import('playwright').Page} page 已打开的浏览器页面
 * @param {{route?: string, timeout?: number}} [opts]
 *   route   登录前跳转的入口（默认 `${UI_BASE}/`，即 shell 根，会自动呈现登录页）；
 *           个别探针需要直达 `/iam#/accounts` 时传入覆盖。
 *   timeout 等待「密码框消失」的超时（默认 30000ms）。
 * @returns {Promise<boolean>} 登录成功为 true
 */
async function uiLogin(page, opts = {}) {
  const route = opts.route || `${UI_BASE}/`;
  const timeout = opts.timeout || 30000;
  await page.goto(route, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector(`${PASS_SEL}, ${PWD_PH_SEL}`, { timeout: 20000 });
  const u = page.locator(USER_SEL).first();
  if (await u.count()) await u.fill(USER);
  const p = page.locator(PASS_SEL).first();
  await p.fill(PWD);
  await p.press('Enter');
  await page.waitForTimeout(1200);
  // 回车未触发提交（部分登录表单只在按钮 click 上挂 handler）时兜底点提交按钮：
  // 优先原生 submit，其次文案含「登」的按钮（覆盖「登录 / 登 录 / 登」三种历史文案）。
  if (await page.locator(PASS_SEL).count()) {
    const submit = page.locator('button[type="submit"]').first();
    await submit
      .click({ timeout: 3000 })
      .catch(() =>
        page.locator('button').filter({ hasText: /登/ }).first().click({ timeout: 3000 }).catch(() => {}),
      );
    await page.waitForTimeout(3000);
  }
  return page
    .waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout })
    .then(() => true)
    .catch(() => false);
}

/**
 * 取浏览器本地的 JWT（UI 登录后供 api({token}) 复用，避免探针为拿 token 再走一遍 HTTP 登录）。
 * 兼容两种存放键名：token / bone_token（不同子应用历史口径不一致，都取一遍）。
 * @param {import('playwright').Page} page
 * @returns {Promise<string|null>}
 */
async function localToken(page) {
  try {
    return await page.evaluate(
      () => localStorage.getItem('token') || localStorage.getItem('bone_token') || null,
    );
  } catch {
    return null;
  }
}

/**
 * 截图落 SHOT_DIR（探针失败留痕用；目录不存在则创建，失败静默不阻断主流程）。
 * @param {import('playwright').Page} page
 * @param {string} name 文件名（不含扩展名，自动加 .png）
 */
async function shot(page, name) {
  try {
    fs.mkdirSync(SHOT_DIR, { recursive: true });
    await page.screenshot({ path: path.join(SHOT_DIR, `${name}.png`) });
    return true;
  } catch {
    return false;
  }
}

/**
 * 等 shell 就绪：侧边栏 / aside 出现（登录后子应用挂载信号）。
 * @param {import('playwright').Page} page
 * @param {{timeout?: number}} [opts]
 */
async function waitAppReady(page, opts = {}) {
  const timeout = opts.timeout || 20000;
  return page
    .waitForSelector('.ant-layout-sider, aside', { timeout })
    .then(() => true)
    .catch(() => false);
}

// 统一用 `exports.x =` 导出：.cjs 可直接解构，.mjs 命名导入可被 cjs-module-lexer 静态识别
exports.uiLogin = uiLogin;
exports.localToken = localToken;
exports.shot = shot;
exports.waitAppReady = waitAppReady;
exports.USER_SEL = USER_SEL;
exports.PASS_SEL = PASS_SEL;
