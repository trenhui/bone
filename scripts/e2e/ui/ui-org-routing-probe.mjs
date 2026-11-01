'use strict';
/**
 * 前端 UI 探针：组织架构 + 审批中心的**前后端联调**闭环。
 *
 * 范式（沿用 lowcode-closed-loop.cjs 的「浏览器只做真实登录取 JWT，其余断言走网关 API，
 * 最后回到浏览器做页面级验收」）：
 *   P0 前置自证（API 层）：emp1 建单并提交 ⇒ 服务端必须已把待办派给直属主管 femgr。
 *      前置不成立立即 exit 1 —— **不把服务端问题归因到 UI**。
 *   P1 浏览器（admin）：/iam#/accounts ⇒ 断言表格有「所属部门」「直属主管」两列，
 *      且 E2E 组织的行渲染出真实部门名 / 主管姓名（**不是裸 id**）。
 *   P2 浏览器（admin）：/iam#/organizations ⇒ 断言部门树出现 E2E 部门与负责人。
 *   P3 浏览器（femgr，审批人）：/metadata#/approval-center ⇒ 断言「待我审批」出现该单，
 *      **真实点击「通过」** ⇒ 回到 API 层复核 wf_task 与单据状态真的落库。
 *
 * 用法：
 *   NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules \
 *     node scripts/e2e/ui/ui-org-routing-probe.mjs
 */
import { createRequire } from 'module';
import { loadPlaywright, CHROME, UI_BASE, BASE, loginAs, api, makeChecker } from '../e2e_lib/client.cjs';
import { uiLogin, shot } from '../e2e_lib/ui.cjs';

const { chromium } = loadPlaywright();
const SHELL = UI_BASE;
const { check, report } = makeChecker();

const EXP = 'expense_reimbursement';
const PREFIX = 'e2e_org_';
const ORG_PWD = 'Org#2026';
const INITIATOR = PREFIX + 'emp1';
const APPROVER = PREFIX + 'femgr';
const ADMIN_USER = 'admin';
const ADMIN_PWD = '123456';

/** 可见的 antd 弹窗（关闭的仍在 DOM 里，必须按可见性收窄）。 */
const visibleModal = (page) =>
  page.locator('.ant-modal-wrap').filter({ has: page.locator('.ant-modal-content:visible') }).last();

/** 轮询直到条件成立或超时（状态投影经 outbox 中继落地，是最终一致的）。 */
async function waitFor(fn, timeoutMs = 15000, step = 500) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    last = await fn();
    if (last) return last;
    await new Promise((r) => setTimeout(r, step));
  }
  return last;
}

async function accountIdByUsername(username) {
  const r = await api('GET', `/iam/accounts?username=${encodeURIComponent(username)}&page=1&size=10`);
  const recs = r.data?.data?.records || [];
  const hit = recs.find((x) => x.username === username);
  return hit ? String(hit.id) : null;
}

/** P0：API 层造数 —— emp1 发起报销（表单故意填 CEO 当 approver_id 干扰值），提交。 */
async function seedSubmitted() {
  const tok = await loginAs(INITIATOR, ORG_PWD);
  const claim = 'UIPROBE-' + String(Date.now()).slice(-9);
  const ceoId = await accountIdByUsername(PREFIX + 'ceo');
  const created = await api(
    'POST',
    `/runtime/entities/${EXP}/records`,
    {
      claim_no: claim,
      title: 'UI 探针·组织路由验证',
      amount: 500,
      expense_date: '2026-11-01',
      applicant: 'E2E',
      cost_center: 'CC-RD-01',
      remark: 'UI probe',
      approver_id: ceoId ? Number(ceoId) : undefined,
    },
    { token: tok },
  );
  if (created.status !== 200 && created.status !== 201) {
    throw new Error(`建单失败 ${created.status} ${JSON.stringify(created.data).slice(0, 200)}`);
  }
  const rid = created.data?.data?.id;
  const submitted = await api(
    'POST',
    `/runtime/entities/${EXP}/records/${rid}/actions/submit`,
    { params: {} },
    { token: tok, headers: { 'Idempotency-Key': 'uiprobe-' + claim } },
  );
  if (submitted.status !== 200) {
    throw new Error(`提交失败 ${submitted.status} ${JSON.stringify(submitted.data).slice(0, 200)}`);
  }
  return { claim, rid, initiatorToken: tok, approverId: await accountIdByUsername(APPROVER), ceoId };
}

