/**
 * Node 探针共享客户端（scripts/e2e/e2e_lib/client.cjs，与 e2e_lib/ 的 Python 公共层并列）
 *
 * 用途：把 scripts/e2e 下 .mjs/.cjs 探针各自复制的样板收敛到一处——
 *   1. 配置常量：网关 API 根 / 前端壳层 / iam 直连 / 种子账号 / 租户 / 截图目录 / 运行后缀；
 *   2. login()/tryLogin()/token()：POST {base}/iam/login 取 JWT（按 base 懒加载缓存）；
 *   3. api()：带 token 的 fetch，返回 {status, data, ok, headers, etag}；
 *   4. loadPlaywright()/CHROME：playwright 加载与 macOS 系统 Chrome 路径
 *      （ESM 不认 NODE_PATH，绝对路径导入的历史踩坑见 scripts/e2e/ui/README.md）；
 *   5. makeChecker()：本地 results[] + check(name, cond, detail) 断言收集与 report() 汇总。
 *
 * 接入方式：
 *   - .cjs：`const { ... } = require('../e2e_lib/client.cjs');`（scripts/e2e 根下用 './e2e_lib/client.cjs'）
 *   - .mjs：`import { ... } from './e2e_lib/client.cjs';`（Node CJS interop；
 *     导出统一用 `exports.x =` 形式，cjs-module-lexer 可静态识别命名导出）
 *
 * 约定（勿破坏）：
 *   - 本模块被 require/import 时**不发起任何网络请求**（登录只发生在 login()/api() 调用时），
 *     保证 `node --check` 与被 import 均无副作用；
 *   - STAMP 为运行后缀：新探针默认 9 位；历史探针分别用 slice(-6)/slice(-7) 口径，
 *     改造时一律用 stamp(n) 保持其原口径，不得改变既有数据命名；
 *   - 断言名称/数量/顺序、截图命名、流程控制属于各探针场景逻辑，不进本文件。
 */
'use strict';
const path = require('path');

// ---------- 配置 ----------
/** 网关 API 根（含 /api/v1）。兼容历史 env 名 BONE_E2E_BASE / BONE_E2E_API */
const BASE = process.env.BONE_E2E_BASE || process.env.BONE_E2E_API || 'http://127.0.0.1:8888/api/v1';
/** 前端壳层（shell，qiankun 宿主页） */
const UI_BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
/** iam 服务直连根（少数探针绕过网关直取 token，路径需自带 /api/v1） */
const IAM_BASE = process.env.BONE_IAM_BASE || 'http://127.0.0.1:8081';
/** 本地联调种子账号；CI / 其它环境用 BONE_E2E_USER / BONE_E2E_PWD 注入真实凭据
 *（字面量已在 .gitleaks.toml 精确豁免；口令必须带缺省值，否则本地裸跑登录 401） */
const USER = process.env.BONE_E2E_USER || 'admin';
const PWD = process.env.BONE_E2E_PWD || '123456';
/** 租户请求头缺省值 */
const TENANT = process.env.BONE_E2E_TENANT || '0';
/** 截图 / 证据落盘目录（仓库 logs/） */
const SHOT_DIR = process.env.BONE_E2E_SHOT_DIR || path.join(__dirname, '..', '..', '..', 'logs');
/** macOS 系统 Chrome：.cjs 探针 chromium.launch({ headless: true, executablePath: CHROME }) 用 */
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
/** playwright 托管在 workbuddy workspace 的绝对路径（NODE_PATH 兜底，见 ui/README.md） */
const PLAYWRIGHT_PATH = '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
/** 运行后缀：默认 9 位；历史探针口径为 -6/-7 位，用 stamp(n) 保持一致 */
const STAMP = String(Date.now()).slice(-9);
/** 运行后缀工厂：stamp(6) 等价于历史探针的 String(Date.now()).slice(-6) */
const stamp = (n = 6) => String(Date.now()).slice(-n);

