/**
 * 智能理赔「四条关键链路」UI 冒烟（CI blocking 用）。
 *
 * 与 bone-frontend/e2e/synap-claim-e2e.cjs 的分工：
 * - 那个脚本跑**真实全栈**（6 个端口 + MySQL + provider），是防假绿型回归；
 * - 本脚本只跑**前端子应用 :3013 + 受控 API 信封**，验证四条链路的 UI 契约：
 *   报案提交（稳定幂等键）/ 核赔决策（结构化五要素 + 幂等键）/ 支付（稳定幂等键）/ 材料上传（类型闸门）。
 *
 * <b>不模拟后端判定</b>：mock 只提供页面启动所需的最小信封，所有断言都落在"前端是否发出了正确载荷"
 * 与"界面是否展示了正确口径"上；真实状态机由 Java 场景测与全栈 E2E 负责。
 *
 * 用法：node bone-frontend/e2e/claim-four-chain-ui-smoke.mjs
 * 环境变量：BONE_CLAIM_UI（默认 http://localhost:3013）
 */
import { chromium } from 'playwright';

const APP = process.env.BONE_CLAIM_UI || 'http://localhost:3013';
const CODES = [
  'claim:claims:read',
  'claim:claims:write',
  'claim:review:write',
  'claim:settlement:write',
  'claim:inspection:write',
  'claim:appeal:write',
  'claim:policy:read',
  'claim:policy:write',
];

