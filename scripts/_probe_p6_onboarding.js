/**
 * 第六轮联调探针：新手指引体系 + 报销/采购/理赔三实体 0-1 人工串测。
 *
 * 模拟一个「第一次用 Bone 建模」的人，完整走：
 *   Phase 0  API 建模：建三实体 + 字段 +（采购）动作定义 + 建模发布
 *   Phase 1  新手指引：首访自动弹出 → 六步走查（3 个 action 步真点）→ 完成标记 → 重进不弹
 *   Phase 2  0-1 设计：每实体 UI 操作——组织表单（移出/字段库加回/加分区）→ 列表精选（隐藏/固定）
 *            → 业务配置（提交校验 / 动作条 / 变体）→ 自检 → 保存 → 待发布
 *   Phase 3  发布闭环：工具栏「去发布」→ 发布页「确认发布」→ 回设计器验证「已发布」
 *   Phase 4  串起来证明：数据 tab 运行时表头 === 设计的契约列（隐藏列不出现、顺序一致）
 *
 * 判定纪律（沿用前轮）：
 * - HARD=功能断言；SOFT=体验断言；所有通过/失败都打点，结尾汇总 + 零 console error 硬断言；
 * - 发布态标签断言走 [data-tour="publish-state"] 精确锚点——页面别处有实体级「已发布」标签，
 *   全局/工具栏模糊匹配会假绿（第五轮踩坑，见 §13.5）；
 * - 自检「零错误」以工具栏错误 Tag 消失 + 「自检通过」Tag 出现 + 面板「本地自检未发现问题」
 *   三重口径判定：自检 tab 角标只是数字（<Tag>{errCount}</Tag>），在左栏匹配「N 错误」恒 0 = 假绿；
 * - 首存后发布态必为「未发布」（never）：建模发布时还没有 ui-schema 行，snapshotOnPublish
 *   对 row.isEmpty() 是 no-op → published 快照为 null，预告按钮文案是「首发内容」；
 * - 分区头「删除分区」与控件行「移出分区」同为 .anticon-delete，故行级操作一律走
 *   [data-tour="control-row"] 锚点，无脑 .first() 会删掉整个分区（导致 sections 恒为 1）。
 */
const { chromium } = require('playwright');

const BASE = 'http://localhost:3000';
const SUFFIX = 'onb' + Date.now().toString().slice(-8);
const results = { steps: [], consoleErrors: [], pageErrors: [] };

const H = (name, ok, detail) => {
  results.steps.push({ name, ok, hard: true, detail });
  console.log(`[HARD][${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ' :: ' + String(detail).slice(0, 180) : ''}`);
};
const S = (name, ok, detail) => {
  results.steps.push({ name, ok, hard: false, detail });
  console.log(`[SOFT][${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ' :: ' + String(detail).slice(0, 180) : ''}`);
};
const note = (m) => console.log(`---- ${m} ----`);

