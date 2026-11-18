// 元数据低代码最小闭环 E2E：模拟人工在 metadata-app 走完
//   新建 RUNTIME 实体 → 加字段 → 发布 → 打开数据 → 新建记录 → 编辑(If-Match)
// 用法：
//   NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-6/bin/node
//   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
//   $NODE scripts/e2e/ui/probe-metadata-loop.mjs > /tmp/md-loop.json
/* 共享 client：playwright 加载 / CHROME / UI BASE / 登录凭证（见 e2e_lib/client.cjs） */
import { mkdirSync } from 'node:fs';
import { loadPlaywright, CHROME, UI_BASE as BASE } from '../e2e_lib/client.cjs';
/* 浏览器登录走共享层（README/ui README §0 纪律：禁止在探针内重抄登录选择器） */
import { uiLogin } from '../e2e_lib/ui.cjs';
/* 生产化测试数据：人名与 Python fixtures 同口径（见 e2e_lib/fixtures.cjs） */
import { dataset } from '../e2e_lib/fixtures.cjs';
const { chromium } = loadPlaywright();
const OUT = '/tmp/md-loop-shots';
mkdirSync(OUT, { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function fillByLabel(page, modalSel, label, value) {
  const item = page.locator(`${modalSel} .ant-form-item`).filter({ hasText: label }).first();
  const input = item.locator('input.ant-input, textarea.ant-input').first();
  await input.fill(value);
}

async function selectFirstOption(page, modalSel, label) {
  const item = page.locator(`${modalSel} .ant-form-item`).filter({ hasText: label }).first();
  await item.locator('.ant-select-selector').click();
  await sleep(500);
  await page.locator('.ant-select-item-option').first().click();
}

const report = { steps: [], errors: [], failedReq: [] };
function step(name, ok, info) {
  report.steps.push({ name, ok, ...(info || {}) });
  console.error(`[${ok ? 'OK ' : 'FAIL'}] ${name}${info && info.detail ? ' :: ' + info.detail : ''}`);
}

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 1000 } })).newPage();
  page.on('pageerror', (e) => report.errors.push({ kind: 'pageerror', text: String(e.message).slice(0, 300) }));
  page.on('console', (m) => { if (m.type() === 'error') report.errors.push({ kind: 'console', text: m.text().slice(0, 300) }); });
  page.on('response', (r) => { if (r.status() >= 400) report.failedReq.push({ url: r.url().replace(BASE, '').slice(0, 140), status: r.status() }); });

  // ---- 登录 ----（共享层 uiLogin；route 保持本探针历史口径：直达 /iam#/accounts）
  const logged = await uiLogin(page, { route: `${BASE}/iam#/accounts` });
  step('UI 登录成功', logged, { detail: logged ? undefined : '登录表单未消失' });

  const stamp = Date.now();
  const entCode = `e2eloop${stamp}`;
  const entName = `E2E闭环${stamp}`;
  // 生产化测试数据（口径真源 e2e_lib/fixtures.py）：每个值只生成一次存变量（README §3 铁律）
  const ds = dataset(stamp);
  const NAME_NEW = ds.person();  // 新建记录填入的姓名（受控词表人名，不用「张三」随手名）
  const NAME_EDIT = ds.person(); // 编辑记录填入的姓名

  // ---- 进入实体列表 ----
  try {
    await page.goto(`${BASE}/metadata#/entities`, { waitUntil: 'domcontentloaded' });
    await sleep(4500);
    // 2026-11-18 前端术语调整：「新建实体」→「新建业务对象」。正则兼容新旧两版文案。
    const addBtn = page.locator('button').filter({ hasText: /新建(业务对象|实体)/ }).first();
    if (await addBtn.count()) { await addBtn.click({ timeout: 6000 }); await sleep(1500); }
    const modalOpen = await page.locator('.ant-modal-content').count();
    step('打开新建实体弹窗', !!modalOpen, { modalOpen });
  } catch (e) { step('打开新建实体弹窗', false, { detail: String(e.message).slice(0, 120) }); await page.screenshot({ path: `${OUT}/01-create-modal.png` }); }

  // ---- 填表单并创建（默认 RUNTIME=1）----
  try {
    // 按输入顺序填（避免「名称」被「显示名」子串误匹配）：显示名/名称/编码/表名
    const txt = page.locator('.ant-modal-content input.ant-input');
    await txt.nth(0).fill(entName);
    await txt.nth(1).fill(entName);
    await txt.nth(2).fill(entCode);
    await txt.nth(3).fill(`md_${entCode}`);
    // 所属模块（非 scoped 必填）：选第一个
    await selectFirstOption(page, '.ant-modal-content', '所属模块');
    await sleep(500);
    // antd 两字按钮注入空格（「确定」实际渲染为「确 定」）——用 \s* 兼容两版
    const ok = page.locator('.ant-modal-footer button').filter({ hasText: /确\s*定/ }).first();
    if (await ok.count()) await ok.click({ timeout: 5000 });
    await sleep(2000);
    // 期望落到详情 ?tab=structure
    const hash = await page.evaluate(() => location.hash);
    if (!/tab=structure/.test(hash)) {
      const diag = await page.evaluate(() => ({
        modalStillOpen: !!document.querySelector('.ant-modal-content'),
        validationErrors: [...document.querySelectorAll('.ant-form-item-explain-error')].map((e) => e.innerText.trim()),
        msg: [...document.querySelectorAll('.ant-message-notice-content')].map((e) => e.innerText).join(' | '),
      }));
      step('创建实体并落到详情结构 Tab', false, { hash, ...diag });
      await page.screenshot({ path: `${OUT}/02-after-create.png` });
    } else {
      step('创建实体并落到详情结构 Tab', true, { hash });
    }
  } catch (e) { step('创建实体', false, { detail: String(e.message).slice(0, 140) }); await page.screenshot({ path: `${OUT}/02-create-fail.png` }); }

  // ---- 加字段（字段向导）----
  try {
    await page.locator('button').filter({ hasText: '新建字段' }).first().click({ timeout: 6000 });
    await sleep(1200);
    // step0 选 STRING 类型卡片（只取可见 modal，避开 antd 保留的隐藏 DOM）
    await page.locator('.ant-modal-content:visible div[role="button"]').filter({ hasText: 'STRING' }).first().click();
    await sleep(400);
    await page.locator('.ant-modal-content:visible button').filter({ hasText: '下一步' }).first().click();
    await sleep(800);
    // 2026-11-18 字段向导改版：显示名/名称（英文）/编码三项均必填（name/code 必填），
    // 且 antd Modal 保留关闭后的隐藏 DOM ⇒ 只取可见输入框、按 placeholder 精确定位，
    // 避免 hasText 子串误命中关系模块同名 label 或隐藏 modal 的输入框。
    const modal2 = page.locator('.ant-modal-content:visible').last();
    const fillPh = async (ph, val) => {
      const inp = modal2.locator(`input[placeholder*="${ph}"]:visible`).first();
      if (await inp.count()) await inp.fill(val);
    };
    await fillPh('如：姓名', '姓名');
    await fillPh('firstName', 'fullName');
    await fillPh('first_name', 'full_name');
    await sleep(300);
    const createField = page.locator('.ant-modal-content:visible button').filter({ hasText: /创\s*建字段|确\s*定/ }).last();
    await createField.click({ timeout: 5000 });
    await sleep(2500);
    const fieldRows = await page.evaluate(() => document.querySelectorAll('tbody tr.ant-table-row').length);
    step('加字段成功（结构表出现行）', fieldRows >= 1, { fieldRows });
    if (fieldRows < 1) await page.screenshot({ path: `${OUT}/03-field-fail.png` });
  } catch (e) { step('加字段', false, { detail: String(e.message).slice(0, 140) }); await page.screenshot({ path: `${OUT}/03-field-fail.png` }); }

  // ---- 发布 ----
  try {
    await page.locator('button').filter({ hasText: /^发\s*布$/ }).first().click({ timeout: 6000 });
    await sleep(1800);
    // 2026-11-18 发布改为二次确认弹窗：按钮文案是「确认发布」，旧的「发布」 matcher 点不动 ⇒
    // 实体一直是草稿(status=0)，数据 Tab 查 runtime records 404、新建记录按钮不渲染。
    const pub = page.locator('.ant-modal-content:visible button').filter({ hasText: /确\s*认\s*发\s*布/ }).first();
    let pubClicked = false;
    if (await pub.count()) { await pub.click({ timeout: 6000 }); pubClicked = true; }
    await sleep(3000);
    const msg = await page.evaluate(() => [...document.querySelectorAll('.ant-message-notice-content')].map(e => e.innerText).join(' | '));
    // 成功判据以「已发布」消息为准（最可靠）；部分版本有二次确认弹窗时，按钮点击且弹窗关闭也算。
    const pubStillOpen = await page.locator('.ant-modal-content:visible button').filter({ hasText: /确\s*认\s*发\s*布/ }).count();
    const success = /已发布|发布成功/.test(msg)
      || (await page.locator('.ant-alert-success').count()) > 0
      || (pubClicked && pubStillOpen === 0);
    step('发布成功', success, { msg: msg.slice(0, 160), hash: await page.evaluate(() => location.hash) });
    if (!success) await page.screenshot({ path: `${OUT}/04-publish-fail.png` });
  } catch (e) { step('发布', false, { detail: String(e.message).slice(0, 140) }); await page.screenshot({ path: `${OUT}/04-publish-fail.png` }); }

  // ---- 打开数据 ----
  // 2026-11-18 前端改版：详情页可直接点「数据」Tab（tabs: 结构/界面设计器/数据/发布/…），
  // 旧的「打开数据」按钮可能不再渲染。优先点 Tab，fallback 到按钮。
  try {
    // 发布确认 Modal 可能残留（发布后仍显示「确认发布/关闭」），遮罩会挡住 tab/按钮点击 ⇒ 先关掉
    const closeBtn = page.locator('.ant-modal-content:visible button').filter({ hasText: /^关\s*闭$/ }).first();
    if (await closeBtn.count()) { await closeBtn.click({ timeout: 5000 }); await sleep(800); }
    const dataTab = page.locator('.ant-tabs-tab').filter({ hasText: /^数\s*据$/ }).first();
    let clicked = false;
    if (await dataTab.count()) { await dataTab.click({ timeout: 6000 }); clicked = true; }
    else {
      const openData = page.locator('button:visible').filter({ hasText: '打开数据' }).first();
      if (await openData.count()) { await openData.click({ timeout: 6000 }); clicked = true; }
    }
    await sleep(2500);
    const hash = await page.evaluate(() => location.hash);
    step('打开数据进入数据 Tab', clicked && /tab=data/.test(hash), { hash, clicked });
  } catch (e) {
    step('打开数据', false, { detail: String(e.message).slice(0, 140) });
    await page.screenshot({ path: `${OUT}/05-opendata-fail.png` });
  }

  // ---- 新建记录 ----
  let recordOk = false;
  try {
    await page.locator('button').filter({ hasText: '新建记录' }).first().click({ timeout: 6000 });
    await sleep(1200);
    // 记录弹窗里的字段输入（STRING → input.ant-input）
    const input = page.locator('.ant-modal-content:visible input.ant-input').first();
    if (await input.count()) await input.fill(NAME_NEW);
    await sleep(300);
    await page.locator('.ant-modal-content:visible button').filter({ hasText: /确\s*定/ }).first().click({ timeout: 5000 });
    await sleep(2500);
    const msg = await page.evaluate(() => [...document.querySelectorAll('.ant-message-notice-content')].map(e => e.innerText).join(' | '));
    recordOk = /创建成功/.test(msg);
    const dataRows = await page.evaluate(() => document.querySelectorAll('tbody tr.ant-table-row').length);
    step('新建记录成功', recordOk, { msg: msg.slice(0, 160), dataRows });
    if (!recordOk) await page.screenshot({ path: `${OUT}/06-record-fail.png` });
  } catch (e) { step('新建记录', false, { detail: String(e.message).slice(0, 140) }); await page.screenshot({ path: `${OUT}/06-record-fail.png` }); }

  // ---- 编辑记录（走 If-Match / 412 代码路径）----
  let rowBtnsDiag = [];
  try {
    await sleep(1200);  // 新建记录弹窗关闭后表格重渲染，等行稳定
    const row = page.locator('tbody tr.ant-table-row:visible').first();
    // 先记录行内所有可点击元素（诊断 + 选候选）
    const rowBtns = await row.locator('button, a, [title], [aria-label], span[role="button"]').evaluateAll(
      es => es.map(e => ({ tag: e.tagName, text: (e.innerText || '').trim().slice(0,20), title: e.getAttribute('title') || '', aria: e.getAttribute('aria-label') || '', cls: (e.className||'').toString().slice(0,40) })).filter(x => x.text || x.title || x.aria).slice(0, 15));
    rowBtnsDiag = rowBtns;
    // 兼容文字按钮与图标按钮（title/aria-label=编辑，或 anticon-edit）
    // 2026-11-18 行操作图标：<span aria-label="edit" class="anticon-edit">（唯一、可见）。
    // 「点击行内编辑」title 挂在多个单元格 DIV 上，.first() 可能命中隐藏列 ⇒ 放最后兜底。
    let edit = row.locator('[aria-label="edit"]').first();
    if (!(await edit.count())) edit = row.locator('button').filter({ hasText: '编辑' }).first();
    if (!(await edit.count())) edit = row.locator('[title="点击行内编辑"]:visible').first();
    if (await edit.count()) {
      // 行操作图标默认 hover 才显示（antd row-hover action）⇒ 先 hover 整行让图标可见
      await row.hover({ timeout: 5000 });
      await sleep(400);
      const editInfo = await edit.evaluate(e => ({ tag: e.tagName, text: (e.innerText||'').trim(), title: e.getAttribute('title'), cls: (e.className||'').toString().slice(0,60), visible: !!(e.offsetWidth||e.offsetHeight) }));
      await edit.click({ timeout: 5000 });
      await sleep(1200);
      const input = page.locator('.ant-modal-content:visible input.ant-input').first();
      if (await input.count()) await input.fill(NAME_EDIT);
      await sleep(200);
      await page.locator('.ant-modal-content:visible button').filter({ hasText: /确\s*定|保\s*存|更\s*新|提\s*交/ }).first().click({ timeout: 5000 });
      await sleep(2200);
      const msg = await page.evaluate(() => [...document.querySelectorAll('.ant-message-notice-content')].map(e => e.innerText).join(' | '));
      step('编辑记录（If-Match 正常提交）', /更新成功/.test(msg), { msg: msg.slice(0, 160), editInfo, rowBtns });
    } else {
      step('编辑记录', false, { detail: '无编辑按钮', rowBtns });
    }
  } catch (e) { step('编辑记录', false, { detail: String(e.message).slice(0, 140), rowBtns: rowBtnsDiag }); await page.screenshot({ path: `${OUT}/07-edit-fail.png` }); }

  report.entityCode = entCode;
  report.entityName = entName;
  await browser.close();
  console.log(JSON.stringify(report, null, 1));
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
