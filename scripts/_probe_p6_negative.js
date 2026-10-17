/**
 * P6 负向探针：验证「自检零错误」这条断言真的有牙齿（而不是恒真的假绿）。
 *
 * 背景：第六轮主探针里，「自检零错误」原先在左栏匹配 /^\d+ 错误$/ —— 但自检 tab 角标只是
 * 数字（<Tag>{errCount}</Tag>），该模式永不命中 → errTags 恒为 0 → 断言恒过 = 假绿。
 * 修正后断言改走「工具栏零错误 Tag + 自检通过 Tag + 面板零问题态」三重口径。
 * 本探针用**真制造错误**的方式证明修正后的口径会失败（而不只是"应该会"）：
 *   1. 建一个带字段的实体；
 *   2. 用 API 直接 PUT 一份**含悬空字段引用**的 ui-schema（控件绑定不存在的 fieldCode）；
 *   3. 打开设计器 —— 此时本地自检必然报 error；
 *   4. 断言：工具栏出现「N 错误」Tag、「自检通过」Tag 消失、面板不再显示「本地自检未发现问题」、
 *      自检 tab 角标 = 错误数（证明角标只是数字，旧口径匹配不到）；
 *   5. 断言：存在错误时点保存会弹「本地自检仍有 N 个错误，确定保存？」二次确认（防带病上线）。
 *
 * 判据纪律：NEG=负向断言，期望「探针能看见错误」。若某条 NEG 失败，说明修正后的口径仍是假绿。
 */
const { chromium } = require('playwright');

const BASE = 'http://localhost:3000';
const SUFFIX = 'neg' + Date.now().toString().slice(-8);
const results = { steps: [], consoleErrors: [], pageErrors: [] };

const N = (name, ok, detail) => {
  results.steps.push({ name, ok, detail });
  console.log(`[NEG][${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ' :: ' + String(detail).slice(0, 200) : ''}`);
};

