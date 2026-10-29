/**
 * 智能理赔「错误提示卫生」探针（claim-toast-hygiene-probe.mjs）
 *
 * 守的是 2026-10-29 用户截图反馈的问题：核赔工作台上沿堆了 4 条 toast
 * （「待办加载失败」×2 +「理算结果加载失败」×2），且**没有任何可用线索**——
 * 只看这行字既不知道是哪个请求失败、也不知道为什么失败，只能截图求助。
 *
 * 三类退化分别锁死（每条都可被「改一行实现」证明会转红）：
 *   A. 无案件时不再自动查占位案件 —— 先前 claimId 回落成 '1'，进页面先白跑一次
 *      （StrictMode 下两次）对不存在案件的查询，失败即文案报「加载失败」。
 *   B. 失败文案要带后端真实原因 —— 先前 catch 一律 `message.error('待办加载失败')`，
 *      把 API 客户端已经挂在错误上的 errorCode/message 全丢了；现有
 *      `resolveErrorMessage` + 已初始化的 @bone/shared-utils 语言包能给出准确原因
 *      （如 SC_CLAIM_NOT_FOUND →「案件不存在或不可见」）。
 *   C. 同类失败不堆叠多条 toast —— 开发态 StrictMode 双挂载并发触发两次，
 *      共用 message key 后 antd 会替换而非并列。
 *
 * 用法：
 *   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
 *   node scripts/e2e/ui/claim-toast-hygiene-probe.mjs
 */
import { UI_BASE as BASE, SHOT_DIR, loadPlaywright, makeChecker } from '../e2e_lib/client.cjs';
import { uiLogin, shot } from '../e2e_lib/ui.cjs';

const { chromium } = loadPlaywright();
const { check, report } = makeChecker();

/** 一条真实存在的案件 ID（tenant 0），用于验证「指定案件后正常查询」不被误伤。 */
const REAL_CLAIM_ID = '770660239849553920';

const errorToasts = (page) =>
  page.locator('.ant-message-error').filter({ hasText: /\S/ }).count();