/* ---------------- 三实体 0-1 规格 ---------------- */
const SPECS = [
  {
    key: 'expense',
    name: '报销单',
    code: `expense_claim_${SUFFIX}`,
    fields: [
      ['expense_no', '报销单号', 'STRING', true],
      ['expense_type', '费用类型', 'STRING', false],
      ['amount', '报销金额', 'DECIMAL', true],
      ['invoice_no', '发票号', 'STRING', false],
      ['expense_date', '报销日期', 'STRING', false],
      ['applicant', '申请人', 'STRING', false],
      ['remark', '备注', 'TEXT', false],
      ['status', '状态', 'STRING', false],
    ],
    actionDefs: [],
    hideCols: ['remark', 'applicant'],   // 列表精选：隐藏 2 列
    pinCol: 'expense_no',                // 列表精选：固定首列
    submitRule: { fieldCode: 'amount', fieldLabel: '报销金额（amount）', opTitle: '大于', value: '0', message: '报销金额须大于 0' },
  },
  {
    key: 'purchase',
    name: '采购订单',
    code: `purchase_order_${SUFFIX}`,
    fields: [
      ['po_no', '采购单号', 'STRING', true],
      ['supplier', '供应商', 'STRING', false],
      ['total_amount', '采购总额', 'DECIMAL', true],
      ['currency', '币种', 'STRING', false],
      ['delivery_date', '交付日期', 'STRING', false],
      ['payment_terms', '付款条款', 'STRING', false],
      ['buyer', '采购员', 'STRING', false],
      ['status', '状态', 'STRING', false],
    ],
    actionDefs: [{ actionCode: 'submit', label: '提交审核', toStatus: 'SUBMITTED' }],
    hideCols: ['payment_terms'],
    pinCol: 'po_no',
    actionCode: 'submit',
  },
  {
    key: 'claim',
    name: '理赔案件',
    code: `claim_case_${SUFFIX}`,
    fields: [
      ['claim_no', '理赔单号', 'STRING', true],
      ['policy_no', '保单号', 'STRING', false],
      ['claim_amount', '理赔金额', 'DECIMAL', true],
      ['invoice_no', '发票号', 'STRING', false],
      ['hospital', '就诊医院', 'STRING', false],
      ['report_date', '报案日期', 'STRING', false],
      ['reviewer', '复核人', 'STRING', false],
      ['status', '状态', 'STRING', false],
    ],
    actionDefs: [],
    hideCols: ['reviewer'],
    pinCol: 'claim_no',
    variant: { label: '核赔视角', roleTitle: '核赔 reviewer' },
  },
];

/* ---------------- 通用 helper ---------------- */
let page;
const designerUrl = (id) => `${BASE}/metadata#/entities/${id}?tab=uischema`;
const runtimeUrl = (id) => `${BASE}/metadata#/entities/${id}?tab=data`;

async function login() {
  await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(2500);
  if (await page.locator('#username').count()) {
    await page.fill('#username', 'admin');
    await page.fill('#password', '123456');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(3000);
  }
}

/**
 * 打开设计器并强制 fresh load：
 * - 外层 tab 由 URL ?tab= 驱动（react-router searchParams），同实体重复 goto 是 no-op，
 *   而发布后设计器的 published 状态只有重新 load() 才会刷新——故一律 reload；
 * - 等左栏可见（而非 attached）：antd Tabs 隐藏面板里的元素仍在 DOM，可见性等待防假绿；
 * - skipTour=true（默认）：首访实体会自动弹引导，探针模拟「跳过引导直接设计」的人
 *   （同时覆盖跳过路径；进度卡承担长期指引）。Phase 1 要走查引导本身，传 false。
 */
