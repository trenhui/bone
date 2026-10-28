/**
 * 9 模块深度建数据探针（关闭通用填表覆盖缺口）。
 *
 * 与 ui-full-module-crud-probe.mjs 的关系：后者通用自动填表对 9 个模块失败（多为探针自身
 * 副作用或价值猜测失误）。本探针改用「显式字段白名单」范式（同 ui-create-data-probe.mjs），
 * 对每个模块写死正确字段取值，真正验证「点新建 → 填正确值 → 落库 → 可见」闭环。
 *
 * 覆盖 9 个此前未真正验证的模块：
 *   权限管理 / 租户管理 / 字段管理 / 质量规则 / 流程编排 / 订单管理 / 发货物流 / 业务数据 / 记录管理
 *
 * 判据：弹窗关闭 + 无失败类 toast +（列表变化 或 成功类 toast）。
 * 失败会打印 HTTP 非 2xx 留痕，便于区分「真产品 bug」vs「探针映射错」。
 */
import {
  UI_BASE as BASE,
  SHOT_DIR,
  stamp,
  USER as E2E_USER,
  PWD as E2E_PWD,
  loadPlaywright,
  makeChecker,
  tryLogin,
  api,
} from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const STAMP = stamp(6); // 保持本探针历史口径（slice(-6)），影响 E2E 数据命名

let REAL_CUSTOMER_ID = null;
let REAL_PRODUCT_ID = null;
let REAL_ORDER_ID = null;

const { results, check, report } = makeChecker();
const shot = (n) => page.screenshot({ path: `${SHOT_DIR}/deep_${n}.png` }).catch(() => {});
const slug = (s) => s.replace(/[^a-z0-9]/gi, '_');

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.setDefaultTimeout(20000);
const consoleErrors = [];
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));
let badResponses = [];
page.on('response', async (r) => {
  const u = r.url();
  if (!u.includes('/api/')) return;
  const st = r.status();
  if (st >= 400) {
    let body = '';
    try { body = (await r.text()).slice(0, 300); } catch {}
    badResponses.push({ url: u.replace(/^https?:\/\/[^/]+/, ''), status: st, body });
  }
});

const waitReady = async (ms = 20000) => {
  const deadline = Date.now() + ms;
  let prev = '';
  while (Date.now() < deadline) {
    const txt = await page.evaluate(() => {
      const e = document.querySelector('#subapp-viewport');
      return e ? (e.innerText || '').trim() : '';
    });
    if (txt.length > 20 && txt === prev) return txt;
    prev = txt;
    await page.waitForTimeout(700);
  }
  return prev;
};

// ---------- 填值辅助（显式，按 label） ----------
const closeDropdown = (modal) =>
  modal.locator('.ant-modal-title, .ant-drawer-title, .ant-modal-body').first().click({ timeout: 2000 }).catch(() => {});

const setInput = async (modal, label, value) => {
  const item = modal.locator('.ant-form-item', { hasText: label }).first();
  await item.locator('input:not([type=hidden])').first().fill(value);
};
const setInputByPlaceholder = async (modal, ph, value) => {
  await modal.locator(`input[placeholder*="${ph}"]`).first().fill(value);
};
/** 权限编码类 AutoComplete：键入唯一新编码，不要选已存在选项（会 409 冲突）。 */
const setCodeInput = async (modal, label, value) => {
  const item = modal.locator('.ant-form-item', { hasText: label }).first();
  const sel = item.locator('.ant-select').first();
  const inp = sel.locator('input').first();
  await inp.click({ timeout: 3000 }).catch(() => {});
  await inp.fill(value);
  await closeDropdown(modal);
};
const setTextarea = async (modal, label, value) => {
  const item = modal.locator('.ant-form-item', { hasText: label }).first();
  await item.locator('textarea').first().fill(value);
};
const setNumber = async (modal, label, value) => {
  const item = modal.locator('.ant-form-item', { hasText: label }).first();
  await item.locator('.ant-input-number input').first().fill(value);
};
const setPicker = async (modal, label) => {
  const item = modal.locator('.ant-form-item', { hasText: label }).first();
  await item.locator('.ant-picker').first().click({ timeout: 2000 }).catch(() => {});
  await page.waitForTimeout(300);
  await page.keyboard.press('Enter').catch(() => {});
  await closeDropdown(modal);
};
const selectFirst = async (modal, label) => {
  const item = modal.locator('.ant-form-item', { hasText: label }).first();
  const sel = item.locator('.ant-select').first();
  await sel.click({ timeout: 3000 }).catch(() => {});
  await page.waitForTimeout(400);
  const opt = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').first();
  if (await opt.count()) await opt.click({ timeout: 3000 }).catch(() => {});
  await closeDropdown(modal);
};
const fillAllNumberInputs = async (modal, value) => {
  for (const num of await modal.locator('.ant-input-number').all()) {
    if (await num.isVisible().catch(() => false)) await num.locator('input').first().fill(value).catch(() => {});
  }
};

