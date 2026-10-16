/**
 * P5 联调探针：发布可见性三件套（第五轮：对标 Figma 版本对比 / GitHub PR summary）
 * 对 dev 栈实测：shell:3000 → metadata:3004(dev) → 网关:8888 → metadata-server:9001
 *
 * 覆盖：
 *  HARD-1  设计器挂载（含首访 3 步引导模态关闭）
 *  HARD-2  草稿/线上对照开关：存在（实体有 published 快照）、切换双向生效、徽标 data-source 真切换
 *  HARD-3  拖放落点高亮：dragstart→dragover 后目标行边框变蓝 + "松开落到此处"；dragend 后复原
 *  HARD-4  变更预告：UI 制造 pending（加字段+保存）→「待发布」徽标 → 弹层列出"新增字段"人话项
 *  HARD-5  还原（删字段+保存）→ 回到「已发布」，预告按钮消失（幂等收尾）
 *  HARD-6  零真实 console 错误 / 页面异常
 */
const { chromium } = require('playwright');

const EID = '765601790585470976';

(async () => {
  let hardFail = 0;
  let softFail = 0;
  const H = (name, ok, d = '') => { console.log(`[HARD] ${ok ? 'PASS' : 'FAIL'}  ${name}  ${d}`); if (!ok) hardFail++; };
  const S = (name, ok, d = '') => { console.log(`[SOFT] ${ok ? 'PASS' : 'FAIL'}  ${name}  ${d}`); if (!ok) softFail++; };

  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1680, height: 1000 } });
  const page = await ctx.newPage();

  const errors = [];
  page.on('console', (m) => {
    if (m.type() !== 'error') return;
    const t = m.text();
    if (t.includes('clearIcon') || t.includes('deprecated')) return;
    errors.push(t);
  });
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message));

  // 登录（shell）
  await page.goto('http://localhost:3000/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(2500);
  if (await page.locator('#username').count()) {
    await page.fill('#username', 'admin');
    await page.fill('#password', '123456');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(3000);
  }

  // ---------- HARD-1 进入设计器 ----------
  await page.goto(`http://localhost:3000/metadata#/entities/${EID}?tab=uischema`, { waitUntil: 'domcontentloaded' });
  let mounted = true;
  try {
    await page.waitForSelector('button[aria-label="撤销"]', { timeout: 45000 });
  } catch {
    mounted = false;
  }
  H('设计器: 挂载（工具栏撤销按钮可见）', mounted);

  // 首访 3 步引导模态（localStorage 空 → 自动弹）：关闭之
  const guideBtn = page.locator('.ant-modal-wrap:visible button', { hasText: '开始使用' });
  if (await guideBtn.count()) {
    await guideBtn.first().click();
    await page.waitForTimeout(400);
  }
  await page.waitForTimeout(1500);

  // 发布态标签作用域：设计器工具栏（撤销按钮的父容器）。页面别处（实体头部状态区）
  // 也有可见的「已发布 / 已发布且最新」标签——全局 getByText 会误报（探针假绿）。
  const toolbar = page.locator('button[aria-label="撤销"]').locator('xpath=..');
  const toolbarTags = async () => (await toolbar.locator('.ant-tag').allInnerTexts()).join('|');
  const isPending = async () => (await toolbar.locator('.ant-tag', { hasText: /^待发布$/ }).count()) > 0;

  // ---------- HARD-2 草稿/线上对照开关 ----------
  const sourceBadge = page.locator('.uis-pv-source');
  const hasToggle = (await sourceBadge.count()) === 1;
  H('线上对照: 预览源徽标存在（实体有 published 快照）', hasToggle);
  if (hasToggle) {
    H('线上对照: 初始为草稿态', (await sourceBadge.getAttribute('data-source')) === 'draft',
      `data-source=${await sourceBadge.getAttribute('data-source')}`);
    await page.locator('.ant-segmented-item', { hasText: '线上' }).first().click();
    await page.waitForTimeout(600);
    const onOk = (await sourceBadge.getAttribute('data-source')) === 'published'
      && (await sourceBadge.innerText()).includes('线上发布版');
    H('线上对照: 切到「线上」徽标变为发布版（只读）', onOk,
      `data-source=${await sourceBadge.getAttribute('data-source')} text=${(await sourceBadge.innerText()).trim()}`);
    // 线上态下草稿差异警示（若当前 pending）
    const warnOn = await page.locator('.uis-root .ant-alert-warning', { hasText: '线上发布版' }).count();
    S('线上对照: pending 态下出现草稿差异警示', warnOn > 0 || !(await isPending()),
      `alert=${warnOn} pending=${await isPending()}`);
    await page.locator('.ant-segmented-item', { hasText: '草稿' }).first().click();
    await page.waitForTimeout(400);
    H('线上对照: 切回「草稿」徽标复原', (await sourceBadge.getAttribute('data-source')) === 'draft');
  }

  // ---------- HARD-3 拖放落点高亮 ----------
  const rows = page.locator('[draggable="true"]:has(code)');
  const rowCount = await rows.count();
  if (rowCount >= 2) {
    const rowA = rows.nth(0);
    const rowB = rows.nth(1);
    const dataTransfer = await page.evaluateHandle(() => new DataTransfer());
    await rowA.dispatchEvent('dragstart', { dataTransfer });
    await rowB.dispatchEvent('dragover', { dataTransfer });
    await page.waitForTimeout(300);
    const overStyle = await rowB.evaluate((el) => {
      const cs = getComputedStyle(el);
      return { border: cs.borderColor, bg: cs.backgroundColor, hint: (el.textContent || '').includes('松开落到此处') };
    });
    H('拖放高亮: 悬停行边框变蓝(#1677ff)', overStyle.border === 'rgb(22, 119, 255)', JSON.stringify(overStyle));
    H('拖放高亮: 悬停行出现「松开落到此处」提示', overStyle.hint);
    await rowA.dispatchEvent('dragend', { dataTransfer });
    await page.waitForTimeout(300);
    const endStyle = await rowB.evaluate((el) => getComputedStyle(el).borderColor);
    H('拖放高亮: 拖拽结束后高亮复原(#f0f0f0)', endStyle === 'rgb(240, 240, 240)', `border=${endStyle}`);
    // 负向：未拖拽的第三行（若存在）不得带高亮
    if (rowCount >= 3) {
      const rowC = await rows.nth(2).evaluate((el) => getComputedStyle(el).borderColor);
      H('拖放高亮: 非悬停行不被误高亮', rowC === 'rgb(240, 240, 240)', `border=${rowC}`);
    }
  } else {
    H('拖放高亮: 结构面板控件行 ≥2 可供拖拽', false, `rows=${rowCount}`);
  }

  // ---------- HARD-4 变更预告（制造 pending：字段库加字段 → 保存） ----------
  let addedCode = null;
  const pendingBefore = await isPending();
  if (!pendingBefore) {
    await page.locator('button[aria-label="添加字段到界面"]').click();
    await page.waitForTimeout(500);
    const paletteTag = page.locator('.ant-popover:visible .ant-tag').first();
    if (await paletteTag.count()) {
      // 标签结构：displayName 文本 + 末尾 code 的 Text span——取 lastElementChild 拿字段 code
      // （曾用 innerText.split 因无分隔符把整个串当 code，导致还原行选择器落空）
      addedCode = (await paletteTag.evaluate((el) => el.lastElementChild?.textContent || '')) || null;
      await paletteTag.click();
      await page.waitForTimeout(400);
    }
    // 关掉字段库 popover（点空白处）
    await page.keyboard.press('Escape');
    await page.waitForTimeout(300);
    await toolbar.locator('button', { hasText: /^保存$/ }).first().click();
    await page.waitForTimeout(2500);
  }
  const pendingAfter = await isPending();
  H('变更预告: 加字段+保存后出现「待发布」徽标', pendingAfter,
    `tags=${await toolbarTags()}${addedCode ? ` added=${addedCode}` : pendingBefore ? ' (进入时既存 pending)' : ' (未取到字段 code)'}`);

  const previewBtn = toolbar.locator('button', { hasText: '变更预告' });
  const btnVisible = (await previewBtn.count()) > 0;
  H('变更预告: 工具栏出现「变更预告」按钮', btnVisible);
  if (btnVisible) {
    await previewBtn.first().click();
    await page.waitForTimeout(600);
    const pop = page.locator('.ant-popover-inner:visible').last();
    const popText = (await pop.count()) ? await pop.innerText() : '';
    // 自造 pending（加了表单字段）时必现「新增字段」；进入时既存 pending 时放宽为人话标记正则
    const nlOk = popText.includes('新增字段') || /新增|移除|改为|顺序调整|已修改|列宽|固定左侧|标题/.test(popText);
    H('变更预告: 弹层列出人话变更项', nlOk, popText.slice(0, 200).replace(/\n/g, ' | '));
    S('变更预告: 自造改动场景弹层含「新增字段」与分组标签',
      !addedCode || (popText.includes('新增字段') && popText.includes('录入表单')),
      `addedCode=${addedCode}`);
    await page.keyboard.press('Escape');
  }

  // ---------- HARD-5 还原（删掉刚加的控件 → 保存 → 回已发布） ----------
  if (addedCode) {
    const addedRow = page.locator('[draggable="true"]:has(code)').filter({ hasText: addedCode }).first();
    const found = (await addedRow.count()) > 0;
    H('还原: 定位到刚添加的控件行', found, `code=${addedCode}`);
    if (found) {
      await addedRow.locator('.anticon-delete').click();
      await page.waitForTimeout(400);
      await toolbar.locator('button', { hasText: /^保存$/ }).first().click();
      await page.waitForTimeout(2500);
    }
    const backPublished = !(await isPending()) && (await toolbar.locator('.ant-tag', { hasText: /^已发布$/ }).count()) > 0;
    H('还原: 删除字段+保存后回到「已发布」', backPublished, `tags=${await toolbarTags()}`);
    await page.waitForTimeout(800);
    const btnGone = (await toolbar.locator('button', { hasText: '变更预告' }).count()) === 0;
    H('还原: 「变更预告」按钮随之消失', btnGone, `tags=${await toolbarTags()}`);
  } else {
    S('还原: 跳过（进入时既存 pending，未制造改动）', true);
  }

  // ---------- HARD-6 console ----------
  H('零真实 console 错误 / 页面异常', errors.length === 0, errors.slice(0, 3).join(' | '));

  console.log(`\n==== P5 汇总: HARD ${hardFail === 0 ? 'ALL PASS' : hardFail + ' FAIL'} | SOFT ${softFail === 0 ? 'ALL PASS' : softFail + ' FAIL'} ====`);
  await browser.close();
  process.exit(hardFail === 0 ? 0 : 1);
})().catch((e) => { console.error('PROBE ERROR:', e); process.exit(2); });