// ---------- 登录（懒加载缓存；按 base 分别缓存） ----------
const _tokens = new Map();
async function _loginRaw(base) {
  const key = String(base).replace(/\/$/, '');
  if (_tokens.has(key)) return { key, tok: _tokens.get(key), r: null, j: null };
  const r = await fetch(`${key}/iam/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PWD }),
  });
  const j = await r.json();
  const tok = j?.data?.token;
  if (tok) _tokens.set(key, tok);
  return { key, tok, r, j };
}
/**
 * POST {base}/iam/login 取 token。
 * 成功：返回 token 并缓存；缺 token：抛错（带响应摘要）；网络/JSON 解析错误原样上抛。
 * @param {string} [base] API 根，默认网关 BASE；iam 直连传 `${IAM_BASE}/api/v1`
 */
async function login(base = BASE) {
  const { tok, r, j } = await _loginRaw(base);
  if (!tok) throw new Error('登录取 token 失败: ' + r.status + ' ' + JSON.stringify(j).slice(0, 200));
  return tok;
}
/**
 * 与 login() 同请求，但缺 token 时返回 null 而**不抛错**（网络/JSON 解析错误仍原样上抛）。
 * 供「登录失败也要继续跑后续断言」的探针保持原流程。
 */
async function tryLogin(base = BASE) {
  const { tok } = await _loginRaw(base);
  return tok || null;
}
/** login() 的别名：取（或触发）懒加载 token */
const token = (base) => login(base);

/**
 * 带 token 的 fetch。返回 {status, data, ok, headers, etag}：
 *   - data   ：响应体 JSON.parse 结果（非 JSON 时为原文），等价于原探针的 res.json()；
 *   - headers：原生 Headers（可 .get('etag') 等）；
 *   - etag   ：res.headers.get('etag') || undefined（便捷字段）。
 * @param {string} method HTTP 方法
 * @param {string} path   相对 API 根的路径（如 '/iam/menus/tree'）；以 http(s) 开头则原样使用
 * @param {*} [body]      请求体（undefined 表示无请求体；对象自动 JSON.stringify）
 * @param {{token?: string, base?: string, loginBase?: string, headers?: object}} [opts]
 *   opts.token   显式 token（UI 登录后从 localStorage 取到的 JWT），不传则懒登录
 *   opts.base    本次请求的 API 根（默认网关 BASE）
 *   opts.headers 附加/覆盖请求头（含 X-Tenant-Id 覆盖）
 */
async function api(method, path, body, opts = {}) {
  const base = opts.base || BASE;
  const url = /^https?:/.test(path) ? path : String(base).replace(/\/$/, '') + (path.startsWith('/') ? path : '/' + path);
  const headers = {};
  const hasBody = body !== undefined;
  if (hasBody) headers['Content-Type'] = 'application/json';
  headers.Authorization = `Bearer ${opts.token || (await login(opts.loginBase || base))}`;
  if (!('X-Tenant-Id' in (opts.headers || {}))) headers['X-Tenant-Id'] = TENANT;
  Object.assign(headers, opts.headers || {});
  const res = await fetch(url, { method, headers, body: hasBody ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  return { status: res.status, data, ok: res.ok, headers: res.headers, etag: res.headers.get('etag') || undefined };
}

// ---------- Playwright ----------
/** 加载 playwright：先按 NODE_PATH/常规解析 require('playwright')，失败再退回 workspace 绝对路径 */
function loadPlaywright() {
  try {
    return require('playwright');
  } catch (e) {
    return require(PLAYWRIGHT_PATH);
  }
}

// ---------- 退出残留清理（与 Python e2e_lib/teardown.py 对称）----------
/**
 * UI 探针在浏览器里创建的业务数据（实体/记录/账号/临时文件）需要退出时回收，
 * 否则库越跑越脏。Python 侧由 teardown.register + atexit 自动回收；Node 侧无 atexit 等价物，
 * 故在此提供 registerCleanup / runCleanup，由探针在 finally 中显式 await 触发。
 *
 * 用法（探针末尾）：
 *   registerCleanup(async () => { await api('DELETE', `/.../${id}`, { token }); });
 *   try { ...断言... } finally { await runCleanup(); await browser.close(); process.exit(code); }
 */
const _cleanups = [];
/** 注册退出清理回调；fn 可同步或返回 Promise；多个按注册逆序执行。 */
function registerCleanup(fn) { _cleanups.push(fn); return fn; }
/** 执行全部清理（逆序）；任一异常只告警不污染测试结果。探针应在 finally 中 await 它。 */
async function runCleanup() {
  while (_cleanups.length) {
    const fn = _cleanups.pop();
    try { await fn(); } catch (e) { console.error('[teardown] 清理异常（不影响测试结果）:', (e && e.message) || e); }
  }
}
// 安全网：若探针直接 process.exit 而未 await runCleanup，exit 时提示遗漏
process.on('exit', () => {
  if (_cleanups.length) {
    console.warn(`[teardown] 警告：${_cleanups.length} 个清理回调未执行（探针应在 finally 中 await runCleanup()）`);
  }
});

// ---------- 断言收集与汇总 ----------
/**
 * 本地 results[] + check 断言收集器（与历史探针逐字同款的 ✓/✗ 输出）。
 * report(title) 打印 `\n<title>：通过 pass / total` 汇总行并返回 {pass, total, results, ok}，
 * 由探针自行决定何时 process.exit（保持各自既有顺序：汇总 → 关浏览器 → 退出码）。
 */
function makeChecker() {
  const results = [];
  const check = (name, cond, detail = '') => {
    results.push({ name, ok: !!cond, detail });
    console.log(`${cond ? '✓' : '✗'} ${name}${cond ? '' : '  ← ' + detail}`);
  };
  const report = (title) => {
    const pass = results.filter((r) => r.ok).length;
    console.log(`\n${title}：通过 ${pass} / ${results.length}`);
    return { pass, total: results.length, results, ok: pass === results.length };
  };
  return { results, check, report };
}

// 统一用 `exports.x =` 导出：.cjs 可直接解构，.mjs 命名导入可被 cjs-module-lexer 静态识别
exports.BASE = BASE;
exports.UI_BASE = UI_BASE;
exports.IAM_BASE = IAM_BASE;
exports.USER = USER;
exports.PWD = PWD;
exports.TENANT = TENANT;
exports.SHOT_DIR = SHOT_DIR;
exports.CHROME = CHROME;
exports.PLAYWRIGHT_PATH = PLAYWRIGHT_PATH;
exports.STAMP = STAMP;
exports.stamp = stamp;
exports.login = login;
exports.tryLogin = tryLogin;
exports.token = token;
exports.api = api;
exports.loadPlaywright = loadPlaywright;
exports.makeChecker = makeChecker;
exports.registerCleanup = registerCleanup;
exports.runCleanup = runCleanup;