/** 纠正后的通用填表：选实体/模型后填充可见必填项，绝不用 Esc（会冒泡关掉整个弹窗）。 */
const genericFill = async (modal) => {
  if (!(await modal.locator('.ant-form-item:visible').count())) {
    for (const t of await modal.locator('.ant-tabs-tab').all()) {
      if (await modal.locator('.ant-form-item:visible').count()) break;
      await t.click().catch(() => {});
      await page.waitForTimeout(400);
    }
  }
  const items = await modal.locator('.ant-form-item').all();
  for (const item of items) {
    if (!(await item.isVisible().catch(() => false))) continue;
    const label = (await item.locator('label').first().innerText().catch(() => '')).replace(/[:：*\s]/g, '');
    const sem = label.toLowerCase();
    const treeSel = item.locator('.ant-tree-select').first();
    const sel = item.locator('.ant-select').first();
    const num = item.locator('.ant-input-number').first();
    const picker = item.locator('.ant-picker').first();
    const ta = item.locator('textarea').first();
    const inp = item.locator('input:not([type=hidden])').first();
    try {
      if ((await treeSel.count()) > 0 && (await treeSel.isVisible().catch(() => false))) {
        await treeSel.click({ timeout: 3000 }); await page.waitForTimeout(400);
        const node = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-tree-node-content-wrapper, .ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-tree-node-content-wrapper').first();
        if (await node.count()) await node.click({ timeout: 3000 }).catch(() => {});
        else await closeDropdown(modal);
      } else if ((await sel.count()) > 0 && (await sel.isVisible().catch(() => false))) {
        await sel.click({ timeout: 3000 });
        const opt = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').first();
        // 选项可能异步加载（枚举/引用远程），等其出现再选
        const ok = await opt.waitFor({ timeout: 4000 }).then(() => true).catch(() => false);
        if (ok) await opt.click({ timeout: 3000 }).catch(() => {});
        await closeDropdown(modal);
      } else if ((await num.count()) > 0 && (await num.isVisible().catch(() => false))) {
        await num.locator('input').first().fill(/长度|上限|max|大小|容量/.test(sem) ? '255' : '1');
      } else if ((await picker.count()) > 0 && (await picker.isVisible().catch(() => false))) {
        await picker.click({ timeout: 2000 }).catch(() => {});
        await page.waitForTimeout(300);
        // 优先点「今天」单元格；退回 Enter 选中当前焦点日期
        const today = page.locator('.ant-picker-dropdown:not(.ant-picker-dropdown-hidden) .ant-picker-cell-today').first();
        if (await today.count()) await today.click({ timeout: 2000 }).catch(() => {});
        else await page.keyboard.press('Enter').catch(() => {});
        await closeDropdown(modal);
      } else if ((await ta.count()) > 0 && (await ta.isVisible().catch(() => false))) {
        await ta.fill(`E2E 自动建数据 ${STAMP}`);
      } else if ((await inp.count()) > 0) {
        const info = await inp.evaluate((el) => ({ type: el.getAttribute('type') || '', readonly: el.hasAttribute('readonly'), disabled: el.hasAttribute('disabled') || el.classList.contains('ant-input-disabled') })).catch(() => ({}));
        if (info.disabled || info.readonly) continue;
        let v;
        if (info.type === 'number') v = '1';
        else if (/\bid\b|编号|序号|雪花/.test(sem)) v = String(760000000000000000n + BigInt(Math.floor(Math.random() * 1e9)));
        else if (/编码|code|标识/.test(sem)) v = `e2e${STAMP}`;
        else if (/名称|name/.test(sem)) v = `E2E${STAMP}`;
        else if (/描述|remark|说明/.test(sem)) v = `E2E 自动建数据 ${STAMP}`;
        else v = `E2E${STAMP}`;
        await inp.fill(v);
      }
    } catch (e) {}
  }
};

