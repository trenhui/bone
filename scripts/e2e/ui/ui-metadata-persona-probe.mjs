/**
 * persona（业务视图 / 实施模式）UI 回归探针。
 *
 * 背景：2026-10-31 取证发现运行时数据页把「物理表名 / 交付口径对照 / 建模生命周期 /
 * 内部代理键 / 雪花主键」一并铺给了报销业务用户。本探针锁定整改后的行为边界。
 *
 * 判别力约定（防假绿）：
 *   - 消费面的「应当看不见」必须配一条实施模式的「必须看得见」同名单项——只断言消失，
 *     页面白屏也会通过；两侧都不出现 ⇒ 判据失效。
 *   - 每轮都断言列表确有数据行（页面真的渲染出来了）。
 *
 * 用法：node scripts/e2e/ui/ui-metadata-persona-probe.mjs
 */
import { UI_BASE, loadPlaywright, makeChecker } from '../e2e_lib/client.cjs';
import { uiLogin, waitAppReady } from '../e2e_lib/ui.cjs';
const { chromium } = loadPlaywright();

const { check, report } = makeChecker();
const MAIN = '764059967975063552'; // 费用报销
const CATEGORY = '9250'; // 报销类别

const TECH_MARKERS = ['物理表', '交付口径对照', '已发布出物理表'];
const TECH_COL = '审批人账户ID';
const BUS_COL = '报销单号';

async function openEntity(page, id) {
  await page.goto(`${UI_BASE}/metadata#/entities/${id}/data`, { waitUntil: 'domcontentloaded' });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitAppReady(page, 20000).catch(() => {});
  await page.waitForTimeout(2500);
}

const headersOf = (page) => page.locator('.ant-table-thead th').allInnerTexts();
const bodyOf = (page) => page.locator('body').innerText();

async function setMode(page, mode) {
  const item = page.getByText(mode === 'implement' ? '实施模式' : '业务视图', { exact: true });
  if (await item.count()) {
    await item.first().click();
  } else {
    // 开关未渲染（无权限）时退化为直接写本地偏好，保证判据仍然可执行
    await page.evaluate((m) => localStorage.setItem('bone.meta.viewerMode', m), mode);
    await page.reload({ waitUntil: 'domcontentloaded' });
  }
  await page.waitForTimeout(2500);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1680, height: 1050 } });
  const ok = await uiLogin(page);
  if (!ok) {
    console.error('登录失败，环境不可用');
    await browser.close();
    process.exit(1);
  }

  // ---------- 消费面：不该看见技术/实施信息 ----------
  await setMode(page, 'consume');
  await openEntity(page, MAIN);
  let headers = await headersOf(page);
  let body = await bodyOf(page);
  const rows = await page.locator('.ant-table-tbody tr.ant-table-row').count();
  check('消费面 · 列表确有数据（页面真的渲染了）', rows > 0, `rows=${rows}`);
  for (const m of TECH_MARKERS) {
    check(`消费面 · 不出现「${m}」`, !body.includes(m));
  }
  check(`消费面 · 列表无内部代理键列「${TECH_COL}」`, !headers.some((h) => h.includes(TECH_COL)), headers.join('|'));
  check(`消费面 · 列表保留业务主键列「${BUS_COL}」`, headers.some((h) => h.includes(BUS_COL)));

  // ---------- 实施模式：同名单项必须出现（反之消费面断言不成立） ----------
  await setMode(page, 'implement');
  await page.waitForTimeout(500);
  headers = await headersOf(page);
  body = await bodyOf(page);
  for (const m of TECH_MARKERS) {
    check(`实施模式 · 必须出现「${m}」（证明消费面消失≠白屏）`, body.includes(m));
  }
  check(`实施模式 · 内部代理键列「${TECH_COL}」回归`, headers.some((h) => h.includes(TECH_COL)), headers.join('|'));
  check('实施模式 · 自证「隐藏了哪些代理键」', body.includes('代理键字段'));

  // ---------- 详情抽屉标题：消费面用业务键，不裸露主键 ----------
  await setMode(page, 'consume');
  await openEntity(page, MAIN);
  // 操作列是 antd <Button type="link">（渲染为 button 而非 a），按 button 文案定位
  const detailLink = page.locator('.ant-table-tbody button:has-text("详情")').first();
  if (await detailLink.count()) {
    await detailLink.click();
    await page.waitForTimeout(2000);
    const title = await page.locator('.ant-drawer-title').first().innerText().catch(() => '');
    check('详情标题 · 用业务键而非代理键', title.includes(BUS_COL) && !title.includes('#'), title);
  } else {
    check('详情标题 · 用业务键而非代理键', false, '未找到「详情」入口');
  }

  // ---------- 第二实体（报销类别）同样收敛 ----------
  await openEntity(page, CATEGORY);
  body = await bodyOf(page);
  check('类别页 · 消费面无物理表信息', !body.includes('物理表'));

  // ---------- 偏好持久化：刷新不回退 ----------
  await setMode(page, 'implement');
  await openEntity(page, CATEGORY);
  body = await bodyOf(page);
  check('偏好持久化 · 刷新后仍在实施模式', body.includes('物理表'));

  await browser.close();
  report('metadata persona 双模式回归');
})();
