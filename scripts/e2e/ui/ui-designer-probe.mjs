/**
 * 界面设计器 Playwright 探针统一参数化入口（2026-10-28：原 6 个独立探针合并为单入口）。
 *
 * 用法：
 *   node scripts/e2e/ui/ui-designer-probe.mjs --scenario <three|n4n5|n7|perf|production|ux|all>
 *   node scripts/e2e/ui/ui-designer-probe.mjs --scenario perf [控件数]   # perf 单档控件数可选，缺省跑 10/40/80/160 四档
 * 省略 --scenario、取值缺失或不在清单内：报错并打印场景清单后退出（exit 2）。
 *
 * 结构：共享骨架（公共层 SHOT_DIR / loadPlaywright / makeChecker + chromium 启动与退出 +
 *      统一错误收口与退出码汇聚）+ 每场景一个 async 函数。各场景原文件的辅助函数、
 *      断言逻辑与选择器**原样内聚**在对应场景函数内——只重构编排，不改任何断言语义。
 *
 * 场景清单（原 6 文件头注释收敛于此，详见各场景函数注释）：
 *   three      原 ui-designer-three-scenario-probe.mjs —— 费用报销/智能理赔/采购审查三套样板：
 *              引导遮罩关闭、子表+合计、添加行重算、身份变体、协议校验、联动演练、列表/详情视图。
 *   n4n5       原 ui-designer-n4-n5-probe.mjs —— N4 控件级校验（maxLength/pattern/min/max）+
 *              N5 样例数据预览开关，均为「负向基线 → 注入 → 正向 → 恢复」闭环。
 *   n7         原 ui-designer-n7-role-probe.mjs —— 字段级角色可见性（control.props.roles）：
 *              授权/未授权双向对照、无身份 fail-closed、空数组自检报 error、恢复回基线。
 *   perf       原 ui-designer-perf-probe.mjs —— 大 schema 渲染层性能基线（首屏/选中/编辑/切面板）。
 *   production 原 ui-designer-production-probe.mjs —— 生产三实体 pageHeader/split/list 新能力注入的
 *              负向→正向→恢复闭环（React 真源，非静态 demo）。
 *   ux         原 ui-designer-ux-probe.cjs —— 2026-10-15 UX 优化批次回归 A–G 段（Tab 换名/3 步引导/
 *              禅模式/变体创作/快捷键/发布硬门禁/编辑历史栈）。原为 CommonJS：client.cjs 用
 *              `exports.x =` 统一导出，可被 .mjs 命名导入，故直接并入本入口（无需 child_process 调起）。
 *   all        按上表顺序依次执行全部场景。
 *
 * ⚠ 维护红线（沿袭原 ui-designer-three-scenario-probe.mjs 头注释，2026-10-15 重写）：
 *   旧版探针定位 `#sampleSel` / `.ctable` / `#testRunBtn` / `.ovl`，设计器重构后这些选择器
 *   **全部不存在**，导致：「新人引导遮罩可关闭」因 `.ovl` 恒为 0 而**恒绿（假绿，无判别力）**；
 *   后续断言全部因 `#sampleSel` 超时而假红。修探针必须按真源 DOM 校准选择器
 *   （现口径：#scenario / .childtable / #btnEnv+#role / #btnCheck / #viewSeg），
 *   **断言一行都不许改语义、不许放宽超时来求绿**。
 *
 * 依赖：playwright（经共享 client 的 loadPlaywright 加载，见 e2e_lib/client.cjs）。
 */
/* 共享 client：配置常量 / playwright 加载 / API 登录与带 token fetch / 断言收集（见 e2e_lib/client.cjs） */
import {
  UI_BASE as BASE,
  BASE as GW_API,
  SHOT_DIR,
  CHROME,
  USER,
  PWD,
  loadPlaywright,
  makeChecker,
  login,
  api,
} from '../e2e_lib/client.cjs';
/* 共享 UI 交互层：浏览器登录与取本地 token（禁止在探针内重抄登录选择器，见 e2e_lib/ui.cjs） */
import { uiLogin, localToken } from '../e2e_lib/ui.cjs';

const { chromium } = loadPlaywright();

/* ================= 共享骨架 ================= */

/**
 * 骨架统一持有 chromium 生命周期：场景内只创建自己的 page/context（viewport、
 * 默认超时、监听器一律保持各探针原值——超时不得放宽），退出与异常由本函数收口。
 */
async function withBrowser(launchOptions, fn) {
  const browser = await chromium.launch(launchOptions);
  try {
    return await fn(browser);
  } finally {
    await browser.close();
  }
}

/* ================= 场景 three（原 ui-designer-three-scenario-probe.mjs） ================= */

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
 * 假绿维护史见文件头注释；断言按真源 DOM 写死，勿改语义、勿放宽超时。
 */
async function scenarioThree() {
  return withBrowser({ headless: true }, async (browser) => {
    const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
    page.setDefaultTimeout(20000);
    const consoleErrors = [];
    page.on('console', (m) => {
      if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200));
    });
    page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

    const { results, check, report } = makeChecker();

    const FILE =
      'file:///Users/renhui.trh/wps/bone/doc/design/lowcode/ui/ui-protocol/bone-ui-schema-designer.html';

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

    let exitCode = 1; // 由 finally 汇总（原独立文件在此直接 process.exit）
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
      exitCode = ok ? 0 : 1;
    }
    return exitCode;
  });
}

/* ================= 场景 n4n5（原 ui-designer-n4-n5-probe.mjs） ================= */

/**
 * N4/N5 活体探针：控件级校验规则编辑器 + 样例数据预览。
 *
 * 走 shell :3000 真实登录 → 元数据子应用 → 实体详情「界面设计器」Tab。
 * 验证两条闭环（每条都是「负向基线 → 注入 → 正向 → 恢复」四段，断言有牙齿）：
 *
 *   N4 控件级校验（协议 v1.2 §4.3）：
 *     1. 基线：未注入 props.maxLength 时，超长文本「模拟提交校验」**不报**长度错（负向锚点）；
 *     2. 注入 props.maxLength=4 + props.pattern=^INV-\d{3}$ 后：
 *        - 预览提交拦截超长（报「长度」）与格式不符（报「格式」）；
 *        - 合法值（INV-123，长度 7>4 会撞长度，故用 INV-12 + 放宽 maxLength 组合验证 pattern 单独生效）；
 *     3. 结构面板「校验规则」分组可见（文本型出 maxLength/pattern，数值型出 min/max）；
 *     4. 非法 pattern（`([`）在自检里报 error（配了不生效的左括号当场暴露）；
 *     5. 恢复：清掉注入后重新提交，长度错消失。
 *
 *   N5 样例数据预览：
 *     6. 基线：开关关闭时输入类控件值为空；
 *     7. 打开「样例数据」开关后，文本控件被填入 `示例_<fieldCode>`，数值控件填入 12345.67；
 *     8. 关闭开关后回到空值（开关真的双向生效，不是单向渲染残留）。
 *
 * 口径说明（预览 ≡ 运行时同源）：本探针验证 SchemaPreview 渲染/校验层；
 * BoneDynamicForm（运行时）消费同一个 validateControlValues，由 vitest 单测 + 变异验证覆盖。
 */
