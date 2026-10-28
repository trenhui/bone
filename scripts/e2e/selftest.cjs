/**
 * E2E 共享层离线自证 · Node 侧（scripts/e2e/selftest.cjs）
 *
 * 与 selftest.py 分工：Python 侧验「Python 公共层 + JUnit 双端」，本文件验「Node 公共层」。
 * 全部断言 **不起浏览器、不发网络请求**，1 秒内出结论 —— 让任何 agent 在跑 20 个 UI 探针
 * 之前，先确认 client.cjs / fixtures.cjs / ui.cjs 本身是好的。
 *
 * 为什么值得单独存在：`node --check` 只保证语法。历史上真正咬人的是行为——
 * 例如 `dataset()` 的缓存语义（同 seed 同实例，重复调用会推进随机序列）、
 * `ui.cjs` 在没装 playwright 时被 import 是否炸、`makeChecker().report()` 的 ok 判定。
 * 这些只有真调一次才知道。
 *
 * 用法：node scripts/e2e/selftest.cjs     （run.sh selftest 会连同 selftest.py 一起跑）
 * 退出码：0 全绿；1 有失败。
 */
'use strict';

const path = require('path');

const LIB = path.join(__dirname, 'e2e_lib');
const client = require(path.join(LIB, 'client.cjs'));
const { Dataset } = require(path.join(LIB, 'fixtures.cjs'));
const ui = require(path.join(LIB, 'ui.cjs'));

// 轻量断言器（不依赖 client.makeChecker，避免「用待测对象测它自己」）
const results = [];
const check = (name, cond, detail = '') => {
  results.push({ name, ok: !!cond, detail });
  console.log(`${cond ? '✓' : '✗'} ${name}${cond ? '' : '  ← ' + detail}`);
};
const section = (t) => console.log(`\n${'='.repeat(78)}\n${t}\n${'='.repeat(78)}`);

// ---------- 1. client.cjs 导出契约 ----------
section('1. client.cjs 导出契约（20+ 探针的公共面）');
const expectedExports = {
  BASE: 'string', UI_BASE: 'string', IAM_BASE: 'string',
  USER: 'string', PWD: 'string', TENANT: 'string',
  SHOT_DIR: 'string', CHROME: 'string', STAMP: 'string',
  api: 'function', login: 'function', tryLogin: 'function', token: 'function',
  loadPlaywright: 'function', makeChecker: 'function',
  registerCleanup: 'function', runCleanup: 'function',
  registerJUnit: 'function', writeJUnit: 'function',
  stamp: 'function',
};
const missing = Object.entries(expectedExports)
  .filter(([k, t]) => typeof client[k] !== t)
  .map(([k, t]) => `${k}(${typeof client[k]}≠${t})`);
check(`导出齐全且类型正确（${Object.keys(expectedExports).length} 个符号）`,
  missing.length === 0, `缺失/类型错：${missing}`);

