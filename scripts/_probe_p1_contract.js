/**
 * P1 联调探针：界面设计器 list 契约 → 运行时数据列表（strict 集合 + 列宽生效）
 * 对 dev 栈实测：shell:3000 → metadata:3004(dev) → 网关:8888 → metadata-server:9001
 * 期望事实源：published ui-schema views.list.columns =
 *   claim_no(150) 被保人(100) 医院等级(110) 申报金额(120) 应赔金额(120) 状态(120)，全 visible
 * 实体共 22 字段 ⇒ strict 集合下运行时表头必须恰好这 6 列（+操作列），
 * 非契约字段（如 理算金额）不得出现——证明"配了不生效"缺口已修复。
 */
const { chromium } = require('playwright');

const EID = '765601790585470976';
const EXPECT_HEADERS = ['报案号', '被保人', '医院等级', '申报金额', '应赔金额', '状态'];

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

  const body = async () => (await page.evaluate(() => document.body.innerText));

  // 登录（shell）
  await page.goto('http://localhost:3000/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(2500);
  if (await page.locator('#username').count()) {
    await page.fill('#username', 'admin');
    await page.fill('#password', '123456');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(3000);
  }

  // ---------- 数据 Tab：运行时列表列 = published 契约 ----------
  await page.goto(`http://localhost:3000/metadata#/entities/${EID}?tab=data`, { waitUntil: 'domcontentloaded' });
  const headersOk = await page.waitForFunction(
    () => {
      const ths = [...document.querySelectorAll('.ant-table-thead .ant-table-cell')]
        .map((t) => (t.textContent || '').trim())
        .filter((t) => t && t !== '操作');
      return ths.length >= 6 ? ths : false;
    },
    { timeout: 45000 },
  ).then((h) => h.jsonValue()).catch(() => null);

  if (!headersOk) {
    H('数据Tab: 运行时表格渲染', false, 'headers 未出现; body=' + (await body()).slice(0, 160));
  } else {
    H('数据Tab: 运行时表格渲染', true, `headers=${JSON.stringify(headersOk)}`);
    const same =
      headersOk.length === EXPECT_HEADERS.length &&
      headersOk.every((h, i) => h === EXPECT_HEADERS[i]);
    H('数据Tab: 表头严格=契约6列且同序(strict 集合)', same, `got=${JSON.stringify(headersOk)}`);
    H('数据Tab: 非契约字段(理算金额)不出现在表头', !headersOk.includes('理算金额'), '');
    const cols = await page.evaluate(() =>
      [...document.querySelectorAll('.ant-table colgroup col')].map((c) => c.style.width || ''));
    H('数据Tab: 契约列宽生效(首列=150px)', cols[0] === '150px', `cols=${JSON.stringify(cols.slice(0, 8))}`);
  }

  // ---------- 交付预览已归位：契约口径核对在设计器，个人口径对照在数据页 ----------
  // IA 收敛（EntityDetail）：顶层「交付预览」Tab 摘除；团队契约口径并入界面设计器，
  // 个人覆盖口径迁到运行时数据页「交付口径对照」卡片；?tab=ui 深链 rewrite 到 uischema。
  await page.goto(`http://localhost:3000/metadata#/entities/${EID}?tab=ui`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(4000);
  const tabLabels = await page.evaluate(() =>
    [...document.querySelectorAll('.ant-tabs-tab')].map((t) => (t.textContent || '').trim()));
  S('定位: 顶层 Tab 已无「交付预览」（已归位）', !tabLabels.some((t) => t.includes('交付预览')), `tabs=${JSON.stringify(tabLabels)}`);
  S('定位: 不再出现旧名「显示设置/个人显示偏好」', !tabLabels.some((t) => t.includes('显示设置') || t.includes('个人显示偏好')), '');
  // 深链兼容：旧 ?tab=ui 必须 rewrite 到 ?tab=uischema，不能静默落首页。
  // ⚠️不能用 includes('tab=ui') 判「仍是旧 key」—— 'tab=uischema' 含该子串会自我否定。
  S('定位: ?tab=ui 深链 rewrite 到 ?tab=uischema（PRD 黄金路径不失效）',
    page.url().includes('tab=uischema') && !/[?&]tab=ui(&|$)/.test(page.url()), `url=${page.url()}`);

  // 契约口径核对：设计器「列表」面板的「运行时最终显示 N 列」摘要
  await page.locator('[role="tab"]', { hasText: '列表' }).last().click().catch(() => {});
  await page.waitForTimeout(1500);
  const designerBody = await body();
  S('契约口径: 设计器出现「运行时最终显示 N 列」核对摘要',
    /运行时最终显示\s*\d+\s*列/.test(designerBody), '');

  // 个人口径对照 + strict 引导：运行时数据页「交付口径对照」卡片
  await page.goto(`http://localhost:3000/metadata#/entities/${EID}/data`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(5000);
  const b2 = await body();
  S('分层: 数据页出现「交付口径对照」卡片', b2.includes('交付口径对照'), '');
  S('分层: 双口径摘要存在（全员所见 / 我的所见）',
    b2.includes('全员所见（契约）') && b2.includes('我的所见（含个人覆盖）'), '');
  S('strict: 出现「不在团队契约」或等价未在契约提示',
    /不在团队契约|未在契约/.test(b2), '');

  // 预览≡运行时（归位后由对照卡片的「全员所见」承担）：契约6列同序。
  // 用对照卡片的列名序列（… → … 箭头链）核对契约列顺序，不依赖已删的预览表格。
  const contractSeq = /全员所见（契约）\s*\d+\s*列\s*\n?([^\n]*)/.exec(b2);
  const seqText = (contractSeq ? contractSeq[1] : '').trim();
  const listedCols = seqText.split(/[、→]/).map((s) => s.trim()).filter(Boolean);
  S('预览≡运行时: 对照卡片「全员所见」列序 = 契约6列同序',
    listedCols.length === EXPECT_HEADERS.length && EXPECT_HEADERS.every((h, i) => listedCols[i].startsWith(h.slice(0, 2))),
    `listed=${JSON.stringify(listedCols)}`);

  H('零 console error', errors.length === 0, errors.slice(0, 3).join(' | '));

  console.log(`\n===== 结果: HARD ${hardFail === 0 ? 'ALL PASS' : hardFail + ' FAIL'} | SOFT ${softFail === 0 ? 'ALL PASS' : softFail + ' FAIL'} =====`);
  await browser.close();
  process.exit(hardFail === 0 ? 0 : 1);
})().catch((e) => { console.error('PROBE CRASH:', e.message); process.exit(2); });
