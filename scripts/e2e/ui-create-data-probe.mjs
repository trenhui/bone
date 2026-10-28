/**
 * 建数据 UI 探针（模拟人工填表）：shell :3000 登录后，进入指定模块的列表页，
 * 点「新建 / 新增」→ 在弹窗里逐项填写 → 提交 → 回列表按关键字检索，确认这条数据**真的落库且可见**。
 *
 * 与 ui-all-menu-regression.mjs 的分工：
 *   - 后者只验证「页面能渲染、按钮能点开」，不落数据；
 *   - 本探针验证「新增 → 落库 → 可检索」的完整闭环，是真正的前后端联调判据。
 *
 * 用法：node scripts/e2e/ui-create-data-probe.mjs
 * 依赖：playwright（经共享 client 的 loadPlaywright 加载，见 e2e_lib/client.cjs）
 */
import {
  UI_BASE as BASE,
  SHOT_DIR,
  stamp,
  USER as E2E_USER,
  PWD as E2E_PWD,
  loadPlaywright,
  makeChecker,
} from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const STAMP = stamp(7); // 保持本探针历史口径（slice(-7)），影响 E2E 数据命名

const { check, report } = makeChecker();

/**
 * 每个用例：菜单路径 + 触发新建的按钮文案 + 弹窗里每个字段的填法。
 * field: 用 label 文本定位（antd Form.Item 的 label），比 placeholder 稳定。
 * kind : text 直接填 / select 选第一个可用选项 / skip 不填（交给默认值）
 */
const CASES = [
  {
    module: 'IAM · 组织机构',
    path: '/iam#/organizations',
    createBtn: /^(新建|新增)/,
    // 实测表单字段：部门名称 / 上级部门(tree-select) / 排序号 / 状态
    fields: [
      { label: '部门名称', kind: 'text', value: `E2E组织${STAMP}` },
      { label: '排序号', kind: 'text', value: '1' },
      { label: '状态', kind: 'select' },
    ],
    searchable: `E2E组织${STAMP}`,
  },
  {
    module: 'IAM · 角色管理',
    path: '/iam#/roles',
    createBtn: /^(新建|新增)/,
    // 实测表单字段：角色名称 / 描述 / 权限（无「角色编码」字段）
    fields: [
      { label: '角色名称', kind: 'text', value: `E2E角色${STAMP}` },
      { label: '描述', kind: 'text', value: 'E2E 建数据探针创建' },
    ],
    searchable: `E2E角色${STAMP}`,
  },
  {
    module: '主数据 · 分类管理',
    path: '/masterdata#/categories',
    createBtn: /^(新建|新增)/,
    fields: [
      { label: '分类名称', kind: 'text', value: `E2E分类${STAMP}` },
      { label: '分类编码', kind: 'text', value: `e2e_cat_${STAMP}` },
    ],
    searchable: `E2E分类${STAMP}`,
  },
  {
    module: '主数据 · 模板管理',
    path: '/masterdata#/templates',
    createBtn: /^(新建|新增)/,
    // 实测表单字段：域编码 / 域名称 / 描述 / 默认治理等级 / 默认字段集（JSON 数组）
    // 「默认字段集」要求合法 JSON 数组，交给页面默认值，探针不覆盖（填错会污染用例）。
    fields: [
      { label: '域编码', kind: 'text', value: `e2e_tpl_${STAMP}` },
      { label: '域名称', kind: 'text', value: `E2E模板${STAMP}` },
      { label: '默认治理等级', kind: 'select' },
    ],
    searchable: `E2E模板${STAMP}`,
  },
  {
    module: '集成 · 连接器管理',
    path: '/integration#/connectors',
    createBtn: /^(新建|新增)/,
    fields: [
      { label: '名称', kind: 'text', value: `E2E连接器${STAMP}` },
      { label: '类型', kind: 'select' },
    ],
    searchable: `E2E连接器${STAMP}`,
  },
];

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(20000);
const consoleErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200));
});
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

const shot = (n) => page.screenshot({ path: `${SHOT_DIR}/ui_create_${n}.png`, fullPage: false }).catch(() => {});

/** 等待 qiankun 容器渲染出内容 */
const waitReady = async (ms = 15000) => {
  const deadline = Date.now() + ms;
  while (Date.now() < deadline) {
    const len = await page.evaluate(() => {
      const e = document.querySelector('#subapp-viewport');
      return e ? (e.innerText || '').trim().length : -1;
    });
    if (len > 20) return true;
    await page.waitForTimeout(600);
  }
  return false;
};

/** 按 label 文本定位表单控件所在 Form.Item，再取其内部输入控件 */
const controlInItem = (root, label) =>
  root.locator('.ant-form-item').filter({ hasText: label }).first().locator('input, textarea, .ant-select').first();