const explicitFill = async (modal, fields) => {
  // 表单字段藏在 Tab 里时（如流程编排的「基本信息」），先翻 Tab 让表单项显露
  if (!(await modal.locator('.ant-form-item:visible').count())) {
    for (const t of await modal.locator('.ant-tabs-tab').all()) {
      if (await modal.locator('.ant-form-item:visible').count()) break;
      await t.click().catch(() => {});
      await page.waitForTimeout(400);
    }
  }
  for (const f of fields) {
    try {
      if (f.type === 'selectFirst') await selectFirst(modal, f.label);
      else if (f.type === 'codeInput') await setCodeInput(modal, f.label, f.value());
      else if (f.type === 'input') await setInput(modal, f.label, f.value());
      else if (f.type === 'inputByPlaceholder') await setInputByPlaceholder(modal, f.placeholder, f.value());
      else if (f.type === 'textarea') await setTextarea(modal, f.label, f.value());
      else if (f.type === 'number') await setNumber(modal, f.label, f.value());
      else if (f.type === 'picker') await setPicker(modal, f.label);
    } catch (e) { console.log(`    fill err ${f.label || f.placeholder}: ${String(e.message).slice(0, 60)}`); }
  }
};

/** 打开某模块的新建弹窗，返回 modal 句柄；无新建入口返回 null。 */
const openCreate = async (path, opts = {}) => {
  await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded' });
  const lb = await waitReady();
  if (!lb || lb.length <= 20) { check(`${opts.name}：列表页渲染`, false, '白屏'); return null; }
  if (opts.selectEntity) {
    const entSel = page.locator('#subapp-viewport .ant-select').first();
    if (await entSel.count()) {
      await entSel.click({ timeout: 3000 }).catch(() => {});
      await page.waitForTimeout(500);
      const opt = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').first();
      if (await opt.count()) { await opt.click({ timeout: 3000 }).catch(() => {}); await page.waitForTimeout(1500); }
    }
  }
  const btn = page.locator('#subapp-viewport button:visible').filter({ hasText: /^(新建|新增|创建|添加)/ }).first();
  if (!(await btn.count())) { console.log(`· ${opts.name}：无新建入口（只读页）`); return null; }
  await btn.click();
  await page.waitForTimeout(1500);
  const modal = page.locator('.ant-modal:visible, .ant-drawer:visible').first();
  if (!(await modal.count())) { check(`${opts.name}：点新建能打开表单`, false, '未出现弹窗'); await shot(slug(path) + '_nomodal'); return null; }
  await modal.locator('.ant-form-item, input, textarea').first().waitFor({ timeout: 8000 }).catch(() => {});
  return modal;
};

const submitAndVerify = async (opts) => {
  const { modal, modalHandle, name, listBefore } = opts;
  badResponses = [];
  const submit = modal.locator('button:visible').filter({ hasText: /^(确\s*定|确\s*认|保\s*存|提\s*交|创\s*建|新\s*增|OK|Save)/ }).first();
  if (!(await submit.count())) { check(`${name}：弹窗内有提交按钮`, false, '未找到确定/保存'); await page.keyboard.press('Escape').catch(() => {}); return; }
  const btnText = (await submit.innerText().catch(() => '')).replace(/\s/g, '');
  await submit.click();
  await page.waitForTimeout(2800);
  const toast = await page.locator('.ant-message, .ant-notification').innerText().catch(() => '');
  const stillOpen = (await modalHandle?.isVisible().catch(() => false)) ? 1 : 0;
  const listAfter = await page.locator('#subapp-viewport').innerText().catch(() => '');
  const changed = listAfter !== listBefore;
  const bad = /失败|错误|异常|error|500|400|已存在|重复|请先修正|不能为空|不存在|必填/i.test(toast);
  const ok = stillOpen === 0 && !bad && (changed || /成功|已创建|已保存|已添加|已新增/.test(toast));
  check(`${name}：新建提交闭环（按钮「${btnText}」）`, ok, `弹窗仍开=${stillOpen} toast=${toast.replace(/\s+/g, ' ').slice(0, 90)} 列表变化=${changed}`);
  if (!ok || stillOpen) {
    await shot(slug(name) + '_fail');
    for (const b of badResponses.slice(0, 4)) console.log(`    ↳ HTTP ${b.status} ${b.url} :: ${b.body.replace(/\s+/g, ' ')}`);
  }
  await page.keyboard.press('Escape').catch(() => {});
  await page.waitForTimeout(400);
};

