/**
 * 保单号 → 保单详情下钻 E2E（2026-11-22）。
 *
 * 判据：
 *   1. 保单管理列表的平台保单号是带稳定 testid 的可点击链接（不是纯文本）；
 *   2. 点击后在**新标签页**打开 /claim#/policy?policyNo=<no>，原页面不动；
 *   3. 新标签页的详情抽屉自动展开并渲染保单字段（复用既有详情实现，不是第二份页面）；
 *   4. 核赔/初审/案件 360/理赔进度四处的保单号也在源码层接入同一组件；
 *   5. 全程无 console error。
 *
 * 依赖：shell :3000 + claim 微应用 :3013 + 网关 :8888 + claim :8087 + iam :8081。
 */
import fs from 'node:fs';
import path from 'node:path';
import { SHOT_DIR, CHROME, loadPlaywright, makeChecker, api } from '../e2e_lib/client.cjs';
import { uiLogin, localToken } from '../e2e_lib/ui.cjs';

const { chromium } = loadPlaywright();
const { check, report } = makeChecker();
const isNoise = (e) =>
  /favicon|Failed to load resource:|\[antd[^\]]*\]|deprecated|ResizeObserver loop|Warning: |single-spa minified message/i.test(e);

const STAMP = Date.now().toString(36).toUpperCase().slice(-6);
const POLICY_NO = `POL-LINK-${STAMP}`;

const launchOpts = { headless: true };
if (fs.existsSync(CHROME)) launchOpts.executablePath = CHROME;
const browser = await chromium.launch(launchOpts);
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
page.setDefaultTimeout(20000);

const consoleErrors = [];
function watchConsole(p) {
  p.on('console', (m) => {
    if (m.type() === 'error' && !isNoise(m.text())) consoleErrors.push(m.text().slice(0, 200));
  });
}
watchConsole(page);