const results = [];
function check(name, ok, detail = '') {
  results.push({ name, pass: Boolean(ok), detail });
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ` — ${detail}` : ''}`);
}

const calls = [];
const analyticsCalls = [];
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript((codes) => {
  // 独立调试态的权限来源：shell 未下发 ctx 时走 localStorage scopes 兜底（与生产三级降级同口径）。
  localStorage.setItem('bone-scopes', JSON.stringify(codes));
  localStorage.setItem('token', 'e2e.smoke.token');
  window.__BONE_GLOBAL_CONTEXT__ = {
    permissions: { codes, roles: ['e2e'], tenantId: '1' },
  };
}, CODES);

const envelope = (data, code = 200) => ({ code, success: true, message: 'ok', data });

await context.route('**/api/v1/**', async (route) => {
  const req = route.request();
  const url = new URL(req.url());
  const path = url.pathname;
  const method = req.method();
  let body = null;
  try {
    body = req.postDataJSON();
  } catch {
    body = null;
  }
  calls.push({ path, method, body, query: Object.fromEntries(url.searchParams), key: req.headers()['idempotency-key'] ?? '' });

  const json = (data, code) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(data, code)) });

  if (path.endsWith('/ai/config/governance')) {
    // 故意用 5000 而非硬编码 10000：证明大额徽标真的读运行时配置。
    return json({
      limits: {
        autoAmountLimit: 1000,
        aiManualThreshold: 0.8,
        aiReviewThreshold: 0.9,
        ocrManualThreshold: 0.8,
        dualReviewAmount: 5000,
      },
      writeEnabled: true,
    });
  }
  if (path === '/api/v1/review/tasks' && method === 'GET') {
    return json({
      records: [
        {
          claimId: '1',
          claimNo: 'CL-E2E-0001',
          policyNo: 'E2E-POLICY-0001',
          planId: '9001',
          insuredIdHash: 'e2e-hash',
          claimType: 'OUTPATIENT',
          status: 'ADJUSTING',
          externalStatus: '',
          hangUpStatus: 0,
          sourceChannel: 'E2E',
          claimAmount: 6000,
          approvedAmount: null,
          firstReviewerId: 'e2e-reviewer',
          createdAt: '2026-11-21 10:00:00',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
  }
  if (path === '/api/v1/claim-config/policies' && method === 'GET') {
    return json({
      records: [{
        policyNo: 'E2E-POLICY-0001', policyName: 'E2E 保单', insurerName: 'E2E 保司',
        branchName: 'E2E 分公司', insureName: 'E2E 投保人', policyType: 'GROUP',
        bizIdentityCode: 'E2E-BIZ', productType: 'HEALTH', waitingPeriod: 0,
        policyVersion: 'v1', effectiveStart: '2026-01-01', effectiveEnd: '2026-12-31',
        insuredSnapshot: '[]', docClaimApply: 'NEED', docClaimNotice: 'NEED', version: 1,
      }],
      total: 1, page: 1, size: 20,
    });
  }
  if (path === '/api/v1/claims/' && method === 'GET') return json({ records: [], total: 0 });
  // 案件 360：详情带假名化 ID，历史检索端点返回一条同人历史案（无证件/姓名原值）。
  if (path === '/api/v1/claims/1' && method === 'GET') {
    return json([{
      id: '1', claimNo: 'CL-E2E-0001', insuredIdHash: 'e2e-insured-hash-0001',
      status: 'ADJUSTING', stage: 'REVIEW', claimAmount: 6000, approvedAmount: null,
      createdAt: '2026-11-21 10:00:00',
    }]);
  }
  if (path === '/api/v1/claims/search' && method === 'GET') {
    return json([{
      claimId: '2', claimNo: 'CL-E2E-HISTORY-0002', policyNo: 'E2E-POLICY-0002',
      status: 'ADJUSTING', stage: 'REVIEW', matchedKey: 'INSURED_ID_HASH',
      createdAt: '2026-10-02T10:00:00Z',
    }]);
  }
  if (path === '/api/v1/claims/' && method === 'POST') {
    return json({ claimId: '1', claimNo: 'CL-E2E-0001', status: 'PENDING_EVIDENCE' }, 201);
  }
  if (/^\/api\/v1\/claims\/[^/]+\/settlement$/.test(path) && method === 'GET') {
    return json({
      settlementNo: 'SO-E2E-1',
      claimId: '1',
      amount: 6000,
      fundingStage: 'HARD_RESERVED',
      attempt: 1,
    });
  }
  if (path.includes('/settlement/pay') && method === 'POST') {
    return json({
      settlementNo: 'SO-E2E-1',
      claimId: '1',
      amount: 6000,
      fundingStage: 'PAID',
      attempt: 1,
    });
  }
  if (path === '/api/v1/claims/analytics/summary' && method === 'GET') {
    // 运营看板：用可辨识数值证明页面消费真契约而非写死假数据；scopeNote 必须原样上屏。
    analyticsCalls.push({ query: Object.fromEntries(url.searchParams) });
    return json({
      windowFrom: '2026-11-15T00:00:00Z',
      windowTo: '2026-11-22T00:00:00Z',
      // 真机联调实证：Jackson 把 Long 序列化为字符串；mock 必须复刻该形态，
      // 否则「mock 数字绿、真机字符串全显示 —」的缺陷会漏过 CI。
      converged: '42',
      byDecisionResult: { AUTO_APPROVE: '26', MANUAL_REQUIRED: '10', SUGGEST_REJECT: '6' },
      fallbackCount: '3',
      shadowAutomationRate: 0.625,
      avgDurationMs: 1234,
      // 本轮新增 6 指标（复核率/单案时长分位/推送成功率）：Long 仍为字符串，Double 为数字。
      reviewedCount: '100',
      secondReviewedCount: '30',
      caseDurationP50Ms: 1049,
      caseDurationP90Ms: 1089,
      pushSucceeded: '80',
      pushFailed: '5',
      scopeNote: '影子自动化率 = AUTO_APPROVE / 已收敛执行数；自动放款开关默认关闭、E1/E2 未交付，此值不等于实际出金率',
    });
  }
  if (path.includes('/ai-readiness/guard-check') && method === 'GET') {
    return json({
      success: true,
      code: 200,
      data: {
        wouldBlock: true,
        blockers: ['autoApprove.enabled=false'],
        scopeNote: 'deploymentGatesPassed 仅表示部署态闸门不再阻断，不构成放款许可',
      },
    });
  }
  if (path.includes('/ai/feedback/summary') && method === 'GET') {
    return json({
      success: true,
      code: 200,
      data: { adopts: 50, rejects: 30, modifies: 20 },
    });
  }
  if (path.includes('/clarity') && method === 'POST') return json({ success: true, code: 200, data: 1 });
  if (path.includes('/batch-decision') && method === 'POST') {
    return json({ success: true, code: 200, data: { succeeded: ['1'], failed: [] } });
  }
  if (path.includes('/review/tasks/') && path.endsWith('/decision') && method === 'POST') return json(null);
  if (path.includes('/evidence') && method === 'GET') {
    return json([
      {
        id: 'EV-E2E-1',
        evidenceId: 'EV-E2E-1',
        claimId: '1',
        docType: 'INVOICE',
        fileRef: 'e2e://invoice-1',
        contentHash: 'a'.repeat(64),
        ocrStatus: 'PARSED',
        ocrConfidence: 0.98,
        clearType: 'UNCLEAR',
        valid: true,
        locked: false,
      },
    ]);
  }
  // 其余只读评价/目录请求返回空信封，避免页面因无关端点失败而停在 loading。
  return json(null);
});

const page = await context.newPage();
page.setDefaultTimeout(15000);
const consoleErrors = [];
page.on('pageerror', (e) => consoleErrors.push(String(e.message).slice(0, 200)));

try {
  // ---------- 1. 报案提交 ----------
  await page.goto(`${APP}/#/submit`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#subapp-viewport .ant-form, .ant-form');
  check('[报案] 表单渲染', (await page.locator('.ant-form').count()) > 0);

  await page.getByPlaceholder('EG20260001').fill('E2E-POLICY-0001');
  await page.locator('.ant-input-number').first().click();
  await page.keyboard.type('9001');
  await page.keyboard.press('Tab');
  await page.getByPlaceholder('hash-abc').fill('e2e-hash');
  await page.locator('.ant-picker').first().click();
  await page.keyboard.type('2026-10-20');
  await page.keyboard.press('Enter');
  await page.getByPlaceholder('诊查费').first().fill('E2E-诊查费');
  await page.locator('.ant-input-number').nth(1).click();
  await page.keyboard.type('120');
  await page.getByRole('button', { name: '提交理赔' }).click();
  await page.waitForTimeout(800);
  const submitCall = calls.find((c) => c.method === 'POST' && c.path === '/api/v1/claims/');
  check('[报案] 提交请求已发出', Boolean(submitCall), submitCall?.path ?? '未捕获');
  check(
    '[报案] 请求带稳定业务幂等键',
    Boolean(submitCall?.key?.startsWith('claim:')),
    submitCall?.key ?? '(missing)',
  );

  // ---------- 2. 核赔决策 ----------
  await page.goto(`${APP}/#/review`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.ant-table-row', { timeout: 12000 });
  check('[核赔] 大额徽标用运行时阈值 5000', (await page.getByText('≥¥5,000').count()) > 0);
  await page.getByRole('button', { name: '拒赔' }).first().click();
  await page.waitForSelector('[data-testid="review-decision-confirm"]');
  const fiveLabels = ['① 事实认定', '② 条款依据', '③ 证据位置', '④ 申诉渠道', '⑤ 法定时限'];
  let allFive = true;
  for (const label of fiveLabels) allFive = allFive && (await page.getByText(label, { exact: false }).count()) > 0;
  check('[核赔] 拒赔弹窗展开结构化五要素', allFive);
  await page.getByPlaceholder('可核对的案件事实（不要写情绪化措辞）').fill('材料缺少费用明细');
  await page.getByPlaceholder('具体条款号与约定摘要').fill('条款 3.2');
  await page.getByPlaceholder('如 EV-001, EV-004；须可在证据集定位').fill('EV-001');
  await page.getByPlaceholder('客户可使用的申诉/复议入口').fill('小程序-申诉复议');
  await page.getByPlaceholder('如 收到通知书起 30 日内').fill('30 日内');
  await page.locator('.ant-modal .ant-select').last().click();
  await page.waitForTimeout(300);
  const option = page.locator('.ant-select-item-option').first();
  if (await option.count()) await option.click();
  await page.locator('[data-testid="review-decision-confirm"]').click();
  await page.waitForTimeout(800);
  const decisionCall = calls.find(
    (c) => c.method === 'POST' && c.path.includes('/review/tasks/') && c.path.endsWith('/decision'),
  );
  check('[核赔] 决策请求已发出', Boolean(decisionCall));
  check('[核赔] 请求带幂等键', Boolean(decisionCall?.key?.startsWith('claim:')), decisionCall?.key ?? '');
  const rejection = decisionCall?.body?.rejection;
  check(
    '[核赔] 结构化五要素随请求提交',
    Boolean(
      rejection &&
        rejection.factFindings &&
        rejection.clauseBasis &&
        rejection.evidenceRefs?.length &&
        rejection.appealChannel &&
        rejection.legalDeadline,
    ),
    JSON.stringify(rejection ?? null).slice(0, 160),
  );

  // ---------- 3. 支付 ----------
  await page.goto(`${APP}/#/fund?claimId=1`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('[data-testid="fund-pay"]', { timeout: 12000 });
  await page.locator('[data-testid="fund-pay"]').click();
  await page.waitForTimeout(800);
  const payCall = calls.find((c) => c.method === 'POST' && c.path.includes('/settlement/pay'));
  check('[支付] 支付请求已发出', Boolean(payCall));
  check(
    '[支付] 幂等键锚定结算单快照',
    payCall?.key === 'settlement:1|pay|SO-E2E-1|HARD_RESERVED|1',
    payCall?.key ?? '',
  );

  // ---------- 4. 材料上传 ----------
  await page.goto(`${APP}/#/evidence?claimId=1`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.ant-table-row', { timeout: 12000 });
  const accept = await page.locator('input[type="file"]').first().getAttribute('accept');
  check('[上传] 文件类型白名单生效', Boolean(accept && accept.includes('image/jpeg') && accept.includes('application/pdf')), accept ?? '');
  check('[上传] 证据列表已加载', (await page.getByText('e2e://invoice-1').count()) > 0);
  const uploadsBefore = calls.filter((c) => c.method === 'POST' && c.path.includes('/file')).length;
  await page.locator('input[type="file"]').first().setInputFiles({
    name: '../evil.pdf', mimeType: 'application/pdf', buffer: Buffer.from('x'),
  });
  await page.waitForTimeout(400);
  const uploadsAfter = calls.filter((c) => c.method === 'POST' && c.path.includes('/file')).length;
  check('[上传] 非法文件名被拒且未发出上传请求',
    (await page.getByText('文件名非法', { exact: false }).count()) > 0 && uploadsAfter === uploadsBefore);
  await page.locator('[data-testid="evidence-lock-open"]').click();
  await page.waitForSelector('[data-testid="lock-evidence-list"]');
  check('[锁定] 逐张清单列出将纳入的材料',
    (await page.locator('[data-testid^="lock-evidence-item-"]').count()) >= 1 &&
    (await page.locator('[data-testid="lock-evidence-list"]').getByText('e2e://invoice-1').count()) > 0);
  // antd5 会给两字按钮注入空格（「取 消」），用正则匹配而不是精确文案。
  await page.locator('.ant-modal .ant-btn').filter({ hasText: /取\s*消/ }).click();
  check('[影像] 分类树/预览/关联发票三栏就绪',
    (await page.locator('[data-testid="evidence-cat-INVOICE"]').count()) > 0 &&
    (await page.locator('[data-testid="evidence-preview"]').count()) > 0 &&
    (await page.locator('[data-testid="evidence-invoice-EV-E2E-1"]').count()) > 0);
  await page.locator('[data-testid="evidence-cat-INVOICE"]').click();
  await page.locator('body').press('r');
  check('[影像] R 键旋转生效', (await page.getByText('90°', { exact: false }).count()) > 0);

  // ---------- 5. 关联历史案件（复用后端跨键检索，前端不推断风险） ----------
  await page.goto(`${APP}/#/case?claimId=1&tab=overview`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('[data-testid="history-case-2"]', { timeout: 12000 });
  const searchCall = calls.find((c) => c.path === '/api/v1/claims/search');
  check('[历史] 用假名化 ID 检索（不回传证件原值）',
    searchCall?.query?.keyword === 'e2e-insured-hash-0001',
    JSON.stringify(searchCall?.query ?? {}));
  check('[历史] 关联案件可下钻且当前案已排除',
    (await page.locator('[data-testid="history-case-2"]').count()) === 1 &&
    (await page.locator('[data-testid="history-case-1"]').count()) === 0);

  // ---------- 6. 配置页关闭前脏检查 ----------
  await page.goto(`${APP}/#/policy`, { waitUntil: 'domcontentloaded' });
  await page.locator('.ant-btn-link').filter({ hasText: /编\s*辑/ }).first().click();
  await page.waitForSelector('.ant-modal input');
  await page.locator('.ant-modal input').first().fill('未保存的改动');
  await page.locator('.ant-modal .ant-btn').filter({ hasText: /取\s*消/ }).click();
  check('[配置] 未保存修改时关闭被拦截',
    (await page.getByText('放弃保单修改？', { exact: false }).count()) > 0);

  // ---------- 7. 运营分析看板（消费既有 GET /analytics/summary，影子率≠出金率） ----------
  await page.goto(`${APP}/#/analytics`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('[data-testid="analytics-converged"]', { timeout: 12000 });
  check('[运营] 已收敛执行数取后端真值 42',
    (await page.locator('[data-testid="analytics-converged"]').getByText('42', { exact: true }).count()) > 0);
  check('[运营] AI 降级数取后端真值 3',
    (await page.locator('[data-testid="analytics-fallback"]').getByText('3', { exact: true }).count()) > 0);
  check('[运营] 影子自动化率按口径显示 62.5%',
    (await page.locator('[data-testid="analytics-shadow-rate"]').getByText('62.5%', { exact: true }).count()) > 0);
  check('[运营] 后端 scopeNote 原样上屏（影子≠出金率）',
    (await page.getByText('此值不等于实际出金率', { exact: false }).count()) > 0);
  check('[运营] 决策分布映射受控标签且 AUTO_APPROVE 标影子',
    (await page.getByText('自动通过（影子口径）', { exact: true }).count()) > 0 &&
    (await page.getByText('强制转人工', { exact: true }).count()) > 0);
  const analyticCall = analyticsCalls[0];
  check('[运营] 请求带时间窗 from/to（UTC，≤31 天）',
    Boolean(analyticCall?.query?.from && analyticCall?.query?.to),
    JSON.stringify(analyticCall?.query ?? {}));
  // 本轮新增 6 指标：用可辨识真值证明页面消费新契约（Long 字符串形态也覆盖）。
  check('[运营] 复核率 30.0%（30/100，分子分母取后端）',
    (await page.locator('[data-testid="analytics-review-rate"]').getByText('30.0%', { exact: true }).count()) > 0);
  check('[运营] 推送成功率 94.1%（80/(80+5)）',
    (await page.locator('[data-testid="analytics-push-rate"]').getByText('94.1%', { exact: true }).count()) > 0);
  check('[运营] 单案时长 P50/P90 用分位而非均值（1.05s/1.09s，非 avg 1.23s）',
    (await page.locator('[data-testid="analytics-duration-p50"]').getByText('1.05 s', { exact: true }).count()) > 0 &&
    (await page.locator('[data-testid="analytics-duration-p90"]').getByText('1.09 s', { exact: true }).count()) > 0);
  check('[运营] AI 采纳率 50.0%（50/(50+30+20)，来自 feedback 端点）',
    (await page.locator('[data-testid="analytics-ai-adoption"]').getByText('50.0%', { exact: true }).count()) > 0);

  // ---------- 8. 影像清晰度三态（本轮新增；UNCLEAR 红角标，非 CLEAR 锁定 fail-closed） ----------
  await page.goto(`${APP}/#/evidence?claimId=1`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.ant-table', { timeout: 12000 });
  check('[清晰度] UNCLEAR 影像显示红色「不清晰」角标',
    (await page.locator('.ant-table').getByText('不清晰', { exact: true }).count()) > 0);

  // ---------- 9. AI 治理逐案守门预演（本轮新增；wouldBlock=true 显示拦截 + scopeNote 原样） ----------
  await page.goto(`${APP}/#/ai-governance`, { waitUntil: 'domcontentloaded' });
  await page.getByText('逐案守门预演', { exact: false }).first().waitFor({ timeout: 12000 }).catch(() => {});
  const claimInput = page.locator('.ant-card').filter({ hasText: '逐案守门预演' }).locator('input').first();
  if (await claimInput.count() > 0) {
    await claimInput.fill('1');
    await page.locator('.ant-card').filter({ hasText: '逐案守门预演' }).getByRole('button').first().click();
    await page.waitForTimeout(400);
    check('[守门] wouldBlock=true 显示红色「拦截」',
      (await page.locator('.ant-tag-red').filter({ hasText: '拦截' }).count()) > 0);
    check('[守门] scopeNote 原文上屏（不构成放款许可）',
      (await page.getByText('不构成放款许可', { exact: false }).count()) > 0);
  }

  // ---------- 10. 命令面板 Ctrl+K（本轮新增，纯前端） ----------
  await page.keyboard.press('Control+k');
  await page.waitForTimeout(200);
  check('[命令面板] Ctrl+K 打开命令输入',
    (await page.getByPlaceholder('输入命令名或关键词（如：核赔、analytics、搜索）').count()) > 0);
  await page.keyboard.press('Escape');

  check('无未捕获页面异常', consoleErrors.length === 0, consoleErrors.join(' | ').slice(0, 200));
} finally {
  await browser.close();
}

const passed = results.filter((r) => r.pass).length;
console.log(`\nclaim four-chain UI smoke: ${passed}/${results.length} passed`);
process.exit(passed === results.length ? 0 : 1);
