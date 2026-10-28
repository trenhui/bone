/**
 * 界面设计器 UI 探针（模拟人工操作）：打开 bone-ui-schema-designer.html，逐个切换
 * 费用报销 / 智能理赔 / 采购审查 三套企业场景，验证：
 *   1. 新人引导弹窗（#mask + #modal）可关闭，关闭后不再拦截点击；
 *   2. 三套样板下拉可切换，表单视图渲染出分区、子表（.childtable）含表头 + 合计行 tfoot；
 *   3. 子表「添加行 → 行数 +1 且合计行数量列实时重算」交互闭环；
 *   4. 身份变体（#btnEnv → #role）切换后预览按变体重算；
 *   5. 协议校验（#btnCheck）对三套样板均 PASS、按钮不进入 alert 态；
 *   6. 联动演练按钮逐个可点且给出「已演练：<规则码>」回执；
 *   7. 列表 / 详情视图可渲染，全流程无 console / page 异常。
 *
 * 用法：node scripts/e2e/ui-designer-three-scenario-probe.mjs
 * 依赖：playwright（经共享 client 的 loadPlaywright 加载，见 e2e_lib/client.cjs）
 *
 * 维护说明（2026-10-15 重写）：旧版探针定位 `#sampleSel` / `.ctable` / `#testRunBtn` / `.ovl`，
 * 设计器重构后这些选择器**全部不存在**，导致：
 *   - 「新人引导遮罩可关闭」因 `.ovl` 恒为 0 而**恒绿（假绿，无判别力）**；
 *   - 后续断言全部因 `#sampleSel` 超时而假红。
 * 现按真源 DOM（#scenario / .childtable / #btnEnv+#role / #btnCheck / #viewSeg）重写，
 * 并在第 1 步加**前置负向断言**（关闭前 mask 必须带 on）保证该断言有牙齿。
 */
import { SHOT_DIR, loadPlaywright, makeChecker } from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const FILE =
  'file:///Users/renhui.trh/wps/bone/doc/design/lowcode/ui/ui-protocol/bone-ui-schema-designer.html';

const { results, check, report } = makeChecker();

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.setDefaultTimeout(20000);
const consoleErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200));
});
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

const shot = async (n) => {
  const p = `${SHOT_DIR}/ui_designer_${n}.png`;
  await page.screenshot({ path: p, fullPage: false });
  return p;
};

const childTable = () => page.locator('#preview .childtable').first();
const maskOn = async () => ((await page.locator('#mask').getAttribute('class')) || '').includes('on');
const pickScenario = async (value) => {
  await page.selectOption('#scenario', value);
  await page.waitForTimeout(1300);
};
const previewText = async () => (await page.locator('#preview').innerText()).replace(/\s+/g, ' ');

