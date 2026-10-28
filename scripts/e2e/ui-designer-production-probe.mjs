/**
 * 生产界面设计器三场景 UI 探针（React 真源，非静态 demo）：
 * shell :3000 登录 → 元数据子应用 → 实体详情「界面设计器」Tab，对
 * 报销单 / 理赔案件 / 采购订单（E2E 最新一轮样板，按雪花 ID 动态取最新）验证：
 *   1. 设计器挂载（.uis-root）且预览头部带实体 code；
 *   2. **负向→正向→恢复闭环**（断言有牙）：
 *      - 未注入时 .uis-page-header 恒不存在（基线）；
 *      - API 注入 pageHeader/split/quickFilters/viewKind=kanban/degradedBanner/footer 后
 *        PUT（If-Match 乐观锁），刷新页面这些元素**必须**真实渲染；
 *      - 恢复原始草稿后再次刷新，注入元素必须消失（防止污染 E2E 基线 + 证明恢复生效）；
 *   3. 列表视图：quickFilters chips / viewKind 徽标 / degradedBanner 警示条 / batchActions 工具条；
 *   4. footer 合规条含 AI 徽标与「演示数据」标注；
 *   5. 全流程无致命 console / page 错误。
 *
 * 口径说明（预览 ≡ 运行时同源）：本探针验证的是 SchemaPreview 渲染层；
 * BoneDynamicForm（运行时）由 vitest 单测 + E2E 87 项覆盖。
 *
 * 用法：node scripts/e2e/ui-designer-production-probe.mjs
 * 依赖：playwright（经共享 client 的 loadPlaywright 加载，见 e2e_lib/client.cjs）
 */
/* 共享 client：playwright 加载 / SHOT_DIR / API 登录与带 token fetch / 断言收集（见 e2e_lib/client.cjs） */
import {
  UI_BASE as BASE,
  SHOT_DIR,
  USER,
  PWD,
  loadPlaywright,
  api,
  login,
  makeChecker,
} from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const { results, check, report } = makeChecker();

/* ---------------- API 辅助（登录与带 token fetch 见 e2e_lib/client.cjs） ---------------- */

async function latestEntities(token, keyword) {
  const { data: d } = await api('GET', '/metadata/entities?page=1&size=500', undefined, { token });
  const recs = (d.data || {}).records || [];
  // 雪花 ID 字典序最大 = 最新创建；取最新一个，避免多轮 E2E 样板实体堆积后挑到旧的
  const hits = recs.filter((x) => x.displayName === keyword).sort((a, b) => String(b.id).localeCompare(String(a.id)));
  return hits[0] || null;
}

async function getSchema(token, eid) {
  const { data: d } = await api('GET', `/metadata/entities/${eid}/ui-schema`, undefined, { token });
  const data = d.data || {};
  // 后端 data.json 是 JSON **字符串**（E2E put_schema 亦以 raw 文本发送）；统一 parse 成对象
  if (typeof data.json === 'string') data.json = JSON.parse(data.json);
  return data;
}

async function putSchema(token, eid, schema, lock) {
  const { status, data: d } = await api('PUT', `/metadata/entities/${eid}/ui-schema`, { json: schema }, {
    token,
    headers: { 'If-Match': String(lock) },
  });
  return { status, body: d };
}

/**
 * 注入探针能力（全部来自协议 v1.1/v1.2 白名单键，后端 validate 不拒）：
 * pageHeader / split-left-40 / quickFilters / viewKind=kanban / degradedBanner{when,text} / footer
 */
function buildProbeSchema(schema) {
  const next = JSON.parse(JSON.stringify(schema));
  const form = next.views.form;
  // pageHeader：取首个分区前 2 个控件（协议要求引用必须存在于某分区控件）
  const firstCtrls = (form.sections[0]?.controls || []).map((c) => c.fieldCode).slice(0, 2);
  if (firstCtrls.length) form.pageHeader = firstCtrls;
  // split：挑第一个控件数 ≥2 的分区做 40/60 分栏
  const splitSec = form.sections.find((s) => (s.controls || []).length >= 2);
  if (splitSec) splitSec.layout = 'split-left-40';
  // dashboard 语义徽标（kind 枚举成员）
  const kindSec = form.sections.find((s) => (s.controls || []).length >= 1);
  if (kindSec) kindSec.kind = 'dashboard';
  // list 新能力（batchActions 统一注入：三实体 E2E 均定义 submit 动作，
  // 但并非每个草稿都配了批量引用——不注入则理赔场景走不到批量工具条渲染路径）
  next.views.list = next.views.list || {};
  next.views.list.quickFilters = ['mine', 'high_amount'];
  next.views.list.viewKind = 'kanban';
  next.views.list.degradedBanner = { when: 'llm_unavailable', text: 'AI 服务不可用，已切换规则审查模式' };
  if (!(next.views.list.batchActions || []).length) next.views.list.batchActions = ['submit'];
  // footer 合规条四键中三键
  next.footer = { disclaimer: '本界面由配置生成，金额以财务系统为准', aiBadge: true, mockDataMark: true };
  return next;
}

