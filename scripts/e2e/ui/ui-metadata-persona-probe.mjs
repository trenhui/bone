/**
 * persona（业务视图 / 实施模式）UI 回归探针。
 *
 * 背景：2026-10-31 取证发现运行时数据页把「物理表名 / 交付口径对照 / 建模生命周期 /
 * 内部代理键 / 雪花主键」一并铺给了报销业务用户。本探针锁定整改后的行为边界。
 *
 * 第二批次（同日）追加三条判据：
 *   ① 状态列必须是**中文**（`enum_values` 为早期字符串数组时只能显示 `APPROVED` 裸码，
 *      根因在数据而非样式，由 scripts/migration/0080 归一）；
 *   ② 引用列显示**业务标签**而非内部 ID（`category_id` 历史上全为 NULL，这条路径从未被验证过）；
 *   ③ 实施模式自检**自愈**：数据补齐后「枚举缺中文标签」提示必须消失（否则提示会长期挂着，
 *      变成没人看的噪音）。
 *
 * 判别力约定（防假绿）：
 *   - 消费面的「应当看不见」必须配一条实施模式的「必须看得见」同名单项——只断言消失，
 *     页面白屏也会通过；两侧都不出现 ⇒ 判据失效。
 *   - 每轮都断言列表确有数据行（页面真的渲染出来了）。
 *
 * 用法：node scripts/e2e/ui/ui-metadata-persona-probe.mjs
 */
import { BASE, UI_BASE, loadPlaywright, login, makeChecker } from '../e2e_lib/client.cjs';
import { uiLogin, waitAppReady } from '../e2e_lib/ui.cjs';
const { chromium } = loadPlaywright();

const { check, report } = makeChecker();
const MAIN = '764059967975063552'; // 费用报销
const CATEGORY = '9250'; // 报销类别

const TECH_MARKERS = ['物理表', '交付口径对照', '已发布出物理表'];
/**
 * 指定审批人 = 申请人指定的**流程输入**字段（0081 统一措辞，原名「审批人账户ID」）。
 * 消费面隐藏、实施面保留：它是内部账户 id，且「谁在审」由引擎台账派生（列表另有「当前审批人」列）。
 */
const TECH_COL = '指定审批人';
const BUS_COL = '报销单号';

/** 平台既有 status 词表的中文措辞（与 16 个同源字段一致） */
const STATUS_LABELS = new Set(['草稿', '已提交', '已审批', '已驳回', '已打款']);
/** 裸枚举码：全大写英文/下划线，≥3 字符（`APPROVED`、`SUBMITTED`） */
const BARE_CODE = /^[A-Z][A-Z_]{2,}$/;

async function openEntity(page, id) {
  await page.goto(`${UI_BASE}/metadata#/entities/${id}/data`, { waitUntil: 'domcontentloaded' });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitAppReady(page, 20000).catch(() => {});
  await page.waitForTimeout(2500);
}

const headersOf = (page) => page.locator('.ant-table-thead th').allInnerTexts();
const bodyOf = (page) => page.locator('body').innerText();

/** 取指定表头列的全部单元格文本（表头文案定位，不依赖列序号硬编码） */
async function columnCells(page, headerText) {
  const headers = await headersOf(page);
  const idx = headers.findIndex((h) => h.trim() === headerText);
  if (idx < 0) return { idx, cells: [] };
  const cells = await page
    .locator(`.ant-table-tbody tr.ant-table-row td:nth-child(${idx + 1})`)
    .allInnerTexts();
  return { idx, cells: cells.map((c) => c.trim()) };
}

/**
 * 造一条带「消费类别」的报销单（幂等：claim_no 固定，已存在则复用）。
 * 走 HTTP 而非 UI 表单：本探针检验的是**列表如何渲染引用值**，
 * 表单录入路径另有探针覆盖；这里把变量收敛到一个字段，失败时归因明确。
 */
