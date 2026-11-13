#!/usr/bin/env node
/**
 * 智能理赔「我的工作台 + 案件360 + AI 建议卡」真·UI 人工操作冒烟。
 *
 * 目的：补齐 API 契约层联调（synap-claim-workbench-feedback-e2e.mjs，40/40）之外的
 *       **浏览器 DOM 级**验证——真实登录、真实渲染、真实点击、真实跳转，走
 *       shell(:3000) → vite 代理(/api) → 网关(:8888) → 后端(:8087) 的完整链路。
 *
 * 覆盖（模拟内勤一次典型作业）：
 *   1. 打开 shell，用 admin/123456 真实填表登录，token 落地 localStorage；
 *   2. 进入 /claim#/workbench，断言「我的工作台」渲染，工作台计数并发拉取后端；
 *   3. 跨键检索 E2E-POLICY-0001（保单号），断言 /api/v1/claims/search 200 且结果按钮出现；
 *   4. 点结果进入案件 360，断言「案件 360」与报案号渲染；
 *   5. AI 建议卡：若可裁决（AI_ASSISTED）则点「采纳」→ 弹窗确定 → 断言落库提示；
 *      否则断言 fail-closed 明确文案 + 零裁决动作（不伪造「AI 未参与 / 无风险」）。
 *   6. 对象内聚：逐个点六个作业 Tab，断言仍停留在案件视图、无案件 ID 输入框、
 *      内容组件确实内嵌、页面标题恰好一个；再验深链 tab 直达、浏览器前进后退、
 *      18 位雪花 ID 逐字不截断、旧作业路由直达与「回案件 360」落在同一 tab。
 *   7. 全程收集 console error / pageerror / 失败请求；截图留证。
 * 计数类断言使用精确值；三种缺陷写法（折叠分组 / Tab 内混入 ID 输入 / ID 截断）
 * 的互补变异验证见本 change 的 Build 记录。
 *
 * 退出码：全部关键断言通过 → 0；任一失败 → 1。产物写 /tmp/bone-ui-smoke-<ts>/。
 */
import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const BASE = process.env.SHELL_BASE || 'http://localhost:3000';
const KEYWORD = process.env.SMOKE_KEYWORD || 'E2E-POLICY-0001';
const TS = new Date().toISOString().replace(/[:.]/g, '-');
const OUT = join(tmpdir(), `bone-ui-smoke-${TS}`);
mkdirSync(OUT, { recursive: true });

const results = [];
const record = (name, ok, detail = '') => {
  results.push({ name, ok, detail });
  console.log(`${ok ? '✓' : '✗'} ${name}${detail ? ' — ' + detail : ''}`);
};

const consoleErrors = [];
const pageErrors = [];
const failedRequests = [];
const apiResponses = []; // {url,status}

