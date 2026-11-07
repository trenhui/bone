#!/usr/bin/env node
/**
 * synapClaim 前后端端到端测试（真实服务栈，非 mock）
 *
 * 前置（仓库内常驻脚本，勿用 /tmp 临时副本）：
 *   bash bone-frontend/e2e/start-services.sh &   # 构建 jar（含网关路由）+ 起后端
 *   bash bone-frontend/e2e/start-frontend.sh &   # 起 shell 与理赔子应用
 *   MySQL(3306) + IAM(8081) + 网关(8888) + synap-claim(8087) + shell(3000) + claim-app(3013)
 * 运行：
 *   node bone-frontend/e2e/synap-claim-e2e.cjs
 *
 * 断言分两组：
 *   A. API 链路（全部经网关 8888，真实 JWT、真实 DB）：登录 / 赔案列表 / AI 就绪清单 /
 *      运行期健康度 / 校准报表，外加负向探针（无 token 401、超 31 天窗口 400、
 *      minSamples=1 不得绕过治理下限产出阈值建议）。
 *   B. 浏览器 UI（Playwright Chromium）：shell 登录 → qiankun 挂载理赔子应用 → 核赔工作台
 *      真实渲染 → hidden 路由 /ai-governance 五个治理区块渲染 → 防假绿文本断言
 *      （未达标 / 模型未参与 / 影子模式）→ 参与率与同窗口 API 独立对账 → 截图。
 *   C. 循环收敛对账：静态 L3 就绪度复核器 vs 运行期 AI 就绪清单，任一端单独翻绿即判红。
 *
 * 产物 outputs/claims-e2e-result.json 的 runtime 段是 L3 就绪度复核器的运行期证据源：
 * 复核器不再只靠静态文件判断，而是消费这里的 participationRate / aiParticipated。
 * 刻意不 mock 任何响应：任何一层不通即失败，并保留截图与 console 错误供定位。
 */
const { chromium, request } = require('playwright');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOT = path.resolve(__dirname, '../..');
const OUT = path.join(ROOT, 'outputs');
const GATEWAY = 'http://127.0.0.1:8888';
const SHELL = 'http://localhost:3000';

/** 证据窗口单一真源：与 aiGovernance.ts 的 governanceWindow() 完全同算法（UTC 日界 30 天、秒级 ISO）。 */
const DAY_MS = 24 * 60 * 60 * 1000;
const isoSecond = (ms) => new Date(ms).toISOString().replace(/\.\d{3}Z$/, 'Z');
const EVIDENCE_WINDOW = (() => {
  const toMs = Math.ceil(Date.now() / DAY_MS) * DAY_MS;
  return {
    from: isoSecond(toMs - 30 * DAY_MS),
    to: isoSecond(toMs),
  };
})();

const result = {
  startedAt: new Date().toISOString(),
  mode: 'real-services-no-mock',
  api: [],
  ui: {},
  runtime: null,
  consoleErrors: [],
  passed: false,
};

function check(name, condition, detail) {
  const row = { name, pass: !!condition, detail };
  result.api.push(row);
  console.log(`  [${row.pass ? 'PASS' : 'FAIL'}] ${name} :: ${String(detail).slice(0, 150)}`);
  return row.pass;
}

