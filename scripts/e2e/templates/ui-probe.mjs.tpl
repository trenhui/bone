/**
 * {{DESC}}
 *
 * 模板来源：scripts/e2e/templates/ui-probe.mjs.tpl（由 scaffold.py 生成，可安全改写）
 *
 * 用法：
 *   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
 *   node scripts/e2e/ui/{{FILE}}
 *
 * 骨架自带五条纪律（改用例时别删——它们是「可复用」的全部内容）：
 *   1. 登录一律 `import { uiLogin } from '../e2e_lib/ui.cjs'`：**禁止在探针内重抄登录选择器**
 *      （selftest 的棘轮会红，且重抄的副本会与共享层漂移 ⇒ 改口径不生效的假绿）；
 *   2. 地址/凭据只从 client.cjs 取：脚本内不出现 host:port 与口令；
 *   3. 造数用 e2e_lib/fixtures.cjs 的 dataset(seed)：同 seed 跨进程同值，残留可归因；
 *   4. 断言用 makeChecker() 的 check(name, cond, detail)：失败详情写**实测值**；
 *   5. 浏览器内造出的数据用 registerCleanup 登记回收（对称 Python teardown.py），
 *      并在 finally 中 await runCleanup()。
 */
import {
  UI_BASE,
  CHROME,
  loadPlaywright,
  makeChecker,
  registerCleanup,
  runCleanup,
  stamp,
} from '../e2e_lib/client.cjs';
import { uiLogin, shot, waitAppReady } from '../e2e_lib/ui.cjs';
import { dataset } from '../e2e_lib/fixtures.cjs';

const { chromium } = loadPlaywright();
const { check, report } = makeChecker();
const STAMP = stamp(9);
const ds = dataset(STAMP);

let browser;
try {
  browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const page = await (
    await browser.newContext({ viewport: { width: 1440, height: 900 } })
  ).newPage();

  // ---- 登录 + 就绪（共享层；成功信号是「密码框消失」而不是 sleep 等待）----
  const logged = await uiLogin(page);
  check('UI 登录成功', logged, '登录表单未消失');
  if (!logged) throw new Error('login failed');

  const ready = await waitAppReady(page);
  check('子应用已挂载', ready, '侧边栏/aside 未出现');

  // ---- 你的场景断言写在下面 ----
  // 例：await page.goto(`${UI_BASE}/metadata#/entities`, { waitUntil: 'domcontentloaded' });
  //     check('实体列表可见', await page.locator('text=实体').count() > 0, '未找到实体入口');
  // 浏览器里造出来的数据，用 registerCleanup(async () => { ... }) 登记回收。
} catch (e) {
  check('探针执行未抛异常', false, String((e && e.message) || e).slice(0, 200));
} finally {
  await runCleanup().catch(() => {});
  if (browser) await browser.close().catch(() => {});
}

const r = report('{{SUITE}}');
// 退出码是结果出口（CI 读它）；JUnit XML 在 BONE_E2E_JUNIT_DIR 下自动落盘。
process.exit(r.ok ? 0 : 1);