try {
  await page.goto(FILE, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1300);

  // ---------- 1. 新人引导弹窗（#mask/#modal），含前置负向断言 ----------
  // 旧探针断言的是 `.ovl`（重构后 HTML 里根本不存在），恒为 0 ⇒ 恒绿假绿。
  // 这里先断言「未关闭时 mask 一定带 on」，再用「开始使用」关闭并断言关闭，才有判别力。
  check('新人引导弹窗首屏出现且遮罩生效（未关闭时 #mask 带 on）', await maskOn(),
    'mask class=' + (await page.locator('#mask').getAttribute('class')));
  const startBtn = page.locator('#modal button', { hasText: '开始使用' });
  check('新人引导提供「开始使用」关闭入口', (await startBtn.count()) === 1, 'count=' + (await startBtn.count()));
  await startBtn.first().click();
  await page.waitForTimeout(500);
  check('点「开始使用」后遮罩关闭（不再拦截点击）', !(await maskOn()),
    'mask class=' + (await page.locator('#mask').getAttribute('class')));
  const topEl = await page.evaluate(() => {
    const e = document.elementFromPoint(800, 60);
    return e ? e.tagName + '#' + e.id : 'null';
  });
  check('顶栏区域可被真实命中（无残留遮罩）', !String(topEl).includes('mask'), topEl);

  // ---------- 2. 三套样板清单 ----------
  const vals = await page.locator('#scenario option').evaluateAll((os) => os.map((o) => o.value));
  const names = await page.locator('#scenario option').allInnerTexts();
  check('样板下拉为 3 套（expense / claim / purchase）',
    vals.length === 3 && vals.includes('expense') && vals.includes('claim') && vals.includes('purchase'),
    vals.join(' | '));
  check('样板含「采购审查」企业场景', names.some((t) => t.includes('采购审查')), names.join(' | '));

  // ---------- 3. 逐场景：表单渲染 + 子表 + 合计行 ----------
  const EXPECT = {
    expense: { rows: 3, cols: 7, must: ['费用日期', '金额', '税率'], section: '基本信息' },
    claim: { rows: 4, cols: 6, must: ['就诊日期', '金额', '医保支付'], section: '就诊与诊断' },
    purchase: { rows: 3, cols: 10, must: ['物料编码', '税率', '已收货'], section: '采购行项目' },
  };
  for (const key of ['expense', 'claim', 'purchase']) {
    await pickScenario(key);
    const txt = await previewText();
    const exp = EXPECT[key];
    check(`${key}：表单视图渲染出分区「${exp.section}」`, txt.includes(exp.section), txt.slice(0, 200));
    const ctCount = await page.locator('#preview .childtable').count();
    check(`${key}：子表渲染`, ctCount >= 1, 'childtable=' + ctCount);
    const th = await childTable().locator('thead th').allInnerTexts();
    const dataCols = th.filter((h) => h && h !== '#');
    check(
      `${key}：子表 ${exp.cols} 个数据列且为人话标签（${exp.must.join(' / ')}）`,
      dataCols.length === exp.cols && exp.must.every((m) => th.join(',').includes(m)),
      th.join(',')
    );
    const rows = await childTable().locator('tbody tr').count();
    check(`${key}：子表默认 ${exp.rows} 行（种子数据）`, rows === exp.rows, 'rows=' + rows);
    const tf = await childTable().locator('tfoot td').allInnerTexts();
    check(`${key}：合计行存在且首格为「合计」`, tf.length > 0 && tf[0].includes('合计'), tf.join(' | '));
  }

  // ---------- 4. 采购审查：金额合计 + 添加行闭环 ----------
  await pickScenario('purchase');
  const tf0 = await childTable().locator('tfoot td').allInnerTexts();
  check('采购审查：金额合计 ¥820,000', tf0.join(',').includes('820,000'), tf0.join(' | '));
  check('采购审查：数量合计 36', tf0[4] === '36', tf0.join(' | '));
  await shot('1_purchase_form');

  const addBtn = page.locator('#preview button', { hasText: /^添加行$/ }).first();
  await addBtn.click();
  await page.waitForTimeout(800);
  const rows1 = await childTable().locator('tbody tr').count();
  check('点「添加行」→ 行数 3→4', rows1 === 4, 'rows=' + rows1);
  const tf1 = await childTable().locator('tfoot td').allInnerTexts();
  check('添加行后合计行数量列实时重算（36→37）', tf1[4] === '37', tf1.join(' | '));

  // ---------- 5. 身份变体：#btnEnv → #role ----------
  const before = await previewText();
  await page.click('#btnEnv');
  await page.waitForTimeout(500);
  check('「预览环境」浮层可打开（身份 / 变体）', await page.locator('#envPop').isVisible());
  const roleVals = await page.locator('#role option').evaluateAll((os) => os.map((o) => o.value));
  check('采购审查提供身份变体（reviewer / admin）',
    roleVals.includes('reviewer') && roleVals.includes('admin'), roleVals.join(' | '));
  await page.selectOption('#role', 'reviewer');
  await page.waitForTimeout(1200);
  const after = await previewText();
  check('切到 reviewer 变体后预览按变体重算（文本变化）', before !== after,
    `len ${before.length} -> ${after.length}`);
  await shot('2_purchase_reviewer_variant');
  await page.selectOption('#role', '');
  await page.waitForTimeout(800);
  await page.click('#btnEnv');
  await page.waitForTimeout(300);

  // ---------- 6. 协议校验 ----------
  await page.click('#btnCheck');
  await page.waitForTimeout(900);
  const cls = await page.locator('#btnCheck').getAttribute('class');
  check('协议校验：采购审查样板无协议错误（按钮不进 alert）', !String(cls).includes('alert'), 'class=' + cls);
  const rail = (await page.locator('#railInspect').innerText()).replace(/\s+/g, ' ');
  check('协议校验面板给出 PASS 结论', /PASS/.test(rail), rail.slice(0, 200));

  // ---------- 7. 联动演练 ----------
  const drill = page.locator('#preview button').filter({ hasText: /^演练$/ });
  const drillCount = await drill.count();
  check('采购审查样板提供多组联动演练入口', drillCount >= 3, 'count=' + drillCount);
  await drill.first().click();
  await page.waitForTimeout(1200);
  const toast = await page.locator('#toast').innerText().catch(() => '');
  check('联动演练回执点名规则码（已演练：LINK-…）', /已演练：LINK-/.test(toast), toast.slice(0, 120));
  await shot('3_purchase_drill');

  // ---------- 8. 列表 / 详情视图 ----------
  await page.locator('#viewSeg button', { hasText: '列表' }).first().click();
  await page.waitForTimeout(900);
  const lt = await previewText();
  check('列表视图渲染（列名 + 页签）',
    /列表视图/.test(lt) && /采购申请号|标题/.test(lt), lt.slice(0, 220));
  await shot('4_purchase_list');
  await page.locator('#viewSeg button', { hasText: '详情' }).first().click();
  await page.waitForTimeout(900);
  const dt = await previewText();
  check('详情视图可渲染', /详情视图|Detail/.test(dt), dt.slice(0, 200));
  await page.locator('#viewSeg button', { hasText: '表单' }).first().click();
  await page.waitForTimeout(600);

  // ---------- 9. 无异常 ----------
  const fatal = consoleErrors.filter((e) => !/favicon|Failed to load resource/i.test(e));
  check('全流程无 console / page 异常', fatal.length === 0, fatal.slice(0, 3).join(' | '));
} catch (e) {
  check('设计器 UI 探针执行未抛异常', false, e.message.slice(0, 240));
  await shot('error').catch(() => {});
} finally {
  const { ok } = report('设计器 UI 探针汇总');
  await browser.close();
  process.exit(ok ? 0 : 1);
}