(async () => {
  const ctx = await request.newContext({ baseURL: GATEWAY });
  console.log('== A. API 链路（经网关 8888）==');

  const login = await ctx.post('/api/v1/iam/login', {
    data: { username: 'admin', password: '123456' },
  });
  const loginBody = await login.json();
  const token = loginBody?.data?.token;
  check('登录经网关 200 且签发 JWT', login.status() === 200 && loginBody?.success === true && !!token,
    `status=${login.status()} tokenLen=${token ? token.length : 0}`);

  const auth = { Authorization: `Bearer ${token}` };
  const claims = await ctx.get('/api/v1/claims/', { headers: auth });
  const claimsBody = await claims.json();
  check('赔案列表真实读库 200', claims.status() === 200 && Array.isArray(claimsBody?.data?.records),
    `status=${claims.status()} records=${claimsBody?.data?.records?.length ?? 'n/a'}`);

  const readiness = await ctx.get('/api/v1/claim-ops/ai-readiness', { headers: auth });
  const readyBody = await readiness.json();
  check('AI 能力就绪清单经网关可达', readiness.status() === 200 && readyBody?.data?.capabilities?.length === 5,
    `status=${readiness.status()} gatesPassed=${readyBody?.data?.deploymentGatesPassed}`);
  check('就绪清单如实报未通过（防假绿）', readyBody?.data?.deploymentGatesPassed === false,
    `blockers=${readyBody?.data?.blockers?.length ?? 'n/a'}`);

  const from = EVIDENCE_WINDOW.from;
  const to = EVIDENCE_WINDOW.to;
  const health = await ctx.get('/api/v1/claim-ops/ai-runtime-health', {
    headers: auth, params: { from, to },
  });
  const healthBody = await health.json();
  const d = healthBody?.data || {};
  check('运行期健康度 UTC 日界 30 天窗口 200',
    health.status() === 200 && typeof d.participationRate === 'number',
    `status=${health.status()} nodes=${d.nodesScanned} window=${from} ~ ${to}`);
  check('证据窗口同源：后端回显与 EVIDENCE_WINDOW 逐字一致',
    d.windowFrom === from && d.windowTo === to,
    `backend=${d.windowFrom} ~ ${d.windowTo} expected=${from} ~ ${to}`);
  result.runtime = {
    windowFrom: d.windowFrom,
    windowTo: d.windowTo,
    evidenceWindowFrom: from,
    evidenceWindowTo: to,
    nodesScanned: d.nodesScanned ?? null,
    aiParticipated: d.aiParticipated ?? null,
    aiDegraded: d.aiDegraded ?? null,
    participationRate: d.participationRate ?? null,
    capabilityMissing: d.capabilityMissing ?? null,
    capturedAt: new Date().toISOString(),
  };
  console.log(`  [EVIDENCE] participationRate=${d.participationRate} aiParticipated=${d.aiParticipated} `
    + `nodesScanned=${d.nodesScanned} capabilityMissing=${d.capabilityMissing}`);

  const noAuth = await ctx.get('/api/v1/claims/');
  check('负向探针：无 token 被网关拦截 401', noAuth.status() === 401, `status=${noAuth.status()}`);

  const tooWide = await ctx.get(
    '/api/v1/claim-ops/ai-runtime-health?from=2026-10-01T00:00:00Z&to=2026-11-05T00:00:00Z',
    { headers: auth });
  const wideBody = await tooWide.json();
  check('负向探针：超过 31 天窗口被业务校验拒绝 400',
    tooWide.status() === 400 && /AI_RUNTIME_HEALTH_QUERY_INVALID/.test(wideBody?.message || ''),
    `status=${tooWide.status()} msg=${wideBody?.message}`);

  const nullish = (v) => v === null || v === undefined;
  const calibration = await ctx.get('/api/v1/claim-ops/ai-calibration/report', { headers: auth });
  const calibBody = await calibration.json();
  const cal = calibBody?.data?.calibration || {};
  // verdict 必须落在受控枚举内（复核器口径一致）：无论有无样本都不允许编造数值或自由文本。
  const CALIB_VERDICTS = ['NO_EVIDENCE', 'UNTRUSTED_EVIDENCE', 'INSUFFICIENT_SAMPLES',
    'SLICE_INCOMPLETE', 'NO_SAFE_THRESHOLD', 'THRESHOLD_PROPOSED'];
  check('校准报表端点经网关可达且 verdict 为受控枚举（部署态与源码一致）',
    calibration.status() === 200 && !!calibBody?.data && CALIB_VERDICTS.includes(cal.verdict),
    `status=${calibration.status()} verdict=${cal.verdict}`);
  check('无盲标样本时 NO_EVIDENCE 且 ECE/Brier/建议阈值全为 null（不编造数值）',
    cal.verdict === 'NO_EVIDENCE' && nullish(cal.expectedCalibrationError)
      && nullish(cal.brierScore) && nullish(cal.recommendedThreshold),
    `verdict=${cal.verdict} ece=${cal.expectedCalibrationError} brier=${cal.brierScore} proposed=${cal.recommendedThreshold}`);

  const bypass = await ctx.get(
    '/api/v1/claim-ops/ai-calibration/report?minSamples=1&minSamplesPerSlice=1',
    { headers: auth });
  const bypassBody = await bypass.json();
  const bcal = bypassBody?.data?.calibration || {};
  check('负向探针：minSamples=1 时生效下限仍为 300/30（守卫可观测、不可绕过）',
    bypass.status() === 200 && bypassBody?.data?.effectiveMinSamples === 300
      && bypassBody?.data?.effectiveMinSamplesPerSlice === 30
      && bcal.verdict !== 'THRESHOLD_PROPOSED' && nullish(bcal.recommendedThreshold),
    `effective=${bypassBody?.data?.effectiveMinSamples}/${bypassBody?.data?.effectiveMinSamplesPerSlice} verdict=${bcal.verdict}`);

  const raised = await ctx.get(
    '/api/v1/claim-ops/ai-calibration/report?minSamples=500&minSamplesPerSlice=50',
    { headers: auth });
  const raisedBody = await raised.json();
  check('正向对照：显式上调到 500/50 时生效值如实回传（只允许上调、不放宽）',
    raised.status() === 200 && raisedBody?.data?.effectiveMinSamples === 500
      && raisedBody?.data?.effectiveMinSamplesPerSlice === 50,
    `effective=${raisedBody?.data?.effectiveMinSamples}/${raisedBody?.data?.effectiveMinSamplesPerSlice}`);
  if (result.runtime) {
    result.runtime.calibrationVerdict = {
      baseline: cal.verdict ?? null,
      withMinSamplesOne: bcal.verdict ?? null,
      effectiveFloorsAtBypass: [bypassBody?.data?.effectiveMinSamples ?? null,
        bypassBody?.data?.effectiveMinSamplesPerSlice ?? null],
      guardHeld: bypassBody?.data?.effectiveMinSamples === 300
        && bcal.verdict !== 'THRESHOLD_PROPOSED' && nullish(bcal.recommendedThreshold),
    };
  }

  const rolloutWindow = '?from=2026-10-07T00:00:00Z&to=2026-11-06T00:00:00Z';
  const beforeRollout = await ctx.get('/api/v1/claim-ops/ai-rollout' + rolloutWindow,
    { headers: auth });
  const beforeStage = (await beforeRollout.json())?.data?.stage ?? null;
  const drill = await ctx.get('/api/v1/claim-ops/ai-rollout/drill', { headers: auth });
  const drillBody = await drill.json();
  const dr = drillBody?.data || {};
  check('回滚演练只读端点可达且报告结构完整（dry-run）',
    drill.status() === 200 && !!dr.currentStage && !!dr.rollbackTargetStage
      && dr.integrityIntact === true && dr.mutations === 0
      && Array.isArray(dr.forbiddenMutations) && dr.forbiddenMutations.length >= 5,
    `current=${dr.currentStage} target=${dr.rollbackTargetStage} mutations=${dr.mutations}`);

  const afterRollout = await ctx.get('/api/v1/claim-ops/ai-rollout' + rolloutWindow,
    { headers: auth });
  const afterStage = (await afterRollout.json())?.data?.stage ?? null;
  check('演练零副作用：演练前后生效档位逐位不变（state integrity）',
    beforeRollout.status() === 200 && afterRollout.status() === 200
      && beforeStage !== null && beforeStage === afterStage,
    `before=${beforeStage} after=${afterStage}`);

  check('报告口径自洽：atFloor 与当前档一致，且档位与放量快照同源',
    (dr.currentStage === 'SHADOW' ? dr.atFloor === true : dr.atFloor === false)
      && dr.currentStage === beforeStage,
    `current=${dr.currentStage} before=${beforeStage} atFloor=${dr.atFloor}`);
  if (result.runtime) {
    result.runtime.rollbackDrill = {
      currentStage: dr.currentStage ?? null,
      rollbackTargetStage: dr.rollbackTargetStage ?? null,
      integrityIntact: dr.integrityIntact ?? null,
      mutations: dr.mutations ?? null,
      stageUnchanged: beforeStage !== null && beforeStage === afterStage,
    };
  }

  console.log('== C. 循环收敛对账（静态判据 vs 运行期就绪清单）==');
  // 复核器读取磁盘产物：先落盘本轮 runtime，避免对账用到上一轮 E2E 的过期证据。
  // 末尾会再写一次完整结果（无 provisional 标记），本处只是中途快照。
  fs.writeFileSync(
    path.join(OUT, 'claims-e2e-result.json'),
    JSON.stringify({ ...result, provisional: true }, null, 2));
  const checker = path.join(ROOT, 'doc/ai-product/synapClaim/tools/_ai_native_readiness.py');
  let checkerOut = '';
  try {
    checkerOut = execFileSync('python3', [checker], { encoding: 'utf8', timeout: 30000 });
  } catch (e) {
    checkerOut = String(e.stdout || '');
  }
  const claimMatch = /l3Claimable\s*=\s*(true|false)/.exec(checkerOut);
  const countMatch = /判据\s*(\d+)\s*项：SATISFIED\s*(\d+)\s*\/\s*BLOCKED\s*(\d+)\s*\/\s*RUNTIME_ONLY\s*(\d+)/.exec(checkerOut);
  check('L3 就绪度复核器可运行且输出可解析', !!claimMatch && !!countMatch,
    `l3Claimable=${claimMatch ? claimMatch[1] : 'n/a'} counts=${countMatch ? countMatch[0] : 'n/a'}`);

  const staticNotReady = !!claimMatch && claimMatch[1] === 'false';
  const runtimeNotReady = readyBody?.data?.deploymentGatesPassed === false;
  check('静态判据与运行期就绪清单结论一致（防单端假绿）',
    staticNotReady && staticNotReady === runtimeNotReady,
    `static=${claimMatch ? claimMatch[1] : 'n/a'} runtime=${readyBody?.data?.deploymentGatesPassed}`);

  const forged = checkerOut.replace('l3Claimable = false', 'l3Claimable = true');
  const forgedClaim = /l3Claimable\s*=\s*(true|false)/.exec(forged)?.[1];
  check('负向自检：静态判据被伪造成可宣称 L3 时会对账失败（判据有判别力）',
    forgedClaim === 'true' && forgedClaim !== (runtimeNotReady ? 'false' : 'true'),
    `forged=${forgedClaim}`);

  if (result.runtime) {
    result.runtime.readinessSnapshot = {
      l3Claimable: claimMatch ? claimMatch[1] : null,
      gates: countMatch ? {
        total: Number(countMatch[1]), satisfied: Number(countMatch[2]),
        blocked: Number(countMatch[3]), runtimeOnly: Number(countMatch[4]),
      } : null,
      alignedWithRuntime: staticNotReady === runtimeNotReady,
      blockedGates: (checkerOut.match(/^  BLOCKED\s+(\S+)/gm) || [])
        .map((ln) => ln.trim().split(/\s+/)[1]).filter(Boolean),
    };
  }

  console.log('== B. 浏览器 UI（Playwright Chromium）==');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on('console', (m) => { if (m.type() === 'error') result.consoleErrors.push(m.text().slice(0, 220)); });
  page.on('pageerror', (e) => result.consoleErrors.push(`pageerror: ${String(e).slice(0, 220)}`));
  const badResponses = [];
  let providerProbeRequests = 0;
  page.on('response', (r) => {
    if (r.status() >= 500) badResponses.push({ status: r.status(), url: r.url().slice(0, 180) });
  });
  page.on('request', (r) => {
    if (r.url().includes('/ai-provider-probe')) providerProbeRequests += 1;
  });

  try {
    await page.goto(SHELL, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 15000 });
    await page.fill('input[placeholder="请输入用户名"]', 'admin');
    await page.fill('input[placeholder="请输入密码"]', '123456');
    await page.click('button.login-button');
    await page.waitForFunction(() => !!localStorage.getItem('token'), { timeout: 20000 });
    result.ui.loggedIn = true;
    await page.screenshot({ path: path.join(OUT, 'claims-e2e-shell.png'), fullPage: false });
    check('浏览器登录成功并落地 token', result.ui.loggedIn, 'localStorage.token 已写入');

    await page.goto(`${SHELL}/claim/review`, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(4000);
    const viewportText = await page.locator('#subapp-viewport').innerText();
    result.ui.url = page.url();
    result.ui.viewportExcerpt = viewportText.replace(/\s+/g, ' ').slice(0, 260);
    const markers = ['核赔工作台', '报案号', '待领取', '决策图'];
    const hits = markers.filter((k) => viewportText.includes(k));
    result.ui.markersHit = hits;
    check('qiankun 子应用容器真实渲染核赔工作台（4 个主体标记命中 >=3）',
      hits.length >= 3, `url=${result.ui.url} hits=${hits.join('/')} excerpt=${result.ui.viewportExcerpt.slice(0, 90)}`);
    await page.screenshot({ path: path.join(OUT, 'claims-e2e-claim.png'), fullPage: false });

    // hidden 路由 /ai-governance 的可达性必须由业务页面深链按钮保证：
    // 真实点击核赔工作台的「AI 治理」，而不是绕过 UI 直接改 location.hash。
    const govNavButton = page.locator('#subapp-viewport button', { hasText: 'AI 治理' }).first();
    const govNavExists = await govNavButton.count() > 0;
    if (govNavExists) await govNavButton.click();
    await page.waitForTimeout(4000);
    result.ui.aiGovernanceDeepLink = {
      buttonFound: govNavExists,
      urlAfterClick: page.url(),
    };
    check('业务页面深链按钮可点击进入 hidden 路由 /ai-governance',
      govNavExists && page.url().includes('#/ai-governance'),
      `buttonFound=${govNavExists} url=${page.url()}`);
    // 负向自检：仅靠改 hash 也能到达（回退路径），但不得把「未点击」误判为深链可用。
    if (!govNavExists) {
      await page.evaluate(() => { window.location.hash = '#/ai-governance'; });
      await page.waitForTimeout(2000);
    }
    const govText = await page.locator('#subapp-viewport').innerText();
    const govMarkers = ['AI 治理', '能力就绪', '运行期模型参与', '独立盲标校准', '放量档位', '回滚演练'];
    const govHits = govMarkers.filter((k) => govText.includes(k));
    result.ui.aiGovernance = {
      url: page.url(),
      excerpt: govText.replace(/\s+/g, ' ').slice(0, 2500),
      markersHit: govHits,
    };
    await page.screenshot({ path: path.join(OUT, 'claims-e2e-ai-governance.png'), fullPage: false });
    check('hidden 路由 /ai-governance 真实渲染 5 个治理区块（6 标记命中 >=5）',
      govHits.length >= 5,
      `url=${result.ui.aiGovernance.url} hits=${govHits.join('/')} excerpt=${result.ui.aiGovernance.excerpt.slice(0, 100)}`);

    // 探活端点每次真实消耗 provider 用量：页面加载与刷新都不得自动请求。
    result.ui.aiGovernance.probeRequestsOnLoad = providerProbeRequests;
    check('provider 探活默认零请求（禁自动轮询）',
      providerProbeRequests === 0 && govText.includes('未测过'),
      `requests=${providerProbeRequests} hasNotTested=${govText.includes('未测过')}`);

    // 校准报表当前为 NO_EVIDENCE：不得编造分桶/切片曲线。
    const calibVerdict = cal?.verdict;
    const calibrationForbidden = ['NO_EVIDENCE', 'UNTRUSTED_EVIDENCE', 'INSUFFICIENT_SAMPLES',
      'SLICE_INCOMPLETE', 'NO_SAFE_THRESHOLD'].includes(calibVerdict);
    const bucketTableRendered = govText.includes('校准分桶明细');
    check('无盲标证据时不渲染校准分桶/切片表格（不编造曲线）',
      !calibrationForbidden || !bucketTableRendered,
      `verdict=${calibVerdict} bucketTableRendered=${bucketTableRendered}`);

    const probeButton = page.locator('#subapp-viewport button', { hasText: 'provider 连通性探活' }).first();
    result.runtime = result.runtime ?? {};
    result.runtime.probeEvidence = null;
    if (await probeButton.count() > 0) {
      // 复用同一次手动点击的响应，不额外发请求（探活每次真实消耗 provider 用量）。
      const probeResponse = page.waitForResponse(
        (r) => r.url().includes('/ai-provider-probe'), { timeout: 10000 });
      await probeButton.click();
      try {
        const probeResp = await probeResponse;
        const probeData = (await probeResp.json())?.data ?? {};
        result.runtime.probeVerdict = probeData.verdict ?? null;
        result.runtime.probeVerdictCapturedAt = new Date().toISOString();
        // 证据束只从同一次真实探活响应透传。采集侧尚未产出的字段一律 null，
        // 由复核器按 PROBE_EVIDENCE_* 阻断；不编造 token / requestId，也不回落到裸判词。
        result.runtime.probeEvidence = probeData.verdict === 'REACHABLE'
          ? {
            httpStatus: probeResp.status(),
            model: probeData.model ?? null,
            provenance: probeData.provenance ?? null,
            promptTokens: probeData.promptTokens ?? null,
            completionTokens: probeData.completionTokens ?? null,
            requestId: probeData.requestId ?? null,
          }
          : null;
      } catch {
        result.runtime.probeVerdict = null;
        result.runtime.probeEvidence = null;
      }
      await page.waitForTimeout(2500);
    }
    check('探活响应判词写入运行期证据（不可读即判红，不编造 REACHABLE）',
      typeof result.runtime?.probeVerdict === 'string' && result.runtime.probeVerdict.length > 0,
      `verdict=${result.runtime?.probeVerdict ?? 'MISSING'}`);
    check('探活证据束随产物落盘且未连通时不得编造（防裸判词假绿）',
      Object.prototype.hasOwnProperty.call(result.runtime ?? {}, 'probeEvidence')
        && (result.runtime.probeVerdict === 'REACHABLE' || result.runtime.probeEvidence === null),
      `verdict=${result.runtime?.probeVerdict ?? 'MISSING'} `
        + `evidence=${JSON.stringify(result.runtime?.probeEvidence ?? null)}`);
    result.ui.aiGovernance.probeRequestsAfterClick = providerProbeRequests;
    check('点击探活按钮后恰好发起 1 次真实探活请求',
      providerProbeRequests === 1,
      `requests=${providerProbeRequests}`);
    const probeText = await page.locator('#subapp-viewport').innerText();
    check('探活结果展示受控判词，不把 NOT_CONFIGURED 写成连通',
      /未配置真实 provider|链路可达|未登记判词|未测过/.test(probeText),
      `excerpt=${probeText.replace(/\s+/g, ' ').slice(0, 140)}`);

    // 参与率期望值由 E2E 侧独立重算，且必须使用页面声明的统计窗口；
    // 同时断言该窗口与 EVIDENCE_WINDOW（同算法 UTC 日界）逐字一致，防止两端口径分裂。
    const winMatch = /统计窗口：([^\s]+)\s*~\s*([^\s]+)/.exec(govText);
    result.ui.aiGovernance.windowSameSource = !!winMatch
      && winMatch[1] === EVIDENCE_WINDOW.from && winMatch[2] === EVIDENCE_WINDOW.to;
    check('证据窗口单一真源：治理面板声明窗口与 EVIDENCE_WINDOW 逐字一致',
      result.ui.aiGovernance.windowSameSource,
      `panel=${winMatch ? `${winMatch[1]} ~ ${winMatch[2]}` : 'none'} `
        + `expected=${EVIDENCE_WINDOW.from} ~ ${EVIDENCE_WINDOW.to}`);
    const uiHealth = winMatch
      ? await ctx.get('/api/v1/claim-ops/ai-runtime-health', {
          headers: auth,
          params: { from: winMatch[1], to: winMatch[2] },
        })
      : null;
    const uiRate = uiHealth ? (await uiHealth.json())?.data?.participationRate : undefined;
    const expectedParticipation = uiRate === null || uiRate === undefined
      ? '无可计算样本' : uiRate === 0 ? '模型未参与' : `${(uiRate * 100).toFixed(2)}%`;
    result.ui.aiGovernance.expectedParticipation = {
      uiRate, label: expectedParticipation,
      windowDeclared: winMatch ? `${winMatch[1]} ~ ${winMatch[2]}` : null,
    };
    const participationProbe = {
      windowDeclared: !!winMatch,
      status: uiHealth ? uiHealth.status() : 0,
      hasExpected: govText.includes(expectedParticipation),
      forbiddenOnStage: govText.includes('AI 已上场'),
      forbiddenFullRate: govText.includes('100.00%'),
      rendered: /\d+\.\d{2}%|无可计算样本|模型未参与/.exec(govText)?.[0] ?? null,
    };
    result.ui.aiGovernance.participationProbe = participationProbe;
    check('防假绿：参与率展示与同窗口 API 独立重算一致（null/0/百分比三态）',
      participationProbe.windowDeclared && participationProbe.status === 200
        && participationProbe.hasExpected
        && !participationProbe.forbiddenOnStage && !participationProbe.forbiddenFullRate,
      `rate=${uiRate} expected=${expectedParticipation} probe=${JSON.stringify(participationProbe)}`);

    check('防假绿：门禁未通过显示未达标，SHADOW 不写成已自动放款',
      govText.includes('未达标') && !govText.includes('已达标')
        && govText.includes('影子模式') && !govText.includes('已自动放款'),
      `excerpt=${result.ui.aiGovernance.excerpt.slice(0, 120)}`);

    check('默认加载的 5 个治理端点全部返回可用数据（第 6 个探活仅手动触发）',
      !govText.includes('数据不可用'),
      `endpointErrors=${(govText.match(/数据不可用/g) || []).length}`);

    check('负向自检：治理页标记不会被核赔工作台文本命中（判据有判别力）',
      govMarkers.filter((k) => viewportText.includes(k)).length < 5,
      `reviewHits=${govMarkers.filter((k) => viewportText.includes(k)).join('/') || 'none'}`);

    const SHELL_MENU_ONLY = 'BONE 一次构建，处处原生 首页仪表盘 组织与成员 权限与角色 安全与审计 '
      + '业务建模 主数据管理 交易管理 集成管理 扩展管理 代码生成 系统管理 智能理赔 报销系统 BONE 平台控制台';
    const looseWouldPass = /核赔|理赔|工作台|待办|案件/.test(SHELL_MENU_ONLY);
    const strictOnMenu = markers.filter((k) => SHELL_MENU_ONLY.includes(k)).length;
    check('负向自检：旧宽松正则确实会被 shell 菜单骗过（故已废弃）', looseWouldPass,
      `loose=${looseWouldPass}`);
    check('负向自检：新严格判据不把菜单文本当成子应用', strictOnMenu < 3,
      `strictHitsOnMenu=${strictOnMenu}`);

    const chainRe = /\/api\/v1\/(claims|review|claim-ops|claim-config)/;
    const chain5xx = badResponses.filter((r) => chainRe.test(r.url));
    result.ui.chain5xx = chain5xx;
    result.ui.external5xx = badResponses.filter((r) => !chainRe.test(r.url));
    check('理赔链路无 5xx', chain5xx.length === 0,
      `chain5xx=${JSON.stringify(chain5xx.slice(0, 3))} external5xx=${result.ui.external5xx.length}`);
  } catch (e) {
    result.ui.error = String(e).slice(0, 400);
    check('浏览器端到端流程完成', false, result.ui.error);
    try { await page.screenshot({ path: path.join(OUT, 'claims-e2e-failure.png') }); } catch (_) {}
  } finally {
    await browser.close();
  }

  // 终态对账：C 组对账用的是中途快照（当时还没有探活判词）。最终产物多出
  // runtime.probeVerdict / UI 证据，若产物被污染却无人复核就是盲点。此处先落盘
  // 终态证据再跑一次复核器，末尾会再写一次完整结果（无 provisional 标记）。
  fs.writeFileSync(
    path.join(OUT, 'claims-e2e-result.json'),
    JSON.stringify({ ...result, provisional: 'final-readiness-check' }, null, 2));
  let finalCheckerOut = '';
  try {
    finalCheckerOut = execFileSync('python3', [checker], { encoding: 'utf8', timeout: 30000 });
  } catch (e) {
    finalCheckerOut = String(e.stdout || '');
  }
  const finalClaim = /l3Claimable\s*=\s*(true|false)/.exec(finalCheckerOut)?.[1];
  const finalCoverage = /^\s*(BLOCKED|SATISFIED|RUNTIME_ONLY)\s+RUNTIME_MODEL_COVERAGE\s+(\S+)/m
    .exec(finalCheckerOut);
  const verdict = result.runtime?.probeVerdict ?? null;
  check('终态对账：最终产物被复核器消费（输出回显本轮探活判词）',
    !!verdict && finalCheckerOut.includes(`probeVerdict=${verdict}`),
    `verdict=${verdict ?? 'MISSING'}`);
  check('终态对账：判词非 REACHABLE 时 RUNTIME_MODEL_COVERAGE 必须 BLOCKED 且 L3 不可宣称',
    finalClaim === 'false' && !!finalCoverage
      && (verdict === 'REACHABLE' || finalCoverage[1] === 'BLOCKED'),
    `claimable=${finalClaim} coverage=${finalCoverage ? `${finalCoverage[1]}/${finalCoverage[2]}` : 'n/a'}`);
  check('终态对账：复核器回显证据束校验结论（不得只读裸判词）',
    /probeEvidence=(OK|PROBE_EVIDENCE_[A-Z_]+)/.test(finalCheckerOut),
    `probeEvidenceLine=${(finalCheckerOut.match(/probeEvidence=\S+/) ?? ['MISSING'])[0]}`);
  if (result.runtime) {
    result.runtime.finalReadinessSnapshot = {
      probeEvidence: result.runtime.probeEvidence ?? null,
      l3Claimable: finalClaim,
      coverageStatus: finalCoverage ? finalCoverage[1] : null,
      coverageReason: finalCoverage ? finalCoverage[2] : null,
      probeVerdict: verdict,
      probeVerdictCapturedAt: result.runtime.probeVerdictCapturedAt ?? null,
    };
  }

  result.passed = result.api.every((r) => r.pass);
  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, 'claims-e2e-result.json'), JSON.stringify(result, null, 2));
  console.log(`\n== 结果：${result.passed ? 'PASS' : 'FAIL'} `
    + `(${result.api.filter((r) => r.pass).length}/${result.api.length}) ==`);
  console.log(`产物：outputs/claims-e2e-result.json, claims-e2e-shell.png, claims-e2e-claim.png, claims-e2e-ai-governance.png`);
  process.exit(result.passed ? 0 : 1);
})().catch((e) => {
  console.error('E2E runner crashed:', e);
  fs.writeFileSync(path.join(OUT, 'claims-e2e-result.json'), JSON.stringify({ ...result, crashed: String(e) }, null, 2));
  process.exit(1);
});