try {
  // ---------- 登录 ----------
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"]', { timeout: 20000 });
  const userSel = 'input[placeholder*="用户名"], input[name="username"], input#username';
  if (await page.locator(userSel).first().count()) await page.locator(userSel).first().fill(E2E_USER);
  const pwd = page.locator('input[type="password"]').first();
  await pwd.fill(E2E_PWD);
  await pwd.press('Enter');
  await page.waitForTimeout(1500);
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('button').filter({ hasText: /登\s*录/ }).first().click().catch(() => {});
    await page.waitForTimeout(3000);
  }
  let ok = false;
  try {
    await page.waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 });
    ok = true;
  } catch {}
  check('UI 登录成功', ok, '登录表单未消失');
  if (!ok) throw new Error('登录失败');

  for (const c of CASES) {
    console.log(`\n———— ${c.module} ————`);
    try {
      await page.goto(`${BASE}${c.path}`, { waitUntil: 'domcontentloaded' });
      const ready = await waitReady();
      if (!ready) {
        check(`${c.module}：列表页渲染`, false, 'qiankun 容器未渲染');
        continue;
      }

      // 1) 打开新建弹窗
      const btn = page.locator('button:visible').filter({ hasText: c.createBtn }).first();
      if (!(await btn.count())) {
        check(`${c.module}：存在可见「新建」按钮`, false, '未找到新建按钮');
        continue;
      }
      await btn.click();
      await page.waitForTimeout(1800);
      const modal = page.locator('.ant-modal:visible, .ant-drawer:visible').first();
      const modalOpen = (await modal.count()) > 0;
      check(`${c.module}：点「新建」打开表单弹窗`, modalOpen, '未出现弹窗/抽屉');
      if (!modalOpen) {
        await shot(c.path.replace(/[^a-z0-9]/gi, '_') + '_nomodal');
        continue;
      }

      // 2) 逐字段填写（label 定位，失败则该字段报出来，不静默跳过）
      let filled = 0;
      const missing = [];
      for (const f of c.fields) {
        const ctrl = controlInItem(modal, f.label);
        if (!(await ctrl.count())) {
          missing.push(f.label);
          continue;
        }
        try {
          const tag = await ctrl.evaluate((e) => e.tagName.toLowerCase() + (e.className || ''));
          if (tag.includes('ant-select')) {
            await ctrl.click({ timeout: 4000 });
            await page.waitForTimeout(600);
            const opt = page
              .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
              .first();
            if (await opt.count()) {
              await opt.click({ timeout: 4000 });
              filled++;
            }
          } else {
            await ctrl.fill(f.value);
            filled++;
          }
        } catch (e) {
          missing.push(`${f.label}(填失败:${e.message.slice(0, 40)})`);
        }
      }
      check(
        `${c.module}：表单字段可定位并填写（${filled}/${c.fields.length}）`,
        missing.length === 0,
        '未填：' + missing.join('、')
      );
      await shot(c.path.replace(/[^a-z0-9]/gi, '_') + '_form');

      // 3) 提交
      // antd 中文按钮会在两字间插空格（「保 存」「确 定」），正则必须容忍
      const submit = modal
        .locator('button:visible')
        .filter({ hasText: /^(确\s*定|确\s*认|保\s*存|提\s*交|创\s*建|新\s*增|OK|Save)/ })
        .first();
      if (!(await submit.count())) {
        check(`${c.module}：弹窗内有提交按钮`, false, '未找到确定/保存按钮');
        await page.keyboard.press('Escape');
        continue;
      }
      await submit.click();
      await page.waitForTimeout(2500);

      // 4) 判据：弹窗关闭 且 （成功 toast 或 列表出现该条）
      const closed =
        (await page.locator('.ant-modal:visible, .ant-drawer:visible').count()) === 0 ||
        (await page.locator('.ant-modal:visible').first().isVisible().catch(() => false)) === false;
      const toastTxt = await page.locator('.ant-message').innerText().catch(() => '');
      await page.waitForTimeout(1200);
      const listTxt = await page.locator('#subapp-viewport').innerText().catch(() => '');
      const visible = listTxt.includes(c.searchable);
      check(
        `${c.module}：提交后新增数据落库且列表可见`,
        visible || (/成功|已创建|新增成功/.test(toastTxt) && closed),
        `toast=${toastTxt.slice(0, 60)} 列表命中=${visible}`
      );
      await shot(c.path.replace(/[^a-z0-9]/gi, '_') + '_after');
      // 关掉可能残留的弹窗
      if (await page.locator('.ant-modal:visible').count()) await page.keyboard.press('Escape').catch(() => {});
    } catch (e) {
      check(`${c.module}：建数据流程未抛异常`, false, e.message.slice(0, 160));
    }
  }

  const fatal = consoleErrors.filter((e) => !/favicon|Failed to load resource:|\[antd[^\]]*\]|deprecated/i.test(e));
  check('建数据全流程无致命 console 错误', fatal.length === 0, fatal.slice(0, 3).join(' | '));
} catch (e) {
  check('建数据探针执行未抛异常', false, e.message.slice(0, 240));
} finally {
  const { ok } = report('建数据 UI 探针汇总');
  await browser.close();
  process.exit(ok ? 0 : 1);
}
