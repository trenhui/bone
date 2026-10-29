// 「驳回退回指定节点」—— UI 回归探针（审批中心真实点击链路）。
//
// 背景（真实缺陷，2026-10-29）：动作定义只有单一 to_status，平台对逃逸动作一律把单据 status 推过去。
// 「退回指定节点」的真实语义是**原地返工**——实例退回上游节点重新派单、单据仍在审批中。
// 接入修复前实测：退回后单据被推到 REJECTED 终态，审批人再点「通过」恒 409，待办永远清不掉的死单。
// 修复（服务端声明 KEEP）+ 界面口径（toast 动词、轨迹标签）必须一起验，故本探针走**真浏览器点击**，
// 不直调 API——API 层已由 expense-purchase-approval-integration-e2e.py 的 S7/S8 覆盖。
//
// 判据（任一不成立即红，且前置失败直接中止，绝不把造数问题算成 UI 缺陷）：
//   0) 服务端真源自证：实例 RUNNING、待办派给独立审批人（审批人 ≠ 发起人，否则引擎挂起）；
//   1) 审批中心待办行可见该单，点「驳回」弹出决策框；
//   2) 决策框出现「退回节点」下拉且**有候选**（候选来自审批轨迹里的历史节点）；
//   3) 选中候选 + 提交 ⇒ toast 文案是「已退回节点」而不是「已驳回」；
//   4) 服务端复核：单据 status 仍 SUBMITTED、实例仍 RUNNING（不是 REJECTED 死单）；
//   5) 发起人视角打开单据详情 ⇒ 审批进度里出现「退回返工」标签且阶段仍是「审批中」。
//
// 用法：
//   NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-6/bin/node
//   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
//   $NODE scripts/e2e/ui/ui-approval-sendback-probe.mjs
import { loadPlaywright, CHROME, api, USER, UI_BASE, makeChecker } from '../e2e_lib/client.cjs';
import { uiLogin, shot } from '../e2e_lib/ui.cjs';

const { chromium } = loadPlaywright();
const SHELL = UI_BASE;
const EXP = 'expense_reimbursement';
const APPROVER_USER = process.env.BONE_E2E_APPROVER_USER || 'mdapprover2';
const APPROVER_PWD = process.env.BONE_E2E_APPROVER_PWD || 'Approver#2026';
const CLAIM_NO = `BX-SB-${String(Date.now()).slice(-9)}`;

const { check, report } = makeChecker();

/** 逃逸动作（submit/reject）强制幂等键；平台缺键直接 400。 */
const idem = () => ({
  'Idempotency-Key': `probe-${Date.now()}-${Math.floor(Math.random() * 1e6)}`,
});

/** 审批人账户 id：优先按用户名查，查不到再退环境变量（缺省 9104）。 */
async function findApproverId() {
  const res = await api('GET', `/iam/accounts?username=${APPROVER_USER}&page=1&size=10`);
  const rows = res.data?.data?.records || [];
  const hit = rows.find((r) => r.username === APPROVER_USER);
  return hit ? hit.id : Number(process.env.BONE_E2E_APPROVER_ID || 9104);
}

async function seedSubmitted(approverId) {
  const created = await api('POST', `/runtime/entities/${EXP}/records`, {
    claim_no: CLAIM_NO,
    title: 'UI 探针-退回指定节点',
    amount: 260,
    expense_date: '2026-10-29',
    applicant: '系统管理员',
    cost_center: 'CC-RD-01',
    remark: 'UI 探针：驳回退回指定节点（原地返工）',
    approver_id: approverId,
  });
  const recordId = created.data?.data?.id;
  if (!recordId) {
    throw new Error(`造单失败 ${created.status} ${JSON.stringify(created.data).slice(0, 300)}`);
  }
  const submitted = await api(
    'POST',
    `/runtime/entities/${EXP}/records/${recordId}/actions/submit`,
    { params: {} },
    { headers: idem() },
  );
  if (submitted.status !== 200) {
    throw new Error(`提交失败 ${submitted.status} ${JSON.stringify(submitted.data).slice(0, 300)}`);
  }
  return recordId;
}