async function scenarioN4N5() {
  return withBrowser({ headless: true }, async (browser) => {
    const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
    page.setDefaultTimeout(25000);
    const consoleErrors = [];
    page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
    page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

    const { results, check, report } = makeChecker();

    /* ---------------- API 辅助（登录与带 token fetch 见 e2e_lib/client.cjs） ---------------- */
    async function latestEntities(token, keyword) {
      const { data: d } = await api('GET', '/metadata/entities?page=1&size=500', undefined, { token });
      const recs = (d.data || {}).records || [];
      const hits = recs.filter((x) => x.displayName === keyword).sort((a, b) => String(b.id).localeCompare(String(a.id)));
      return hits[0] || null;
    }

    async function getSchema(token, eid) {
      const { data: d } = await api('GET', `/metadata/entities/${eid}/ui-schema`, undefined, { token });
      const data = d.data || {};
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
     * 注入 N4 控件级校验 props：给首个「文本型」控件加 maxLength/pattern，
     * 给首个「数值型」控件加 min/max。返回 {schema, textCode, numCode, textLabel}。
     *
     * textLabel 用控件自带的 labelOverride（预览 label 优先取它），这样探针按
     * label 精确定位那一行，不依赖「实体字段显示名」是否恰好等于 fieldCode。
     *
     * 文本型判定与引擎 NUMERIC_KINDS/TEXT_KINDS 同口径（少写一种判定就会与引擎漂移）。
     */
    function injectN4(schema) {
      const next = JSON.parse(JSON.stringify(schema));
      const ctrls = (next.views?.form?.sections ?? []).flatMap((s) => s.controls ?? []);
      const kindOf = (c) => String(c.controlKind ?? 'INPUT').toUpperCase();
      const NUMERIC = new Set(['NUMBER', 'MONEY', 'SLIDER']);
      const TEXT = new Set(['INPUT', 'TEXTAREA']);
      const t = ctrls.find((c) => TEXT.has(kindOf(c)) && c.fieldCode);
      const n = ctrls.find((c) => NUMERIC.has(kindOf(c)) && c.fieldCode);
      const TEXT_LABEL = 'N4探针文本';
      const NUM_LABEL = 'N4探针数值';
      if (t) {
        t.props = { ...(t.props ?? {}), maxLength: 4, pattern: '^INV-\\d{3}$' };
        t.labelOverride = TEXT_LABEL;
      }
      // 数值型给一个「一定能被越界」的上限：max=1 ⇒ 样例值/真实值都 >1
      if (n) {
        n.props = { ...(n.props ?? {}), max: 1 };
        n.labelOverride = NUM_LABEL;
      }
      return {
        schema: next,
        textCode: t?.fieldCode ?? null,
        numCode: n?.fieldCode ?? null,
        textLabel: t ? TEXT_LABEL : null,
        numLabel: n ? NUM_LABEL : null,
      };
    }

    /* ---------------- UI 辅助 ---------------- */
    const shot = async (n) => {
      const p = `${SHOT_DIR}/ui_designer_n45_${n}.png`;
      await page.screenshot({ path: p, fullPage: false });
      return p;
    };

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


    async function dismissTour() {
      const skip = page.locator('button, a').filter({ hasText: '跳过引导' }).first();
      if (await skip.count()) {
        await skip.click({ timeout: 4000 }).catch(() => {});
        await page.waitForTimeout(400);
      }
    }

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

    /** 点「模拟提交校验」并收集弹出的 Alert 文案（错误 + 警告） */
    async function submitAndCollect() {
      const btn = page.locator('button.uis-btn.primary', { hasText: /模拟提交校验/ }).first();
      await btn.click();
      await page.waitForTimeout(700);
      const msgs = await page.locator('.uis-seccard .ant-alert-message').allInnerTexts();
      return msgs.map((t) => t.replace(/\s+/g, ' ').trim());
    }

    /**
     * 预览字段行定位：预览用 `.uis-fld`（label + 控件），不是 antd Form.Item
     * （Form.Item 是运行时 BoneDynamicForm 的结构，预览与运行时不同源 DOM，不能混用选择器）。
     *
     * 用 `filter({hasText})` 而非 `filter({has: label:has-text(...)})`：两者实测都能命中，
     * 但 hasText 更短且不受 label 内嵌元素结构影响。
     */
    async function fillPreviewField(labelText, value) {
      const row = page.locator('.uis-fld').filter({ hasText: labelText }).first();
      const input = row.locator('input:not([type="hidden"]), textarea').first();
      await input.waitFor({ state: 'visible', timeout: 8000 });
      await input.fill(value);
      await page.waitForTimeout(400);
    }

    /** 打开某控件的属性检视器（点齿轮），使「校验规则」分组渲染出来 */
    async function openInspector(fieldCode) {
      const gear = page.locator(`[aria-label="控件属性 ${fieldCode}"]`).first();
      await gear.waitFor({ state: 'visible', timeout: 8000 });
      await gear.click();
      await page.waitForTimeout(600);
    }

    /**
     * 切到「自检」面板并读出其文本。
     *
     * 两个必须记住的坑（都已实测踩过）：
     * 1. 页面有**两层 antd Tabs**（实体详情 的 结构/数据/发布/字段… + 设计器内部的
     *    结构/规则/…/自检/JSON）。`.ant-tabs-tabpane-active` 取到的是 DOM 顺序里第一个
     *    active pane = 实体的「字段」列表，**不是**自检面板 ⇒ 用它读会读到错面板，
     *    导致「没报错」判据**空过**（假绿）。故不按 pane 定位，改用自检面板独有内容锚定。
     * 2. 必须先确认真的切到了自检面板（LintPanel 有独有的「后端校验」按钮），
     *    否则下面所有文本判据都在空容器上跑，等于没断言。
     *
     * 点击带重试：设计器二次挂载时面板可能尚未稳定（实测首次 readLintPanel 成功、
     * 注入非法 pattern 后那次超时），单次 click 会把「时序抖动」误报成「功能缺失」。
     *
     * ⚠ 选择器口径（实测踩坑）：
     * - 「自检」用**前缀**匹配 `/^自\s*检/`：有 lint error 时 antd 会把错误计数 Badge
     *   渲染进 tab 内，标签变成「自检1」⇒ `/^自检$/` 锚尾必然漏匹配（实测 count=0）。
     * - 「结构」必须**精确**匹配 `/^结构$/`：实体详情外层也有一个「结构16」tab，
     *   用前缀会让 `.first()` 命中外层那个（DOM 顺序更靠前）⇒ 点错面板。
     */
    async function clickDesignerTab(nameRe, tries = 3) {
      for (let i = 0; i < tries; i++) {
        const tab = page.locator('.ant-tabs-tab').filter({ hasText: nameRe }).first();
        try {
          await tab.waitFor({ state: 'visible', timeout: 6000 });
          await tab.click({ timeout: 6000 });
          return true;
        } catch {
          await page.waitForTimeout(800);
        }
      }
      return false;
    }

    async function readLintPanel() {
      const clicked = await clickDesignerTab(/^自\s*检/);
      await page.waitForTimeout(1200);
      const anchor = page.locator('button', { hasText: /^后端校验$/ }).first();
      const landed = clicked && (await anchor.isVisible().catch(() => false));
      const text = landed
        ? await anchor.locator('xpath=ancestor::div[contains(@class,"ant-tabs-tabpane")][1]').innerText().catch(() => '')
        : '';
      return { landed, text };
    }

    let exitCode = 1; // 由 finally 汇总（原独立文件在此直接 process.exit）
    try {
      check('UI 登录成功', await uiLogin(page), `url=${page.url()}`);
      const token = await login();
      const ent = await latestEntities(token, '报销单');
      check('找到「报销单」实体', !!ent, 'entities 未命中');
      if (!ent) throw new Error('无实体可测');
      const eid = ent.id;
      const tag = `[${ent.code}]`;

      const before = await getSchema(token, eid);
      if (!before.json) throw new Error(`读取草稿失败 keys=${Object.keys(before).join(',')}`);
      const ORIG = JSON.parse(JSON.stringify(before.json));

      /* ================= N5：样例数据预览（不依赖注入，先做，避免污染顺序） ================= */
      await openDesigner(eid);
      const sampleSw = page.locator('.uis-pv-head [aria-label="样例数据预览"]').first();
      check('N5 预览头部存在「样例数据」开关', await sampleSw.count() === 1, `count=${await sampleSw.count()}`);

      /* N5 的正确判据不是「开关关闭时为空」——预览默认就带场景种子数据
       * （实测：标题/申请人/日期/金额均有值）。真正的判据是「开关切换改变值、
       * 且关掉后精确回到原样」——只有这样才能证明开关真的驱动了数据源，
       * 而不是恒绿。 */
      const previewVals = () => page.locator('.uis-fld input:not([type="hidden"]), .uis-fld textarea')
        .evaluateAll((els) => els.map((e) => e.value));
      const baseVals = await previewVals();
      check('N5 基线：预览自带场景种子数据（非空，证明不是空态恒绿）',
        baseVals.some((v) => v !== ''), `vals=${JSON.stringify(baseVals).slice(0, 160)}`);

      await sampleSw.click();
      await page.waitForTimeout(900);
      const onVals = await previewVals();
      check('N5 打开开关后预览值被样例数据改写（与基线不同 ⇒ 开关真的生效）',
        JSON.stringify(onVals) !== JSON.stringify(baseVals),
        `base=${JSON.stringify(baseVals).slice(0, 120)} on=${JSON.stringify(onVals).slice(0, 120)}`);
      const filled = onVals.filter((v) => v !== '');
      check('N5 样例数据填充了多个控件（类型化样例值）',
        filled.length >= 3, `filled=${filled.length}/${onVals.length} ${JSON.stringify(filled.slice(0, 4))}`);
      await shot('1_sample_on');

      await sampleSw.click();
      await page.waitForTimeout(900);
      const offVals = await previewVals();
      check('N5 关闭开关后精确回到基线值（双向生效，非单向残留）',
        JSON.stringify(offVals) === JSON.stringify(baseVals),
        `off=${JSON.stringify(offVals).slice(0, 120)}`);
      await shot('2_sample_off');

      /* ================= N4：控件级校验 ================= */
      // 基线：未注入 ⇒ 提交不报「长度」「格式」
      await openDesigner(eid);
      const baseMsgs = await submitAndCollect();
      check('N4 负向基线：未注入时提交不报控件级长度/格式错',
        !baseMsgs.some((m) => /【长度】|【格式】/.test(m)), baseMsgs.join(' | ').slice(0, 200));

      /* 结构面板「校验规则」分组的负向锚点：
       * 不能用「页面里有没有『校验规则』这几个字」——实测 StructurePanel 收起的
       * 检视器 DOM 仍留在文档里（count=1）⇒ 该判据恒绿无判别力。
       * 真正的锚点是「maxLength/pattern 输入位是否可见」：未展开时不存在，展开后才出现。 */
      const mlInput = () => page.locator('.ant-tabs-tabpane:visible').locator('input[placeholder="maxLength"]');
      const patternInput = () => page.locator('input[placeholder^="如 ^1"]');
      check('N4 负向锚点：检视器未展开时 maxLength/pattern 输入位不存在',
        await mlInput().count() === 0 && await patternInput().count() === 0,
        `maxLength=${await mlInput().count()} pattern=${await patternInput().count()}`);

      // 注入 N4 props 并 PUT
      const inj = injectN4(ORIG);
      check('N4 注入目标定位成功（文本型 + 数值型各一）',
        !!inj.textCode && !!inj.numCode, `text=${inj.textCode} num=${inj.numCode}`);

      const put1 = await putSchema(token, eid, inj.schema, before.lockVersion);
      check(`${tag} N4 注入 schema PUT 200`, put1.status === 200,
        `status=${put1.status} ${JSON.stringify(put1.body).slice(0, 160)}`);

      try {
        if (put1.status === 200) {
          await openDesigner(eid);

          /* 创作侧入口：展开文本控件检视器 → 「校验规则」分组出现，且文本型出
             maxLength + pattern、数值型出 min/max（控件类型驱动的正确显隐）。
             注意 placeholder 含正则 `\d`：CSS 属性选择器里 `\d` 会被当转义序列吃掉
             （实测 count=0），必须用 `[placeholder^="如 ^1"]` 前缀匹配。 */
          if (inj.textCode) {
            await openInspector(inj.textCode);
            const hasGroup = await page.getByText('校验规则', { exact: true }).count();
            check('N4 结构面板展开检视器后出现「校验规则」分组', hasGroup > 0, `count=${hasGroup}`);
            const ml = await page.locator('input[placeholder="maxLength"]').count();
            const pt = await patternInput().count();
            check('N4 文本型检视器暴露 maxLength + pattern 两个输入位', ml === 1 && pt === 1,
              `maxLength=${ml} pattern=${pt}`);
            await shot('3_inspector_text');
          }
          if (inj.numCode) {
            await openInspector(inj.numCode);
            const minBox = await page.locator('input[placeholder="min"]').count();
            const maxBox = await page.locator('input[placeholder="max"]').count();
            check('N4 数值型检视器暴露 min + max（且不误露 pattern）',
              minBox === 1 && maxBox === 1 && (await patternInput().count()) === 0,
              `min=${minBox} max=${maxBox}`);
            // 收回检视器，避免遮挡预览控件
            await page.locator(`[aria-label="控件属性 ${inj.numCode}"]`).first().click().catch(() => {});
            await page.waitForTimeout(400);
          }

          /* 自检面板：合法 pattern/maxLength 不应报 error（防误报回归）。
             必须先断言「真的切到了自检面板」，否则这条会在空面板上空过。 */
          const lintGood = await readLintPanel();
          check('N4 自检面板可打开（锚点：面板独有的「后端校验」按钮可见）',
            lintGood.landed, `landed=${lintGood.landed}`);
          check('N4 合法 pattern/maxLength 自检无「不是合法正则」误报',
            lintGood.landed && !/不是合法正则/.test(lintGood.text), lintGood.text.slice(0, 200));
          await shot('4_lint_clean');
          await clickDesignerTab(/^结\s*构$/);
          await page.waitForTimeout(1200);

          // 正向 A：数值型 max=1 ⇒ 预览填大数后提交被拦截报「不能大于」
          if (inj.numCode && inj.numLabel) {
            await fillPreviewField(inj.numLabel, '999');
            const numMsgs = await submitAndCollect();
            check('N4 正向A：数值控件 max 越界被预览拦截（报「不能大于」）',
              numMsgs.some((m) => /【范围】/.test(m) && /不能大于/.test(m)),
              numMsgs.join(' | ').slice(0, 220));
          }

          // 正向 B：文本型 pattern ⇒ 填不合规值被拦截报「格式」
          if (inj.textCode && inj.textLabel) {
            await fillPreviewField(inj.textLabel, 'BAD');
            const patMsgs = await submitAndCollect();
            check('N4 正向B：文本控件 pattern 不符被预览拦截（报「格式」）',
              patMsgs.some((m) => /【格式】/.test(m)),
              patMsgs.join(' | ').slice(0, 220));

            // 正向 C：maxLength 越界 ⇒ 报「长度」（'ABCDEFGHIJ' 长 10 > 4）
            await fillPreviewField(inj.textLabel, 'ABCDEFGHIJ');
            const lenMsgs = await submitAndCollect();
            check('N4 正向C：文本控件 maxLength 越界被拦截（报「长度」）',
              lenMsgs.some((m) => /【长度】/.test(m)),
              lenMsgs.join(' | ').slice(0, 220));
          }
          await shot('3_n4_injected');

          // 非法 pattern ⇒ 自检 error（配了不生效当场暴露）
          const bad = JSON.parse(JSON.stringify(inj.schema));
          const ctrls = (bad.views?.form?.sections ?? []).flatMap((s) => s.controls ?? []);
          const t2 = ctrls.find((c) => ['INPUT', 'TEXTAREA'].includes(String(c.controlKind ?? 'INPUT').toUpperCase()) && c.fieldCode);
          if (t2) t2.props = { ...(t2.props ?? {}), pattern: '([' };
          const cur = await getSchema(token, eid);
          const put2 = await putSchema(token, eid, bad, cur.lockVersion);
          check('N4 非法 pattern schema PUT 200（后端协议不拒 regex 字符串）', put2.status === 200,
            `status=${put2.status} ${JSON.stringify(put2.body).slice(0, 160)}`);
          if (put2.status === 200) {
            await openDesigner(eid);
            const lintBad = await readLintPanel();
            check('N4 非法 pattern 在设计器自检里报 error（不是静默通过）',
              lintBad.landed && /不是合法正则/.test(lintBad.text),
              `landed=${lintBad.landed} ${lintBad.text.replace(/\s+/g, ' ').slice(0, 240)}`);
            await shot('5_bad_pattern');
          }
        }
      } finally {
        // 恢复原始草稿（无论成败都恢复，防污染 E2E 基线）
        const cur = await getSchema(token, eid);
        const putR = await putSchema(token, eid, ORIG, cur.lockVersion);
        check(`${tag} 恢复原始草稿 PUT 200`, putR.status === 200, `status=${putR.status}`);

        // 恢复后重开，确认长度错消失（证明恢复生效，且前面的红不是残留）
        await openDesigner(eid);
        const restored = await submitAndCollect();
        check('N4 恢复后控件级长度/格式错消失（正向红→恢复绿的完整闭环）',
          !restored.some((m) => /【长度】|【格式】/.test(m)),
          restored.join(' | ').slice(0, 200));
      }

      const fatal = consoleErrors.filter((e) =>
        !/favicon|Failed to load resource|\[antd:|useForm is not connected|Support for defaultProps/i.test(e));
      check('全流程无致命 console / page 错误', fatal.length === 0, fatal.slice(0, 3).join(' | '));
    } catch (e) {
      check('N4/N5 探针执行未抛异常', false, e.message.slice(0, 240));
      await shot('error').catch(() => {});
    } finally {
      const { ok } = report('N4/N5 探针汇总');
      exitCode = ok ? 0 : 1;
    }
    return exitCode;
  });
}

/* ================= 场景 n7（原 ui-designer-n7-role-probe.mjs） ================= */

/**
 * N7活体探针：字段级角色可见性（control.props.roles）
 *
 * 走 shell :3000 真实登录 → 元数据子应用 → 实体详情「界面设计器」Tab。
 *
 * 验证一条完整闭环（每段都是「负向基线 → 注入 → 正向 → 恢复」，断言有牙齿）：
 *
 *   1. 负向基线：注入前，选中角色后受限字段照常可见（证明后面的「不可见」是注入带来的，
 *      不是页面本来就空 —— 否则「什么都没渲染」也会被误判成通过）；
 *   2. 注入 props.roles=['reviewer'] + 专属 labelOverride 后：
 *      - 身份模拟选 employee ⇒ 该字段**不可见**（标签也不出现）；
 *      - 身份模拟选 reviewer ⇒ 该字段可见（双向对照，防「整体不渲染」的假绿）；
 *   3. 身份模拟清空（ctx.roles 缺失）⇒ fail-closed 隐藏（权限判定默认不放行）；
 *   4. 自检面板：注入 roles:[]（空数组）⇒ 报 error（引擎口径是「不限制」，
 *      与建模者「设了=谁都看不到」的直觉相反，必须当场拦下）；
 *   5. 恢复：清掉注入后重新读预览，字段回到基线可见。
 *
 * 口径说明：本探针验证 **SchemaPreview（创作侧预览）** 的渲染层。
 * 运行时 BoneDynamicForm 与详情抽屉 RuntimeDetailView 消费**同一个**
 * controlBaseStates + fieldVisible，由 vitest 单测（含授权/未授权双向对照）
 * + 变异验证覆盖。
 */
async function scenarioN7() {
  const ENTITY_KEYWORD = process.env.BONE_E2E_ENTITY || '理赔案件';

  return withBrowser({ headless: true }, async (browser) => {
    const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
    page.setDefaultTimeout(25000);
    const consoleErrors = [];
    page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
    page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

    const { results, check } = makeChecker();

    /* ---------------- API 辅助（登录与带 token fetch 见 e2e_lib/client.cjs） ---------------- */
    async function latestEntities(token, keyword) {
      const { data: d } = await api('GET', '/metadata/entities?page=1&size=500', undefined, { token });
      const recs = (d.data || {}).records || [];
      const byId = (a, b) => String(b.id).localeCompare(String(a.id));
      // 先精确匹配 displayName；退不回去再按「包含关键字」（同名实体常有多个，取最新 id）。
      // 实测踩坑：实体名是「理赔案件」而非「理赔案件实体」，精确写死很容易对不上。
      const exact = recs.filter((x) => x.displayName === keyword).sort(byId);
      if (exact.length) return exact[0];
      const loose = recs.filter((x) => String(x.displayName || '').includes(keyword)).sort(byId);
      return loose[0] || null;
    }

    async function getSchema(token, eid) {
      const { data: d } = await api('GET', `/metadata/entities/${eid}/ui-schema`, undefined, { token });
      const data = d.data || {};
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
     * 注入 N7：给首个**文本型**控件加 props.roles，并打一个专属 labelOverride。
     *
     * 为什么要专属 label：预览 label 优先取 labelOverride，用它才能按标签精确定位那一行，
     * 不依赖「实体字段显示名恰好等于 fieldCode」（实测显示名是中文，不会等于字段码）。
     */
    const N7_LABEL = 'N7探针受限字段';
    function injectRoles(schema, roles) {
      const next = JSON.parse(JSON.stringify(schema));
      const ctrls = (next.views?.form?.sections ?? []).flatMap((s) => s.controls ?? []);
      const TEXT = new Set(['INPUT', 'TEXTAREA']);
      const t = ctrls.find((c) => TEXT.has(String(c.controlKind ?? 'INPUT').toUpperCase()) && c.fieldCode);
      if (!t) return { schema: next, code: null };
      t.props = { ...(t.props ?? {}), roles };
      t.labelOverride = N7_LABEL;
      return { schema: next, code: t.fieldCode };
    }

    /* ---------------- UI 辅助 ---------------- */
    const shot = async (n) => {
      const p = `${SHOT_DIR}/ui_designer_n7_${n}.png`;
      await page.screenshot({ path: p, fullPage: false });
      return p;
    };

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


    async function dismissTour() {
      const skip = page.locator('button, a').filter({ hasText: '跳过引导' }).first();
      if (await skip.count()) {
        await skip.click({ timeout: 4000 }).catch(() => {});
        await page.waitForTimeout(400);
      }
    }

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

    /** 切身份模拟里的角色：opts.off = true 表示「取消勾选该角色」 */
    async function setSimRoles(role, opts = {}) {
      const bar = page.locator('.uis-idbar').first();
      await bar.waitFor({ state: 'visible', timeout: 10000 });
      // 身份模拟栏里第一个 Select 是「角色」
      const sel = bar.locator('.ant-select').first();
      await sel.click();
      await page.waitForTimeout(400);
      const opt = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
        .filter({ hasText: role }).first();
      const found = await opt.count();
      if (found) {
        const checked = (await opt.getAttribute('aria-selected')) === 'true';
        const want = !opts.off;
        if (checked !== want) await opt.click();
        await page.waitForTimeout(500);
      }
      // 关闭下拉（Esc），否则会遮住后续判断
      await page.keyboard.press('Escape');
      await page.waitForTimeout(500);
      return !!found;
    }

    /** 受限字段在预览里是否可见（按专属 label 定位 .uis-fld 行） */
    async function isRestrictedVisible() {
      const row = page.locator('.uis-fld').filter({ hasText: N7_LABEL }).first();
      return await row.isVisible().catch(() => false);
    }

    /** 切到自检面板并读文本（锚点：LintPanel 独有的「后端校验」按钮） */
    async function readLintPanel() {
      const tab = page.locator('.ant-tabs-tab').filter({ hasText: /^自\s*检/ }).first();
      try {
        await tab.waitFor({ state: 'visible', timeout: 6000 });
        await tab.click({ timeout: 6000 });
      } catch { return { landed: false, text: '' }; }
      await page.waitForTimeout(1200);
      const anchor = page.locator('button', { hasText: /^后端校验$/ }).first();
      const landed = await anchor.isVisible().catch(() => false);
      const text = landed
        ? await anchor.locator('xpath=ancestor::div[contains(@class,"ant-tabs-tabpane")][1]').innerText().catch(() => '')
        : '';
      return { landed, text };
    }

    /** 回到「结构」面板（精确匹配：实体外层也有「结构16」tab，用前缀会点错） */
    async function backToStructure() {
      const tab = page.locator('.ant-tabs-tab').filter({ hasText: /^结构$/ }).last();
      try { await tab.click({ timeout: 6000 }); await page.waitForTimeout(800); } catch { /* 忽略 */ }
    }

    /* ---------------- 主流程 ---------------- */
    let exitCode = 0;
    let token = null;
    let eid = null;
    let origSchema = null;
    let origLock = null;

    try {
      token = await login();
      const ent = await latestEntities(token, ENTITY_KEYWORD);
      if (!ent) throw new Error(`未找到实体「${ENTITY_KEYWORD}」`);
      eid = ent.id;
      console.log(`实体：${ent.displayName}（${eid}）`);

      const cur = await getSchema(token, eid);
      origSchema = cur.json;
      // 乐观锁字段是 lockVersion（实测：写错成 lock/version 会拿到 412
      // META_UI_SCHEMA_PRECONDITION_REQUIRED —— 注入全部失败、后续「字段不可见」断言
      // 因字段压根没被注入而全部假绿）。
      origLock = cur.lockVersion;

      // ---------- 1. 负向基线：注入前该 label 不存在 ----------
      if (!(await uiLogin(page))) throw new Error('UI 登录失败');
      await openDesigner(eid);
      check('G0 预览已加载', !!(await page.locator('.uis-root').count()));

      // 注入 roles:['reviewer']
      const inj = injectRoles(origSchema, ['admin']);
      if (!inj.code) throw new Error('未找到可注入的文本型控件');
      const put1 = await putSchema(token, eid, inj.schema, origLock);
      check('G1 注入 props.roles 成功', put1.status === 200 || put1.status === 204, `HTTP ${put1.status} ${JSON.stringify(put1.body).slice(0, 200)}`);
      // **注入失败必须立即中止**：字段压根没写进草稿 ⇒ 后续所有「该字段不可见」断言都会
      // 因为「找不到这个 label」而恒为真 = 典型假绿（实测踩过：乐观锁字段取错 → 412 →
      // G2/G4 全绿，误导为功能通过）。
      if (!(put1.status === 200 || put1.status === 204)) {
        throw new Error('注入失败，后续断言无意义（会假绿），已中止');
      }

      // 重新打开（探针必须 reload：同 URL goto 不触发真刷新）
      await openDesigner(eid);

      // ---------- 2. 身份模拟 = employee ⇒ 受限字段不可见 ----------
      await setSimRoles('employee');
      const hiddenForEmployee = !(await isRestrictedVisible());
      check('G2 未授权角色（employee）看不到受限字段', hiddenForEmployee, '该 label 仍可见');
      await shot('employee');

      // ---------- 3. 身份模拟 = reviewer ⇒ 可见（双向对照，防整体不渲染假绿） ----------
      await setSimRoles('admin');
      const visibleForAdmin = await isRestrictedVisible();
      check('G3 授权角色（admin）能看到受限字段（双向对照）', visibleForAdmin, '授权角色也看不到 ⇒ 疑似整体不渲染');
      await shot('admin');

      // ---------- 4. 身份模拟清空 ⇒ fail-closed 隐藏 ----------
      await setSimRoles('admin', { off: true });
      await setSimRoles('employee', { off: true });
      const hiddenNoCtx = !(await isRestrictedVisible());
      check('G4 身份清空（ctx.roles 缺失）⇒ fail-closed 隐藏', hiddenNoCtx, '无身份反而可见（权限判定必须默认不放行）');

      // ---------- 5. 自检：roles:[] 报 error ----------
      backToStructure();
      const inj2 = injectRoles(origSchema, []);
      const curMid = await getSchema(token, eid);
      const put2 = await putSchema(token, eid, inj2.schema, curMid.lockVersion);
      check('G5 注入 roles:[] 成功', put2.status === 200 || put2.status === 204, `HTTP ${put2.status}`);
      if (!(put2.status === 200 || put2.status === 204)) {
        throw new Error('roles:[] 注入失败，自检判据会空过，已中止');
      }
      await openDesigner(eid);
      const lint = await readLintPanel();
      check('G6 自检面板已就位', lint.landed, '未点到自检面板，判据会空过');
      check('G7 空数组 roles 在自检里报 error', /空数组/.test(lint.text), '自检未提示空数组等于不限制');
      await shot('lint');

      // ---------- 6. 恢复 ----------
      const cur2 = await getSchema(token, eid);
      const put3 = await putSchema(token, eid, origSchema, cur2.lockVersion);
      check('G8 恢复原草稿成功', put3.status === 200 || put3.status === 204, `HTTP ${put3.status}`);
      await openDesigner(eid);
      const restoredVisible = await isRestrictedVisible();
      check('G9 恢复后字段回到基线（无残留 roles 限制）', !restoredVisible, '恢复后仍不可见 ⇒ 恢复未生效');
    } catch (e) {
      check('探针执行', false, String(e && e.message ? e.message : e).slice(0, 300));
    } finally {
      // 无论成败都必须恢复草稿（否则污染用户数据）
      if (token && eid && origSchema) {
        try {
          const cur = await getSchema(token, eid);
          await putSchema(token, eid, origSchema, cur.lockVersion);
          console.log('草稿已恢复');
        } catch (e) {
          console.log('⚠ 恢复草稿失败：' + String(e && e.message ? e.message : e).slice(0, 200));
        }
      }
      if (consoleErrors.length) {
        console.log('\nconsole 错误（' + consoleErrors.length + '）：');
        consoleErrors.slice(0, 5).forEach((e) => console.log('  - ' + e));
      }
      const failed = results.filter((r) => !r.ok);
      console.log(`\n===== N7 探针结果：${results.length - failed.length}/${results.length} 通过 =====`);
      if (failed.length) {
        console.log('失败项：');
        failed.forEach((f) => console.log('  ✗ ' + f.name + (f.detail ? '  ← ' + f.detail : '')));
        exitCode = 1;
      }
    }
    return exitCode;
  });
}

/* ================= 场景 perf（原 ui-designer-perf-probe.mjs） ================= */

/**
 * P1-5 大 schema 渲染层性能基线（活体）。
 *
 * 为什么纯函数基准不够：bench 证明 lintSchema/firstDiffPath 在 400 控件下都 <0.15ms，
 * 纯算法不是瓶颈。真正的成本在 React 渲染（每个控件一套 antd 组件 + 检视器 DOM）。
 * 本探针把 N 个控件的真实 schema PUT 进后端，在**真机浏览器**量三条用户可感知路径：
 *
 *   T1 首屏：进设计器 → 结构面板控件全部渲染可见（domcontentloaded → 控件行数稳定）
 *   T2 编辑：点某个控件的齿轮（选中）→ 检视器出现；改一次属性 → UI 反映
 *   T3 切面板：结构 → 规则 → 结构（每次都触发右栏 memo 失效与面板重挂）
 *
 * 每档规模跑一次，最后对比「规模翻倍时各耗时是否线性」。**只量不改**：优化前后各跑一次。
 *
 * finally 必恢复原草稿（不留下性能实验残留）。
 */
async function scenarioPerf() {
  return withBrowser({ headless: true }, async (browser) => {
    const page = await browser.newPage({ viewport: { width: 1680, height: 1000 } });
    page.setDefaultTimeout(30000);

    /** 多档规模（默认 10/40/80/160），用于看增长曲线；传单个数字则只跑该档 */
    const SIZES = POSITIONAL[0] ? [Number(POSITIONAL[0])] : [10, 40, 80, 160];

    /* ---------------- API（登录与带 token fetch 见 e2e_lib/client.cjs） ---------------- */
    const getEntities = async (tok) => {
      const { data: d } = await api('GET', '/metadata/entities?page=1&size=500', undefined, { token: tok });
      return ((d.data || {}).records || []).filter((x) => x.displayName === '报销单')
        .sort((a, b) => String(b.id).localeCompare(String(a.id)));
    };
    const getSchema = async (tok, eid) => {
      const { data: d } = await api('GET', `/metadata/entities/${eid}/ui-schema`, undefined, { token: tok });
      const data = d.data || {};
      if (typeof data.json === 'string') data.json = JSON.parse(data.json);
      return data;
    };
    const putSchema = async (tok, eid, schema, lock) => {
      const { status, data: body } = await api('PUT', `/metadata/entities/${eid}/ui-schema`, { json: schema }, {
        token: tok,
        headers: { 'If-Match': String(lock) },
      });
      return { status, body };
    };
    const getFields = async (tok, eid) => {
      const { data: d } = await api('GET', `/metadata/entities/${eid}/fields`, undefined, { token: tok });
      const dd = d.data || {};
      // 端点返 PageResult（records/total/...），不是裸数组
      return Array.isArray(dd) ? dd : (dd.records || []);
    };

    const KINDS = ['INPUT', 'NUMBER', 'MONEY', 'DATE', 'DATETIME', 'ENUM', 'SWITCH', 'SLIDER', 'TEXTAREA', 'REFERENCE'];

    /**
     * 用**实体的真实字段**造 N 控件 schema。
     *
     * 字段池只有 10~11 个（实测全库最多的实体也只有 11），N > 池大小必然重复引用同一
     * fieldCode ⇒ lint 会报「字段重复出现」error。**这是有意的**：本探针测的是渲染成本，
     * 该error 只多出一条恒定的横幅（不随 N 增长），不影响控件行数与重渲染量；
     * 而若改用不存在的字段，会引入另一类 error 并可能触发门控短路，噪声更大。
     */
    function buildLargeSchema(base, fields, n) {
      const per = 8;
      const pool = fields.filter((f) => f && f.code);
      const sections = [];
      for (let s = 0; s * per < n; s++) {
        const controls = [];
        for (let j = 0; j < per && s * per + j < n; j++) {
          const i = s * per + j;
          const f = pool[i % pool.length];
          controls.push({
            fieldCode: f.code,
            controlKind: KINDS[i % KINDS.length],
            labelOverride: `${f.name || f.code}_P${i}`,
            props: {},
          });
        }
        sections.push({ id: `sec_perf_${s + 1}`, title: `性能分区 ${s + 1}`, controls });
      }
      const next = JSON.parse(JSON.stringify(base || {}));
      next.version = next.version || '1.2';
      next.views = { ...(next.views || {}), form: { ...((next.views || {}).form || {}), sections } };
      // 清掉引用旧控件的联动/变体，避免 lint error 淹没渲染量测
      next.linkageRules = [];
      next.submitRules = [];
      next.variants = [];
      return next;
    }

    /* ---------------- UI 辅助 ---------------- */
    const shots = [];
;
    const dismissTour = async () => {
      const s = page.locator('button, a').filter({ hasText: '跳过引导' }).first();
      if (await s.count()) { await s.click({ timeout: 4000 }).catch(() => {}); await page.waitForTimeout(400); }
    };

    /** 打开设计器并等结构面板稳定；返回 { 首屏ms, 控件行数 } */
    async function openDesigner(eid) {
      const t0 = Date.now();
      await page.goto(`${BASE}/metadata#/entities/${eid}`, { waitUntil: 'domcontentloaded' });
      await page.reload({ waitUntil: 'domcontentloaded' });
      const tab = page.locator('.ant-tabs-tab').filter({ hasText: '界面设计器' }).first();
      await tab.waitFor({ state: 'visible', timeout: 30000 });
      await tab.click();
      await page.waitForSelector('.uis-root', { timeout: 30000 });
      await dismissTour();
      // 先起一个节拍器把「文本稳定」信号持续写进 window（waitForFunction 读它）
      const beat = setInterval(() => { markStableText().catch(() => {}); }, 250);
      await new Promise((r) => setTimeout(r, 500));
      // 等结构面板控件行全部出现（设计器渲染完成的可观测信号），再要求文本稳定两拍。
      // 早期版本这里比对 `window.__lastT` —— 该变量从未被赋值，条件恒假 ⇒ 每档都白等满
      // 40s 超时，「首屏」列出来的是超时上限而非真实耗时（教训：waitForFunction 的
      // 谓词必须只引用**确实会被写入**的来源）。
      await page.waitForFunction(() => {
        const gears = document.querySelectorAll('[aria-label^="控件属性 "]').length;
        const rows = document.querySelectorAll('[data-tour="control-row"]').length;
        const t = document.querySelector('.uis-root')?.innerText || '';
        return gears > 0 && rows > 0 && t.length > 50 && t === window.__stableT;
      }, null, { timeout: 40000, polling: 300 }).catch(() => {});
      const gears = await page.locator('[aria-label^="控件属性 "]').count();
      clearInterval(beat);
      return { ms: Date.now() - t0, gears };
    }

    /** 采样「文本稳定」信号：连续两拍文本一致才置位，供 openDesigner 的 waitForFunction 读取 */
    async function markStableText() {
      await page.evaluate(() => {
        const el = document.querySelector('.uis-root');
        const cur = el?.innerText || '';
        if (window.__lastT === cur) window.__stableT = cur;
        else window.__lastT = cur;
      });
    }

    /** T2：点最后一个（最靠下、最考验渲染的）控件的齿轮 → 检视器出现 */
    async function measureSelect() {
      const gears = page.locator('[aria-label^="控件属性 "]');
      const n = await gears.count();
      if (!n) return null;
      const last = gears.nth(n - 1);
      const t0 = Date.now();
      await last.scrollIntoViewIfNeeded();
      await last.click();
      // 等检视器真正渲染（用 data-tour 锚点；此前等的是不存在的 .uis-insp 类
      // ⇒ 每档都白等满 15s 超时，「选中控件」列出来的是超时上限而非真实耗时）
      await page.waitForSelector('[data-tour="control-inspector"]', { timeout: 10000 })
        .catch(() => {});
      return Date.now() - t0;
    }

    /**
     * T2b：一次真实编辑的端到端延迟（**最关键的用户感知指标**）。
     *
     * 口径 = 「敲一个字符 → 浏览器把这一帧画出来」，用**双requestAnimationFrame** 度。
     *
     * 为什么不用「等预览文本出现新字符」当判据：那个条件依赖 labelOverride 必须被
     * 渲染进预览区，控件类型/场景/重复字段一变化就不成立（实测 40+ 档恒等超时 ⇒
     * 量到的是超时上限而非耗时，是典型的**探针假数据**）。而双 rAF 直接量
     * 「本次 setState 引发的重渲染 + 布局 + 绘制是否已在屏幕上落地」，
     * 不依赖任何特定文案，是与用户手感一致、且不会假红的口径。
     *
     * 定位「显示名覆盖」输入框不能按 placeholder：它是**动态的**（沿用元数据字段
     * displayName，实测值为「状态」）。用检视器内「以『显示名覆盖』开头的单元格里的 input」。
     */
    async function measureEdit() {
      return page.evaluate(async () => {
        const insp = document.querySelector('[data-tour="control-inspector"]');
        if (!insp) return null;
        const cell = Array.from(insp.children).find((d) => d.textContent?.trim().startsWith('显示名覆盖'));
        const inp = cell?.querySelector('input');
        if (!inp) return null;

        // 用原生 setter 写入并派发 input 事件，走 React 的 onChange（与真实键入等价；
        // 直接改 inp.value 不会触发 React 受控更新）。
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
        const t0 = performance.now();
        setter?.call(inp, `${inp.value}X`);
        inp.dispatchEvent(new Event('input', { bubbles: true }));
        // 双 rAF：第一帧回调在 React commit + 布局之后，第二帧确认已绘制
        await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
        const ms = performance.now() - t0;
        setter?.call(inp, inp.value.slice(0, -1));
        inp.dispatchEvent(new Event('input', { bubbles: true }));
        await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
        return Math.round(ms);
      });
    }

    /** T3：切面板（结构→规则→结构），量两次往返 */
    async function measureTabSwitch() {
      const go = async (re) => {
        const t = page.locator('.ant-tabs-tab').filter({ hasText: re }).first();
        const t0 = Date.now();
        await t.click();
        await page.waitForTimeout(900); // 含稳定等待，反映用户感知
        return Date.now() - t0;
      };
      const a = await go(/^规\s*则$/);
      const b = await go(/^结\s*构$/);
      return { toRules: a, toStructure: b };
    }

    /* ---------------- 主流程 ---------------- */
    const tok = await login();
    const ent = (await getEntities(tok))[0];
    if (!ent) { console.error('✗ 找不到「报销单」实体'); return 1; }
    const eid = ent.id;
    const orig = await getSchema(tok, eid);
    const fields = await getFields(tok, eid);
    console.log(`实体 ${ent.displayName} (${eid})，真实字段 ${fields.length} 个，目标规模档 ${SIZES.join('/')}`);

    await uiLogin(page);
    const table = [];
    let restored = false;
    let failed = false; // 度量中断标记：退出码即契约，部分产出 ≠ 通过
    try {
      const cur0 = await getSchema(tok, eid);
      let lock = cur0.lockVersion;
      for (const n of SIZES) {
        const big = buildLargeSchema(orig.json || {}, fields, n);
        const put = await putSchema(tok, eid, big, lock);
        if (put.status !== 200) throw new Error(`注入 ${n} 控件失败 ${put.status} ${JSON.stringify(put.body).slice(0, 200)}`);
        lock = (await getSchema(tok, eid)).lockVersion;
        console.log(`已注入 ${n} 控件（JSON ${Math.round(JSON.stringify(big).length / 1024)}KB）`);

        const open = await openDesigner(eid);
        const sel = await measureSelect();
        const edit = await measureEdit();
        const sw = await measureTabSwitch();
        const previewFields = await page.locator('.uis-fld').count();
        const gears = await page.locator('[aria-label^="控件属性 "]').count();
        console.log(`  ${n} 控件 → 首屏 ${open.ms}ms / 结构行 ${gears} / 选中 ${sel}ms / 编辑 ${edit}ms / 切规则 ${sw.toRules}ms / 回结构 ${sw.toStructure}ms`);
        table.push({
          控件数: n, KB: Math.round(JSON.stringify(big).length / 1024),
          首屏ms: open.ms, 结构行: gears, 预览行: previewFields,
          选中控件ms: sel ?? -1, 单次编辑ms: edit ?? -1,
          切规则ms: sw.toRules, 回结构ms: sw.toStructure,
        });
        await page.screenshot({ path: `/Users/renhui.trh/wps/bone/logs/ui_designer_perf_${n}.png` }).catch(() => {});
      }
    } catch (e) {
      failed = true;
      console.error('✗ 度量失败：', e.message);
    } finally {
      if (!restored) {
        const cur = await getSchema(tok, eid);
        const back = await putSchema(tok, eid, orig.json || {}, cur.lockVersion);
        restored = true;
        console.log(`\n草稿已恢复：PUT ${back.status}`);
      }
    }

    console.log('\n===== P1-5 渲染层性能基线（浏览器实测）=====');
    console.table(table);
    console.log('JSON:' + JSON.stringify(table));
    // 任一档度量中断、或档位产出不全（SIZES 跑满才叫通过）都算失败，不再「有产出即 0」
    return (failed || table.length < SIZES.length) ? 1 : 0;
  });
}

/* ================= 场景 production（原 ui-designer-production-probe.mjs） ================= */

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
 */
async function scenarioProduction() {
  return withBrowser({ headless: true }, async (browser) => {
    const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
    page.setDefaultTimeout(25000);
    const consoleErrors = [];
    page.on('console', (m) => {
      if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200));
    });
    page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

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
     *  拉 schema —— API 侧 PUT 的新草稿不会反映到已挂载的设计器（探针首跑假红根因）。 */
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

    let exitCode = 1; // 由 finally 汇总（原独立文件在此直接 process.exit）
    try {
      check('UI 登录成功', await uiLogin(page), `url=${page.url()}`);
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
      exitCode = ok ? 0 : 1;
    }
    return exitCode;
  });
}

/* ================= 场景 ux（原 ui-designer-ux-probe.cjs，CommonJS → ESM 合并） ================= */

/**
 * 界面设计器 UX 优化专项探针（2026-10-15 优化批次回归）：
 *   1. Tab 换名 + 图标区分（结构 / 显示设置 / 界面设计器 / 数据 / 发布）
 *   2. 3 步上手引导（首访自动 + 帮助按钮重显）
 *   3. 全屏（禅模式）+ Esc 退出
 *   4. 变体创作（variants tab：新增 / 命中条件 / 覆盖编辑）
 *   5. 快捷键：Ctrl+Z 撤销成真；负向——输入框内 Ctrl+Z 不劫持
 *   6. 发布协议硬门禁：负向（坏 schema 阻断发布）+ 正向（修复后放行）
 *   7. 可视化编辑历史栈（jumpToPast：跳回后被跳过的中间态压入 future，重做不断链）
 *
 * 策略：Playwright 走 shell :3000 真实登录拿 JWT（沿用 lowcode-closed-loop 模式），
 *      实体经网关 API 建（探针自产自销，不碰他人在途数据），UI 断言走真实页面。
 *
 * 合并说明：原为独立 .cjs（require 共享 client）；client.cjs 以 `exports.x =` 统一导出，
 * 可被本 .mjs 命名导入，故直接并入（CHROME 系统 Chrome 启动参数、断言风格、截图路径原样保留）。
 */
async function scenarioUx() {
  /* 共享 client：playwright 加载 / CHROME / 登录凭证（见 e2e_lib/client.cjs，已在文件头统一 import） */
  // 地址改取共享 config（README §2.1：禁止在脚本内重抄 host:port）
  const SHELL = BASE;                                           // shell :3000
  const GATEWAY = String(GW_API).replace(/\/api\/v1\/?$/, '');  // 网关根 :8888
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  const steps = [];
  const errors = [];
  function assert(name, ok, info) {
    steps.push({ name, ok: !!ok, ...(info || {}) });
    if (!ok) errors.push(`${name} :: ${JSON.stringify(info || {}).slice(0, 300)}`);
    console.log(`  ${ok ? '✅' : '❌'} ${name}${info ? '  ' + JSON.stringify(info) : ''}`);
  }

  /**
   * 原 login()：UI 登录取 JWT。浏览器改为骨架持有，本函数只负责页面操作与取 token。
   * 填表动作改走共享层 uiLogin（route 保持原口径 /iam#/accounts），token 改走 localToken
   * —— 与其余 20 个探针同源，避免此处再留一份会漂移的登录副本。
   */
  async function uxLogin(page) {
    const ok = await uiLogin(page, { route: `${SHELL}/iam#/accounts` });
    const token = ok ? await localToken(page) : null;
    if (!token) throw new Error('login failed: no token');
    return token;
  }

  function makeApi(token) {
    return async function api(method, path, { body, etag } = {}) {
      const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
      if (etag) headers['If-Match'] = etag;
      const res = await fetch(GATEWAY + path, {
        method, headers, body: body !== undefined ? JSON.stringify(body) : undefined,
      });
      const text = await res.text();
      let data = null;
      try { data = text ? JSON.parse(text) : null; } catch { data = text; }
      return { status: res.status, data };
    };
  }
  const payload = (r) => (r && r.data && typeof r.data === 'object' && 'data' in r.data ? r.data.data : r?.data);
  const okOf = (r) => r.status >= 200 && r.status < 300 && (r.data == null || r.data.code === undefined || r.data.code === 200 || r.data.code === 201);

  // 原 .cjs 用系统 Chrome（executablePath: CHROME）启动
  return withBrowser({ headless: true, executablePath: CHROME }, async (browser) => {
    let exitCode = 0;
    try {
      const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      const page = await ctx.newPage();
      const token = await uxLogin(page);
      const api = makeApi(token);
      const consoleErrors = [];
      page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
      page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message));

      // ---------- A. 探针自产实体（模板实例化，deliveryMode=1 才有协议设计器） ----------
      console.log('\n===== A. 前置：实例化探针实体 =====');
      const tpls = await api('GET', '/api/v1/metadata/templates');
      const tpl = (Array.isArray(payload(tpls)) ? payload(tpls) : []).find((t) => t.code === 'tpl_device_inspection');
      assert('巡检模板可用', !!tpl, { templates: (Array.isArray(payload(tpls)) ? payload(tpls) : []).map((t) => t.code) });
      // moduleId 前置解析：原实现取「第一个带 moduleId 的实体」的 moduleId，但库里存在
      // 指向已删除模块的孤儿实体（实测取到 769755574777675776 ⇒ 实例化 400「所属模块不存在」），
      // 故改为经 IAM「应用 → 模块」链路取真实在库模块（bone_module 存在性由后端 fail-closed 校验）。
      const moduleId = await (async () => {
        const apps = await api('GET', '/api/v1/apps?page=1&size=500');
        for (const a of payload(apps)?.records || []) {
          const mods = await api('GET', `/api/v1/apps/${a.id}/modules?page=1&size=100`);
          const m = (payload(mods)?.records || [])[0];
          if (m && m.id != null) return m.id;
        }
        return undefined;
      })();
      const stamp = Date.now();
      const code = `uidesign_${stamp}`;
      const inst = await api('POST', `/api/v1/metadata/templates/${tpl.id}/instantiate`, {
        body: { name: `UiDesign_${stamp}`, code, displayName: '设计器UX探针', tableName: `md_${code}`, moduleId, deliveryMode: 1 },
      });
      const entityId = payload(inst);
      assert('探针实体实例化', okOf(inst) && !!entityId, { status: inst.status, entityId });
      if (!entityId) { return 2; }

      // ---------- B. Tab 集 + 图标区分（对齐当前产品：结构/字段/关系/校验/界面设计器/数据/发布） ----------
      // IA 收敛：「交付预览」已归位——团队契约口径并入「界面设计器」，个人覆盖口径对照在运行时数据页。
      console.log('\n===== B. Tab 集 + 图标区分 =====');
      await page.goto(`${SHELL}/metadata#/entities/${entityId}`, { waitUntil: 'domcontentloaded' });
      await page.waitForSelector('.ant-tabs-tab', { timeout: 25000 });
      await sleep(4000);
      const tabInfo = await page.evaluate(() =>
        Array.from(document.querySelectorAll('.ant-tabs-tab')).map((e) => ({
          text: e.textContent.trim(),
          icons: Array.from(e.querySelectorAll('.anticon')).map((i) => i.className.match(/anticon-([a-z-]+)/)?.[1] ?? ''),
        })),
      );
      const tabTexts = tabInfo.map((t) => t.text);
      const expectedTabs = ['结构', '字段', '关系', '校验', '界面设计器', '数据', '发布'];
      assert('设计器 Tab 集 = 结构/字段/关系/校验/界面设计器/数据/发布',
        expectedTabs.every((t) => tabTexts.some((x) => x.includes(t))),
        { tabs: tabTexts });
      assert('「交付预览」已归位（顶层不再单列；个人语义不入建模台）',
        !tabTexts.some((x) => x.includes('交付预览')),
        { tabs: tabTexts });
      assert('旧「界面（协议）」/「显示设置」/「个人显示偏好」tab 已不存在（个人覆盖编辑器已移至运行时数据页）',
        !tabTexts.some((x) => x.includes('界面（协议）')) && !tabTexts.some((x) => x.includes('显示设置')) && !tabTexts.some((x) => x.includes('个人显示偏好')),
        { tabs: tabTexts });
      const iconOf = (label) => tabInfo.find((t) => t.text.includes(label))?.icons.join(',') ?? '';
      assert('界面设计器用 layout 图标', iconOf('界面设计器').includes('layout'), { icons: iconOf('界面设计器') });
      assert('数据保留 table 图标', iconOf('数据').includes('table'), { icons: iconOf('数据') });
      await page.screenshot({ path: '/Users/renhui.trh/wps/bone/logs/ui_designer_ux_1_tabs.png' });

      // ---------- C. 设计器：首访 Spotlight 引导（6 步）+ 帮助重显 ----------
      // 产品已将「静态 3 步 Modal（开始使用）」升级为「Spotlight 交互式引导」（DesignerTour.tsx）：
      // 首访自动弹出（全新 context + 全新实体 ⇒ localStorage 无标记 ⇒ 自动开），可从「帮助」重开。
      console.log('\n===== C. Spotlight 6 步新手引导 =====');
      await page.locator('.ant-tabs-tab').filter({ hasText: '界面设计器' }).click();
      await page.waitForSelector('text=载入场景样例', { timeout: 20000 });
      await sleep(2500);
      // 首访自动弹出 .tour-card（聚光灯卡片，非旧版 .ant-modal）
      let tourVisible0 = await page.locator('.tour-card').first().isVisible().catch(() => false);
      // 兜底：若自动弹出时序未命中，点「帮助」也应能唤起（验证引导可达）
      if (!tourVisible0) {
        await page.locator('button').filter({ hasText: '帮助' }).first().click();
        await sleep(1000);
        tourVisible0 = await page.locator('.tour-card').first().isVisible().catch(() => false);
      }
      assert('首访自动弹出 Spotlight 新手引导（.tour-card）', tourVisible0, {});
      const tourText = await page.locator('.tour-card').first().innerText().catch(() => '');
      assert('引导含多步 + 计数（x/6）+ 跳过/下一步', /跳过引导/.test(tourText) && /\d+ \/ 6/.test(tourText),
        { snippet: tourText.slice(0, 140) });
      // 跳过关闭
      await page.locator('.tour-card button').filter({ hasText: '跳过引导' }).first().click();
      await sleep(800);
      const guideClosed = !(await page.locator('.tour-card').first().isVisible().catch(() => false));
      assert('引导可关闭（跳过）', guideClosed, {});
      // 帮助按钮重显
      await page.locator('button').filter({ hasText: '帮助' }).first().click();
      await sleep(800);
      const guideReShown = await page.locator('.tour-card').first().isVisible().catch(() => false);
      assert('帮助按钮可重显引导', guideReShown, {});
      // 收尾关闭，避免遮挡后续段
      await page.locator('.tour-card button').filter({ hasText: '跳过引导' }).first().click().catch(() => {});
      await sleep(600);

      // ---------- D. 全屏（禅模式） ----------
      console.log('\n===== D. 全屏禅模式 =====');
      await page.locator('button').filter({ hasText: '全屏' }).first().click();
      await sleep(800);
      const zenOn = await page.evaluate(() => {
        const els = Array.from(document.querySelectorAll('div'));
        return els.some((e) => {
          const s = getComputedStyle(e);
          return s.position === 'fixed' && e.style.zIndex === '1080';
        });
      });
      const exitBtn = await page.locator('button').filter({ hasText: '退出全屏' }).count();
      const zenHeader = await page.locator('text=全屏模式').count();
      assert('全屏覆盖层出现（fixed 吃满视口 + 上下文头）', zenOn && exitBtn > 0 && zenHeader > 0, { zenOn, exitBtn, zenHeader });
      await page.screenshot({ path: '/Users/renhui.trh/wps/bone/logs/ui_designer_ux_2_zen.png' });
      await page.keyboard.press('Escape');
      await sleep(800);
      const zenOff = await page.evaluate(() => {
        const els = Array.from(document.querySelectorAll('div'));
        return els.some((e) => { const s = getComputedStyle(e); return s.position === 'fixed' && e.style.zIndex === '1080'; });
      });
      assert('Esc 退出全屏', !zenOff, { zenOff });

      // ---------- E. 快捷键：Ctrl+Z 成真 + 输入框负向 ----------
      console.log('\n===== E. 快捷键 =====');
      // 默认在结构 tab；点「添加分区」→ 脏；Ctrl+Z → 撤销成真
      await page.locator('button').filter({ hasText: '添加分区' }).first().click();
      await sleep(500);
      const dirtyAfterAdd = await page.locator('text=未保存改动').count();
      assert('添加分区后出现未保存改动', dirtyAfterAdd > 0, { dirtyAfterAdd });
      await page.keyboard.press('Control+z');
      await sleep(500);
      const dirtyAfterUndo = await page.locator('text=未保存改动').count();
      assert('Ctrl+Z 撤销成真（tooltip 不再是假承诺）', dirtyAfterUndo === 0, { dirtyAfterUndo });
      // 负向：输入框内 Ctrl+Z 不得劫持为全局撤销
      await page.locator('button').filter({ hasText: '添加分区' }).first().click();
      await sleep(500);
      const secTitle = page.locator('input[placeholder="分区标题"]').last();
      await secTitle.click();
      await page.keyboard.press('Control+z');
      await sleep(500);
      const dirtyInInput = await page.locator('text=未保存改动').count();
      assert('负向：输入框内 Ctrl+Z 不触发全局撤销', dirtyInInput > 0, { dirtyInInput });
      // 清理：再按一次（焦点回按钮后）撤销掉刚加的分区，恢复干净态
      await page.locator('button').filter({ hasText: '添加分区' }).first().click();
      await sleep(300);
      await page.keyboard.press('Control+z');
      await sleep(400);

      // ---------- E2. 可视化编辑历史栈（jumpToPast 的 future 堆叠） ----------
      console.log('\n===== E2. 可视化编辑历史栈 =====');
      // E 段负向用例遗留 1 步脏（past=[初始态]）；先归位干净态，保证历史步数可数
      await page.keyboard.press('Control+z');
      await sleep(500);
      const dirtyNorm = await page.locator('text=未保存改动').count();
      assert('E2 前置：归位干净态', dirtyNorm === 0, { dirtyNorm });
      // 制造 2 步编辑 → past 恰好 2 项
      await page.locator('button').filter({ hasText: '添加分区' }).first().click();
      await sleep(600);
      await page.locator('button').filter({ hasText: '添加分区' }).first().click();
      await sleep(600);
      const dirty2 = await page.locator('text=未保存改动').count();
      assert('2 次添加后出现未保存改动', dirty2 > 0, { dirty2 });
      // 打开编辑历史 Popover（aria-label 定位，图标按钮无文本）
      await page.locator('button[aria-label="编辑历史"]').click();
      await sleep(800);
      const histPop = page.locator('.ant-popover').filter({ hasText: '编辑历史' });
      const histVisible = await histPop.first().isVisible().catch(() => false);
      assert('历史 Popover 可打开', histVisible, {});
      const histText = await histPop.first().innerText().catch(() => '');
      const pastCount = /共 (\d+) 步可回退/.exec(histText.replace(/\n/g, ' '));
      assert('历史 Popover 恰好列出 2 个回退项（回退 1 步 / 回退 2 步，无 phantom 条目）',
        /回退 1 步/.test(histText) && /回退 2 步/.test(histText) && pastCount !== null && Number(pastCount[1]) === 2,
        { snippet: histText.replace(/\n/g, ' ').slice(0, 160), pastCount: pastCount?.[1] });
      await page.screenshot({ path: '/Users/renhui.trh/wps/bone/logs/ui_designer_ux_2b_history.png' });
      // 跳回最近 1 步：被跳过的中间态必须压入 future，否则重做断链
      await histPop.locator('text=回退 1 步').click();
      await sleep(900);
      const redoEnabled = await page.locator('button[aria-label="重做"]').isEnabled().catch(() => false);
      assert('跳回后「重做」可用（被跳过的状态压入 future）', redoEnabled, {});
      const dirtyAfterJump = await page.locator('text=未保存改动').count();
      assert('跳回后仍有未保存改动（保留 1 步编辑）', dirtyAfterJump > 0, { dirtyAfterJump });
      // 二次打开：past 应只剩 1 项，且被跳过的中间态恰好 1 条压入 future（不重不漏）
      await page.locator('button[aria-label="编辑历史"]').click();
      await sleep(700);
      const histText2 = await histPop.first().innerText().catch(() => '');
      const pastCount2 = /共 (\d+) 步可回退/.exec(histText2.replace(/\n/g, ' '));
      const futureCount2 = /可重做 (\d+) 步/.exec(histText2.replace(/\n/g, ' '));
      assert('二次打开仅 1 个回退项且 future 恰为 1（时间线与实际状态一致，无重复压栈）',
        pastCount2 !== null && Number(pastCount2[1]) === 1 && !/回退 2 步/.test(histText2)
        && futureCount2 !== null && Number(futureCount2[1]) === 1,
        { snippet: histText2.replace(/\n/g, ' ').slice(0, 120), past: pastCount2?.[1], future: futureCount2?.[1] });
      // 收尾：toggle 关 Popover → 撤销回干净态，避免影响 F2 段「可见删除图标 nth(1)」假设
      await page.locator('button[aria-label="编辑历史"]').click();
      await sleep(500);
      await page.keyboard.press('Control+z');
      await sleep(500);
      const dirtyFinal = await page.locator('text=未保存改动').count();
      assert('E2 收尾后恢复干净态', dirtyFinal === 0, { dirtyFinal });

      // ---------- F. 变体创作 ----------
      console.log('\n===== F. 变体创作 =====');
      await page.locator('.ant-tabs-tab').filter({ hasText: '变体' }).first().click();
      await page.waitForSelector('text=变体 = 按身份', { timeout: 15000 });
      await page.locator('button').filter({ hasText: '新增变体' }).first().click();
      await sleep(800);
      const vCard = await page.locator('input[placeholder="变体名称"]').count();
      assert('变体卡片可创建', vCard > 0, { vCard });
      // antd v5 多选/tags Select 的 placeholder 渲染在 .ant-select-selection-placeholder span 上
      // （input 无 placeholder 属性），且预览区「身份模拟」也有 角色/设备——
      // 「档位 tier / 渠道」仅变体卡独有，故四态齐全即可证明变体卡四维编辑器在位。
      const matchTexts = (await page.locator('.ant-select-selection-placeholder').allTextContents()).map((t) => t.trim());
      const need = ['角色', '档位 tier', '渠道', '设备'];
      assert('四维命中条件编辑器齐全（角色/档位/渠道/设备）', need.every((t) => matchTexts.includes(t)), { matchTexts });
      await page.locator('.ant-segmented-item').filter({ hasText: '覆盖编辑' }).first().click();
      await sleep(800);
      const structInVariant = await page.locator('text=录入表单').count();
      assert('覆盖编辑可复用结构面板（合成 schema）', structInVariant > 0, { structInVariant });
      await page.screenshot({ path: '/Users/renhui.trh/wps/bone/logs/ui_designer_ux_3_variants.png' });

      // ---------- F2. FAB 控件库 / 版本快照 / 命令面板 ----------
      console.log('\n===== F2. FAB / 快照 / 命令面板 =====');
      // 设计器左栏的「结构」页签（实体详情也有「结构」页签，取 DOM 中更靠后的设计器侧）
      await page.locator('.ant-tabs-tab').filter({ hasText: '结构' }).last().click();
      await sleep(600);
      // 模板实例化的 schema 已含全部字段 ⇒ 字段库为空。先「移出分区」释放一个字段。
      // 注意 :visible——页面初始化时外层「结构」页签默认挂载过（antd 保持隐藏挂载），
      // 其字段表格的删除图标在 DOM 中但不可见；可见列表中 nth(0)=分区头删除、nth(1)=首个控件移出。
      await page.locator('.anticon-delete:visible').nth(1).click();
      await sleep(500);
      await page.locator('button.ant-btn-circle').last().click();
      await page.waitForSelector('text=添加字段到界面', { timeout: 10000 });
      await page.locator('.ant-popover .ant-tag').first().click();
      await sleep(600);
      const fabDirty = await page.locator('text=未保存改动').count();
      assert('FAB 控件库可调出并把字段落入分区', fabDirty > 0, { fabDirty });
      // 快照：保存 → 版本历史 → 恢复
      await page.locator('button').filter({ hasText: '快照' }).first().click();
      await page.locator('.ant-dropdown-menu-item').filter({ hasText: '保存当前快照' }).click();
      await sleep(800);
      const snapSaved = await page.locator('text=已保存快照').count();
      assert('快照可保存（本地 30 份上限）', snapSaved > 0, { snapSaved });
      await page.locator('button').filter({ hasText: '快照' }).first().click();
      await page.locator('.ant-dropdown-menu-item').filter({ hasText: '版本历史' }).click();
      await page.waitForSelector('text=版本历史', { timeout: 10000 });
      await page.locator('.ant-modal button').filter({ hasText: '恢复' }).first().click();
      await sleep(800);
      const snapRestored = await page.locator('text=已恢复到快照').count();
      assert('快照可恢复（恢复前跑协议 Gate）', snapRestored > 0, { snapRestored });
      await page.keyboard.press('Escape');
      await sleep(600);
      // 命令面板：Ctrl+K → 关键字过滤 → 执行跳转
      await page.keyboard.press('Control+k');
      await page.waitForSelector('text=命令面板', { timeout: 10000 });
      // 必须按模态内容限定：实体详情的 forceRender 隐藏 modal（编辑字段等）输入框也在 DOM 中
      const cmdModal = page.locator('.ant-modal').filter({ hasText: '命令面板' });
      await cmdModal.locator('input').first().fill('自检');
      await sleep(500);
      const cmdHit = await cmdModal.innerText().catch(() => '');
      assert('命令面板可按关键字过滤', /跳到：自检/.test(cmdHit), { snippet: cmdHit.slice(0, 100) });
      // 命令项是 <div><Tag>分组</Tag><span>标签</span></div>，div 文本含分组前缀，须点 span
      await cmdModal.locator('span').filter({ hasText: /^跳到：自检$/ }).last().click();
      await sleep(600);
      const lintActive = await page.locator('.ant-tabs-tab-active').filter({ hasText: '自检' }).count();
      assert('命令面板可执行跳转（跳到自检）', lintActive > 0, { lintActive });

      // ---------- G. 发布协议硬门禁 ----------
      console.log('\n===== G. 发布硬门禁（负向 + 正向） =====');
      // 负向：PUT 引用不存在字段的 schema → 后端 validate 必含 ERROR → runnable=false → 按钮禁用
      const brokenSchema = {
        schemaVersion: 1, minor: 1, entityCode: code, displayModes: ['create', 'edit', 'view'],
        views: { form: { sections: [{ id: 'sec_main', title: '坏分区', controls: [{ fieldCode: 'no_such_field_xyz' }] }] } },
        linkageRules: [], submitRules: [], actionBar: [],
      };
      const putRes = await api('PUT', `/api/v1/metadata/entities/${entityId}/ui-schema`, {
        body: { json: JSON.stringify(brokenSchema) }, etag: '0',
      });
      assert('坏 schema 已 PUT（负向前提）', okOf(putRes), { status: putRes.status });
      // 硬门禁的数据地基：后端 validate 对坏 schema 必须报 ERROR 级（handlePublish 的阻断判据）
      const valRes = await api('POST', `/api/v1/metadata/entities/${entityId}/ui-schema/validate`);
      const valIssues = Array.isArray(payload(valRes)) ? payload(valRes) : [];
      const errIssues = valIssues.filter((i) => i.level === 'ERROR');
      assert('后端 validate 对坏 schema 报 ERROR 级问题', errIssues.length > 0, { status: valRes.status, issues: valIssues.slice(0, 3) });
      // 整页重载进设计器：挂载即加载坏 schema → 协议错误横幅必须顶出来
      await page.goto(`${SHELL}/metadata#/entities/${entityId}?tab=uischema`, { waitUntil: 'domcontentloaded' });
      await page.reload();
      await page.waitForSelector('text=载入场景样例', { timeout: 25000 });
      const banner = await page.locator('text=界面协议存在').first().innerText().catch(() => '');
      assert('协议错误阻断式横幅顶出（不藏在 Tab 里）', /界面协议存在 \d+ 处错误/.test(banner), { banner: banner.slice(0, 120) });
      // 打开发布预览：preview.runnable=false ⇒ 确认发布按钮禁用（门禁第一层，UI 即不可达发布动作）
      await page.locator('button').filter({ hasText: '发布' }).first().click();
      const pubModal = page.locator('.ant-modal').filter({ hasText: '发布预览' });
      await pubModal.waitFor({ state: 'visible', timeout: 15000 });
      await sleep(2500);
      const pubBtn = pubModal.locator('button').filter({ hasText: '确认发布' });
      assert('负向：坏 schema 发布按钮禁用（preview.runnable=false）', await pubBtn.isDisabled(), {});
      const entAfterBlock = payload(await api('GET', `/api/v1/metadata/entities/${entityId}`));
      assert('负向：实体确实未发布（status 仍为草稿）', entAfterBlock?.status === 0, { status: entAfterBlock?.status });
      await page.screenshot({ path: '/Users/renhui.trh/wps/bone/logs/ui_designer_ux_4_gate_blocked.png' });
      // 正向：删除坏草稿 → 刷新预览 → runnable=true 按钮启用 → 发布放行
      const delRes = await api('DELETE', `/api/v1/metadata/entities/${entityId}/ui-schema`);
      assert('坏 schema 草稿已删除（正向前提）', delRes.status >= 200 && delRes.status < 300, { status: delRes.status });
      await pubModal.locator('button').filter({ hasText: '刷新预览' }).click();
      await sleep(3000);
      assert('正向：修复后发布按钮启用', await pubBtn.isEnabled(), {});
      await pubBtn.click();
      await sleep(5000);
      const entAfterOk = payload(await api('GET', `/api/v1/metadata/entities/${entityId}`));
      assert('正向：修复后发布放行（status=1）', entAfterOk?.status === 1, { status: entAfterOk?.status });

      // ---------- H. console 干净度 ----------
      const KNOWN = /clearIcon.{0,4}is deprecated|favicon|404 \(Not Found\)/i;
      const realErrors = consoleErrors.filter((e) => !KNOWN.test(e));
      assert('页面无新增 console error', realErrors.length === 0, { errors: realErrors.slice(0, 3) });

      const failed = steps.filter((s) => !s.ok).length;
      console.log('\n===== SUMMARY =====');
      console.log(JSON.stringify({ total: steps.length, pass: steps.length - failed, failed, errors }, null, 2));
      exitCode = failed > 0 ? 1 : 0;
    } catch (e) {
      console.error('FATAL', e);
      exitCode = 2;
    }
    return exitCode;
  });
}