/* ---------------- UI 辅助 ---------------- */
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.setDefaultTimeout(25000);
const consoleErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200));
});
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

const shot = async (n) => {
  const p = `${SHOT_DIR}/ui_designer_prod_${n}.png`;
  await page.screenshot({ path: p, fullPage: false });
  return p;
};

/** 等画布稳定：连续两次采样文本一致（防 qiankun 挂载抢跑） */
async function waitForStable(sel, rounds = 3, gap = 400) {
  let last = '';
  for (let i = 0; i < rounds; i++) {
    await page.waitForTimeout(gap);
    const cur = await page.locator(sel).first().innerText().catch(() => '');
    if (cur && cur === last) return cur;
    last = cur;
  }
  return last;
}

async function uiLogin() {
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"], input[placeholder*="密码"]', { timeout: 20000 });
  const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
  if (await page.locator(userSel).first().count()) {
    await page.locator(userSel).first().fill(USER);
  }
  const pwd = page.locator('input[type="password"]').first();
  await pwd.fill(PWD);
  await pwd.press('Enter');
  await page.waitForTimeout(1200);
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('button').filter({ hasText: /登\s*录/ }).first().click().catch(() => {});
    await page.waitForTimeout(3000);
  }
  try {
    await page.waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 });
    return true;
  } catch {
    return false;
  }
}

/** 关闭新人引导遮罩（首屏 .tour-card 吞点击；memory 既有教训：探针必须先跳过） */
async function dismissTour() {
  const skip = page.locator('button, a').filter({ hasText: '跳过引导' }).first();
  if (await skip.count()) {
    await skip.click({ timeout: 4000 }).catch(() => {});
    await page.waitForTimeout(400);
  }
}

/** 打开实体详情并进入「界面设计器」Tab，返回画布文本。
 *  必须整页 reload：page.goto 同一 URL（含 hash）是同文档导航，SPA 不会重新
 * 拉 schema —— API 侧 PUT 的新草稿不会反映到已挂载的设计器（探针首跑假红根因）。 */
async function openDesigner(entityId) {
  await page.goto(`${BASE}/metadata#/entities/${entityId}`, { waitUntil: 'domcontentloaded' });
  await page.reload({ waitUntil: 'domcontentloaded' });
  const tab = page.locator('.ant-tabs-tab').filter({ hasText: '界面设计器' }).first();
  await tab.waitFor({ state: 'visible', timeout: 25000 });
  await tab.click();
  await page.waitForSelector('#subapp-viewport .uis-root, .uis-root', { timeout: 25000 });
  await dismissTour();
  return waitForStable('.uis-root');
}

const SCENARIOS = [
  { keyword: '报销单', label: '报销' },
  { keyword: '理赔案件', label: '智能理赔' },
  { keyword: '采购订单', label: '采购' },
];