try {
  check('UI 登录成功', await uiLogin(page), page.url());
  await page.waitForSelector('.shell-sider', { timeout: 20000 });
  const token = await localToken(page);
  check('浏览器本地取到 JWT（供 API 造数）', Boolean(token), token ? 'ok' : 'localStorage 无 token');

  // 造一张真实保单：不依赖测试环境的历史数据漂移。
  const created = await api(
    'POST',
    '/claim-config/policies',
    {
      policyNo: POLICY_NO,
      policyName: `保单号下钻探针 ${STAMP}`,
      insurerName: '中国人民财产保险股份有限公司',
      branchName: '深圳中心支公司',
      insureName: '汇智精密制造（深圳）有限公司',
      bizIdentityCode: 'UI-PROBE-BIZ-001',
      productType: 'GROUP_SUPPLEMENTARY_MEDICAL',
      effectiveStart: '2026-01-01T00:00:00Z',
      effectiveEnd: '2027-12-31T00:00:00Z',
      waitingPeriod: 0,
      policyVersion: 'v1',
      insuredSnapshot: JSON.stringify([`link-probe-insured-${STAMP}`]),
    },
    { token },
  );
  check('造数：保单已建', created.status === 201, `status=${created.status}`);

  // 真实用户路径：先展开「智能理赔」域菜单，再点侧栏「保单管理」（域内分组是常驻标题，不是折叠菜单）。
  const domain = page.locator('.shell-sider .ant-menu-submenu-title').filter({ hasText: '智能理赔' }).first();
  await domain.waitFor({ state: 'visible', timeout: 20000 });
  const domainOpened = await domain.evaluate((el) => (el.parentElement?.className || '').includes('ant-menu-submenu-open'));
  if (!domainOpened) await domain.click();
  await page.waitForTimeout(600);

  const menu = page.locator('.shell-sider .ant-menu-item').filter({ hasText: '保单管理' }).first();
  await menu.waitFor({ state: 'visible', timeout: 20000 });
  await menu.click();
  await page.waitForTimeout(2500);

  // 用搜索框收敛到本用例保单，避免列表分页导致假红。
  const box = page.locator('#subapp-viewport input[placeholder*="保单号"]').first();
  await box.fill(POLICY_NO);
  await box.press('Enter');
  await page.waitForTimeout(1800);

  const link = page.locator(`[data-testid="policy-no-link-${POLICY_NO}"]`);
  const linkCount = await link.count();
  check('保单号渲染为可点击链接', linkCount > 0, `链接数=${linkCount}`);

  if (linkCount > 0) {
    const href = await link.getAttribute('href');
    check('href 指向保单详情焦点深链', Boolean(href && href.includes(`/claim-detail#/policy?policyNo=${POLICY_NO}`)), `href=${href}`);
    check('链接声明在新标签页打开', (await link.getAttribute('target')) === '_blank', `target=${await link.getAttribute('target')}`);

    const listUrl = page.url();
    const [detailPage] = await Promise.all([
      context.waitForEvent('page', { timeout: 15000 }),
      link.click(),
    ]);
    watchConsole(detailPage);
    await detailPage.waitForLoadState('domcontentloaded').catch(() => {});
    await detailPage.waitForTimeout(2800);

    const detailUrl = detailPage.url();
    check('新标签页打开保单详情焦点页', detailUrl.includes('/claim-detail#/policy?policyNo=') && detailUrl.includes(POLICY_NO), `url=${detailUrl}`);
    check('焦点页无左侧菜单', (await detailPage.locator('.shell-sider').count()) === 0, `shell-sider 数=${await detailPage.locator('.shell-sider').count()}`);
    check('焦点页无顶栏', (await detailPage.locator('.shell-header').count()) === 0, `shell-header 数=${await detailPage.locator('.shell-header').count()}`);
    check('焦点页无保单列表（详情即整页）', (await detailPage.locator('#subapp-viewport .ant-table').count()) === 0, `列表表数=${await detailPage.locator('#subapp-viewport .ant-table').count()}`);
    check('原列表页面未被导航走', page.url() === listUrl, `before=${listUrl} after=${page.url()}`);

    const drawer = detailPage.locator('.ant-drawer').filter({ hasText: '保单详情' });
    check('保单详情抽屉自动展开', (await drawer.count()) > 0 && (await drawer.first().isVisible().catch(() => false)), `抽屉数=${await drawer.count()}`);
    const drawerText = await drawer.first().innerText().catch(() => '');
    check('详情抽屉渲染出保单字段', drawerText.includes(POLICY_NO) && drawerText.includes('基础信息'), `text长度=${drawerText.length}`);
    try {
      await detailPage.screenshot({ path: `${SHOT_DIR}/policy-focus-detail-${Date.now()}.png`, fullPage: false });
    } catch { /* 截图失败不影响判据 */ }
    await detailPage.close();
  }

  // 其余四处由同一组件接入：源码级断言，避免依赖难造的赔案/员工端数据。
  const srcRoot = path.resolve(
    path.dirname(new URL(import.meta.url).pathname),
    '../../../bone-frontend/apps/bone-claim-app/src/pages',
  );
  for (const [file, label] of [
    ['ReviewWorkbenchPage.tsx', '核赔工作台'],
    ['PreExamWorkbenchPage.tsx', '初审工作台'],
    ['Case360Page.tsx', '案件 360'],
    ['ClaimProgressPage.tsx', '理赔进度'],
  ]) {
    const src = fs.readFileSync(path.join(srcRoot, file), 'utf8');
    check(`${label}保单号接入 PolicyNoLink`, src.includes('<PolicyNoLink'), `源码未找到 ${file} 的 PolicyNoLink`);
  }

  check('全程无 console error', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));

  try {
    await page.screenshot({ path: `${SHOT_DIR}/policy-no-link-${Date.now()}.png`, fullPage: false });
  } catch { /* 截图失败不影响判据 */ }
} finally {
  await browser.close();
}

const r = report('保单号下钻保单详情 E2E');
process.exit(r.ok ? 0 : 1);