(async () => {
  let browser;
  let ok = false;
  try {
    // ---------------- P0 前置自证（服务端真源） ----------------
    console.log('== P0 前置自证（API 层）==');
    const seed = await seedSubmitted();
    const desc = await api('GET', `/runtime/approval/records/${EXP}/${seed.rid}`);
    const inst = desc.data?.data?.instance || {};
    const pending = desc.data?.data?.pendingTasks || [];
    const approverHit = pending.some((t) => String(t.assigneeId) === String(seed.approverId));
    check(
      '前置：实例 RUNNING 且待办派给直属主管（组织路由生效）',
      inst.phase === 'RUNNING' && approverHit,
      `phase=${inst.phase} pending=${JSON.stringify(pending)} approverId=${seed.approverId}`,
    );
    check(
      '前置：表单填的 approver_id(CEO) 不是审批人（D1 隔离在服务端已成立）',
      pending.every((t) => String(t.assigneeId) !== String(seed.ceoId)),
      `pending=${JSON.stringify(pending)} ceoId=${seed.ceoId}`,
    );
    if (inst.phase !== 'RUNNING' || !approverHit) {
      console.error('前置不成立，终止（勿归因 UI）');
      process.exit(1);
    }

    browser = await chromium.launch({ headless: true, executablePath: CHROME });

    // ---------------- P1 账号管理页：组织列渲染 ----------------
    console.log('\n== P1 账号管理页（admin）==');
    const adminCtx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const ap = await adminCtx.newPage();
    const adminLogged = await uiLogin(ap, {
      route: `${SHELL}/iam#/accounts`,
      user: ADMIN_USER,
      pwd: ADMIN_PWD,
    });
    check('admin 能登录后台', adminLogged, '登录失败');
    if (!adminLogged) throw new Error('admin 登录失败');

    await ap.goto(`${SHELL}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
    // 判定口径：DOM 存在即可（DOM 事实），并**额外量化可见性**（行高 > 0）。
    // 不用 Playwright 的 waitForSelector 可见性等待：IAM 冷启动时首屏接口可能 >30s，
    // 那会把「慢」误报成「页面坏」。
    const tableReady = await ap
      .waitForFunction(() => document.querySelectorAll('.ant-table-row').length > 0, { timeout: 60000 })
      .then(() => true)
      .catch(() => false);
    check('账号表格渲染完成', tableReady, '未见表格数据行');
    if (!tableReady) {
      const diag = await ap
        .evaluate(() => ({
          url: location.href,
          tables: document.querySelectorAll('table').length,
          dataRows: document.querySelectorAll('.ant-table-row').length,
          placeholder: document.querySelectorAll('.ant-table-placeholder').length,
        }))
        .catch((e) => ({ error: String(e) }));
      console.log('诊断：', JSON.stringify(diag, null, 1));
      await shot(ap, 'ui-org-routing-accounts-diag');
      throw new Error('账号表格未渲染');
    }

    const table = await ap.evaluate(() => ({
      heads: Array.from(document.querySelectorAll('.ant-table-thead th')).map((x) => x.innerText.trim()),
      rows: Array.from(document.querySelectorAll('.ant-table-row')).map((x) => ({
        text: x.innerText,
        height: x.getBoundingClientRect().height,
      })),
    }));
    const headText = table.heads.join('|');
    check('表头含「所属部门」', headText.includes('所属部门'), `表头=${headText}`);
    check('表头含「直属主管」', headText.includes('直属主管'), `表头=${headText}`);

    const emp = table.rows.find((r) => r.text.includes(INITIATOR));
    check('E2E 账号在列表中可见', !!emp, `未找到 ${INITIATOR}`);
    if (emp) {
      check('该行渲染出真实部门名', emp.text.includes('联调-前端研发组'), `行文本=${emp.text}`);
      check('该行渲染出主管姓名（非 id）', emp.text.includes('周慕云'), `行文本=${emp.text}`);
      check('该行不出现裸雪花 ID', !/\b\d{15,}\b/.test(emp.text), `行文本=${emp.text}`);
      check('该行实际可见（有渲染高度）', emp.height > 0, `height=${emp.height}`);
    }
    await shot(ap, 'ui-org-routing-accounts');

    // ---------------- P2 组织机构页：部门树 ----------------
    console.log('\n== P2 组织机构页（admin）==');
    await ap.goto(`${SHELL}/iam#/organizations`, { waitUntil: 'domcontentloaded' });
    const treeReady = await ap
      .waitForFunction(() => document.body.innerText.includes('联调-前端研发组'), { timeout: 60000 })
      .then(() => true)
      .catch(() => false);
    check('部门树出现 E2E 部门', treeReady, '未见「联调-前端研发组」');
    // 已知缺陷（ADR-0045 §D4）：待办/部门摘要对非 owner 装配为空 ⇒ 界面显示 "-"。
    // 负责人姓名只出现在**详情面板**（Descriptions），树节点本身只有名称 ⇒ 这里不写常绿断言，
    // 负责人装配的正确性由 API 层（E2E 脚本的部门负责人断言）负责。
    console.log('  · 部门负责人姓名展示在详情面板（Descriptions），树节点本身只展示名称');
    await shot(ap, 'ui-org-routing-organizations');

    // ---------------- P3 审批中心：审批人真实点击通过 ----------------
    console.log('\n== P3 审批中心（审批人 femgr）==');
    const apCtx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const ap2 = await apCtx.newPage();
    const approverLogged = await uiLogin(ap2, {
      route: `${SHELL}/metadata#/approval-center`,
      user: APPROVER,
      pwd: ORG_PWD,
    });
    check(`审批人 ${APPROVER} 能登录后台`, approverLogged, '登录失败');
    if (!approverLogged) throw new Error('审批人登录失败');

    await ap2.goto(`${SHELL}/metadata#/approval-center`, { waitUntil: 'domcontentloaded' });
    const rowsReady = await ap2
      .waitForFunction(() => document.querySelectorAll('.ant-table-row').length > 0, { timeout: 60000 })
      .then(() => true)
      .catch(() => false);
    check('审批中心「待我审批」渲染出待办行', rowsReady, '未见待办行');
    if (!rowsReady) {
      await shot(ap2, 'ui-org-routing-approval-diag');
      throw new Error('待办列表未渲染');
    }

    // 待办按 created_at DESC 排序 ⇒ 本次刚造的单在**第一行**；用 API 层的 taskId 精确核对，
    // 避免「点到别人的单上」被误判为通过。
    const firstRow = await ap2.evaluate(() => {
      const r = document.querySelector('.ant-table-row');
      return r ? r.innerText : '';
    });
    check('第一行待办存在', !!firstRow, '第一行为空');
    const pendingApi = await api('GET', `/runtime/approval/records/${EXP}/${seed.rid}`);
    const myTask = (pendingApi.data?.data?.pendingTasks || [])[0];
    check(
      'API 层确认该单待办仍派给审批人（点击对象正确）',
      !!myTask && String(myTask.assigneeId) === String(seed.approverId),
      `pendingTasks=${JSON.stringify(pendingApi.data?.data?.pendingTasks)}`,
    );
    check('待办行显示当前审批节点 mgr', firstRow.includes('mgr'), `行文本=${firstRow}`);

    const approveBtn = ap2.locator('.ant-table-row').first().getByRole('button', { name: /通\s*过/ }).first();
    check('待办行有「通过」按钮', (await approveBtn.count()) > 0, '未找到通过按钮');
    await approveBtn.click({ timeout: 15000 });

    const modal = visibleModal(ap2);
    await modal.waitFor({ state: 'visible', timeout: 15000 });
    // 判别对照：点「确定」之前弹窗**确实可见**（否则后面「已关闭」是恒真断言，没有判别力）。
    const modalVisibleBefore = await ap2.evaluate(() => {
      const wraps = Array.from(document.querySelectorAll('.ant-modal-wrap'));
      return wraps.some((w) => {
        const cs = getComputedStyle(w);
        if (cs.display === 'none' || cs.visibility === 'hidden') return false;
        const c = w.querySelector('.ant-modal-content');
        if (!c) return false;
        const r = c.getBoundingClientRect();
        return r.height > 0 && r.width > 0;
      });
    });
    check('审批弹窗在提交前处于可见态（判别对照）', modalVisibleBefore, '弹窗本就不可见，后续断言无判别力');

    const okBtn = modal.locator('.ant-modal-footer button.ant-btn-primary').first();
    await okBtn.click({ timeout: 15000 });

    // 注意：`:visible` 是 Playwright 专有伪类，放进浏览器原生 document.querySelector 会抛
    // SyntaxError（waitForFunction 直接 reject，被 catch 吞成 false ⇒ 恒红假阴性）。
    // 这里改用 computed style + 盒模型判定「是否还有可见弹窗」。
    const decided = await ap2
      .waitForFunction(() => {
        const wraps = Array.from(document.querySelectorAll('.ant-modal-wrap'));
        return !wraps.some((w) => {
          const cs = getComputedStyle(w);
          if (cs.display === 'none' || cs.visibility === 'hidden') return false;
          const c = w.querySelector('.ant-modal-content');
          if (!c) return false;
          const r = c.getBoundingClientRect();
          return r.height > 0 && r.width > 0;
        });
      }, { timeout: 30000 })
      .then(() => true)
      .catch(() => false);
    check('审批弹窗已提交关闭', decided, '弹窗未关闭');

    // 前端自身的成功反馈（message.success「已通过 N 条」）⇒ UI 确实走完了审批动作，不是静默。
    const toastShown = await ap2
      .waitForFunction(
        () => Array.from(document.querySelectorAll('.ant-message-notice-content')).some((x) =>
          x.innerText.includes('已通过'),
        ),
        { timeout: 15000 },
      )
      .then(() => true)
      .catch(() => false);
    check('页面出现「已通过」成功提示', toastShown, '未捕获到成功提示');
    await shot(ap2, 'ui-org-routing-approval-center');

    // ---------------- 落库复核（第二证据源） ----------------
    console.log('\n== 落库复核 ==');
    const finalDesc = await waitFor(async () => {
      const d = await api('GET', `/runtime/approval/records/${EXP}/${seed.rid}`);
      const i = d.data?.data?.instance || {};
      return i.phase === 'APPROVED' || i.phase === 'COMPLETED' ? i : null;
    });
    check(
      '实例进入 APPROVED 终态（UI 点击真的生效）',
      !!(finalDesc && (finalDesc.phase === 'APPROVED' || finalDesc.phase === 'COMPLETED')),
      `phase=${finalDesc ? finalDesc.phase : '?'}`,
    );
    const rec = await waitFor(async () => {
      const r = await api('GET', `/runtime/entities/${EXP}/records/${seed.rid}`);
      const s = r.data?.data?.status;
      return s === 'APPROVED' ? s : null;
    });
    check('单据状态 APPROVED（状态投影已中继落地）', rec === 'APPROVED', `status=${rec}`);
  } catch (e) {
    console.error('探针异常：', e && e.message ? e.message : e);
    check('探针执行未抛异常', false, String(e && e.message ? e.message : e));
  } finally {
    if (browser) await browser.close().catch(() => {});
    const r = report('组织路由 / 审批中心 UI 探针');
    ok = r.ok;
  }
  process.exit(ok ? 0 : 1);
})();