try {
  check('UI 登录成功', await uiLogin(), `url=${page.url()}`);
  const token = await login();

  for (const sc of SCENARIOS) {
    const ent = await latestEntities(token, sc.keyword);
    if (!ent) {
      check(`[${sc.label}] 找到最新「${sc.keyword}」实体`, false, 'entities 列表未命中');
      continue;
    }
    const eid = ent.id;
    const tag = `[${sc.label} ${ent.code}]`;

    /* 1. 基线（未注入）：页头/分栏/新 list 能力必须都不存在 —— 负向锚点 */
    const baseText = await openDesigner(eid);
    check(`${tag} 设计器挂载且预览头部带实体 code`, baseText.includes(ent.code), baseText.slice(0, 120));
    const basePageHeader = await page.locator('.uis-page-header').count();
    const baseSplit = await page.locator('.uis-split').count();
    check(`${tag} 负向锚点：未注入时页头条/分栏不存在`, basePageHeader === 0 && baseSplit === 0,
      `pageHeader=${basePageHeader} split=${baseSplit}`);

    /* 2. 注入新能力并 PUT（If-Match 乐观锁） */
    const before = await getSchema(token, eid);
    const rawSchema = before.json;
    if (!rawSchema) {
      check(`${tag} 读取草稿 schema`, false, `data keys=${Object.keys(before).join(',')}`);
      continue;
    }
    const probe = buildProbeSchema(rawSchema);
    const put1 = await putSchema(token, eid, probe, before.lockVersion);
    check(`${tag} 注入探针 schema PUT 200`, put1.status === 200, `status=${put1.status} ${JSON.stringify(put1.body).slice(0, 160)}`);
    if (put1.status !== 200) continue;

    // 无论后续断言成败，finally 里恢复原始草稿（防探针中途崩掉污染 E2E 基线）
    try {
      /* 3. 刷新后正向断言：新能力真实渲染 */
      await openDesigner(eid);
      const phCount = await page.locator('.uis-page-header').count();
      const phItems = await page.locator('.uis-page-header .uis-ph-item').count();
      check(`${tag} pageHeader 页头条渲染（引用字段可见）`, phCount === 1 && phItems >= 1, `bar=${phCount} items=${phItems}`);

      const splitCount = await page.locator('.uis-split').count();
      const splitCols = await page.locator('.uis-split .uis-split-col').count();
      check(`${tag} split-left-40 分栏渲染（左右两栏）`, splitCount === 1 && splitCols === 2, `split=${splitCount} cols=${splitCols}`);

      const kindBadge = await page.locator('.uis-sec-title .kind').count();
      check(`${tag} section.kind 语义徽标渲染（含分栏分区标题）`, kindBadge >= 1, `badges=${kindBadge}`);

      const footerText = await page.locator('.uis-footer').innerText().catch(() => '');
      const mockMark = await page.locator('.uis-footer .mock-mark').count();
      const aiBadge = await page.locator('.uis-footer .ai-badge').count();
      check(`${tag} footer 合规条渲染（免责声明 + AI 徽标 + 演示数据标注）`,
        footerText.includes('本界面由配置生成') && mockMark === 1 && aiBadge === 1,
        `text=${footerText.slice(0, 60)} mock=${mockMark} ai=${aiBadge}`);

      /* 4. 列表视图新能力 */
      await page.locator('.uis-pv-head .ant-segmented-item').filter({ hasText: '列表' }).first().click();
      await page.waitForTimeout(1200);
      const qfCount = await page.locator('.uis-quick-filters .uis-qf').count();
      check(`${tag} quickFilters 快捷筛选 chips 渲染`, qfCount === 2, `chips=${qfCount}`);
      const vkText = await page.locator('.uis-vk-badge').innerText().catch(() => '');
      check(`${tag} viewKind=kanban 徽标渲染（P1 降级提示）`, vkText.includes('kanban'), vkText.slice(0, 60));
      const dgText = await page.locator('.uis-degraded').innerText().catch(() => '');
      check(`${tag} degradedBanner 降级横幅渲染（when+text）`,
        dgText.includes('AI 服务不可用') && dgText.includes('llm_unavailable'), dgText.slice(0, 80));
      const batchBar = await page.locator('.uis-batch-bar').count();
      check(`${tag} batchActions 批量动作工具条渲染`, batchBar === 1, `bar=${batchBar}`);
      await shot(`${sc.label}_list`);
    } finally {
      /* 5. 恢复原始草稿（防污染 E2E 基线），并验证恢复生效 */
      const afterPut = await getSchema(token, eid);
      const putBack = await putSchema(token, eid, rawSchema, afterPut.lockVersion);
      check(`${tag} 恢复原始草稿 PUT 200`, putBack.status === 200, `status=${putBack.status}`);
      await openDesigner(eid).catch(() => '');
      const phAfter = await page.locator('.uis-page-header').count();
      const splitAfter = await page.locator('.uis-split').count();
      check(`${tag} 恢复后页头条/分栏消失（基线干净）`, phAfter === 0 && splitAfter === 0,
        `pageHeader=${phAfter} split=${splitAfter}`);
      await shot(`${sc.label}_restored`).catch(() => {});
    }
  }

  const fatal = consoleErrors.filter(
    (e) => !/favicon|Failed to load resource:|\[antd[^\]]*\]|deprecated/i.test(e)
  );
  check('全流程无致命 console 错误', fatal.length === 0, fatal.slice(0, 3).join(' | '));
} catch (e) {
  check('UI 探针执行未抛异常', false, e.message.slice(0, 200));
  await shot('error').catch(() => {});
} finally {
  const { ok } = report('生产设计器探针汇总');
  await browser.close();
  process.exit(ok ? 0 : 1);
}
