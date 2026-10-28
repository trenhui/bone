// BONE UI 巡检 v4：修正 iam 两级菜单（先展开 SubMenu 再点子项）
// v3 报"iam 8 个菜单点击失效、hash 停在 #/accounts"，根因是探针没展开 SubMenu ——
// "角色管理"等在折叠的"权限与角色"里，不在 DOM（候选=0），不是产品缺陷。
/* 共享 client：playwright 加载 / CHROME / UI BASE / 登录凭证（见 e2e_lib/client.cjs） */
import { mkdirSync } from 'node:fs';
import { loadPlaywright, CHROME, UI_BASE as BASE, USER, PWD } from '../e2e_lib/client.cjs';
const { chromium } = loadPlaywright();
const OUT = '/tmp/bone-ui-screens';
mkdirSync(OUT, { recursive: true });

// 父级 → 子级（iam 是两级；其余应用是平铺的）
// 真源：GET /api/v1/iam/menus/tree（2026-10-13 实测）。菜单结构随三入口建模 UX 与
// 多渠道交易域上线已变：metadata 由「建模工作台/模型管理/关系管理/运行时数据」收敛为
// 「模型设计/业务对象/业务数据」；generator 新增「代码生成」、模板管理更名「代码生成模板」；
// commerce 由 2 项扩到 8 项（渠道/上架/库存/买家/广播/发货此前从未被巡检过）。
const APP_MENUS = {
  iam: [
    ['组织与成员', ['组织机构', '用户管理']],
    ['权限与角色', ['角色管理', '权限管理', '菜单管理', '应用管理']],
    ['安全与审计', ['租户管理', '审计日志', '审计设置']],
  ],
  metadata: [['业务建模', ['模型设计', '业务对象', '业务数据']]],
  masterdata: [['主数据管理', ['域工作台', '主数据模型', '字段管理', '分类管理', '模板管理', '记录管理', '参考数据', '质量规则', '质量结果', '质量问题', '治理看板']]],
  integration: [['集成管理', ['连接器管理', '流程编排', '运行监控']]],
  system: [['系统管理', ['系统配置', '监控告警', '日志管理', 'K8s 部署', '字典管理', '定时任务']]],
  extension: [['扩展管理', ['扩展点目录', '插件仓库', '部署管理', '依赖图谱', '低代码市场', '运行日志']]],
  generator: [['代码生成', ['数据源管理', '代码生成', '代码生成模板', '生成历史']]],
  commerce: [['交易管理', ['订单管理', '支付管理', '渠道管理', '商品上架', '库存管理', '买家映射', '广播任务', '发货物流']]],
};

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 950 } })).newPage();
  const errors = [], failedReq = [];
  let cur = '';
  page.on('pageerror', e => errors.push({ route: cur, kind: 'pageerror', text: String(e.message).slice(0, 250) }));
  page.on('console', m => { if (m.type() === 'error') errors.push({ route: cur, kind: 'console', text: m.text().slice(0, 250) }); });
  page.on('response', r => { if (r.status() >= 400) failedReq.push({ route: cur, url: r.url().replace(BASE, ''), status: r.status() }); });

  await page.goto(`${BASE}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);
  const u = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if (await u.count() && await u.isVisible().catch(() => false)) {
    await u.fill(USER);
    await page.locator('input[type="password"], input[placeholder="密码"]').first().fill(PWD);
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await page.waitForTimeout(4000);
  }

  const results = [];
  for (const [app, groups] of Object.entries(APP_MENUS)) {
    await page.goto(`${BASE}/${app}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3000);
    for (const [parent, children] of groups) {
      // 展开父级 SubMenu（幂等：已展开时点击会折叠，故先判断 aria-expanded）
      const sub = page.locator('.ant-menu-submenu').filter({ hasText: new RegExp(`^${parent}`) }).first();
      if (await sub.count()) {
        const expanded = await sub.locator('.ant-menu-submenu-title').first().getAttribute('aria-expanded');
        if (expanded !== 'true') { try { await sub.locator('.ant-menu-submenu-title').first().click({ timeout: 4000 }); await page.waitForTimeout(1200); } catch (e) {} }
      }
      for (const child of children) {
        cur = `${app}/${child}`;
        const e0 = errors.length, f0 = failedReq.length;
        const before = page.url().split('#')[1] || '';
        const loc = page.locator('.ant-menu-item').filter({ hasText: new RegExp(`^${child}$`) }).first();
        const found = await loc.count() > 0;
        let err = '';
        let clicked = false;
        if (found) {
          // v5：单击超时不再直接判「点不动」。实测 metadata「运行时数据」首次点击就超时，
          // 但等 Vite 编译完再点同一元素即可跳成 #/runtime（页面本身好好的，是探针太急）。
          // 注意退出条件必须用独立的 clicked 标记：早先写成 `&& err === ''`，
          // 第 1 次失败后 err 已被赋值 ⇒ 循环当次就退出，重试压根没发生（探针假红）。
          let clicked = false;
          for (let attempt = 1; attempt <= 3 && !clicked; attempt++) {
            try {
              await loc.click({ timeout: 4000 + attempt * 4000 });
              clicked = true;
            } catch (e) {
              await page.waitForTimeout(1500);
            }
          }
        }
        await page.waitForTimeout(2800);
        // 判据取「点了成功 或 hash 已经变了」两者之一，不要只看 click() 是否报错：
        // Vite 首屏编译时 playwright 常报「元素 detached」，但导航其实已经发生，
        // 照 click 报错判 FAIL 会把正常页面记成探针自噪（metadata 运行时数据 已踩）。
        const nowHash = await page.evaluate(() => location.hash || '');
        if (!clicked && nowHash === before) err = 'click-timeout';
        const m = await page.evaluate(() => {
          const q = s => document.querySelectorAll(s).length;
          const rows = [...document.querySelectorAll('tbody tr.ant-table-row')];
          return {
            hash: location.hash,
            tableRows: q('tbody tr.ant-table-row'), cards: q('.ant-card'), empty: q('.ant-empty'),
            pagination: q('.ant-pagination'), inputs: q('input.ant-input, .ant-select-selector, textarea'),
            rowSample: rows.slice(0, 2).map(r => r.innerText.replace(/\s+/g, ' ').trim().slice(0, 55)),
          };
        });
        results.push({
          app, menu: child, found, err, before, ...m,
          navigated: before !== m.hash,
          newErrors: errors.length - e0, newFailed: failedReq.slice(f0),
          verdict: m.tableRows > 0 ? 'DATA' : m.empty > 0 ? 'EMPTY' : (m.cards > 0 ? 'CARDS' : (m.inputs > 0 ? 'FORM' : 'THIN')),
        });
        await page.screenshot({ path: `${OUT}/${app}-${child}.png` });
      }
    }
  }
  await browser.close();
  console.log(JSON.stringify({ results, errors, failedReq }, null, 1));
})();
