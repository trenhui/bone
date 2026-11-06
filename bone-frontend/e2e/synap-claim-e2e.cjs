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
 *      运行期健康度，外加两个负向探针（无 token 401、超 31 天窗口 400）。
 *   B. 浏览器 UI（Playwright Chromium）：shell 登录 → qiankun 挂载理赔子应用 → 核赔工作台
 *      真实渲染 → 截图。
 *
 * 产物 outputs/claims-e2e-result.json 的 runtime 段是 L3 就绪度复核器的运行期证据源：
 * 复核器不再只靠静态文件判断，而是消费这里的 participationRate / aiParticipated。
 * 刻意不 mock 任何响应：任何一层不通即失败，并保留截图与 console 错误供定位。
 */
const { chromium, request } = require('playwright');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '../..');
const OUT = path.join(ROOT, 'outputs');
const GATEWAY = 'http://127.0.0.1:8888';
const SHELL = 'http://localhost:3000';

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

  const from = '2026-10-06T00:00:00Z';
  const to = '2026-11-05T00:00:00Z';
  const health = await ctx.get(`/api/v1/claim-ops/ai-runtime-health?from=${from}&to=${to}`, { headers: auth });
  const healthBody = await health.json();
  const d = healthBody?.data || {};
  check('运行期健康度 31 天窗口 200', health.status() === 200 && typeof d.participationRate === 'number',
    `status=${health.status()} nodes=${d.nodesScanned}`);
  result.runtime = {
    windowFrom: d.windowFrom,
    windowTo: d.windowTo,
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

  console.log('== B. 浏览器 UI（Playwright Chromium）==');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on('console', (m) => { if (m.type() === 'error') result.consoleErrors.push(m.text().slice(0, 220)); });
  page.on('pageerror', (e) => result.consoleErrors.push(`pageerror: ${String(e).slice(0, 220)}`));
  const badResponses = [];
  page.on('response', (r) => {
    if (r.status() >= 500) badResponses.push({ status: r.status(), url: r.url().slice(0, 180) });
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

  result.passed = result.api.every((r) => r.pass);
  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, 'claims-e2e-result.json'), JSON.stringify(result, null, 2));
  console.log(`\n== 结果：${result.passed ? 'PASS' : 'FAIL'} `
    + `(${result.api.filter((r) => r.pass).length}/${result.api.length}) ==`);
  console.log(`产物：outputs/claims-e2e-result.json, claims-e2e-shell.png, claims-e2e-claim.png`);
  process.exit(result.passed ? 0 : 1);
})().catch((e) => {
  console.error('E2E runner crashed:', e);
  fs.writeFileSync(path.join(OUT, 'claims-e2e-result.json'), JSON.stringify({ ...result, crashed: String(e) }, null, 2));
  process.exit(1);
});