const describe = async (recordId) =>
  (await api('GET', `/runtime/approval/records/${EXP}/${recordId}`)).data?.data || {};

/** 当前可见的 antd 弹窗（已关闭的仍在 DOM 里，必须按可见性收窄，否则选到隐藏弹窗的按钮）。 */
const visibleModal = (page) =>
  page.locator('.ant-modal-wrap').filter({ has: page.locator('.ant-modal-content:visible') }).last();

(async () => {
  const approverId = await findApproverId();
  const recordId = await seedSubmitted(approverId);

  // ---- 前置自证：服务端真源必须已是「RUNNING + 待办在该审批人手上」。
  const before = await describe(recordId);
  const beforePhase = before.instance?.phase;
  const pendingBefore = before.pendingTasks || [];
  check(
    '前置：实例 RUNNING 且待办派给独立审批人',
    beforePhase === 'RUNNING' && pendingBefore.some((t) => String(t.assigneeId) === String(approverId)),
    `phase=${beforePhase} pending=${JSON.stringify(pendingBefore)}`,
  );
  if (beforePhase !== 'RUNNING') {
    console.error('前置不成立，终止（勿归因 UI）');
    process.exit(1);
  }

  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  let ok = false;
  try {
    // ---------------- 审批人视角：审批中心 → 驳回 → 退回节点
    const approverCtx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const ap = await approverCtx.newPage();
    const loggedIn = await uiLogin(ap, {
      route: `${SHELL}/metadata#/approval-center`,
      user: APPROVER_USER,
      pwd: APPROVER_PWD,
    });
    check(`审批人 ${APPROVER_USER} 能登录后台`, loggedIn, '登录失败（凭据/账号未就绪）');
    if (!loggedIn) throw new Error('审批人登录失败');

    await ap.goto(`${SHELL}/metadata#/approval-center`, { waitUntil: 'domcontentloaded' });
    await ap
      .waitForFunction((no) => document.body.innerText.includes(no), CLAIM_NO, { timeout: 30000 })
      .catch(() => {});
    const row = ap.locator('tr', { hasText: CLAIM_NO }).first();
    check('审批中心「我的待办」出现该单', (await row.count()) > 0, `未见单号 ${CLAIM_NO}`);
    if (!(await row.count())) throw new Error('待办行未出现');

    // antd 5 两字按钮注入字间空格 ⇒ 用正则
    await row.getByRole('button', { name: /驳\s*回/ }).first().click();
    const modal = visibleModal(ap);
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    const nodeFormItem = modal.locator('.ant-form-item').filter({ hasText: '退回节点' }).first();
    check('决策框出现「退回节点」入口', (await nodeFormItem.count()) > 0, '未找到退回节点表单项');
    const select = nodeFormItem.locator('.ant-select').first();
    await select.click();
    // 下拉是渲染到 body 的独立容器；隐藏态容器也在 DOM 里，必须排除
    const dropdown = ap.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last();
    await dropdown.waitFor({ state: 'visible', timeout: 10000 }).catch(() => {});
    const options = dropdown.locator('.ant-select-item-option');
    const optCount = await options.count();
    check('退回节点候选非空（服务端按流程定义给出）', optCount > 0, `候选数=${optCount}`);
    if (optCount === 0) throw new Error('退回节点无候选，无法验证原地返工');
    const optTexts = await options.allInnerTexts();
    check(
      '候选里含发起节点 start',
      optTexts.some((t) => t.includes('start')),
      `候选=${JSON.stringify(optTexts)}`,
    );
    await options.filter({ hasText: 'start' }).first().click();

    const ta = modal.locator('textarea').first();
    await ta.fill('发票信息不全，退回发起节点补正后重提');
    await modal.locator('.ant-modal-footer button.ant-btn-primary').first().click();

    // 等**业务** toast 落地。不能直接取 `.ant-message-notice-content` 的最后一条：
    // 登录成功的 toast 可能还挂在 DOM 里（antd 约 3s 自动消失），取最后一条会读到「登录成功」而误判。
    // 故按文案特征轮询，把整批 notice 文本拼起来判。
    const toastText = await ap
      .waitForFunction(
        () => {
          const all = [...document.querySelectorAll('.ant-message-notice-content')]
            .map((e) => e.textContent || '')
            .join('|');
          return /已退回|已驳回|操作失败|成功 \d+ 条/.test(all) ? all : null;
        },
        null,
        { timeout: 15000 },
      )
      .then((h) => h.jsonValue())
      .then((v) => String(v || '').replace(/\s+/g, ''))
      .catch(() => '');
    check('toast 文案为「已退回节点」（不是「已驳回」）', toastText.includes('已退回节点'), `toast=${toastText}`);
    check('toast 未误报「已驳回」', !toastText.includes('已驳回'), `toast=${toastText}`);
    await shot(ap, 'approval-sendback-toast');

    // ---- 服务端复核：单据没被推到 REJECTED 终态
    const after = await describe(recordId);
    const detail = (await api('GET', `/runtime/entities/${EXP}/records/${recordId}`)).data?.data || {};
    check('单据 status 仍为 SUBMITTED（原地返工，不落 REJECTED）', detail.status === 'SUBMITTED',
      `status=${detail.status}`);
    check('实例仍 RUNNING 且返工待办仍在',
      after.instance?.phase === 'RUNNING' && (after.pendingTasks || []).length > 0,
      `phase=${after.instance?.phase} pending=${JSON.stringify(after.pendingTasks || [])}`);
    await approverCtx.close();

    // ---------------- 发起人视角：单据详情里的审批进度轨迹
    const adminCtx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const ad = await adminCtx.newPage();
    const adminOk = await uiLogin(ad, { route: `${SHELL}/metadata#/runtime?entity=${EXP}`, user: USER });
    check('发起人（admin）能登录后台', adminOk, '登录失败');
    if (adminOk) {
      await ad.goto(`${SHELL}/metadata#/runtime?entity=${EXP}`, { waitUntil: 'domcontentloaded' });
      await ad
        .waitForFunction((no) => document.body.innerText.includes(no), CLAIM_NO, { timeout: 30000 })
        .catch(() => {});
      const r2 = ad.locator('tr', { hasText: CLAIM_NO }).first();
      check('运行态列表出现该单', (await r2.count()) > 0, `未见单号 ${CLAIM_NO}`);
      if (await r2.count()) {
        await r2.getByRole('button', { name: /详\s*情/ }).first().click();
        await ad.locator('.ant-drawer-content').first().waitFor({ state: 'visible', timeout: 10000 }).catch(() => {});
        await ad.waitForTimeout(900);
        const drawer = await ad.evaluate(() => {
          const t = document.body.innerText;
          const m = t.match(/审批进度[\s\S]{0,220}/);
          return {
            hasProgress: t.includes('审批进度'),
            hasRunning: t.includes('审批中'),
            hasSentBack: t.includes('退回返工'),
            hasTerminalReject: /已驳回[^，。]{0,4}$/m.test(t),
            snippet: m ? m[0].replace(/\s+/g, ' ').slice(0, 200) : '',
          };
        });
        check('详情抽屉渲染「审批进度」', drawer.hasProgress, drawer.snippet);
        check('阶段显示「审批中」', drawer.hasRunning, drawer.snippet);
        check('轨迹把该次决策标为「退回返工」而非「驳回」', drawer.hasSentBack, drawer.snippet);
        await shot(ad, 'approval-sendback-timeline');
      }
      await adminCtx.close();
    }

    const r = report('驳回退回指定节点（UI）');
    ok = r.ok;
  } finally {
    await browser.close();
  }

  console.log(
    JSON.stringify({ ok, claimNo: CLAIM_NO, recordId, approverId, approver: APPROVER_USER }, null, 1),
  );
  process.exit(ok ? 0 : 1);
})().catch((e) => {
  console.error('FATAL', e.message);
  process.exit(1);
});
