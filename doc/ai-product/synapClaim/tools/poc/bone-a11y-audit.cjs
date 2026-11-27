const { chromium } = require('playwright');
const BASE = 'http://127.0.0.1:3013';
const TOKEN = process.env.POC_TOKEN || '';
const ROUTES = ['review', 'policy', 'quota', 'mappings', 'library'];

const auditDom = () => {
  const name = (el) =>
    (el.getAttribute('aria-label') || el.getAttribute('aria-labelledby') ||
      (el.querySelector('caption') ? el.querySelector('caption').textContent.trim() : '') || '').trim();
  const tables = [...document.querySelectorAll('table')].map((t, i) => ({
    i, name: name(t), headers: t.querySelectorAll('th').length,
  }));
  // 可访问名称：显式 aria / 文本 / title / aria-describedby（antd Tooltip 包裹）/
  // 子元素 aria-label（图标按钮）/ aria-haspopup（antd Select 触发器）均视为有名称。
  const accName = (el) =>
    el.getAttribute('aria-label') ||
    el.getAttribute('aria-labelledby') ||
    (el.innerText || '').trim() ||
    el.getAttribute('title') ||
    (el.getAttribute('aria-describedby') ? '(described)' : '') ||
    (el.querySelector('[aria-label]') ? '(child-icon)' : '') ||
    (el.getAttribute('aria-haspopup') ? '(popup)' : '');
  const unnamedButtons = [...document.querySelectorAll('button')]
    .filter((b) => !accName(b))
    .map((b) => b.outerHTML.slice(0, 130));
  const unlabeledInputs = [...document.querySelectorAll('input')]
    .filter((i) => {
      if (i.type === 'hidden') return false;
      // antd Select 内部 search input 由 combobox role + aria-controls/expanded 承载，
      // 且被 aria-hidden 的隐藏控件不进可访问树，二者都不算缺口。
      if (i.getAttribute('role') === 'combobox' || i.closest('[aria-hidden="true"]')) return false;
      const label = i.id ? document.querySelector('label[for="' + CSS.escape(i.id) + '"]') : null;
      return !i.getAttribute('aria-label') && !i.getAttribute('aria-labelledby') &&
        !label && !i.closest('label') && !i.getAttribute('title');
    })
    .map((i) => i.outerHTML.slice(0, 130));
  return {
    tables: tables.length,
    unnamedTables: tables.filter((t) => !t.name),
    unnamedButtons,
    unlabeledInputs,
  };
};

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 950 } });
  await ctx.addInitScript((t) => localStorage.setItem('token', t), TOKEN);
  const page = await ctx.newPage();
  const summary = {};
  for (const route of ROUTES) {
    await page.goto(BASE + '/sclaim/claim/' + route, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3200);
    summary[route] = await page.evaluate(auditDom);
    console.log(route, JSON.stringify({
      tables: summary[route].tables,
      unnamedTables: summary[route].unnamedTables.length,
      unnamedButtons: summary[route].unnamedButtons.length,
      unlabeledInputs: summary[route].unlabeledInputs.length,
    }));
  }

  // Modal 焦点陷阱：编辑保单弹窗
  await page.goto(BASE + '/sclaim/claim/policy?page=1&size=10', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3200);
  const trigger = page.locator('button:has-text("编辑")').first();
  await trigger.click();
  await page.waitForTimeout(1200);
  const focusState = [];
  focusState.push(await page.evaluate(() => {
    const modal = document.querySelector('.ant-modal-content');
    return { inside: !!modal && modal.contains(document.activeElement), active: document.activeElement?.outerHTML?.slice(0, 90) };
  }));
  for (let i = 0; i < 12; i++) {
    await page.keyboard.press('Tab');
    focusState.push(await page.evaluate(() => {
      const modal = document.querySelector('.ant-modal-content');
      return !!modal && modal.contains(document.activeElement);
    }));
  }
  const escaped = focusState.slice(1).filter((v) => v === false || (v && v.inside === false)).length;
  await page.keyboard.press('Escape');
  await page.waitForTimeout(700);
  const restored = await page.evaluate(() => (document.activeElement?.innerText || '').includes('编辑'));
  console.log('modal', JSON.stringify({ initial: focusState[0], tabEscapes: escaped, focusRestoredAfterEsc: restored }));
  console.log('FULL=' + JSON.stringify({ summary, focusState }));
  await browser.close();
})().catch((e) => { console.error('AUDIT FAIL:', e.message); process.exit(1); });