// ---------- 模块配置（显式字段白名单） ----------
const MODULES = [
  {
    key: 'permissions', name: '权限管理', path: '/iam#/permissions',
    fields: [
      { type: 'input', label: '权限名称', value: () => `E2E权限${STAMP}` },
      { type: 'codeInput', label: '权限编码', value: () => `e2e:perm:${STAMP}` },
      { type: 'input', label: '资源类型', value: () => 'api' },
      { type: 'input', label: '操作类型', value: () => 'read' },
      { type: 'textarea', label: '描述', value: () => `E2E自动建数据${STAMP}` },
    ],
  },
  {
    key: 'tenants', name: '租户管理', path: '/iam#/tenants',
    fields: [
      { type: 'input', label: '租户名称', value: () => `E2E租户${STAMP}` },
      { type: 'input', label: '租户编码', value: () => `e2e${STAMP}` },
      { type: 'number', label: '租户等级', value: () => '1' },
      { type: 'input', label: '管理员邮箱', value: () => `e2e_${STAMP}@bone.local` },
    ],
  },
  {
    key: 'fields', name: '字段管理', path: '/masterdata#/fields',
    fields: [
      { type: 'selectFirst', label: '所属模型' },
      { type: 'input', label: '字段名称', value: () => `E2E字段${STAMP}` },
      { type: 'input', label: '字段编码', value: () => `e2e${STAMP}` },
      { type: 'selectFirst', label: '字段类型' },
      { type: 'number', label: '长度', value: () => '255' },
      { type: 'selectFirst', label: '是否必填' },
      { type: 'number', label: '显示顺序', value: () => '1' },
      { type: 'textarea', label: '描述', value: () => `E2E字段描述${STAMP}` },
    ],
  },
  {
    key: 'rules', name: '质量规则', path: '/masterdata#/rules',
    fields: [
      { type: 'selectFirst', label: '所属模型' },
      { type: 'input', label: '规则名称', value: () => `E2E规则${STAMP}` },
      { type: 'selectFirst', label: '规则类型' },
      { type: 'input', label: '规则表达式', value: () => 'field=id' },
      { type: 'selectFirst', label: '严重程度' },
      { type: 'textarea', label: '描述', value: () => `E2E规则描述${STAMP}` },
    ],
  },
  {
    key: 'flows', name: '流程编排', path: '/integration#/flows',
    fields: [
      { type: 'input', label: '流程名称', value: () => `E2E流程${STAMP}` },
      { type: 'textarea', label: '流程描述', value: () => `E2E流程描述${STAMP}` },
    ],
  },
  {
    key: 'orders', name: '订单管理', path: '/commerce#/orders',
    fields: [
      { type: 'input', label: '客户ID', value: () => REAL_CUSTOMER_ID || '1001' },
      { type: 'selectFirst', label: '来源渠道' },
      { type: 'inputByPlaceholder', placeholder: '商品ID', value: () => REAL_PRODUCT_ID || '1' },
      { type: 'inputByPlaceholder', placeholder: '商品名称', value: () => `E2E商品${STAMP}` },
      { type: 'fillNumbers', value: () => '1' },
    ],
  },
  {
    key: 'shipments', name: '发货物流', path: '/commerce#/shipments',
    fields: [
      { type: 'input', label: '订单ID', value: () => REAL_ORDER_ID || '1' },
      { type: 'selectFirst', label: '渠道' },
      { type: 'input', label: '收货人', value: () => `E2E收货${STAMP}` },
      { type: 'input', label: '收货电话', value: () => '13800000001' },
      { type: 'textarea', label: '收货地址', value: () => `E2E地址${STAMP}` },
    ],
  },
  { key: 'runtime', name: '业务数据', path: '/metadata#/runtime', entityDriven: true },
  { key: 'records', name: '记录管理', path: '/masterdata#/records', generic: true },
];

