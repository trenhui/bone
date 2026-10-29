// 取各页面真实可点击元素（按钮文字 + 图标按钮的 title/aria-label），用于确认"新增"入口的真实形态
/* 共享 client：playwright 加载 / CHROME / UI BASE（见 e2e_lib/client.cjs）
 * 共享 UI 登录：uiLogin（凭据由 client.cjs 供给，登录选择器唯一真源见 e2e_lib/ui.cjs） */
import { loadPlaywright, CHROME, UI_BASE as BASE } from '../e2e_lib/client.cjs';
import { uiLogin } from '../e2e_lib/ui.cjs';
const { chromium } = loadPlaywright();
const PAGES = [
  ['iam', '#/accounts'], ['iam', '#/roles'], ['iam', '#/tenants'], ['iam', '#/organizations'], ['iam', '#/apps'],
  ['system', '#/alerts'], ['system', '#/config'], ['system', '#/dict'], ['system', '#/schedule'],
  ['integration', '#/connectors'], ['integration', '#/flows'],
  ['metadata', '#/entities'], ['masterdata', '#/entities'], ['generator', '#/datasources'],
];
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 950 } })).newPage();
  // 登录：统一走共享 uiLogin，直达 iam 登录路由（禁止在探针内重抄登录选择器）
  await uiLogin(page, { route: `${BASE}/iam#/accounts` });
  const out = {};
  for (const [app, hash] of PAGES) {
    await page.goto(`${BASE}/${app}${hash}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2800);
    out[`${app}${hash}`] = await page.evaluate(() => {
      const btns = [...document.querySelectorAll('button, [role="button"], a.ant-btn')].map(b => ({
        text: (b.innerText || '').trim().slice(0, 14),
        title: b.getAttribute('title') || b.getAttribute('aria-label') || '',
        cls: b.className.slice(0, 50),
      })).filter(b => b.text || b.title);
      // 只保留页面主操作区的（排除侧边栏与顶栏）
      const main = [...document.querySelectorAll('.ant-card button, .ant-table button, .ant-btn-primary, [class*="toolbar"] button, [class*="header"] button, .ant-space button')].map(b => (b.innerText || '').trim() || b.getAttribute('title') || b.getAttribute('aria-label') || '(图标)').slice(0, 16);
      return { allButtons: [...new Set(btns.map(b => b.text || b.title))].slice(0, 22), mainActions: [...new Set(main)].slice(0, 12) };
    });
  }
  await browser.close();
  console.log(JSON.stringify(out, null, 1));
})();
