/**
 * 「审批人」模型回归探针（列表派生列 + 详情轨迹 + 表单选择器）。
 *
 * 背景（2026-10-31 设计复核）：单据上原本只有一个 `approver_id` 标量，而审批是
 * (节点 × 轮次 × 办理人) 的 1:N 事实——实测同一实例 `finance_cosign` 节点同时挂 2 条 PENDING
 * （会签）。单据字段还只是**流程输入**（申请人指定），改派/加签只改 wf_task 不回写，必然陈旧。
 *
 * 本探针锁定整改后的口径：
 *   ① 列表有「当前审批人」列，且**从引擎台账派生**（未进流程=破折号；已通过=已通过；
 *      流转中=办理人姓名 / N 人待办），绝不出现账户 id 裸值；
 *   ② 详情「审批进度」用**节点中文名**（定义 label），不把 `finance_cosign` 印给业务用户；
 *   ③ 单据字段改名为「指定审批人」并仍进表单（否则申请人无法指定 ⇒ 引擎解析不出 ⇒ 挂起）；
 *   ④ 表单控件是**人员选择器**而不是「填账户 id 的数字框」。
 *
 * 判别力约定：每条「应该出现」都配一条反向断言（例如「已通过」存在的同时必须有破折号行，
 * 证明不是整列同一个值），否则整列渲染成常量也会通过。
 *
 * 用法：node scripts/e2e/ui/ui-metadata-approver-model-probe.mjs
 */
import { BASE, UI_BASE, loadPlaywright, login, makeChecker } from '../e2e_lib/client.cjs';
import { uiLogin, waitAppReady } from '../e2e_lib/ui.cjs';
const { chromium } = loadPlaywright();

const { check, report } = makeChecker();
const ENTITY = '764059967975063552'; // 费用报销

/** 已通过 / 已驳回 / 破折号 / 流转中——四种合法单元格形态 */
const CELL_PATTERNS = {
  approved: (t) => t.includes('已通过'),
  rejected: (t) => t.includes('已驳回'),
  none: (t) => t.trim() === '—',
  running: (t) => t.includes('待办') || t.includes('流转中') || t.includes('待指派'),
};

async function openEntity(page) {
  await page.goto(`${UI_BASE}/metadata#/entities/${ENTITY}/data`, { waitUntil: 'domcontentloaded' });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitAppReady(page, 20000).catch(() => {});
  await page.waitForTimeout(3000);
}

const headersOf = (page) => page.locator('.ant-table-thead th').allInnerTexts();

async function columnCells(page, headerText) {
  const headers = await headersOf(page);
  const idx = headers.findIndex((h) => h.trim() === headerText);
  if (idx < 0) return { idx, cells: [] };
  const cells = await page
    .locator(`.ant-table-tbody tr.ant-table-row td:nth-child(${idx + 1})`)
    .allInnerTexts();
  return { idx, cells: cells.map((c) => c.trim()) };
}

async function setMode(page, mode) {
  const item = page.getByText(mode === 'implement' ? '实施模式' : '业务视图', { exact: true });
  if (await item.count()) {
    await item.first().click();
  } else {
    await page.evaluate((m) => localStorage.setItem('bone.meta.viewerMode', m), mode);
    await page.reload({ waitUntil: 'domcontentloaded' });
  }
  await page.waitForTimeout(2800);
}

/**
 * 造一条**会签**单据（>5000 走 v2 定义的 finance_cosign 节点，指派 9104/9105 两人并行）。
 *
 * <p>为什么必须造：默认列表里绝大多数单据要么没进流程、要么单签，光靠存量数据验证不了
 * 「多人同时待办」这一形态——而这正是单据单值 approver_id 表达不了、本次要证明的核心。
 * 幂等：claim_no 固定，已存在则复用（不重复造单）。
 */