try {
  // 登录
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
  const logged = await page.waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 }).then(() => true).catch(() => false);
  check('UI 登录成功', logged, '登录表单未消失');
  if (!logged) throw new Error('登录失败');

  // 预取真实引用：
  //   · 订单管理建单需要「真实客户 + 真实商品」→ 取首个有 customerId 的订单及其明细里的商品；
  //   · 发货物流需要「可发货状态的订单」→ 取首个 PAID 订单（CREATED 会被 409 正确拒，这是产品做对了）。
  const token = await tryLogin();
  if (token) {
    const or = await api('GET', '/orders?page=1&size=50', undefined, { token }).catch(() => null);
    if (or && or.ok) {
      const oj = or.data;
      const recs = oj?.data?.records || [];
      const custOrder = recs.find((r) => r.customerId) || recs[0];
      if (custOrder?.customerId) REAL_CUSTOMER_ID = String(custOrder.customerId);
      const dr = await api('GET', `/orders/${custOrder.id}`, undefined, { token }).catch(() => null);
      if (dr && dr.ok) {
        const dj = dr.data;
        const items = dj?.data?.items || [];
        if (items.length && items[0].productId) REAL_PRODUCT_ID = String(items[0].productId);
      }
      const paid = recs.find((r) => r.status === 'PAID');
      if (paid) REAL_ORDER_ID = String(paid.id);
    }
  }
  console.log(`预取真实引用：customer=${REAL_CUSTOMER_ID} product=${REAL_PRODUCT_ID} order(PAID)=${REAL_ORDER_ID}`);

  for (const m of MODULES) {
    const errStart = consoleErrors.length;
    try {
      const opts = { name: m.name, selectEntity: !!m.entityDriven };
      const modal = await openCreate(m.path, opts);
      if (!modal) continue;
      const modalHandle = await modal.elementHandle().catch(() => null);
      const listBefore = await page.locator('#subapp-viewport').innerText().catch(() => '');

      if (m.entityDriven || m.generic) {
        await genericFill(modal);
      } else {
        await explicitFill(modal, m.fields);
        if (m.fields.some((f) => f.type === 'fillNumbers')) await fillAllNumberInputs(modal, '1');
      }
      await shot(slug(m.name) + '_form');
      if (!(await modalHandle?.isVisible().catch(() => false))) {
        check(`${m.name}：填表后表单仍然打开`, false, '填表过程把弹窗关掉了');
        continue;
      }
      await submitAndVerify({ modal, modalHandle, name: m.name, listBefore });
      const pageErrs = consoleErrors.slice(errStart);
      if (pageErrs.length) console.log(`    ↳ 本页 console ${pageErrs.length} 条：${pageErrs.join(' ~ ').replace(/\s+/g, ' ').slice(0, 200)}`);
    } catch (e) {
      check(`${m.name}：建数据流程未抛异常`, false, String(e.message).slice(0, 160));
    }
  }

  const fatal = consoleErrors.filter(
    (e) =>
      !/favicon|Failed to load resource|\[antd[^\]]*\]|deprecated|ResizeObserver|unique .*key.* prop|useForm` is not connected to any Form element|AxiosError: Request failed with status code/i.test(e)
  );
  check('全流程无致命 console 错误', fatal.length === 0, fatal.slice(0, 3).join(' | '));
} catch (e) {
  check('探针执行未抛异常', false, String(e.message).slice(0, 240));
} finally {
  const { pass } = report('9 模块深度建数据汇总');
  await browser.close();
  process.exit(pass === results.length ? 0 : 1);
}