const toastText = (page) =>
  page.locator('.ant-message-error').allInnerTexts().then((t) => t.join(' | '));

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1500, height: 950 } });
  const page = await ctx.newPage();
  page.setDefaultTimeout(20000);

  const loggedIn = await uiLogin(page, { route: `${BASE}/` });
  check('shell 登录成功', loggedIn, loggedIn ? '' : '后续断言无意义');
  if (!loggedIn) {
    await browser.close();
    report('理赔错误提示卫生探针');
    process.exit(1);
  }

  // ---------- A. 未指定案件：不应对任何案件发起理算查询 ----------
  const adjustReqs = [];
  const onAdjustReq = (r) => {
    if (/\/api\/v1\/claims\/[^/]+\/adjust(\?|$)/.test(new URL(r.url()).pathname + new URL(r.url()).search)) {
      adjustReqs.push(r.url());
    }
  };
  page.on('request', onAdjustReq);

  await page.goto(`${BASE}/claim#/adjust`, { waitUntil: 'domcontentloaded' }).catch(() => {});
  await page.waitForTimeout(4000);

  check(
    '未指定案件时不发起理算查询（无占位自动查）',
    adjustReqs.length === 0,
    adjustReqs.length ? `仍在查询：${adjustReqs.slice(0, 2).join(' , ')}` : '0 次（保持空态）',
  );

  const adjustEmpty = await page
    .locator('#subapp-viewport')
    .innerText()
    .catch(() => '');
  check(
    '未指定案件时展示「尚未理算」空态（而非报错）',
    /尚未理算|暂无理算结果/.test(adjustEmpty),
    adjustEmpty.slice(0, 80).replace(/\n/g, ' / '),
  );
  check(
    '未指定案件时无错误 toast',
    (await errorToasts(page)) === 0,
    await toastText(page),
  );
  await shot(page, 'claim_toast_hygiene_adjust_empty');

  page.off('request', onAdjustReq);

  // ---------- B. 指定真实案件：仍应正常查询且不报错 ----------
  const realReqs = [];
  const onReal = (r) => {
    const p = new URL(r.url()).pathname;
    if (/\/adjust(\?|$)/.test(p + new URL(r.url()).search)) realReqs.push(r.url());
  };
  page.on('request', onReal);
  await page.goto(`${BASE}/claim#/adjust?claimId=${REAL_CLAIM_ID}`, { waitUntil: 'domcontentloaded' }).catch(() => {});
  await page.waitForTimeout(4000);
  check(
    '指定案件时仍发起理算查询（未被误伤）',
    realReqs.length > 0,
    `${realReqs.length} 次`,
  );
  check('指定案件时无错误 toast', (await errorToasts(page)) === 0, await toastText(page));
  await shot(page, 'claim_toast_hygiene_adjust_case');
  page.off('request', onReal);

  // ---------- C. 强制失败：文案要带真实原因，且只出现 1 条 ----------
  // 用 URL 谓词而非 glob：`/'?` 在 URL 里是字面量，glob 的 `?`/`**` 组合匹配不稳定，
  // 断言不成立时必须是「真的没失败」而不是「压根没拦到」（本项目判别力铁律）。
  let intercepted = 0;
  await page.route(
    (url) => url.pathname === '/api/v1/claims/',
    async (route) => {
      intercepted += 1;
      await route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          code: 404,
          message: '请求资源不存在',
          data: {
            type: 'https://bone.wps.cn/problems/claim-not-found',
            title: 'Not Found',
            status: 404,
            detail: '案件 id=1 不存在',
            errorCode: 'SC_CLAIM_NOT_FOUND',
          },
        }),
      });
    },
  );

  await page.goto(`${BASE}/claim#/review`, { waitUntil: 'domcontentloaded' }).catch(() => {});

  // toast 默认 3s 自动关闭：固定等待会整段错过（先前 4.5s 采样读到「0 条」是采样点不对，
  // 不是没报错）。这里轮询取「首次出现」的文本，并追踪过程中最大并列数。
  const deadline = Date.now() + 10000;
  let maxCount = 0;
  let failText = '';
  while (Date.now() < deadline) {
    const c = await errorToasts(page).catch(() => 0);
    if (c > maxCount) maxCount = c;
    if (c > 0 && !failText) {
      failText = await toastText(page).catch(() => '');
      // 趁 toast 还在屏上截图：放到循环外会因 3s 自动关闭而只剩「无 toast」的空图。
      await shot(page, 'claim_toast_hygiene_forced_failure').catch(() => {});
    }
    if (c === 0 && failText) break; // 出现过且已消失，无需再等
    await page.waitForTimeout(200);
  }
  const failCount = maxCount;

  // 观测到的原文进日志：截图会因 toast 3s 自动关闭而错位，这里的文本才是稳定证据。
  console.log(`   观测 toast ×${failCount}：${failText || '<无>'}`);

  // 前置放在采样之后：goto 后 domcontentloaded 时微应用尚未挂载，此时计数为 0
  // 是正常的时间差，不是没拦到——放在这里才是真闸门。
  check('已成功拦截待办列表请求（失败注入生效）', intercepted > 0, `拦截 ${intercepted} 次`);

  check(
    '失败文案带后端真实原因（而非笼统「待办加载失败」）',
    /案件不存在或不可见/.test(failText),
    failText || '<无 toast>',
  );
  check(
    '同类失败只出现 1 条 toast（StrictMode 双挂载不刷屏）',
    failCount === 1,
    `实际 ${failCount} 条：${failText.slice(0, 120)}`,
  );
  // 不在这里补拍：toast 已 3s 自动关闭，此处再拍同名截图会把循环内的有效留证覆盖成空图。

  await page.unroute((url) => url.pathname === '/api/v1/claims/');

  // ---------- D. 正常链路：核赔工作台无错误 toast ----------
  await page.goto(`${BASE}/claim#/review`, { waitUntil: 'domcontentloaded' }).catch(() => {});
  await page.waitForTimeout(4000);
  check('正常链路核赔工作台无错误 toast', (await errorToasts(page)) === 0, await toastText(page));
  await shot(page, 'claim_toast_hygiene_review_ok');

  await browser.close();
  const res = report('理赔错误提示卫生探针');
  console.log(`\n截图：${SHOT_DIR}/claim_toast_hygiene_*.png`);
  process.exit(res.ok ? 0 : 1);
})();
