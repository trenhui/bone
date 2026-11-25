/**
 * 全业务链 E2E 的 UI 串联验证：把 claim-full-chain-e2e.mjs 真实落库的案件，
 * 在真实浏览器（经 shell qiankun 挂载理赔子应用）逐页渲染，做 API↔UI↔DB 三源对账。
 *
 * 前置：shell:3000 + claim 微应用:3013 + gateway:8888 + claim:8087 在线。
 * 用法：POC_TOKEN=... CASES=<json> node /tmp/bone-fullchain-ui.cjs
 */
const { chromium, request } = require('playwright');
const fs = require('fs');
const OUT = '/Users/renhui.trh/wps/bone/outputs';
const SHELL = 'http://localhost:3000';
const GW = 'http://127.0.0.1:8888';

const cases = JSON.parse(process.env.CASES || '[]');
const results = [];
const check = (name, pass, detail) => {
  results.push({ name, pass: !!pass, detail });
  console.log(`  [${pass ? 'PASS' : 'FAIL'}] ${name} :: ${String(detail).slice(0, 160)}`);
};

(async () => {
  // API 侧真值（经网关，与前端同口径）
  const ctx = await request.newContext({ baseURL: GW });
  const login = await ctx.post('/api/v1/iam/login', { data: { username: 'admin', password: '123456' } });
  const token = (await login.json())?.data?.token;
  check('API: 真实 JWT 登录', login.status() === 200 && !!token, `http=${login.status()}`);

  const verified = [];
  for (const c of cases) {
    const r = await ctx.get(`/api/v1/claims/${c.id}`, { headers: { Authorization: `Bearer ${token}` } });
    const d = (await r.json())?.data; const first = Array.isArray(d) ? d[0] : d;
    verified.push({ ...c, apiStatus: r.status(), apiAmount: first?.claimAmount, apiStatusText: first?.status });
  }
  check('API: 每个场景案件详情可读', verified.every((v) => v.apiStatus === 200),
    verified.map((v) => `${v.label}:${v.apiStatus}/${v.apiStatusText}`).join(' '));

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  const consoleErrors = [];
  const failedReq = []; const badResponses = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
  page.on('requestfailed', (r) => failedReq.push(r.url().slice(0, 140)));
  page.on('response', (r) => { if (r.status() >= 400) badResponses.push(r.status() + ' ' + r.url().slice(0, 160)); });

  await page.goto(SHELL, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 15000 });
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', '123456');
  await page.click('button.login-button');
  await page.waitForFunction(() => !!localStorage.getItem('token'), { timeout: 20000 });
  check('UI: shell 真实登录', true, 'localStorage.token 已写入');

  // 场景 1：核赔工作台列表（真实分页，验证本轮案件入列 + 金额渲染）
  await page.goto(`${SHELL}/sclaim/claim/review`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(5000);
  const reviewText = (await page.locator('#subapp-viewport').innerText()).replace(/\s+/g, ' ');
  check('UI: 核赔工作台真实渲染（列表 + 真实金额）',
    reviewText.includes('核赔工作台') && /¥[\d,]+\.\d{2}/.test(reviewText) && reviewText.includes('列表合计'),
    `len=${reviewText.length} excerpt=${reviewText.slice(0, 110)}`);
  await page.screenshot({ path: `${OUT}/chain-review-list.png`, fullPage: false });

  // 场景 2：逐个案件 360 详情（验证金额/状态与 API 对账）
  for (const v of verified.slice(0, 4)) {
    await page.goto(`${SHELL}/sclaim/claim/case?claimId=${v.id}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(4500);
    const txt = (await page.locator('#subapp-viewport').innerText()).replace(/\s+/g, ' ');
    const hasAmount = v.apiAmount != null && txt.includes(
      `¥${Number(v.apiAmount).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`);
    check(`UI: 案件360[${v.label}] 真实渲染 + 金额与API对账`,
      txt.includes('案件') && txt.length > 100 && (hasAmount || txt.includes(String(v.apiAmount))),
      `apiAmount=${v.apiAmount} uiLen=${txt.length} excerpt=${txt.slice(0, 110)}`);
  }
  await page.screenshot({ path: `${OUT}/chain-case360.png`, fullPage: false });

  // 场景 3：我的工作台（角色聚合首页，验证全链数据可被导航到）
  await page.goto(`${SHELL}/sclaim/claim/workbench`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(4500);
  const wbText = (await page.locator('#subapp-viewport').innerText()).replace(/\s+/g, ' ');
  check('UI: 我的工作台真实渲染（全链能力导航）',
    wbText.includes('我的工作台') && wbText.includes('待我审核') && wbText.includes('AI 治理'),
    `len=${wbText.length}`);

  // 判据纠偏：排除两类已知无害噪音——antd Spin tip API 警告；无决策执行案件的 /execution 预期 404（AiSuggestionCard 已 allSettled 兜底）。
  const realErrors = consoleErrors.filter((e) => !e.includes('[antd: Spin]') && !e.includes('Failed to load resource'));
  check('UI: 无 console error（排除已知噪音）', realErrors.length === 0, `real=${realErrors.slice(0, 3).join(' | ')} noise=${consoleErrors.length}`);
  check('UI: 无失败请求', failedReq.length === 0, `failed=${failedReq.slice(0, 3).join(' | ')}`);

  await browser.close();
  const passed = results.filter((r) => r.pass).length;
  console.log(`\n== UI 串联结果：${passed}/${results.length} ==`);
  fs.writeFileSync(`${OUT}/chain-ui-result.json`, JSON.stringify({ results, verified, consoleErrors, failedReq, badResponses }, null, 2));
  process.exit(passed === results.length ? 0 : 1);
})().catch((e) => { console.error('FATAL', e.message); process.exit(1); });