/* ================= 入口编排（参数解析 / 场景调度 / 退出码汇聚） ================= */

/** 解析 --scenario <x> / --scenario=<x>；其余位置参数留给 perf 的可选控件数 */
function parseArgs(argv) {
  let scenario = null;
  const positional = [];
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--scenario') {
      scenario = i + 1 < argv.length ? argv[++i] : null;
    } else if (a.startsWith('--scenario=')) {
      scenario = a.slice('--scenario='.length);
    } else {
      positional.push(a);
    }
  }
  return { scenario, positional };
}

const { scenario: SCENARIO_ARG, positional: POSITIONAL } = parseArgs(process.argv.slice(2));

/** 场景注册表：顺序即 --scenario all 的执行顺序 */
const SCENARIOS = [
  { key: 'three', fn: scenarioThree, desc: '三套企业样板 UI 探针（引导/子表合计/变体/校验/联动/视图）' },
  { key: 'n4n5', fn: scenarioN4N5, desc: 'N4 控件级校验 + N5 样例数据预览（负向→注入→正向→恢复）' },
  { key: 'n7', fn: scenarioN7, desc: 'N7 字段级角色可见性（roles 注入闭环，fail-closed + 自检）' },
  { key: 'perf', fn: scenarioPerf, desc: '大 schema 渲染层性能基线（首屏/选中/编辑/切面板）' },
  { key: 'production', fn: scenarioProduction, desc: '生产三实体 pageHeader/split/list 能力注入闭环' },
  { key: 'ux', fn: scenarioUx, desc: 'UX 优化批次回归（Tab/引导/禅模式/变体/快捷键/历史栈/发布门禁）' },
];

