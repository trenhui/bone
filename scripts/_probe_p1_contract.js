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

  // ---------- 列表显示 Tab：定位修正 + strict 引导 ----------
  await page.goto(`http://localhost:3000/metadata#/entities/${EID}?tab=ui`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(4000);
  const tabLabels = await page.evaluate(() =>
    [...document.querySelectorAll('.ant-tabs-tab')].map((t) => (t.textContent || '').trim()));
  S('定位: 「显示设置」已更名为「列表显示」', tabLabels.some((t) => t.includes('列表显示')), `tabs=${JSON.stringify(tabLabels)}`);
  S('定位: 不再出现旧名「显示设置」', !tabLabels.some((t) => t.includes('显示设置')), '');

  const b2 = await body();
  S('分层: 契约分层 banner 出现(列集合/列序/列宽归设计器)',
    b2.includes('界面设计器') && b2.includes('契约'), '');
  S('strict: 配置表出现「未在契约」提示', b2.includes('未在契约'), '');

  // 预览（列表）与运行时同列：切到「预览」分段，行=契约6列同序
  const previewBtn = page.locator('.ant-segmented').first().locator('.ant-segmented-item', { hasText: '预览' });
  if (await previewBtn.count()) {
    await previewBtn.first().click();
    await page.waitForTimeout(1200);
    const rows = await page.evaluate(() => {
      const tbl = document.querySelector('.ant-table-wrapper .ant-table-tbody');
      if (!tbl) return [];
      // antd v5 在 scroll.x 下渲染 .ant-table-measure-row（度量行，无 td）——剔除
      return [...tbl.querySelectorAll('tr')]
        .filter((tr) => !tr.classList.contains('ant-table-measure-row'))
        .map((tr) => {
          const td = tr.querySelector('td');
          return td ? (td.textContent || '').trim() : '';
        })
        .filter((t) => t !== '');
    });
    S('预览≡运行时: 预览列表行=契约6列同序',
      rows.length === EXPECT_HEADERS.length && rows.every((r, i) => r === EXPECT_HEADERS[i]),
      `rows=${JSON.stringify(rows)}`);
  } else {
    S('预览≡运行时: 预览分段可点', false, '未找到预览分段');
  }

  H('零 console error', errors.length === 0, errors.slice(0, 3).join(' | '));

  console.log(`\n===== 结果: HARD ${hardFail === 0 ? 'ALL PASS' : hardFail + ' FAIL'} | SOFT ${softFail === 0 ? 'ALL PASS' : softFail + ' FAIL'} =====`);
  await browser.close();
  process.exit(hardFail === 0 ? 0 : 1);
})().catch((e) => { console.error('PROBE CRASH:', e.message); process.exit(2); });
