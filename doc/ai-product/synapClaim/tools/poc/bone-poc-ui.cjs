const { chromium } = require('playwright');
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const BASE = 'http://127.0.0.1:3013';
const TOKEN = process.env.POC_TOKEN || '';
const SHOTS = '/tmp/bone-poc-screens';
const results = [];

function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail });
  console.log((ok ? '[PASS] ' : '[FAIL] ') + name + ' :: ' + detail);
}

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 950 } });
  await ctx.addInitScript((t) => localStorage.setItem('token', t), TOKEN);
  // 独立渲染分支没有 Shell，注入最小事件总线以模拟 bone:theme:change 广播。
  await ctx.addInitScript(() => {
    const listeners = {};
    window.__BONE_EVENT_BUS__ = {
      on: (e, fn) => { (listeners[e] = listeners[e] || []).push(fn); },
      off: (e, fn) => { listeners[e] = (listeners[e] || []).filter((f) => f !== fn); },
      emit: (e, data) => { (listeners[e] || []).forEach((f) => f(data)); },
    };
  });
  const page = await ctx.newPage();
  const errors = [];
  const failures = [];
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));
  page.on('console', (m) => { if (m.type() === 'error') errors.push('console: ' + m.text().slice(0, 200)); });
  page.on('response', (r) => { if (r.status() >= 400) failures.push(r.status() + ' ' + r.url().slice(0, 140)); });

  // ---- 场景 1：核赔页 URL 真源直达 ----
  await page.goto(BASE + '/sclaim/claim/review?f_taskStatus=WAITING&page=2&size=10', {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(3800);
  const reviewUrl = page.url();
  const reviewRows = await page.locator('.ant-table-tbody tr.ant-table-row').count();
  check('核赔页 URL 查询参数直达且未被吞掉',
    reviewUrl.includes('f_taskStatus=WAITING') && reviewUrl.includes('page=2') && reviewRows === 10,
    'URL=' + reviewUrl.split('3013')[1] + ', 行数=' + reviewRows);
  await page.screenshot({ path: SHOTS + '/01-review-url-source.png' });

  // ---- 场景 2：切筛选自动回第 1 页（setFilter 合并语义）----
  const taskSelect = page.locator('.ant-select').filter({ hasText: '待领取' }).last();
  await taskSelect.click();
  await page.waitForTimeout(600);
  await page.locator('.ant-select-item-option').filter({ hasText: '处理中（已领取）' }).first().click();
  await page.waitForTimeout(2600);
  const afterFilter = page.url();
  check('切任务维筛选写回 URL 并回第 1 页',
    afterFilter.includes('f_taskStatus=PROCESSING') && !afterFilter.includes('page='),
    'URL=' + afterFilter.split('3013')[1]);

  // ---- 场景 3：刷新保持 + 浏览器后退可恢复 ----
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3200);
  const afterReload = page.url();
  check('刷新后筛选保持', afterReload.includes('f_taskStatus=PROCESSING'),
    'URL=' + afterReload.split('3013')[1]);
  await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
  await page.waitForTimeout(2600);
  const afterBack = page.url();
  check('浏览器后退恢复上一查询条件', afterBack.includes('f_taskStatus=WAITING'),
    'URL=' + afterBack.split('3013')[1]);

  // ---- 场景 4：批量拒赔显式禁用 ----
  await page.goto(BASE + '/sclaim/claim/review?f_taskStatus=WAITING&page=1&size=10', {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(3200);
  await page.locator('.ant-table-tbody tr.ant-table-row').first().locator('input[type="checkbox"]').click();
  await page.waitForTimeout(600);
  const reject = page.locator('button:has-text("批量拒赔")').first();
  const rejectCount = await reject.count();
  const rejectDisabled = rejectCount ? await reject.isDisabled() : false;
  check('批量拒赔按钮显式禁用（五要素合规）',
    rejectCount > 0 && rejectDisabled,
    '按钮数=' + rejectCount + ', disabled=' + rejectDisabled);
  await page.screenshot({ path: SHOTS + '/02-batch-reject-disabled.png' });

  // ---- 场景 5：额度页关键字同步（外部清空后搜索框不残留）----
  await page.goto(BASE + '/sclaim/claim/quota?q=e2e&page=1&size=10', {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(3800);
  const searchInput = page.locator('input[placeholder*="被保人假名化 ID"]').first();
  const initialValue = await searchInput.inputValue().catch(() => '<none>');
  check('额度页 URL 关键字回填搜索框', initialValue === 'e2e', '输入框值=' + initialValue);
  await page.screenshot({ path: SHOTS + '/03-quota-keyword-sync.png' });

  // 切到公账视图会 clearQuery（applyQueryString('')），关键字应同步清空
  await page.locator('.ant-segmented-item').filter({ hasText: '公账额度' }).first().click();
  await page.waitForTimeout(2400);
  const clearedValue = await searchInput.inputValue().catch(() => '<none>');
  const clearedUrl = page.url();
  check('切视图清空查询后搜索框同步清空（不残留旧文本）',
    clearedValue === '' && !clearedUrl.includes('q='),
    '输入框值=' + clearedValue + ', URL=' + clearedUrl.split('3013')[1]);

  // ---- 场景 6：主题事件跟随（Shell 广播语义）----
  const cardBgBefore = await page.locator('.ant-card').first().evaluate((el) =>
    getComputedStyle(el).backgroundColor).catch(() => '<none>');
  await page.evaluate(() => window.__BONE_EVENT_BUS__.emit('bone:theme:change', { theme: 'dark' }));
  await page.waitForTimeout(1200);
  const cardBgAfter = await page.locator('.ant-card').first().evaluate((el) =>
    getComputedStyle(el).backgroundColor).catch(() => '<none>');
  check('主题广播后 antd token 生效',
    cardBgBefore !== '<none>' && cardBgBefore !== cardBgAfter,
    '卡片背景 ' + cardBgBefore + ' → ' + cardBgAfter);
  await page.screenshot({ path: SHOTS + '/04-theme-dark.png' });

  // ---- 场景 7：运行时健康汇总 ----
  check('无失败网络请求（4xx/5xx）', failures.length === 0, '失败数=' + failures.length +
    (failures.length ? ', 样例=' + failures.slice(0, 3).join(' | ') : ''));
  check('无 console/pageerror', errors.length === 0, '错误数=' + errors.length +
    (errors.length ? ', 样例=' + errors.slice(0, 3).join(' | ') : ''));

  const passed = results.filter((r) => r.ok).length;
  console.log('\n===== 真实 UI POC: ' + passed + '/' + results.length + ' PASS =====');
  console.log('screens: ' + SHOTS);
  await browser.close();
})().catch((e) => { console.error('POC PROBE FAIL:', e.message); process.exit(1); });