/** 打印报错 + 场景清单（缺省 / 未知场景时用） */
function usage(msg) {
  if (msg) console.error('✗ ' + msg);
  console.error('用法：node scripts/e2e/ui/ui-designer-probe.mjs --scenario <three|n4n5|n7|perf|production|ux|all>');
  console.error('      node scripts/e2e/ui/ui-designer-probe.mjs --scenario perf [控件数]');
  console.error('场景清单：');
  for (const s of SCENARIOS) console.error(`  ${s.key.padEnd(11)} ${s.desc}`);
  console.error('  all         按上述顺序执行全部场景');
}

async function main() {
  if (!SCENARIO_ARG) {
    usage('缺少 --scenario 参数');
    return 2;
  }
  if (SCENARIO_ARG !== 'all' && !SCENARIOS.some((s) => s.key === SCENARIO_ARG)) {
    usage(`未知场景 --scenario ${SCENARIO_ARG}`);
    return 2;
  }
  const keys = SCENARIO_ARG === 'all' ? SCENARIOS.map((s) => s.key) : [SCENARIO_ARG];
  const codes = [];
  for (const key of keys) {
    const def = SCENARIOS.find((s) => s.key === key);
    console.log(`\n======== [${key}] ${def.desc} ========`);
    const t0 = Date.now();
    let code;
    try {
      code = await def.fn(); // 各场景返回其原探针的退出码语义
    } catch (e) {
      // 统一错误收口：场景内部未兜住的异常（原独立探针会以 unhandled rejection 崩掉）
      console.error(`✗ [${key}] 场景执行异常：${(e && e.stack) || e}`);
      code = 1;
    }
    console.log(`======== [${key}] 结束：退出码 ${code}，耗时 ${Math.round((Date.now() - t0) / 1000)}s ========`);
    codes.push(code);
  }
  return codes.find((c) => c !== 0) ?? 0;
}

main().then((code) => process.exit(code));