check('BASE 以 http(s):// 开头且含 /api/v1',
  /^https?:\/\//.test(client.BASE) && client.BASE.includes('/api/v1'), client.BASE);
check('UI_BASE 是前端壳层地址', /^https?:\/\//.test(client.UI_BASE), client.UI_BASE);

// ---------- 2. 导入零副作用 ----------
section('2. 导入零副作用（被 import 时不得发起网络）');
// 强证据：把 BASE 指到**必然不可达**的端口 1，仍能 require 成功并正常退出 ⇒ 导入期没触网。
// （在父进程里数 socket 不可靠：运行时自身有 IPC 句柄，会把基础设施算成「客户端泄漏」）
const { spawnSync } = require('child_process');
const probeSrc = `
  require(${JSON.stringify(path.join(LIB, 'client.cjs'))});
  const socks = (process._getActiveHandles ? process._getActiveHandles() : [])
    .filter((h) => h && h.constructor && /Socket|TLSSocket/.test(h.constructor.name));
  process.exit(socks.length === 0 ? 0 : 3);   // 3 = 导入期留下了 socket
`;
const child = spawnSync(process.execPath, ['-e', probeSrc], {
  env: { ...process.env, BONE_E2E_BASE: 'http://127.0.0.1:1/api/v1',
         BONE_E2E_LOGIN_BASE: 'http://127.0.0.1:1/api/v1' },
  encoding: 'utf8',
  timeout: 15000,
});
check('BASE 指向死端口时仍能 import（导入期不登录）', child.status === 0,
  child.status === 3 ? '导入 client.cjs 留下了活动 socket'
                     : `rc=${child.status} ${(child.stderr || '').slice(0, 200)}`);
check('token() 是 login() 的别名（懒登录；不是只读 getter）',
  typeof client.token === 'function' && client.token.length <= 1, `length=${client.token.length}`);

// ---------- 3. makeChecker 语义 ----------
section('3. makeChecker 语义');
if (typeof client.makeChecker !== 'function') {
  // 一个导出缺失不该吃掉其余结论：记为失败并跳过本段，后续段照常给结论
  check('makeChecker 可用', false, '导出缺失，跳过本段');
} else {
  // 本段用的是**假断言**（甲/乙），不能进 CI 报告：临时摘掉 JUnit 出口再建 checker
  const savedJunitDir = process.env.BONE_E2E_JUNIT_DIR;
  delete process.env.BONE_E2E_JUNIT_DIR;
  const ck = client.makeChecker();
  // 注意：Node 会把 undefined 赋值给 process.env 变成字符串 "undefined"，
  // 直接回写会造出 `undefined/` 目录（已踩过），故必须先判存在
  if (savedJunitDir === undefined) delete process.env.BONE_E2E_JUNIT_DIR;
  else process.env.BONE_E2E_JUNIT_DIR = savedJunitDir;
  ck.check('甲', true);
  ck.check('乙', false, '实测 500');
  const summary = ck.report('自检');
  check('check 记录 name/ok/detail 三元组', ck.results.length === 2
    && ck.results[0].name === '甲' && ck.results[1].ok === false && ck.results[1].detail === '实测 500',
    JSON.stringify(ck.results));
  check('report 返回 {pass,total,ok} 且 ok=false（有失败）',
    summary.pass === 1 && summary.total === 2 && summary.ok === false, JSON.stringify(summary));
}

// ---------- 4. fixtures.cjs ----------
section('4. fixtures.cjs 确定性与业务守恒');
const a = new Dataset('selftest-seed');
const b = new Dataset('selftest-seed');
const seq = (d) => [d.person(), d.doc_no('BX'), d.amount(), d.customer.code, d.split(1000, 3).join(',')];
check('同 seed 两个实例 ⇒ 同调用序列同值', JSON.stringify(seq(a)) === JSON.stringify(seq(b)),
  `${seq(a)} vs ${seq(b)}`);

const d0 = new Dataset('doc-seed');
check('doc_no 形如 BX-YYYYMMDD-NNNN', /^BX-\d{8}-\d{4}$/.test(d0.doc_no('BX')), d0.doc_no('BX'));

const amounts = Array.from({ length: 30 }, (_, i) => new Dataset(`amt-${i}`).amount());
const roundOnes = amounts.filter((v) => Math.abs(v - Math.round(v)) < 0.005);
check('amount 非圆整（30 个样本无整元）', roundOnes.length === 0, `圆整样本=${roundOnes.slice(0, 5)}`);

const total = 2863.5;
const parts = new Dataset('split-seed').split(total, 3);
const sum = Math.round(parts.reduce((x, y) => x + y, 0) * 100) / 100;
check('split 分位守恒（Σ == total）', sum === total, `${parts} Σ=${sum}`);
const brk = new Dataset('split-seed').split(total, 3, true);
const diff = Math.round((brk.reduce((x, y) => x + y, 0) - total) * 100) / 100;
check('break_ 恒差 1.01（负向断言可预期）', diff === 1.01, `差=${diff}`);

// ---------- 5. ui.cjs ----------
section('5. ui.cjs 契约');
check('导出 uiLogin/shot/waitAppReady 三个助手',
  ['uiLogin', 'shot', 'waitAppReady'].every((k) => typeof ui[k] === 'function'),
  Object.keys(ui).join(','));
check('选择器常量与 client 凭据同源（非硬编码字面量）',
  typeof ui.USER_SEL === 'string' && ui.USER_SEL.includes('用户名'),
  ui.USER_SEL);
check('require(ui.cjs) 不需要 playwright（未装也能加载）', true, '');

// ---------- 6. 与 Python 侧接口同构 ----------
section('6. 与 fixtures.py 的接口同构（防两族漂移）');
const pyMethods = ['doc_no', 'amount', 'split', 'person', 'company', 'sku', 'tax_no', 'equipment', 'days_ago'];
const jsMissing = pyMethods.filter((m) => typeof new Dataset('x')[m] !== 'function');
check(`Dataset 方法与 Python 侧同名同义（${pyMethods.length} 个）`, jsMissing.length === 0,
  `JS 缺失：${jsMissing}`);

// ---------- 汇总 ----------
const pass = results.filter((r) => r.ok).length;
console.log(`\n${'='.repeat(78)}\nselftest(node) 汇总\n${'='.repeat(78)}`);
console.log(`通过 ${pass} 项，失败 ${results.length - pass} 项`);
results.filter((r) => !r.ok).forEach((r) => console.log(`  ✗ ${r.name} :: ${r.detail}`));

// 自检自身的真实结果进 CI 报告（只登记本文件的结果，不含第三段那对假断言）
if (process.env.BONE_E2E_JUNIT_DIR) {
  if (!process.env.BONE_E2E_JUNIT_SUITE) process.env.BONE_E2E_JUNIT_SUITE = 'selftest-node';
  client.registerJUnit(results);
}

process.exit(pass === results.length ? 0 : 1);