async function ensureCategorizedRecord() {
  const tk = await login();
  const claimNo = 'BX-PERSONA-PROBE';
  const list = await fetch(`${BASE}/runtime/entities/expense_reimbursement/records?page=1&size=200`, {
    headers: { Authorization: `Bearer ${tk}` },
  }).then((r) => r.json());
  const rows = list?.data?.records ?? list?.data?.list ?? [];
  const hit = rows.find((r) => r.claim_no === claimNo);
  if (hit) return { claimNo, created: false, categoryId: hit.category_id };

  const res = await fetch(`${BASE}/runtime/entities/expense_reimbursement/records`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${tk}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      claim_no: claimNo,
      title: 'persona 探针-引用列回显',
      amount: 120.5,
      category_id: 111, // tenant 0 的「差旅」类别（lookup 候选按租户过滤）
      applicant: 'admin',
    }),
  });
  const body = await res.json().catch(() => ({}));
  if (res.status >= 300 || body?.success === false) {
    return { claimNo, created: false, error: `${res.status} ${body?.message ?? ''}` };
  }
  return { claimNo, created: true, categoryId: 111 };
}

async function setMode(page, mode) {
  const item = page.getByText(mode === 'implement' ? '实施模式' : '业务视图', { exact: true });
  if (await item.count()) {
    await item.first().click();
  } else {
    // 开关未渲染（无权限）时退化为直接写本地偏好，保证判据仍然可执行
    await page.evaluate((m) => localStorage.setItem('bone.meta.viewerMode', m), mode);
    await page.reload({ waitUntil: 'domcontentloaded' });
  }
  await page.waitForTimeout(2500);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1680, height: 1050 } });
  const ok = await uiLogin(page);
  if (!ok) {
    console.error('登录失败，环境不可用');
    await browser.close();
    process.exit(1);
  }

  // ---------- 消费面：不该看见技术/实施信息 ----------
  await setMode(page, 'consume');
  await openEntity(page, MAIN);
  let headers = await headersOf(page);
  let body = await bodyOf(page);
  const rows = await page.locator('.ant-table-tbody tr.ant-table-row').count();
  check('消费面 · 列表确有数据（页面真的渲染了）', rows > 0, `rows=${rows}`);
  for (const m of TECH_MARKERS) {
    check(`消费面 · 不出现「${m}」`, !body.includes(m));
  }
  check(`消费面 · 列表无内部代理键列「${TECH_COL}」`, !headers.some((h) => h.includes(TECH_COL)), headers.join('|'));
  check(`消费面 · 列表保留业务主键列「${BUS_COL}」`, headers.some((h) => h.includes(BUS_COL)));

  // ---------- 状态列必须是中文（裸码 = 数据层缺陷，不是样式问题） ----------
  const statusCol = await columnCells(page, '状态');
  check('状态列 · 列存在', statusCol.idx >= 0, `headers=${headers.join('|')}`);
  check('状态列 · 有值可判', statusCol.cells.length > 0, `cells=${JSON.stringify(statusCol.cells.slice(0, 5))}`);
  const bare = statusCol.cells.filter((c) => BARE_CODE.test(c));
  check(
    '状态列 · 不出现裸枚举码（APPROVED/SUBMITTED 之类）',
    bare.length === 0,
    bare.slice(0, 5).join('|'),
  );
  const unknown = statusCol.cells.filter((c) => c && c !== '—' && !STATUS_LABELS.has(c));
  check(
    '状态列 · 全部落在平台既有中文词表内',
    unknown.length === 0,
    unknown.slice(0, 5).join('|'),
  );

  // ---------- 状态 Tag 的语义色：同一状态跨实体同色，不随选项下标漂移 ----------
  const statusTags = statusCol.idx >= 0
    ? await page
        .locator(`.ant-table-tbody tr.ant-table-row td:nth-child(${statusCol.idx + 1}) .ant-tag`)
        .evaluateAll((ns) => ns.map((n) => ({ text: n.textContent.trim(), cls: n.className })))
    : [];
  check('状态 Tag · 以徽标形式渲染', statusTags.length > 0, `tags=${statusTags.length}`);
  const approvedTags = statusTags.filter((t) => t.text === '已审批');
  check(
    '状态 Tag · 「已审批」语义为成功色（不随下标轮转）',
    approvedTags.length === 0 || approvedTags.every((t) => t.cls.includes('ant-tag-success')),
    approvedTags.slice(0, 3).map((t) => `${t.text}:${t.cls}`).join(' | '),
  );
  const semantic = statusTags.filter((t) => /ant-tag-(success|error|warning|processing)/.test(t.cls));
  check('状态 Tag · 至少一种语义色生效（证明走 enumTone 而非轮转）', semantic.length > 0, `${semantic.length}/${statusTags.length}`);
  // ---------- 引用列：显示业务标签，不显示内部 ID ----------
  const seeded = await ensureCategorizedRecord();
  if (seeded.error) {
    check('引用列 · 造数成功（带消费类别的报销单）', false, seeded.error);
  } else {
    await openEntity(page, MAIN);
    const row = page.locator('.ant-table-tbody tr.ant-table-row', { hasText: seeded.claimNo }).first();
    const found = (await row.count()) > 0;
    check('引用列 · 探针记录出现在列表', found, seeded.claimNo);
    if (found) {
      const catIdx = (await headersOf(page)).findIndex((h) => h.trim() === '消费类别');
      const catText = catIdx >= 0 ? (await row.locator(`td:nth-child(${catIdx + 1})`).innerText()).trim() : '';
      check('引用列 · 消费类别显示中文类别名', catText.length > 0 && !/^\d+$/.test(catText), catText);
    }
  }

  // ---------- 实施模式：同名单项必须出现（反之消费面断言不成立） ----------
  await setMode(page, 'implement');
  await page.waitForTimeout(500);
  headers = await headersOf(page);
  body = await bodyOf(page);
  for (const m of TECH_MARKERS) {
    check(`实施模式 · 必须出现「${m}」（证明消费面消失≠白屏）`, body.includes(m));
  }
  check(`实施模式 · 内部代理键列「${TECH_COL}」回归`, headers.some((h) => h.includes(TECH_COL)), headers.join('|'));
  check('实施模式 · 自证「隐藏了哪些代理键」', body.includes('代理键字段'));
  // 点名而非只报个数：只报「隐藏了 1 个字段」等于让人自己去找
  check(
    '实施模式 · 自检点名被隐藏的字段（指定审批人）',
    body.includes(TECH_COL),
    body.split('\n').filter((l) => l.includes('代理键字段')).join(' / ').slice(0, 200),
  );
  // 自愈：0080 归一后「枚举缺中文标签」必须消失（否则提示会长期挂着变成噪音）
  check('实施模式 · 枚举标签已补齐，缺标签提示消失', !body.includes('枚举缺中文标签'));
  check('实施模式 · 提供修复入口', body.includes('去建模台修复'));

  // ---------- 详情抽屉标题：消费面用业务键，不裸露主键 ----------
  await setMode(page, 'consume');
  await openEntity(page, MAIN);
  // 操作列是 antd <Button type="link">（渲染为 button 而非 a），按 button 文案定位
  const detailLink = page.locator('.ant-table-tbody button:has-text("详情")').first();
  if (await detailLink.count()) {
    await detailLink.click();
    await page.waitForTimeout(2000);
    const title = await page.locator('.ant-drawer-title').first().innerText().catch(() => '');
    check('详情标题 · 用业务键而非代理键', title.includes(BUS_COL) && !title.includes('#'), title);
  } else {
    check('详情标题 · 用业务键而非代理键', false, '未找到「详情」入口');
  }

  // ---------- 第二实体（报销类别）同样收敛 ----------
  await openEntity(page, CATEGORY);
  body = await bodyOf(page);
  check('类别页 · 消费面无物理表信息', !body.includes('物理表'));

  // ---------- 偏好持久化：刷新不回退 ----------
  await setMode(page, 'implement');
  await openEntity(page, CATEGORY);
  body = await bodyOf(page);
  check('偏好持久化 · 刷新后仍在实施模式', body.includes('物理表'));

  await browser.close();
  report('metadata persona 双模式回归');
})();