async function openDesigner(id, { skipTour = true } = {}) {
  await page.goto(designerUrl(id), { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForSelector('[data-tour="left-panel"]', { state: 'visible', timeout: 45000 });
  await page.waitForTimeout(1500);
  if (skipTour) {
    const card = page.locator('.tour-card');
    if (await card.count()) {
      await card.locator('button', { hasText: '跳过引导' }).click();
      await page.waitForTimeout(400);
    }
  }
}

/** 设计器左栏 tab 点击（作用域限定 + 前缀匹配：自检 tab 带错误数角标，文本不恒等） */
async function clickTab(label) {
  await page.locator('[data-tour="left-panel"] .ant-tabs-tab', { hasText: new RegExp(`^${label}`) }).first().click();
  await page.waitForTimeout(500);
}

/** 工具栏作用域（撤销按钮的父容器：页面别处有实体级「已发布」标签，全局匹配会假绿） */
const toolbar = () => page.locator('button[aria-label="撤销"]').locator('xpath=..');

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1680, height: 1000 } });
  page = await ctx.newPage();
  page.on('console', (m) => {
    if (m.type() === 'error') results.consoleErrors.push(m.text());
  });
  page.on('pageerror', (e) => results.pageErrors.push(String(e)));

  await login();
  note('Phase 0 · API 建模：三实体 + 字段 + 动作定义 + 建模发布');

  /* ---------- Phase 0：API 建模 ---------- */
  const created = await page.evaluate(async (specs) => {
    const token = localStorage.getItem('token');
    const H = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' };
    const out = [];
    for (const sp of specs) {
      const cr = await (await fetch('/api/v1/metadata/entities', {
        method: 'POST', headers: H,
        body: JSON.stringify({ name: sp.code, code: sp.code, displayName: sp.name, tableName: 't_' + sp.code, type: 1, deliveryMode: 1 }),
      })).json();
      if (cr.code !== 200) { out.push({ key: sp.key, error: 'create:' + JSON.stringify(cr).slice(0, 120) }); continue; }
      const id = String(cr.data);
      for (const [code, disp, type, req] of sp.fields) {
        const fr = await (await fetch(`/api/v1/metadata/entities/${id}/fields`, {
          method: 'POST', headers: H,
          body: JSON.stringify({ name: code, code, displayName: disp, type, required: req }),
        })).json();
        if (fr.code !== 200) { out.push({ key: sp.key, error: 'field:' + code }); break; }
      }
      for (const ad of (sp.actionDefs ?? [])) {
        await (await fetch(`/api/v1/metadata/entities/${id}/action-defs`, {
          method: 'POST', headers: H,
          body: JSON.stringify({ actionCode: ad.actionCode, label: ad.label, toStatus: ad.toStatus }),
        })).json();
      }
      const pub = await (await fetch(`/api/v1/metadata/entities/${id}/publish`, { method: 'POST', headers: H })).json();
      out.push({ key: sp.key, id, name: sp.name, publishCode: pub.code, publishMsg: pub.code !== 200 ? String(pub.message || '').slice(0, 100) : '' });
    }
    return out;
  }, SPECS);

  const byKey = {};
  for (const c of created) {
    byKey[c.key] = c;
    H(`建模: ${c.name} 创建+字段+建模发布`, !!c.id && c.publishCode === 200, c.error || c.publishMsg || `id=${c.id}`);
  }
  const ents = SPECS.map((sp) => ({ spec: sp, ent: byKey[sp.key] })).filter((x) => x.ent && x.ent.id);
  H('建模: 三实体全部就绪', ents.length === 3, `ready=${ents.length}/3`);
  if (ents.length < 3) {
    console.log('FATAL: 建模失败，探针终止');
    console.log(JSON.stringify(results.steps, null, 1));
    await browser.close();
    return;
  }

  /* ---------- Phase 1：新手指引走查（理赔实体） ---------- */
  note('Phase 1 · 新手指引：首访自动弹出 + 六步走查');
  const claim = ents.find((x) => x.spec.key === 'claim');
  // Phase 1 必须保留自动弹出的引导（不能走 openDesigner 的 skipTour 路径）
  await page.goto(designerUrl(claim.ent.id), { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForSelector('[data-tour="left-panel"]', { state: 'visible', timeout: 45000 });
  await page.waitForTimeout(1500);

  // 注意：antd 对「纯文本两字按钮」会在字间插空格（渲染成「完 成」「继 续」），
  // hasText:'完成' 永远匹配不到——前进按钮一律用 .ant-btn-primary 定位（主按钮即前进按钮）。
  const card = page.locator('.tour-card');
  const fwd = () => card.locator('.ant-btn-primary');
  H('引导: 首访自动弹出 Spotlight 卡片', (await card.count()) > 0);
  const stepTitle = (await card.count()) ? await card.locator('.ant-typography').first().innerText().catch(() => '') : '';
  const stepCount = (await card.count()) ? await card.innerText() : '';
  H('引导: 第 1 步为「两栏工作台」且计数 1/6', stepTitle.includes('两栏工作台') && stepCount.includes('1 / 6'), `${stepTitle} | ${stepCount.slice(0, 40)}`);
  H('引导: 聚光灯高亮环渲染', (await page.locator('.tour-ring').count()) > 0);

  // 第 2 步（action：点字段库 FAB）
  await fwd().click();
  await page.waitForTimeout(400);
  const fab = page.locator('button[aria-label="添加字段到界面"]');
  H('引导: 第 2 步锚点高亮在字段库按钮', (await page.locator('.tour-ring').count()) > 0 && (await card.count()) > 0);
  await fab.click();
  await page.waitForTimeout(600);
  H('引导: action 步点 FAB 后推进到第 3 步', (await card.innerText()).includes('整分区'), await card.innerText().then((t) => t.slice(0, 60)));
  // 关掉字段库 popover（点预览画布空白处，不碰引导）
  await page.locator('[data-tour="preview"]').click({ position: { x: 20, y: 20 } }).catch(() => {});
  await page.waitForTimeout(300);

  // 第 4 步（action：点「列表」tab）
  await fwd().click();
  await page.waitForTimeout(400);
  await clickTab('列表');
  await page.waitForTimeout(500);
  H('引导: action 步点「列表」tab 后推进到第 5 步', (await card.innerText()).includes('加第一条业务规则'), await card.innerText().then((t) => t.slice(0, 60)));

  // 第 5 步（action：点「规则」tab）
  await clickTab('规则');
  await page.waitForTimeout(500);
  H('引导: action 步点「规则」tab 后推进到第 6 步', (await card.innerText()).includes('自检 → 保存 → 发布'), await card.innerText().then((t) => t.slice(0, 60)));

  // 完成
  await fwd().click();
  await page.waitForTimeout(500);
  H('引导: 完成后卡片消失', (await card.count()) === 0);

  const tourSeen = await page.evaluate((id) => localStorage.getItem('bone-designer-tour-v2:' + id), claim.ent.id);
  H('引导: 完成后按实体写入完成标记', tourSeen === '1', `key=bone-designer-tour-v2:${claim.ent.id}`);

  // 重进不再自动弹出
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForSelector('[data-tour="left-panel"]', { timeout: 45000 });
  await page.waitForTimeout(1500);
  H('引导: 重进同一实体不再自动弹出', (await card.count()) === 0);
  // 显式等按钮落地再断言：reload 后设计器要重新拉 schema/字段，工具栏会晚于左栏出现，
  // 固定 sleep 属拍脑袋（第六轮踩坑：断言时 count=0，单独复现又是 1）
  const helpBtn = page.locator('button', { hasText: '帮助' });
  await helpBtn.first().waitFor({ state: 'attached', timeout: 10000 }).catch(() => {});
  H('引导: 可从工具栏「帮助」重开', (await helpBtn.count()) > 0);
  H('进度卡: 0-1 新手指引进度条可见', (await page.getByText('0-1 新手指引').count()) > 0);

  /* ---------- Phase 2：三实体 0-1 设计 ---------- */
  note('Phase 2 · 0-1 设计：组织表单 → 列表精选 → 业务配置 → 保存');

  /* 每实体一个执行单元：单实体抛错只记一条失败，不吃掉后续实体（探针要能一次跑完三实体） */
  const runEntity = async (spec, ent) => {
    const tag = `[${spec.name}]`;
    await openDesigner(ent.id);

    /* (a) 组织表单：移出一个控件 → 加分区 → 字段库加回新分区 */
    // 移出控件必须精确命中「控件行」的删除图标：分区头也有 .anticon-delete（删除分区），
    // 无脑 .first() 删掉的是整个分区——removeSection 无护栏，删完再点「添加分区」只会补出
    // 一个空的 sec_1，sections 恒为 1（第六轮「添加分区失效」的真因，不是时序问题）。
    const ctrlTotal0 = await page.locator('[data-tour="control-row"]').count();
    const ctrlRow = page.locator('[data-tour="control-row"]').first();
    const ctrlTxt = await ctrlRow.innerText();
    const movedCode = spec.fields.map((f) => f[0]).find((c) => ctrlTxt.includes(c)) ?? '';
    H(`${tag} 表单: 默认单一分区铺满全部字段（${spec.fields.length} 控件）`, ctrlTotal0 === spec.fields.length, `controls=${ctrlTotal0}`);
    await ctrlRow.locator('.anticon-delete').click();
    await page.waitForTimeout(500);
    const ctrlTotal1 = await page.locator('[data-tour="control-row"]').count();
    H(`${tag} 表单: 移出控件 ${movedCode || '(未识别)'}`, movedCode !== '' && ctrlTotal1 === ctrlTotal0 - 1, `now=${ctrlTotal1}`);

    // 添加分区：轮询 + 重试（不靠固定 sleep 猜时序）
    let secCount = await page.locator('[data-tour="section"]').count();
    for (let i = 0; i < 3 && secCount < 2; i++) {
      await page.locator('button', { hasText: '添加分区' }).click();
      await page.waitForTimeout(600);
      secCount = await page.locator('[data-tour="section"]').count();
    }
    H(`${tag} 表单: 添加分区后 ≥2 个分区`, secCount >= 2, `sections=${secCount}`);

    // 字段库：把移出的字段加回（点选落在最后一个分区）
    await page.locator('button[aria-label="添加字段到界面"]').click();
    await page.waitForTimeout(700);
    const palTag = page.locator('.ant-popover:visible .ant-tag', { hasText: movedCode }).first();
    const palCount = await palTag.count();
    H(`${tag} 表单: 字段库列出待用字段 ${movedCode || '(未识别)'}`, palCount > 0, `popoverTags=${await page.locator('.ant-popover:visible .ant-tag').count()}`);
    if (palCount) {
      await palTag.click();
      await page.waitForTimeout(600);
    }
    const ctrlTotal2 = await page.locator('[data-tour="control-row"]').count();
    H(`${tag} 表单: 加回后控件数复原（${ctrlTotal0}）`, ctrlTotal2 === ctrlTotal0, `now=${ctrlTotal2}`);
    const lastSecTxt = await page.locator('[data-tour="section"]').last().innerText();
    H(`${tag} 表单: 加回的控件落在新分区`, lastSecTxt.includes(movedCode), lastSecTxt.slice(0, 60).replace(/\n/g, ' '));
    await page.keyboard.press('Escape');
    await page.waitForTimeout(300);

    /* (b) 列表精选：隐藏列 + 固定列 */
    await clickTab('列表');
    for (const code of spec.hideCols) {
      const row = page.locator('[data-tour="list-row"]', { hasText: code }).first();
      if (await row.count()) {
        await row.locator('.ant-switch').nth(1).click();
        await page.waitForTimeout(350);
        const hidden = (await row.innerText()).includes('已隐藏');
        H(`${tag} 列表: 隐藏列 ${code}`, hidden);
      } else {
        H(`${tag} 列表: 隐藏列 ${code}`, false, 'row not found');
      }
    }
    const pinRow = page.locator('[data-tour="list-row"]', { hasText: spec.pinCol }).first();
    await pinRow.locator('.ant-switch').first().click();
    await page.waitForTimeout(350);
    const pinned = (await pinRow.locator('.ant-switch').first().getAttribute('aria-checked')) === 'true';
    H(`${tag} 列表: 固定列 ${spec.pinCol}`, pinned);

    /* (c) 业务配置：提交校验 / 动作条 / 变体 */
    if (spec.submitRule) {
      await clickTab('规则');
      await page.locator('.ant-segmented-item', { hasText: '提交校验' }).click();
      await page.waitForTimeout(300);
      await page.locator('button', { hasText: '添加提交校验' }).click();
      await page.waitForTimeout(500);
      const rc = page.locator('[data-tour="rule-card"]').last();
      // 取数字段（tags select，下拉第 2 个：函数/取数字段/比较符/比较值类型）
      await rc.locator('.ant-select').nth(1).click();
      await page.waitForTimeout(300);
      await page.locator(`.ant-select-item-option[title="${spec.submitRule.fieldLabel}"]`).click();
      await page.waitForTimeout(300);
      // 比较符
      await rc.locator('.ant-select').nth(2).click();
      await page.waitForTimeout(300);
      await page.locator(`.ant-select-item-option[title="${spec.submitRule.opTitle}"]`).click();
      await page.waitForTimeout(300);
      // 比较值 + 失败提示
      await rc.getByPlaceholder('比较值').fill(spec.submitRule.value);
      await rc.getByPlaceholder('如：费用明细合计必须等于申报金额').fill(spec.submitRule.message);
      await page.waitForTimeout(400);
      const sent = await rc.innerText();
      H(`${tag} 规则: 提交校验句子含字段/符/值`, sent.includes(spec.submitRule.fieldCode) && sent.includes(spec.submitRule.opTitle), sent.slice(0, 120).replace(/\n/g, ' '));
      const msgVal = await rc.getByPlaceholder('如：费用明细合计必须等于申报金额').inputValue();
      H(`${tag} 规则: 失败提示已填写`, msgVal === spec.submitRule.message, msgVal);
    }
    if (spec.actionCode) {
      await clickTab('动作条');
      await page.locator('button', { hasText: '添加动作' }).click();
      await page.waitForTimeout(500);
      // 动作码控件随「实体是否已定义动作」二选一：有定义=Select（选项来自 GET action-defs 真源），
      // 无定义=自由 Input。无脑查 Input 会在有定义时找不到（第六轮踩坑）；且留下的空 actionCode
      // 会触发本地自检错误 → 保存弹 Modal.confirm → 后续点击全被遮罩吃掉（连带假失败）。
      let pickedBy = '';
      const codeSelect = page.locator('.ant-select-selection-placeholder', { hasText: '选择已定义的动作' });
      if (await codeSelect.count()) {
        // 点 .ant-select-selector 而非 placeholder 文本：showSearch 的 Select 里
        // search input 覆盖在 placeholder 上，点文本会被 input 拦截 pointer events（第六轮踩坑）
        await codeSelect.first()
          .locator('xpath=ancestor::*[contains(@class,"ant-select-selector")]')
          .click();
        await page.waitForTimeout(400);
        await page.locator('.ant-select-item-option', { hasText: spec.actionCode }).first().click();
        await page.waitForTimeout(500);
        pickedBy = 'Select(动作定义真源)';
      } else {
        const codeInput = page.getByPlaceholder('动作编码（实体未定义动作时可手填）');
        if (await codeInput.count()) {
          await codeInput.fill(spec.actionCode);
          await page.waitForTimeout(400);
          pickedBy = 'Input(手填降级)';
        }
      }
      const panelTxt = await page.locator('[data-tour="left-panel"]').innerText();
      H(`${tag} 动作条: 添加动作 ${spec.actionCode}（${pickedBy || '未找到动作码控件'}）`,
        pickedBy !== '' && panelTxt.includes(spec.actionCode),
        panelTxt.slice(0, 100).replace(/\n/g, ' '));
    }
    if (spec.variant) {
      await clickTab('变体');
      // 按钮文案是「新增变体」不是「添加变体」（第六轮踩坑：hasText 永不命中，30s 超时）
      await page.locator('button', { hasText: '新增变体' }).click();
      await page.waitForTimeout(500);
      await page.getByPlaceholder('变体名称').fill(spec.variant.label);
      await page.waitForTimeout(200);
      // 角色（antd Select 无 placeholder 属性）：点占位 span 会话被 tags 模式的 search input
      // 拦截 pointer events（与动作码同一坑），故点其 .ant-select-selector 外框
      await page.locator('.ant-select-selection-placeholder', { hasText: '角色' }).first()
        .locator('xpath=ancestor::*[contains(@class,"ant-select-selector")]')
        .click();
      await page.waitForTimeout(400);
      await page.locator('.ant-select-item-option', { hasText: spec.variant.roleTitle }).first().click();
      await page.waitForTimeout(400);
      // 变体名在 <Input value> 里，不在 innerText（第六轮踩坑：拿面板文本断言输入框值 → 恒失败）
      const vName = await page.getByPlaceholder('变体名称').inputValue();
      const roleTags = await page.locator('.ant-select-selection-item', { hasText: 'reviewer' }).count();
      const hitDesc = await page.locator('[data-tour="left-panel"]').innerText();
      H(`${tag} 变体: 添加「${spec.variant.label}」并命中 reviewer`,
        vName === spec.variant.label && roleTags > 0,
        `name=${vName} roleTags=${roleTags} 摘要=${hitDesc.slice(0, 120).replace(/\n/g, ' ')}`);
    }

    /* (d) 自检 → 保存 → 未发布（首发内容） */
    await clickTab('自检');
    await page.waitForTimeout(800);
    // 反假绿（第六轮踩坑）：自检 tab 角标只是数字（<Tag>{errCount}</Tag>），
    // 在左栏里匹配「N 错误」永远命中不了 → 恒为 0 的假绿。错误 Tag 只在工具栏，
    // 故此处以「工具栏零错误 Tag + 自检通过 Tag + 面板零问题态」三重口径判定。
    const lintPanelTxt = await page.locator('[data-tour="left-panel"]').innerText();
    const lintErrTags = await toolbar().locator('.ant-tag', { hasText: /错误$/ }).count();
    const lintOkTags = await toolbar().locator('.ant-tag', { hasText: '自检通过' }).count();
    H(`${tag} 自检: 零协议错误（工具栏口径 + 面板零问题态）`,
      lintErrTags === 0 && lintOkTags === 1 && lintPanelTxt.includes('本地自检未发现问题'),
      `errTags=${lintErrTags} okTags=${lintOkTags} clean=${lintPanelTxt.includes('本地自检未发现问题')}`);

    await page.locator('[data-tour="save"]').click();
    await page.waitForTimeout(2500);
    // 首存必为「未发布」：Phase 0 的建模发布时还没有 ui-schema 行，
    // UiSchemaApplicationService.snapshotOnPublish 对 row.isEmpty() 直接 no-op，
    // published 快照仍为 null（→ never 态），预告按钮文案也是「首发内容」而非「变更预告」。
    const pubState1 = (await page.locator('[data-tour="publish-state"]').innerText()).trim();
    H(`${tag} 保存: 发布态为「未发布」（首存，尚未上线）`, pubState1.includes('未发布'), pubState1);
    S(`${tag} 保存: 「首发内容」预告按钮出现`, (await page.locator('button', { hasText: '首发内容' }).count()) > 0);

    /* (e) 发布闭环：去发布 → 确认发布 → 已发布 */
    await page.locator('button', { hasText: '去发布' }).click();
    await page.waitForTimeout(1200);
    const confirmBtn = page.locator('button', { hasText: '确认发布' });
    const onPublishTab = (await confirmBtn.count()) > 0;
    H(`${tag} 发布: 「去发布」跳到发布页（确认发布可见）`, onPublishTab);
    if (onPublishTab) {
      await confirmBtn.click();
      await page.waitForTimeout(5000);
      // 回设计器复核（fresh load 读服务端 published 快照）
      await openDesigner(ent.id);
      await page.waitForTimeout(800);
      const pubState2 = (await page.locator('[data-tour="publish-state"]').innerText()).trim();
      H(`${tag} 发布: 回设计器显示「已发布」`, pubState2.includes('已发布'), pubState2);
      const btnGone = (await page.locator('button', { hasText: '去发布' }).count()) === 0;
      H(`${tag} 发布: 「去发布」按钮随之消失`, btnGone);
      // 进度卡 publish 里程碑（展开后应含发布上线完成态或庆祝条）
      const checklistTxt = await page.locator('[data-tour="left-panel"]').innerText();
      S(`${tag} 进度卡: 五项里程碑全部完成（庆祝条）`, checklistTxt.includes('0-1 上手完成'), checklistTxt.slice(0, 120).replace(/\n/g, ' '));
    }

    /* (f) 串起来证明：运行时表头 === 设计契约列 */
    await page.goto(runtimeUrl(ent.id), { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(3000);
    const headers = await page.evaluate(() => {
      const ths = [...document.querySelectorAll('.ant-table-thead th')];
      return ths.map((th) => (th.innerText || '').trim()).filter(Boolean);
    });
    const fixedThs = await page.evaluate(() =>
      [...document.querySelectorAll('.ant-table-thead th.ant-table-cell-fix-left')]
        .map((th) => (th.innerText || '').trim()).filter(Boolean));
    const expectedVisible = spec.fields.filter((f) => !spec.hideCols.includes(f[0])).map((f) => f[1]);
    const missing = expectedVisible.filter((h) => !headers.some((x) => x.includes(h)));
    const leaked = spec.hideCols.map((c) => spec.fields.find((f) => f[0] === c)[1]).filter((h) => headers.some((x) => x.includes(h)));
    H(`${tag} 串测: 运行时表头含全部可见列（${expectedVisible.length}）`, missing.length === 0, `missing=${JSON.stringify(missing)} headers=${JSON.stringify(headers).slice(0, 160)}`);
    H(`${tag} 串测: 隐藏列不出现在运行时`, leaked.length === 0, `leaked=${JSON.stringify(leaked)}`);
    // 固定列：契约 pinned 在运行时渲染成 antd fix-left（不是「首列」——pin 与排序是两个维度）
    const pinLabel = spec.fields.find((f) => f[0] === spec.pinCol)[1];
    S(`${tag} 串测: 固定列生效（${spec.pinCol} → fix-left）`, fixedThs.some((t) => t.includes(pinLabel)), `fixedThs=${JSON.stringify(fixedThs)}`);
    // 列序：默认契约列序 = 字段码字典序（buildDefaultSchema 按 fields 原序，字段 API 按 code 排序），
    // 故断言「表头在字典序中的下标严格递增」——断言的是契约序，不是「pin 必须在首位」。
    // 运行时表头含非契约的界面构件列（操作），先剔除再比序，并单独确认剔除项只有它。
    const dictOrder = [...spec.fields].filter((f) => !spec.hideCols.includes(f[0]))
      .sort((a, b) => (a[0] < b[0] ? -1 : 1)).map((f) => f[1]);
    const idxSeq = headers
      .filter((h) => dictOrder.some((e) => h.includes(e)))
      .map((h) => dictOrder.findIndex((e) => h.includes(e)));
    const extras = headers.filter((h) => !dictOrder.some((e) => h.includes(e)));
    S(`${tag} 串测: 表头顺序与契约一致（字段码字典序）`,
      idxSeq.length === dictOrder.length && idxSeq.every((v, i) => v === i) && extras.every((x) => x === '操作'),
      `headers=${headers.join(' | ')}`);
  };

  for (const { spec, ent } of ents) {
    await runEntity(spec, ent).catch((e) =>
      H(`[${spec.name}] 0-1 流程未跑完（抛错）`, false,
        String((e && e.message) || e).split('\n').slice(0, 5).join(' ⏎ ').slice(0, 320)));
  }

  /* ---------- 汇总 ---------- */
  note('汇总');
  const hard = results.steps.filter((s) => s.hard);
  const soft = results.steps.filter((s) => !s.hard);
  const hardFail = hard.filter((s) => !s.ok);
  const softFail = soft.filter((s) => !s.ok);
  console.log(`HARD: ${hard.length - hardFail.length}/${hard.length}  SOFT: ${soft.length - softFail.length}/${soft.length}`);
  if (hardFail.length) console.log('HARD FAILED:\n' + hardFail.map((s) => `  × ${s.name} :: ${s.detail || ''}`).join('\n'));
  if (softFail.length) console.log('SOFT FAILED:\n' + softFail.map((s) => `  × ${s.name} :: ${s.detail || ''}`).join('\n'));
  H('零 console error', results.consoleErrors.length === 0, results.consoleErrors.slice(0, 3).join(' | '));
  H('零 page error', results.pageErrors.length === 0, results.pageErrors.slice(0, 3).join(' | '));
  // 汇总行须在最后两条断言之后重算：否则日志会显示 66/66 而实际 PASS 68 条
  // （「零 console/page error」两条assertion 在上面汇总之后才追加），读日志的人
  // 极易误判为"漏了两条断言"或"数字漂移"。教训：汇总数字的口径必须覆盖全部已记录断言。
  const hardAll = results.steps.filter((s) => s.hard);
  const softAll = results.steps.filter((s) => !s.hard);
  const hardFailAll = hardAll.filter((s) => !s.ok);
  const softFailAll = softAll.filter((s) => !s.ok);
  console.log(`\n---- 最终汇总（含收尾断言）----`);
  console.log(`HARD: ${hardAll.length - hardFailAll.length}/${hardAll.length}  SOFT: ${softAll.length - softFailAll.length}/${softAll.length}`);
  console.log(`RESULT: ${hardFailAll.length === 0 && results.consoleErrors.length === 0 && results.pageErrors.length === 0 ? 'ALL PASS' : 'HAS FAILURES'}`);

  await browser.close();
})().catch((e) => {
  console.error('PROBE CRASH:', e);
  process.exit(1);
});