async function ensureCosignCase() {
  const tk = await login();
  const auth = { Authorization: `Bearer ${tk}`, 'Content-Type': 'application/json' };
  const claimNo = 'BX-APPROVER-COSIGN';
  const found = await fetch(
    `${BASE}/runtime/entities/expense_reimbursement/records?page=1&size=5&q=claim_no:eq:${claimNo}`,
    { headers: auth },
  ).then((r) => r.json());
  const existing = found?.data?.records?.[0];
  if (existing?.id) {
    return String(existing.id);
  }
  const created = await fetch(`${BASE}/runtime/entities/expense_reimbursement/records`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({
      claim_no: claimNo,
      title: '会签审批人演示（>5000）',
      amount: 8800,
      applicant: '系统管理员',
      expense_date: '2026-10-30',
    }),
  }).then((r) => r.json());
  const id = created?.data?.id;
  if (!id) return null;
  await fetch(`${BASE}/runtime/entities/expense_reimbursement/records/${id}/actions/submit`, {
    method: 'POST',
    headers: { ...auth, 'Idempotency-Key': `approver-probe-${Date.now()}` },
    body: '{}',
  });
  return String(id);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1680, height: 1050 } });
  await uiLogin(page);
  await page.evaluate(() => localStorage.setItem('bone.meta.viewerMode', 'consume'));
  const cosignId = await ensureCosignCase();
  await openEntity(page);

  // ---------- ① 列表「当前审批人」列 ----------
  const headers = await headersOf(page);
  check(
    '列表 · 存在「当前审批人」列',
    headers.some((h) => h.includes('当前审批人')),
    headers.join('|'),
  );
  const rows = await page.locator('.ant-table-tbody tr.ant-table-row').count();
  check('列表 · 确有数据行', rows > 0, `rows=${rows}`);

  let col = await columnCells(page, '当前审批人');
  check('当前审批人 · 列有值可判', col.cells.length > 0, JSON.stringify(col.cells.slice(0, 6)));

  /* 首页默认按创建时间倒序，最近造的单大多没进流程 ⇒ 先看首页；若一条「已通过」都没有，
     把页大小调到 100 再判（历史已审单据排在其后）。这一步同时验证「页大小 > 服务端单批上限 50」
     时列仍然正确——分块请求没做的话，第 51 行往后会静默变成「—」。 */
  if (!col.cells.some(CELL_PATTERNS.approved)) {
    const sizeChanger = page.locator('.ant-pagination-options .ant-select').first();
    if (await sizeChanger.count()) {
      await sizeChanger.click();
      await page.waitForTimeout(600);
      const opt100 = page
        .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
        .filter({ hasText: /^100/ })
        .first();
      if (await opt100.count()) {
        await opt100.click();
        await page.waitForTimeout(3500);
        col = await columnCells(page, '当前审批人');
      }
    }
  }

  check(
    '当前审批人 · 破折号行存在（未进流程≠待指派）',
    col.cells.some(CELL_PATTERNS.none),
    JSON.stringify(col.cells.slice(0, 8)),
  );
  check(
    '当前审批人 · 已通过行存在（读的是引擎 phase，不是单据 status）',
    col.cells.some(CELL_PATTERNS.approved),
    JSON.stringify(col.cells.slice(0, 12)),
  );
  check(
    '当前审批人 · 无账户 id 裸值（不能把 9104 印给业务用户）',
    !col.cells.some((c) => /^\d{1,6}$/.test(c)),
    JSON.stringify(col.cells.filter((c) => /^\d+$/.test(c))),
  );

  // ---------- ①b 会签：同节点多人必须体现「等 N 人」 ----------
  const cosignRow = page
    .locator('.ant-table-tbody tr.ant-table-row')
    .filter({ hasText: 'BX-APPROVER-COSIGN' })
    .first();
  check('会签样例已就位（列表第 1 页可见）', (await cosignRow.count()) > 0, `id=${cosignId}`);
  if (await cosignRow.count()) {
    const cellsText = (await cosignRow.locator('td').allInnerTexts()).map((c) => c.trim());
    const approverCell = cellsText[col.idx] ?? '';
    check(
      '会签 · 同一节点 2 位办理人折叠为「等 2 人」',
      /等\s*2\s*人/.test(approverCell),
      `cell=${approverCell}`,
    );
    check(
      '会签 · 折叠后仍不含账户 id',
      !/\d{3,}/.test(approverCell),
      `cell=${approverCell}`,
    );
  }

  // ---------- ② 详情审批进度：节点中文名 ----------
  const targetRow = page
    .locator('.ant-table-tbody tr.ant-table-row')
    .filter({ hasText: 'BX-281518363-10' })
    .first();
  if (await targetRow.count()) {
    await targetRow.getByRole('button', { name: '详情' }).first().click();
  } else {
    await page.locator('.ant-table-tbody tr.ant-table-row').first().getByText('详情').first().click();
  }
  await page.waitForTimeout(2500);
  let drawer = await page.locator('.ant-drawer-body').innerText();
  check('详情 · 显示「审批进度」区块', drawer.includes('审批进度'), drawer.slice(0, 120));
  check('详情 · 轨迹用节点中文名（大额会签）', drawer.includes('大额会签'), drawer.slice(0, 300));
  check(
    '详情 · 不把节点码印给业务用户（无 finance_cosign）',
    !drawer.includes('finance_cosign'),
    drawer.slice(0, 300),
  );
  check(
    '详情 · 办理人显示姓名而非账户 id',
    !/办理人\s*\d{2,}/.test(drawer),
    drawer.split('\n').filter((l) => l.includes('办理人')).join(' / ').slice(0, 200),
  );
  check(
    '详情 · 发起人显示姓名而非账户 id',
    !/发起人\s*\d+/.test(drawer),
    drawer.split('\n').filter((l) => l.includes('发起人')).join(' / ').slice(0, 160),
  );
  /* 会签单据的详情：同一节点 2 条待办必须**并列展示**（这正是单值 approver_id 表达不了的事实）。
     换一张单据看——上一张已办结，没有待办行。 */
  await page.keyboard.press('Escape');
  await page.waitForTimeout(900);
  if (await cosignRow.count()) {
    await cosignRow.getByRole('button', { name: '详情' }).first().click();
    await page.waitForTimeout(2600);
    const cosignDrawer = await page.locator('.ant-drawer-body').innerText();
    check(
      '详情(会签) · 待办 2 人且两位办理人并列',
      /待办\s*2\s*人/.test(cosignDrawer) && cosignDrawer.includes('平台冒烟审批人B'),
      cosignDrawer.split('\n').filter((l) => l.includes('待办')).join(' / ').slice(0, 220),
    );
    check(
      '详情(会签) · 当前节点用中文名（大额会签）',
      cosignDrawer.includes('大额会签'),
      cosignDrawer.slice(0, 160),
    );
  }
  await page.keyboard.press('Escape');
  await page.waitForTimeout(800);

  // ---------- ③④ 表单：指定审批人仍可录入，且是人员选择器 ----------
  await setMode(page, 'consume');
  await page.getByRole('button', { name: /新建|新增/ }).first().click();
  await page.waitForTimeout(1800);
  const modal = await page.locator('.ant-modal-body').innerText();
  check('表单 · 保留「指定审批人」输入项（缺失即流程挂起）', modal.includes('指定审批人'), modal.slice(0, 400));
  check('表单 · 不再出现旧名「审批人账户ID」', !modal.includes('审批人账户ID'));
  // 人员选择器 = combobox 形态（数字输入框不会有 role=combobox）
  const comboCount = await page.locator('.ant-modal-body .ant-select').count();
  check('表单 · 指定审批人是人员选择器（ant-select）', comboCount > 0, `select=${comboCount}`);
  // 打开下拉，确认选项是姓名而不是数字
  const approverSelect = page
    .locator('.ant-modal-body .ant-form-item')
    .filter({ hasText: '指定审批人' })
    .locator('.ant-select')
    .first();
  if (await approverSelect.count()) {
    await approverSelect.click();
    await page.waitForTimeout(1200);
    const options = await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .allInnerTexts();
    check('表单 · 下拉选项可搜索到姓名', options.length > 0, `options=${options.length}`);
    check(
      '表单 · 选项文案是姓名不是账户 id',
      options.length > 0 && options.every((o) => !/^\d+$/.test(o.trim())),
      options.slice(0, 5).join('|'),
    );
  }
  await page.keyboard.press('Escape');
  await page.waitForTimeout(500);

  await browser.close();
  process.exit(report('审批人模型探针').ok ? 0 : 1);
})();