const main = async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    locale: 'zh-CN',
  });
  const page = await context.newPage();

  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push(m.text());
  });
  page.on('pageerror', (e) => pageErrors.push(String(e)));
  page.on('requestfailed', (r) => {
    const u = r.url();
    // 忽略明显非业务的外部资源失败（字体/图标 CDN 等）
    if (/\/api\//.test(u) || /localhost/.test(u)) {
      failedRequests.push(`${r.method()} ${u} :: ${r.failure()?.errorText}`);
    }
  });
  page.on('response', (r) => {
    const u = r.url();
    if (/\/api\/v1\//.test(u)) apiResponses.push({ url: u, status: r.status() });
  });

  let claimNoSeen = '';
  let claimIdSeen = '';
  try {
    // ── 1. 打开 shell 并真实登录 ─────────────────────────────
    await page.goto(BASE + '/', { waitUntil: 'domcontentloaded' });
    const loginInput = page.locator('input[placeholder="请输入用户名"]');
    // 注意：isVisible() 不自动等待，domcontentloaded 时 React 尚未挂载 → 必须 waitFor
    const needLogin = await loginInput
      .waitFor({ timeout: 20000 })
      .then(() => true)
      .catch(() => false);
    record('登录页渲染（含用户名输入框）', needLogin);

    if (needLogin) {
      await loginInput.fill('admin');
      await page.locator('input[placeholder="请输入密码"]').fill('123456');
      await page.locator('button.login-button').click();
    }
    await page.waitForFunction(() => !!localStorage.getItem('token'), null, { timeout: 20000 });
    const token = await page.evaluate(() => localStorage.getItem('token'));
    record('真实登录成功（token 落地 localStorage）', !!token && token.split('.').length === 3,
      token ? `JWT 段数=${token.split('.').length}` : 'no token');

    // ── 2. 我的工作台 ────────────────────────────────────────
    await page.goto(BASE + '/claim#/workbench', { waitUntil: 'domcontentloaded' });
    await page.locator('text=我的工作台').first().waitFor({ timeout: 30000 });
    record('工作台渲染：标题「我的工作台」', true);

    // 等后端计数落定（page/reviewTasks/summary 并发）
    await page.waitForTimeout(2500);
    const bodyRendered = (await page.locator('text=为什么我看到这些卡片？').count()) > 0;
    record('工作台正文渲染（画像说明卡片）', bodyRendered);
    await page.screenshot({ path: join(OUT, '01-workbench.png'), fullPage: true });

    const countResponses = apiResponses.filter(
      (r) => /\/api\/v1\/claims(\?|\/search)/.test(r.url) || /\/api\/v1\/review\/tasks/.test(r.url),
    );
    const countOk = countResponses.some((r) => r.status === 200);
    record('工作台计数真实拉取后端（claims/review ≥1 个 200）', countOk,
      countResponses.map((r) => `${r.status} ${new URL(r.url).pathname}${new URL(r.url).search}`).slice(0, 3).join(' | '));

    // 页面无「—」全空：至少一个指标卡出现数字（真实数据）
    const numericMetric = await page.evaluate(() => {
      const strongs = Array.from(document.querySelectorAll('.ant-card-extra strong, .ant-typography strong'));
      return strongs.some((el) => /^\d+$/.test((el.textContent || '').trim()));
    });
    record('工作台指标卡显示真实数字（非全部「—」）', numericMetric);

    // ── 3. 跨键检索 ──────────────────────────────────────────
    const searchRespPromise = page
      .waitForResponse((r) => r.url().includes('/api/v1/claims/search') && r.status() === 200, { timeout: 15000 })
      .catch(() => null);
    const searchInput = page.locator(
      'input[placeholder="输入案件 ID 或 报案号/保单号/发票号/姓名/手机"]',
    );
    await searchInput.fill(KEYWORD);
    // antd 按钮文案可能被注入空格，统一用角色+正则容忍空格
    await page.getByRole('button', { name: /打开/ }).first().click();
    const searchResp = await searchRespPromise;
    record('跨键检索命中后端 /api/v1/claims/search（200）', !!searchResp);

    if (searchResp) {
      const body = await searchResp.json().catch(() => null);
      const hits = body?.data ?? [];
      record('检索返回真实案件命中', Array.isArray(hits) && hits.length > 0,
        `hits=${Array.isArray(hits) ? hits.length : 'n/a'}`);
      if (Array.isArray(hits) && hits.length > 0) {
        claimNoSeen = String(hits[0].claimNo || hits[0].claimId || '');
        claimIdSeen = String(hits[0].claimId ?? '');
      }
    }

    // 结果按钮出现（文案为报案号 CL… 或「案件 {id}」）
    const resultBtn = page.getByRole('button', { name: /CL\d{6,}|案件 \d/ }).first();
    const resultVisible = await resultBtn
      .waitFor({ timeout: 12000 })
      .then(() => true)
      .catch(() => false);
    record('工作台渲染检索结果按钮', resultVisible, claimNoSeen);
    await page.screenshot({ path: join(OUT, '02-search.png'), fullPage: true });

    // ── 4. 进入案件 360 ─────────────────────────────────────
    if (resultVisible) {
      await resultBtn.click();
      await page.waitForURL(/\/claim#\/case\?claimId=/, { timeout: 15000 }).catch(() => {});
      const onCase = /\/claim#\/case\?claimId=/.test(page.url());
      record('点击结果跳转案件 360（URL 含 claimId）', onCase, page.url());
      await page.waitForTimeout(1500);
      const hasCaseTitle = (await page.locator('text=案件 360').count()) > 0 || (await page.locator('text=案件详情').count()) > 0;
      record('案件 360 渲染标题', hasCaseTitle);
      const claimIdInUrl = page.url().match(/claimId=(\d+)/)?.[1] ?? '';
      // 逐字比对后端检索返回的雪花 ID：只判长度会放过 Number() 造成的末位丢失
      // （Number('776102367333449728') 仍是 18 位，但末两位已被悄悄改成 00）。
      record(
        '18 位雪花 ID 逐字不截断（URL = 检索返回值，无 Number 化）',
        claimIdInUrl.length >= 15 && claimIdSeen !== '' && claimIdInUrl === claimIdSeen,
        `URL=${claimIdInUrl} / 后端=${claimIdSeen}`,
      );
      await page.screenshot({ path: join(OUT, '03-case360.png'), fullPage: true });

      // ── 5. AI 结论卡 ─────────────────────────────────────
      const aiCardExists = await page
        .getByText('AI 结论卡')
        .first()
        .waitFor({ timeout: 10000 })
        .then(() => true)
        .catch(() => false);
      record('案件 360 渲染 AI 结论卡区域', aiCardExists);
      if (aiCardExists) {
        // 两字按钮被 antd 注入空格（采 纳 / 确 定），正则容忍
        const adoptBtn = page.getByRole('button', { name: /^采\s*纳$/ }).first();
        const adoptVisible = await adoptBtn
          .waitFor({ timeout: 4000 })
          .then(() => true)
          .catch(() => false);
        if (adoptVisible) {
          await adoptBtn.click();
          const okBtn = page
            .locator('.ant-modal-footer button.ant-btn-primary')
            .first();
          await okBtn.waitFor({ timeout: 8000 }).catch(() => {});
          await okBtn.click().catch(() => {});
          const ok = await page
            .getByText(/裁决事件已落库/)
            .first()
            .waitFor({ timeout: 12000 })
            .then(() => true)
            .catch(() => false);
          record('AI 卡「采纳」经 UI 落库（出现落库成功提示）', ok);
        } else {
          const failClosed = await page
            .getByText(/当前不提供采纳|暂无可判定的 AI 参与事实/)
            .first()
            .waitFor({ timeout: 5000 })
            .then(() => true)
            .catch(() => false);
          record('AI 卡 fail-closed 明确呈现（无裁决动作 + 解释文案）', failClosed);
          const decisionBtns = await page
            .getByRole('button', { name: /^(采\s*纳|驳\s*回|修\s*改)$/ })
            .count();
          record('非 AI_ASSISTED 案件不出现采纳/驳回/修改动作', decisionBtns === 0,
            `实际 ${decisionBtns} 个`);
        }
        await page.screenshot({ path: join(OUT, '04-ai-card.png'), fullPage: true });
      }

      // ── 5b. 六个作业 Tab 内嵌：不跳离案件、不再手输 ID、不出现双标题 ──
      const EMBEDDED_TABS = [
        '材料与 OCR', '理算归因', '资金与结算', '申诉复议', '挂起 / 恢复', '后督质检',
      ];
      for (const label of EMBEDDED_TABS) {
        const tabBtn = page.getByRole('tab', { name: label }).first();
        const clicked = await tabBtn.click().then(() => true).catch(() => false);
        await page.waitForTimeout(500);
        const stillCase = /\/claim#\/case\?claimId=/.test(page.url());
        const idInputCount = await page.locator('input[placeholder="案件ID"]').count();
        const embeddedCount = await page.locator('[data-case-content="true"]').count();
        const titleCount = await page.locator('h4.ant-typography').count();
        record(
          `[${label}] 页内作业：停留案件视图 / 无 ID 输入 / 内容内嵌 / 无重复标题`,
          clicked && stillCase && idInputCount === 0 && embeddedCount > 0 && titleCount === 1,
          `clicked=${clicked} url=${stillCase} idInput=${idInputCount} embedded=${embeddedCount} title=${titleCount}`,
        );
      }
      await page.screenshot({ path: join(OUT, '05-case360-embedded-tab.png'), fullPage: true });

      // ── 5c. 深链分享、前进后退、旧路由与返回案件 360 ─────────────
      await page.getByRole('tab', { name: '概览' }).first().click().catch(() => {});
      await page.waitForTimeout(300);
      await page.getByRole('tab', { name: '材料与 OCR' }).first().click().catch(() => {});
      await page.waitForTimeout(400);
      record('Tab 切换写入 URL（tab=material 可分享）', /tab=material/.test(page.url()), page.url());
      await page.goBack().catch(() => {});
      await page.waitForTimeout(500);
      const backTab = (await page.locator('.ant-tabs-tab-active').first().innerText().catch(() => '')).trim();
      record('浏览器后退恢复上一个视图（概览）',
        /概览/.test(backTab) && !/tab=material/.test(page.url()), `${backTab} | ${page.url()}`);
      await page.goForward().catch(() => {});
      await page.waitForTimeout(500);
      const forwardTab = (await page.locator('.ant-tabs-tab-active').first().innerText().catch(() => '')).trim();
      record('浏览器前进恢复材料视图',
        /材料与 OCR/.test(forwardTab) && /tab=material/.test(page.url()), `${forwardTab} | ${page.url()}`);

      await page.goto(BASE + `/claim#/case?claimId=${claimIdInUrl}&tab=fund`, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1500);
      const fundTab = (await page.locator('.ant-tabs-tab-active').first().innerText().catch(() => '')).trim();
      const fundIdInput = await page.locator('input[placeholder="案件ID"]').count();
      record('深链分享：带 tab=fund 直接落在资金与结算视图', /资金与结算/.test(fundTab), fundTab);
      record('深链直达的内嵌视图同样无需手输 ID', fundIdInput === 0, `idInput=${fundIdInput}`);

      await page.goto(BASE + `/claim#/evidence?claimId=${claimIdInUrl}`, { waitUntil: 'domcontentloaded' });
      await page.locator('h4.ant-typography').first().waitFor({ timeout: 20000 }).catch(() => {});
      const oldTitle = (await page.locator('h4.ant-typography').first().innerText().catch(() => '')).trim();
      const oldIdInput = await page.locator('input[placeholder="案件ID"]').count();
      record('旧作业路由带 claimId 直达不 404（标题 + 单个 ID 条）',
        oldTitle === '材料上传' && oldIdInput === 1, `title=${oldTitle} idInput=${oldIdInput}`);
      const backToCase = page.getByRole('button', { name: /案件 360/ }).first();
      const backVisible = await backToCase.waitFor({ timeout: 5000 }).then(() => true).catch(() => false);
      if (backVisible) {
        await backToCase.click();
        await page.waitForTimeout(800);
        record('从旧作业页回到同一 claimId 的 material 视图',
          page.url().includes(`claimId=${claimIdInUrl}`) && /tab=material/.test(page.url()), page.url());
      } else {
        record('从旧作业页回到同一 claimId 的 material 视图', false, '未找到「案件 360」动作');
      }
    } else {
      record('跳过案件 360/AI 卡（检索无结果按钮）', false, 'no result button');
    }
  } catch (e) {
    record('执行异常', false, String(e?.message || e));
  }

  // ── 6. 运行时健康 ────────────────────────────────────────
  const api5xx = apiResponses.filter((r) => r.status >= 500);
  record('无 5xx 业务接口响应', api5xx.length === 0,
    api5xx.map((r) => `${r.status} ${new URL(r.url).pathname}`).slice(0, 4).join(' | '));
  record('无 pageerror（未捕获 JS 异常）', pageErrors.length === 0, pageErrors.slice(0, 3).join(' | '));
  record('无 API 请求失败（网络层）', failedRequests.length === 0, failedRequests.slice(0, 3).join(' | '));

  // 关键业务接口 200 汇总
  const keyApiOk = apiResponses.some((r) => /\/api\/v1\/claims/.test(r.url) && r.status === 200);
  record('关键业务接口 /api/v1/claims 出现 200', keyApiOk);

  await browser.close();

  const passed = results.filter((r) => r.ok).length;
  const total = results.length;
  const report = {
    base: BASE,
    keyword: KEYWORD,
    outDir: OUT,
    passed,
    total,
    ok: passed === total,
    results,
    consoleErrors,
    pageErrors,
    failedRequests,
    apiResponses,
  };
  writeFileSync(join(OUT, 'report.json'), JSON.stringify(report, null, 2));
  console.log(`\n智能理赔 UI 冒烟：通过 ${passed} / ${total}`);
  console.log(`产物目录：${OUT}`);
  if (consoleErrors.length) {
    console.log(`\n[console.error ${consoleErrors.length} 条，前 5 条]`);
    consoleErrors.slice(0, 5).forEach((e) => console.log('  - ' + e.slice(0, 200)));
  }
  process.exit(passed === total ? 0 : 1);
};

main().catch((e) => {
  console.error('FATAL', e);
  process.exit(2);
});
