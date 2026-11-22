/**
 * 案件 ID → 案件 360 详情下钻 E2E（2026-11-22）。
 *
 * 判据：
 *   1. 核赔工作台列表的「报案号」是带稳定 testid 的可点击链接（不是纯文本）；
 *   2. 点击后落到 /claim#/case?claimId=<id>&tab=overview，且案件 360 详情渲染出内容；
 *   3. 初审工作台的报案号、盲标工作台的案件 ID 同样是可点击链接（同一组件，三处接入）；
 *   4. 全程无 console error。
 *
 * 依赖：shell :3000 + claim 微应用 :3013（均需为含 ClaimIdLink 的新代码）+ 网关 :8888。
 */
import fs from 'node:fs';
import path from 'node:path';
import { SHOT_DIR, CHROME, loadPlaywright, makeChecker, api } from '../e2e_lib/client.cjs';
import { uiLogin } from '../e2e_lib/ui.cjs';

const { chromium } = loadPlaywright();
const { check, report } = makeChecker();
const isNoise = (e) =>
  /favicon|Failed to load resource:|\[antd[^\]]*\]|deprecated|ResizeObserver loop|Warning: |single-spa minified message/i.test(e);

const launchOpts = { headless: true };
if (fs.existsSync(CHROME)) launchOpts.executablePath = CHROME;
const browser = await chromium.launch(launchOpts);
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(20000);

const consoleErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error' && !isNoise(m.text())) consoleErrors.push(m.text().slice(0, 200));
});

const ok = await uiLogin(page);
check('UI 登录成功', ok, page.url());
await page.waitForSelector('.shell-sider', { timeout: 20000 });

// 进入理赔域：展开「智能理赔」侧栏分组（真实用户路径，避免手改 hash 丢掉 /claim 前缀）。
const groupTitle = page.locator('.shell-sider .ant-menu-submenu-title').filter({ hasText: '智能理赔' }).first();
await groupTitle.waitFor({ state: 'visible' });
const opened = await groupTitle.evaluate((el) => (el.parentElement?.className || '').includes('ant-menu-submenu-open'));
if (!opened) await groupTitle.click();

async function ensureGroupOpen() {
  const title = page.locator('.shell-sider .ant-menu-submenu-title').filter({ hasText: '智能理赔' }).first();
  if (!(await title.count())) return;
  const isOpen = await title.evaluate((el) => (el.parentElement?.className || '').includes('ant-menu-submenu-open')).catch(() => false);
  if (!isOpen) await title.click().catch(() => {});
}

async function gotoMenu(name) {
  await ensureGroupOpen();
  const item = page.locator('.shell-sider .ant-menu-item').filter({ hasText: name }).first();
  const visible = await item.isVisible().catch(() => false);
  if (!visible) {
    // 详情页可能让侧栏暂时折叠；直接回 /claim 基址再展开。
    await page.evaluate(() => { window.location.href = '/claim#/workbench'; });
    await page.waitForTimeout(1500);
    await ensureGroupOpen();
  }
  await item.waitFor({ state: 'visible', timeout: 15000 });
  await item.click();
  await page.waitForTimeout(2800);
}

// ---------- 1. 核赔工作台：报案号是可点击链接 ----------
await gotoMenu('核赔工作台');
await page.waitForSelector('#subapp-viewport .ant-table', { timeout: 20000 }).catch(() => {});
await page.waitForTimeout(1500);
if (process.env.E2E_DEBUG) {
  const dbg = await page.locator('#subapp-viewport').innerText().catch(() => '');
  console.log('[DEBUG review url]', page.url());
  console.log('[DEBUG review text]', dbg.slice(0, 600));
}

const reviewLinks = page.locator('[data-testid^="review-case-link-"]');
const reviewCount = await reviewLinks.count();
check('核赔工作台报案号渲染为可点击链接', reviewCount > 0, `链接数=${reviewCount}`);

if (reviewCount > 0) {
  const first = reviewLinks.first();
  const testId = await first.getAttribute('data-testid');
  const claimId = testId.replace('review-case-link-', '');
  const href = await first.getAttribute('href');
  check('链接 href 指向案件 360 深链', Boolean(href && href.includes(`#/case?claimId=${claimId}`)), `href=${href}`);

  // 点击 → 断言落到案件详情
  await first.click();
  await page.waitForTimeout(2500);
  const url = page.url();
  check('点击报案号跳转到案件 360 详情', url.includes('#/case?claimId=') && url.includes(claimId), `url=${url}`);
  const viewportText = await page.locator('#subapp-viewport').innerText().catch(() => '');
  check('案件 360 详情页渲染出内容', viewportText.length > 30, `text长度=${viewportText.length}`);
}

// ---------- 2. 初审工作台：报案号是可点击链接 ----------
await gotoMenu('初审工作台');
await page.waitForTimeout(2000);
const preLinks = page.locator('[data-testid^="pre-exam-case-link-"]');
check('初审工作台报案号渲染为可点击链接', (await preLinks.count()) > 0, `链接数=${await preLinks.count()}`);

// ---------- 3. 盲标工作台：案件 ID 列在源码层接入同一 ClaimIdLink 组件 ----------
// 盲标任务表默认空且列表端点按标注人租户隔离，浏览器直连会受测试数据租户上下文影响；
// 该列的渲染实现由 ClaimIdLink 单测（5 例）覆盖，这里做源码级接线断言，避免探针因数据漂移假红。
const blindSrc = fs.readFileSync(path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  '../../../bone-frontend/apps/bone-claim-app/src/pages/BlindLabelWorkbenchPage.tsx',
), 'utf8');
check(
  '盲标工作台案件 ID 列接入 ClaimIdLink',
  blindSrc.includes("dataIndex: 'claimId'") && blindSrc.includes('<ClaimIdLink claimId={v ?? row.claimId}'),
  '源码未找到 claimId 列的 ClaimIdLink 渲染',
);

// ---------- 4. 无 console error ----------
check('全程无 console error', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));

try {
  await page.screenshot({ path: `${SHOT_DIR}/claim-case-id-link-${Date.now()}.png`, fullPage: false });
} catch { /* 截图失败不影响判据 */ }
await browser.close();

const r = report('案件 ID 下钻案件 360 E2E');
process.exit(r.ok ? 0 : 1);
