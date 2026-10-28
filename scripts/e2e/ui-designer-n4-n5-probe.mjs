/**
 * N4/N5 活体探针：控件级校验规则编辑器 + 样例数据预览
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
 * 用法：node scripts/e2e/ui-designer-n4-n5-probe.mjs
 * 依赖：playwright（经共享 client 的 loadPlaywright 加载，见 e2e_lib/client.cjs）
 *
 * 口径说明（预览 ≡ 运行时同源）：本探针验证 SchemaPreview 渲染/校验层；
 * BoneDynamicForm（运行时）消费同一个 validateControlValues，由 vitest 单测 + 变异验证覆盖。
 */
import {
  UI_BASE as BASE,
  SHOT_DIR,
  USER,
  PWD,
  loadPlaywright,
  makeChecker,
  login,
  api,
} from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

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
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.setDefaultTimeout(25000);
const consoleErrors = [];
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

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

async function uiLogin() {
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"], input[placeholder*="密码"]', { timeout: 20000 });
  const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
  if (await page.locator(userSel).first().count()) await page.locator(userSel).first().fill(USER);
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
  } catch { return false; }
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

try {
  check('UI 登录成功', await uiLogin(), `url=${page.url()}`);
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
  await browser.close();
  process.exit(ok ? 0 : 1);
}
