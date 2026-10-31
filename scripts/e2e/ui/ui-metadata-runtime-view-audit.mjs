/**
 * 运行时数据页「用户视角 vs 实施视角」取证探针（只读）。
 *
 * 目的：回答「http://localhost:3000/metadata#/entities/:id/data 到底把哪些技术/实施配置信息
 * 暴露给了业务用户」——必须先看真实渲染结果，禁止凭想象下结论。
 *
 * 产出：
 *   1. 两个页面（报销主单 / 报销类别）的全页截图；
 *   2. 列表表头文本（真实的列集合）；
 *   3. 首行详情抽屉的文本抽样 + 截图；
 *   4. 页面全文中「技术/实施向」关键词命中（租户/版本号/信用代码/契约/左上角召回源等）。
 *
 * 用法：node scripts/e2e/ui/ui-metadata-runtime-view-audit.mjs
 */
import { UI_BASE, loadPlaywright } from '../e2e_lib/client.cjs';
import { uiLogin, waitAppReady } from '../e2e_lib/ui.cjs';
const { chromium } = loadPlaywright();

const PAGES = [
  { label: 'expense-reimbursement', entityId: '764059967975063552' },
  { label: 'expense-category', entityId: '9250' },
];

/** 技术/实施向关键词（中英双语，覆盖 DB 列名口径） */
const TECH_TOKENS = [
  'tenant', 'tenant_id', '租户', 'version', '版本号', 'deleted', '逻辑删除',
  '物理表', 'table_name', '契约', 'contract', 'ui-schema', 'overlay', 'schema',
  '3540', '0552', // 便于识别裸露雪花 ID/短 ID
];

async function audit(page, def) {
  const url = `${UI_BASE}/metadata#/entities/${def.entityId}/data`;
  await page.goto(url, { waitUntil: 'domcontentloaded' });
  await waitAppReady(page, 20000).catch(() => {});
  await page.waitForTimeout(3000);

  const headers = await page.locator('.ant-table-thead th').allInnerTexts();
  const bodyText = await page.locator('body').innerText();
  const hits = TECH_TOKENS.filter((t) => bodyText.toLowerCase().includes(t.toLowerCase()));
  const shot1 = `/tmp/audit-${def.label}-list.png`;
  await page.screenshot({ path: shot1, fullPage: true });

  // 详情抽屉：点第一个「查看」，抓抽屉内标签集合
  let detail = { labels: [], shot: null };
  const viewBtn = page.locator('button:has-text("查看"), a:has-text("查看")').first();
  if (await viewBtn.count()) {
    await viewBtn.click();
    await page.waitForTimeout(2500);
    detail.labels = await page.locator('.ant-drawer label, .ant-drawer .ant-descriptions-item-label').allInnerTexts();
    detail.shot = `/tmp/audit-${def.label}-detail.png`;
    await page.screenshot({ path: detail.shot, fullPage: false });
  }

  return { url, headers, hits, shot1, detail, textLen: bodyText.length };
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1680, height: 1050 } });
  const ok = await uiLogin(page);
  console.log(JSON.stringify({ step: 'login', ok }, null, 2));
  if (!ok) { await browser.close(); process.exit(1); }

  for (const def of PAGES) {
    const r = await audit(page, def);
    console.log(JSON.stringify({ step: def.label, ...r }, null, 2));
  }
  await browser.close();
})();
