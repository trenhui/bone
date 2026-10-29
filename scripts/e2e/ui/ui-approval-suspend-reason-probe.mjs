// 挂起原因可诊断 —— UI 回归探针。
//
// 背景（真实缺陷，2026-10-29）：实例挂起（SUSPENDED）时引擎不派单，单据详情抽屉里只有
// 「已挂起 + 零待办」。用户无法区分「审批人没填」「审批人就是发起人本人（职责分离）」
// 「组织信息缺失」，现场只能翻服务端日志。修复后服务端 describe 返回 suspendReason，
// 前端 ApprovalProgress 把它渲染进告警。
//
// 判据（必须真渲染，不看「组件有没有挂上」）：
//   1) 造一张「审批人 = 发起人本人」的报销单并提交 ⇒ 引擎按设计挂起（服务端真源先自证，
//      否则 UI 红/绿都不可信）；
//   2) 打开该单详情抽屉 ⇒ 出现「审批进度」区块与挂起态；
//   3) 告警里出现具体原因关键词（自审批/职责分离），而不是只有「已挂起」。
//
// 用法：
//   NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-6/bin/node
//   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
//   $NODE scripts/e2e/ui/ui-approval-suspend-reason-probe.mjs
import { loadPlaywright, CHROME, api, USER } from '../e2e_lib/client.cjs';
import { uiLogin } from '../e2e_lib/ui.cjs';

const { chromium } = loadPlaywright();
const SHELL = 'http://localhost:3000';
const EXP = 'expense_reimbursement';

/** 逃逸动作（submit）强制幂等键；平台缺键直接 400。 */
const idem = () => ({ 'Idempotency-Key': `probe-${Date.now()}-${Math.floor(Math.random() * 1e6)}` });

async function seedSelfApprovalRecord() {
  const claimNo = `BX-UI-${String(Date.now()).slice(-9)}`;
  const created = await api('POST', `/runtime/entities/${EXP}/records`, {
    claim_no: claimNo,
    title: 'UI 探针-挂起原因可诊断',
    amount: 120,
    expense_date: '2026-10-29',
    applicant: USER,
    remark: 'UI 探针：审批人=发起人本人',
    approver_id: 1, // 与发起人相同 ⇒ 触发自审批守卫（正确行为是挂起，不是放行）
  });
  const recordId = created.data?.data?.id;
  if (!recordId) throw new Error(`造单失败 ${created.status} ${JSON.stringify(created.data).slice(0, 300)}`);

  const submitted = await api('POST', `/runtime/entities/${EXP}/records/${recordId}/actions/submit`,
    { params: {} }, { headers: idem() });
  if (submitted.status !== 200) {
    throw new Error(`提交失败 ${submitted.status} ${JSON.stringify(submitted.data).slice(0, 300)}`);
  }
  return { claimNo, recordId };
}

(async () => {
  const { claimNo, recordId } = await seedSelfApprovalRecord();

  // 服务端真源先自证：挂起态 + 原因非空。前置不成立时直接终止，避免把造数问题误判成 UI 缺陷。
  const desc = await api('GET', `/runtime/approval/records/${EXP}/${recordId}`);
  const phase = desc.data?.data?.instance?.phase;
  const reason = desc.data?.data?.suspendReason;
  if (phase !== 'SUSPENDED' || !reason) {
    throw new Error(`前置失败：phase=${phase} reason=${reason ?? 'null'}（勿归因 UI）`);
  }

  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  await uiLogin(page, { route: `${SHELL}/iam#/accounts` });

  await page.goto(`${SHELL}/metadata#/runtime?entity=${EXP}`, { waitUntil: 'domcontentloaded' });
  await page
    .waitForFunction((no) => document.body.innerText.includes(no), claimNo, { timeout: 30000 })
    .catch(() => {});

  const row = page.locator('tr', { hasText: claimNo }).first();
  if (!(await row.count())) throw new Error(`列表未出现单号 ${claimNo}（列表筛选/分页行为不符）`);
  // antd 5 两字按钮会注入字间空格 ⇒ 用正则匹配
  await row.getByRole('button', { name: /详\s*情/ }).first().click();

  await page.waitForFunction(() => document.body.innerText.includes('审批进度'), null, { timeout: 20000 })
    .catch(() => {});
  // 抽屉有 300ms 左右滑入动画：不等它停稳就截图/读数，会拿到「内容还在视口外」的中间态。
  await page.locator('.ant-drawer-content').first().waitFor({ state: 'visible', timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(900);

  const r = await page.evaluate(() => {
    const t = document.body.innerText;
    const m = t.match(/流程已挂起[\s\S]{0,200}/) || t.match(/已挂起[\s\S]{0,200}/);
    return {
      hasProgress: t.includes('审批进度'),
      hasSuspended: t.includes('挂起'),
      hasReason: t.includes('自审批') || t.includes('职责分离'),
      snippet: m ? m[0].replace(/\s+/g, ' ').slice(0, 180) : '',
    };
  });

  await page.screenshot({ path: 'logs/approval-suspend-reason.png' });
  await browser.close();

  const ok = r.hasProgress && r.hasSuspended && r.hasReason;
  console.log(JSON.stringify({ ok, claimNo, recordId, phase, reason, ...r }, null, 1));
  process.exit(ok ? 0 : 1);
})().catch((e) => {
  console.error('FATAL', e.message);
  process.exit(1);
});