let page;
const designerUrl = (id) => `${BASE}/metadata#/entities/${id}?tab=uischema`;
const toolbar = () => page.locator('button[aria-label="撤销"]').locator('xpath=..');

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1680, height: 1000 } });
  page = await ctx.newPage();
  page.on('console', (m) => { if (m.type() === 'error') results.consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => results.pageErrors.push(String(e)));

  await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(2500);
  if (await page.locator('#username').count()) {
    await page.fill('#username', 'admin');
    await page.fill('#password', '123456');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(3000);
  }

  // 建实体 + 字段 + 直接 PUT 带悬空引用的 ui-schema
  const seeded = await page.evaluate(async (suffix) => {
    const H = { Authorization: 'Bearer ' + localStorage.getItem('token'), 'Content-Type': 'application/json' };
    const code = `neg_lint_${suffix}`;
    const cr = await (await fetch('/api/v1/metadata/entities', {
      method: 'POST', headers: H,
      body: JSON.stringify({ name: code, code, displayName: '负向探针实体', tableName: 't_' + code, type: 1, deliveryMode: 1 }),
    })).json();
    if (cr.code !== 200) return { error: 'create:' + JSON.stringify(cr).slice(0, 150) };
    const id = String(cr.data);
    for (const [fc, disp] of [['amount', '金额'], ['remark', '备注']]) {
      const fr = await (await fetch(`/api/v1/metadata/entities/${id}/fields`, {
        method: 'POST', headers: H,
        body: JSON.stringify({ name: fc, code: fc, displayName: disp, type: 'DECIMAL', required: false }),
      })).json();
      if (fr.code !== 200) return { error: 'field:' + fc };
    }
    // 悬空引用：ghost_field 不存在 → lint 必报 error
    const bad = {
      schemaVersion: 1, minor: 1, entityCode: code, displayModes: ['create', 'edit', 'view'],
      views: {
        form: { sections: [{ id: 'sec_main', title: '基本信息', controls: [
          { fieldCode: 'amount', controlKind: 'MONEY' },
          { fieldCode: 'ghost_field', controlKind: 'INPUT' },
        ] }] },
        list: { columns: [{ fieldCode: 'amount', visible: true, width: 120 }] },
      },
      linkageRules: [], submitRules: [], actionBar: [], variants: [],
    };
    const sr = await (await fetch(`/api/v1/metadata/entities/${id}/ui-schema`, {
      method: 'PUT', headers: { ...H, 'If-Match': '0' }, body: JSON.stringify({ json: JSON.stringify(bad) }),
    })).json();
    return { id, saveCode: sr.code, saveMsg: String(sr.message || '').slice(0, 120) };
  }, SUFFIX);

  N('种子: 建实体 + 字段 + 写入含悬空引用的 ui-schema',
    !!seeded.id && seeded.saveCode === 200, seeded.error || `id=${seeded.id} save=${seeded.saveCode} ${seeded.saveMsg || ''}`);
  if (!seeded.id) {
    console.log('RESULT: FATAL（种子失败）');
    await browser.close();
    return;
  }

  await page.goto(designerUrl(seeded.id), { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForSelector('[data-tour="left-panel"]', { state: 'visible', timeout: 45000 });
  await page.waitForTimeout(2000);
  const card = page.locator('.tour-card');
  if (await card.count()) {
    await card.locator('button', { hasText: '跳过引导' }).click().catch(() => {});
    await page.waitForTimeout(400);
  }

  // 1) 工具栏必须出现「N 错误」，且「自检通过」消失
  const errTags = await toolbar().locator('.ant-tag', { hasText: /错误$/ }).count();
  const okTags = await toolbar().locator('.ant-tag', { hasText: '自检通过' }).count();
  const errTagText = errTags ? await toolbar().locator('.ant-tag', { hasText: /错误$/ }).first().innerText() : '';
  N('负向: 工具栏出现「N 错误」Tag（修正后的口径能看见错误）', errTags === 1 && /\d+ 错误/.test(errTagText), `errTags=${errTags} text="${errTagText}"`);
  N('负向: 「自检通过」Tag 消失', okTags === 0, `okTags=${okTags}`);

  // 2) 自检面板不再显示零问题态
  await page.locator('[data-tour="left-panel"] .ant-tabs-tab', { hasText: /^自检/ }).first().click();
  await page.waitForTimeout(800);
  const panelTxt = await page.locator('[data-tour="left-panel"]').innerText();
  N('负向: 自检面板不再显示「本地自检未发现问题」', !panelTxt.includes('本地自检未发现问题'), panelTxt.slice(0, 100).replace(/\n/g, ' '));
  N('负向: 自检面板列出错误明细（含 ghost_field）', panelTxt.includes('ghost_field'), panelTxt.slice(0, 160).replace(/\n/g, ' '));

  // 3) 自检 tab 角标只是数字（旧口径 /^\d+ 错误$/ 匹配不到 → 假绿的根因）
  const lintTabTxt = await page.locator('[data-tour="left-panel"] .ant-tabs-tab', { hasText: /^自检/ }).first().innerText();
  N('反证: 自检 tab 角标只是数字（证明旧口径为何恒 0）', /^\d+$/.test(lintTabTxt.replace('自检', '').trim()), `lintTab="${lintTabTxt.replace(/\n/g, ' ')}"`);

  // 4) 带错保存必须弹二次确认（防带病上线）
  //    注意：保存按钮 disabled={!dirty}，而错误 schema 是 API 直写的（本地无改动）→ 必须先
  //    制造一处**不消除错误**的脏改动（字段库加 remark，ghost_field 悬空引用仍在），
  //    否则点不动按钮会误判成"没有二次确认"。
  await page.locator('button[aria-label="添加字段到界面"]').click();
  await page.waitForTimeout(700);
  const palTag = page.locator('.ant-popover:visible .ant-tag', { hasText: 'remark' }).first();
  const palHas = await palTag.count();
  if (palHas) { await palTag.click(); await page.waitForTimeout(700); }
  await page.keyboard.press('Escape');
  await page.waitForTimeout(400);
  const saveBtn = page.locator('[data-tour="save"]');
  const dirtyReady = (await saveBtn.count()) > 0 && (await saveBtn.isEnabled());
  const errStill = await toolbar().locator('.ant-tag', { hasText: /错误$/ }).count();
  N('准备: 制造脏改动后保存按钮可用且错误仍在', palHas > 0 && dirtyReady && errStill === 1,
    `paletteRemark=${palHas} saveEnabled=${dirtyReady} errTags=${errStill}`);
  await saveBtn.click();
  await page.waitForTimeout(1500);
  const confirmTxt = await page.locator('.ant-modal-confirm').innerText().catch(() => '');
  N('负向: 带错保存弹二次确认（本地自检仍有 N 个错误）', confirmTxt.includes('本地自检仍有') && confirmTxt.includes('确定保存'), confirmTxt.replace(/\n/g, ' ').slice(0, 120));
  const footerBtn = page.locator('.ant-modal-confirm button.ant-btn-primary');
  if (await footerBtn.count()) { await footerBtn.click().catch(() => {}); await page.waitForTimeout(1500); }

  console.log(`NEG: ${results.steps.filter((s) => s.ok).length}/${results.steps.length}`);
  console.log(`RESULT: ${results.steps.every((s) => s.ok) ? 'TEETH CONFIRMED（断言有牙齿）' : 'STILL GREEN（存在假绿）'}`);
  console.log(`console errors=${results.consoleErrors.length} page errors=${results.pageErrors.length}`);
  await browser.close();
})().catch((e) => {
  console.error('PROBE CRASH:', String(e).split('\n').slice(0, 6).join(' ⏎ '));
  process.exit(1);
});