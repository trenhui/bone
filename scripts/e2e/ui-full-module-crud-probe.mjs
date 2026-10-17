/**
 * 全模块「新建 → 落库 → 可见」通用联调探针（模拟人工填表）。
 *
 * 与 scripts/e2e/ui-create-data-probe.mjs 的分工：
 *   - 后者是**手写字段白名单**（5 个高价值模块，字段 label 写死，回归稳定）；
 *   - 本探针是**通用自动填表**：从 IAM 菜单树取运行时真源菜单，逐个进列表页，
 *     找到「新建/新增/创建」按钮 → 自动识别弹窗内所有控件并填值 → 提交 →
 *     校验「弹窗关闭 + 无服务端报错 + 列表行数或文本发生变化」。
 *     目的是把「每个模块能不能真的建出一条数据」这件事做成全量机械判据，
 *     而不是只验证页面能渲染。
 *
 * 判据口径（三项全中才算通过）：
 *   1. 弹窗在提交后关闭（或出现成功类 toast）；
 *   2. 提交过程中无 5xx / 无「业务异常」toast；
 *   3. 列表区文本发生变化（说明真的刷出了新数据，而非静默失败）。
 *
 * 用法：node scripts/e2e/ui-full-module-crud-probe.mjs
 * 依赖：playwright（绝对路径导入托管 workspace 里的包）
 */
import pw from '/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const BASE = process.env.BONE_UI_BASE || 'http://localhost:3000';
const API = process.env.BONE_E2E_API || 'http://127.0.0.1:8888/api/v1';
const SHOT_DIR = '/Users/renhui.trh/wps/bone/logs';
const STAMP = String(Date.now()).slice(-6);
const E2E_USER = process.env.BONE_E2E_USER || 'admin';
const E2E_PWD = process.env.BONE_E2E_PWD || '123456';

// 只跑这些模块（空数组 = 全跑）。用 BONE_E2E_ONLY=/iam#/accounts,/system#/dict 局部复跑。
const ONLY = (process.env.BONE_E2E_ONLY || '')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);

const results = [];
const check = (name, cond, detail = '') => {
  results.push({ name, ok: !!cond, detail });
  console.log(`${cond ? '✓' : '✗'} ${name}${cond ? '' : '  ← ' + detail}`);
};

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.setDefaultTimeout(20000);
const consoleErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200));
});
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 200)));

// 提交类请求的非 2xx 响应留痕：失败时才知道是哪个端点、服务端说了什么，
// 否则失败文案只有前端 toast 的「操作失败」，排查无从下手。
let badResponses = [];
page.on('response', async (r) => {
  const u = r.url();
  if (!u.includes('/api/')) return;
  const st = r.status();
  if (st >= 400) {
    let body = '';
    try {
      body = (await r.text()).slice(0, 300);
    } catch {}
    badResponses.push({ url: u.replace(/^https?:\/\/[^/]+/, ''), status: st, body });
  }
});

const shot = (n) => page.screenshot({ path: `${SHOT_DIR}/crud_${n}.png` }).catch(() => {});
const slug = (s) => s.replace(/[^a-z0-9]/gi, '_');

/** 等待 qiankun 容器渲染出内容并稳定（连续两次采样一致，防抢跑点到上一页残留 DOM） */
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

/**
 * 自动填表：遍历弹窗内的可见 Form.Item，按控件类型填值。
 * 覆盖 antd 全部常见控件：TreeSelect / Select(单/多) / InputNumber / DatePicker / 文本域(结构化) / Input。
 * 语义化取值：
 *   · ID 类 → 数字串（雪花 Long，填字母会被 Jackson 拒）
 *   · 编码/标识/名称/角色/用户名 → 带 STAMP 的唯一值（避免跨轮次 409 冲突假红）
 *   · cron/调度/表达式 → 合法 cron（"0 0 12 * * ?"）；规则表达式 → k=v
 *   · JSON/data/schema 文本域 → 最小合法对象 {"key":"value"}
 *   · 邮箱 → 合法邮箱；手机 → 合法手机号；密码 → 强密码
 *   · 数字框 → "1"；日期 → 今天（不填文本避免破坏 DatePicker）
 */
const autoFill = async (modal) => {
  const filled = [];
  const skipped = [];
  const items = await modal.locator('.ant-form-item').all();
  for (const item of items) {
    try {
      if (!(await item.isVisible().catch(() => false))) continue;
      const label = (await item.locator('label').first().innerText().catch(() => '')).replace(/[:：*\s]/g, '');
      const ph = (await item.locator('input,textarea').first().getAttribute('placeholder').catch(() => '')) || '';
      const sem = (label + ' ' + ph).toLowerCase();
      const closeDropdown = async () =>
        modal.locator('.ant-modal-title, .ant-drawer-title, .ant-modal-body').first().click({ timeout: 2000 }).catch(() => {});

      // 0) 跳过表单已预设的合法默认值（如数据源 port=3306 / host=localhost / 字典 category=LIST），
      //    不要覆盖成脏值，否则会把能成功的创建打成 400（如把端口填成 "E2E312314" 导致 Integer 解析失败）。
      const presetVal = await item.locator('input:not([type=hidden])').first().inputValue().catch(() => '');
      const presetSel = await item.locator('.ant-select-selection-item').first().count().catch(() => 0);
      if ((presetVal && presetVal.trim()) || presetSel > 0) {
        skipped.push(label || '(预设值)');
        continue;
      }

      // 1) TreeSelect（部门/机构树）——class 含 .ant-tree-select，下拉里是树节点而非 .ant-select-item-option
      const treeSel = item.locator('.ant-tree-select').first();
      const isTree =
        (await treeSel.count()) > 0 && (await treeSel.isVisible().catch(() => false));
      if (isTree) {
        await treeSel.click({ timeout: 3000 });
        await page.waitForTimeout(400);
        const node = page
          .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-tree-node-content-wrapper, .ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-tree-node-content-wrapper')
          .first();
        if (await node.count()) {
          await node.click({ timeout: 3000 });
          filled.push(label || 'tree');
        } else {
          await closeDropdown();
          skipped.push(label || 'tree(无选项)');
        }
        continue;
      }

      // 2) 普通 Select（含多选 mode=multiple）
      const sel = item.locator('.ant-select').first();
      const isSelect = (await sel.count()) > 0 && (await sel.isVisible().catch(() => false));
      if (isSelect) {
        // 编码/标识类字段常为 AutoComplete（平台目录），点开后会命中已存在的编码 → 409 假红；
        // 改为直接键入唯一编码，不点下拉选项。
        if (/编码|code|标识/.test(sem)) {
          const inp = sel.locator('input').first();
          await inp.click({ timeout: 3000 }).catch(() => {});
          await inp.fill(`e2e${STAMP}`);
          await page.keyboard.press('Enter').catch(() => {});
          await page.keyboard.press('Escape').catch(() => {});
          filled.push(label || 'code');
        } else {
          await sel.click({ timeout: 3000 });
          await page.waitForTimeout(400);
          const opt = page
            .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
            .first();
          if (await opt.count()) {
            await opt.click({ timeout: 3000 });
            filled.push(label || 'select');
          } else {
            await closeDropdown();
            skipped.push(label || 'select(无选项)');
          }
        }
        continue;
      }

      // 3) InputNumber —— 必须填数字，否则受控值非数字会污染必填校验
      const numWrap = item.locator('.ant-input-number').first();
      const isNum = (await numWrap.count()) > 0 && (await numWrap.isVisible().catch(() => false));
      if (isNum) {
        await numWrap.locator('input').first().fill('1');
        filled.push(label || 'number');
        continue;
      }

      // 4) DatePicker / TimePicker —— 点开选今天，不填文本
      const picker = item.locator('.ant-picker').first();
      const isPicker = (await picker.count()) > 0 && (await picker.isVisible().catch(() => false));
      if (isPicker) {
        await picker.click({ timeout: 2000 }).catch(() => {});
        await page.waitForTimeout(400);
        await page.keyboard.press('Enter').catch(() => {});
        await page.keyboard.press('Escape').catch(() => {});
        skipped.push(label || 'datepicker');
        continue;
      }

      // 5) 文本域：结构化字段给最小合法值，自由文本给描述
      const ta = item.locator('textarea').first();
      if ((await ta.count()) > 0 && (await ta.isVisible().catch(() => false))) {
        // 字段 schema（field_schema / 字段结构 / 字段集）必须填 JSON 数组，而非对象，否则后端校验为非法。
        if (/field[\s_-]?schema|字段结构|字段集|字段定义|schema/i.test(sem)) {
          await ta.fill('[{"key":"value"}]');
        } else if (/表达式|规则/i.test(sem)) {
          await ta.fill('status=active');
        } else if (/json|data|映射|配置|条件|结构体/i.test(sem)) {
          await ta.fill('{"key":"value"}');
        } else {
          await ta.fill(`E2E 自动建数据 ${STAMP}`);
        }
        filled.push(label || 'textarea');
        continue;
      }

      // 6) 输入类（Input）：按语义生成值
      const ctrl = item.locator('input:not([type=hidden])').first();
      if (!(await ctrl.count())) {
        skipped.push(label || '(匿名)');
        continue;
      }
      const info = await ctrl.evaluate((el) => ({
        type: el.getAttribute('type') || '',
        placeholder: el.getAttribute('placeholder') || '',
        readonly: el.hasAttribute('readonly'),
        disabled: el.hasAttribute('disabled') || el.classList.contains('ant-input-disabled'),
      }));
      if (info.disabled || info.readonly) {
        skipped.push(label || '(只读)');
        continue;
      }
      let v;
      const labelLower = label.toLowerCase();
      // 数字框（极少数 <input type=number>）填 "1"，与 InputNumber 分支保持一致
      if (info.type === 'number') v = '1';
      // 字典「编码分段」看起来像编码，但语义是逗号分隔的长度（如 2,2,2），必须单独处理
      else if (/分段|segment|codesegment/i.test(sem)) v = '2,2,2';
      else if (/密码/.test(sem)) v = 'E2e@12345';
      else if (/邮箱|mail/i.test(sem)) v = `e2e_${STAMP}@bone.local`;
      else if (/手机|电话|phone|mobile/i.test(sem)) v = '13800000001';
      else if (/端口|port/i.test(sem)) v = '3306';
      // ID 类字段是雪花 ID（Long），必须填数字字符串。判定只认 label 里的独立「id/编号/序号/雪花」，
      // 用 \bid\b 词边界，避免误伤「租户名称/租户编码」等命名字段（中文「单」与拉丁 i 之间恰是词边界）。
      else if (/\bid\b|编号|序号|雪花/.test(labelLower)) {
        v = String(760000000000000000n + BigInt(Math.floor(Math.random() * 1e9)));
      } else if (/编码|code|标识/i.test(sem)) v = `e2e${STAMP}`;
      // cron/调度/定时 → 合法 cron；表达式/规则 → k=v 片段（status=active），二者要分开，
      // 且表达式必须排在线编码分支之前（质量规则「规则表达式」的 placeholder 含「字段编码」字样，
      // 若编码分支先命中会把表达式填成 e2e，后端报「片段不是 k=v 形式」）。
      else if (/cron|调度|定时/.test(sem)) v = '0 0 12 * * ?';
      else if (/表达式|规则|condition|rule/i.test(sem)) v = 'status=active';
      else if (/名称|name|角色|username/.test(sem)) v = `E2E${STAMP}`;
      else if (/描述|remark|note|memo|说明/.test(sem)) v = `E2E 自动建数据 ${STAMP}`;
      else v = `E2E${STAMP}`;
      await ctrl.fill(v);
      filled.push(label || 'text');
    } catch (e) {
      skipped.push('err:' + String(e.message).slice(0, 30));
    }
  }
  return { filled, skipped };
};

try {
  // ---------- 登录取菜单真源 ----------
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
  const logged = await page
    .waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 })
    .then(() => true)
    .catch(() => false);
  check('UI 登录成功', logged, '登录表单未消失');
  if (!logged) throw new Error('登录失败，后续用例无意义');

  // 菜单树：走独立 API 会话拿运行时真源（页面内 fetch 不一定带上网关要求的所有头）
  let menus = [];
  try {
    const lr = await fetch(`${API}/iam/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: E2E_USER, password: E2E_PWD }),
    });
    const lj = await lr.json();
    const token = lj?.data?.token;
    const mr = await fetch(`${API}/iam/menus/tree`, {
      headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '0' },
    });
    const mj = await mr.json();
    const out = [];
    const walk = (ns, parent) => {
      for (const n of ns || []) {
        if (n.path && n.path.includes('#')) out.push({ name: `${parent ? parent + ' · ' : ''}${n.name}`, path: n.path });
        walk(n.children || [], n.name);
      }
    };
    walk(mj.data || [], '');
    menus = out;
  } catch (e) {
    console.log('菜单树获取失败：' + String(e.message).slice(0, 120));
  }
  check('运行时菜单真源可获取（>0）', menus.length > 0, `实际 ${menus.length}`);

  const targets = ONLY.length ? menus.filter((m) => ONLY.includes(m.path)) : menus;
  console.log(`\n菜单真源：共 ${menus.length} 个可导航菜单，本次建数据巡检 ${targets.length} 个\n`);

  let attempted = 0;
  for (const m of targets) {
    const before = await waitReady().catch(() => '');
    try {
      await page.goto(`${BASE}${m.path}`, { waitUntil: 'domcontentloaded' });
      const listBefore = await waitReady();
      if (!listBefore || listBefore.length <= 20) {
        check(`${m.name}：列表页渲染`, false, 'qiankun 容器未渲染（白屏）');
        continue;
      }

      // 业务数据 / runtime 跨实体运营台：「新建记录」按钮在选实体前 disabled。
      // 探针必须先选一个已发布实体，按钮才可用；否则会卡在「等新建按钮」超时（假红）。
      if (/业务数据|runtime/i.test(m.name)) {
        const entSel = page.locator('#subapp-viewport .ant-select').first();
        if (await entSel.count()) {
          await entSel.click({ timeout: 3000 }).catch(() => {});
          await page.waitForTimeout(500);
          const opt = page
            .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
            .first();
          if (await opt.count()) {
            await opt.click({ timeout: 3000 }).catch(() => {});
            await page.waitForTimeout(1500);
          } else {
            await page.locator('.ant-modal-title, .ant-drawer-title, .ant-modal-body').first().click({ timeout: 2000 }).catch(() => {});
          }
        }
      }

      const btn = page.locator('#subapp-viewport button:visible').filter({ hasText: /^(新建|新增|创建|添加)/ }).first();
      if (!(await btn.count())) {
        // 无新建入口的页面（看板/监控/日志类）不算缺陷，登记为 skip
        console.log(`· ${m.name}：无「新建」入口（只读页），跳过建数据`);
        continue;
      }
      attempted++;
      badResponses = [];
      await btn.click();
      await page.waitForTimeout(1500);
      const modal = page.locator('.ant-modal:visible, .ant-drawer:visible').first();
      if (!(await modal.count())) {
        check(`${m.name}：点「新建」能打开表单`, false, '未出现弹窗/抽屉');
        await shot(slug(m.path) + '_nomodal');
        continue;
      }

      const { filled, skipped } = await autoFill(modal);
      await shot(slug(m.path) + '_form');

      if (!(await page.locator('.ant-modal:visible, .ant-drawer:visible').count())) {
        check(`${m.name}：填表后表单仍然打开`, false, '填表过程把弹窗关掉了（探针自身副作用）');
        continue;
      }

      const submit = modal
        .locator('button:visible')
        .filter({ hasText: /^(确\s*定|确\s*认|保\s*存|提\s*交|创\s*建|新\s*增|OK|Save)/ })
        .first();
      if (!(await submit.count())) {
        check(`${m.name}：弹窗内有提交按钮`, false, '未找到确定/保存按钮');
        await page.keyboard.press('Escape').catch(() => {});
        continue;
      }
      const btnText = (await submit.innerText().catch(() => '')).replace(/\s/g, '');
      await submit.click();
      await page.waitForTimeout(2600);

      const toast = await page.locator('.ant-message, .ant-notification').innerText().catch(() => '');
      const stillOpen = await page.locator('.ant-modal:visible, .ant-drawer:visible').count();
      const listAfter = await page.locator('#subapp-viewport').innerText().catch(() => '');
      const changed = listAfter !== listBefore;
      const bad = /失败|错误|异常|error|500|400|已存在|重复|请先修正/i.test(toast);
      // 判据：弹窗关闭 + 无失败类 toast +（列表真的刷新出变化 或 出现成功类 toast）。
      // 之所以接受「成功 toast」作为第二判据：列表多为分页，新建的记录可能落在第 N 页，
      // 首屏文本不变化不代表没落库——但「弹窗关闭 + 成功 toast」已经足以证明提交成功。
      const okSubmit = stillOpen === 0 && !bad && (changed || /成功|已创建|已保存|已添加|已新增/.test(toast));
      check(
        `${m.name}：新建提交闭环（填${filled.length}项/跳${skipped.length}项，按钮「${btnText}」）`,
        okSubmit,
        `弹窗仍开=${stillOpen} toast=${toast.replace(/\s+/g, ' ').slice(0, 90)} 列表变化=${changed}`
      );
      if (stillOpen || bad) {
        await shot(slug(m.path) + '_fail');
        for (const b of badResponses.slice(0, 3)) {
          console.log(`    ↳ HTTP ${b.status} ${b.url} :: ${b.body.replace(/\s+/g, ' ')}`);
        }
        await page.keyboard.press('Escape').catch(() => {});
      }
    } catch (e) {
      check(`${m.name}：建数据流程未抛异常`, false, String(e.message).slice(0, 160));
    }
  }
  console.log(`\n实际触发新建闭环：${attempted} 个模块`);

  // 已知豁免（逐条注明理由，不搞大兜底）：
  //   · `useForm is not connected to any Form element`：antd dev-only 警告——组件顶层
  //     Form.useForm() 先于抽屉/弹窗内的 <Form> 挂载，首帧必然触发。不影响功能，prod build 不打印。
  //     根治要改 ~30 个页面（把 useForm 移到 Modal 内或始终渲染 Form），单列 P2，不在本探针拦截。
  //   · AxiosError 堆栈：是「提交被服务端拒绝」的**结果**而非独立故障，
  //     失败原因已由上面的逐模块断言与 HTTP 留痕覆盖，否则同一问题计两次。
  const fatal = consoleErrors.filter(
    (e) =>
      !/favicon|Failed to load resource|\[antd[^\]]*\]|deprecated|ResizeObserver/i.test(e) &&
      !/useForm` is not connected to any Form element/.test(e) &&
      !/AxiosError: Request failed with status code/.test(e)
  );
  check('全流程无致命 console 错误', fatal.length === 0, fatal.slice(0, 3).join(' | '));
} catch (e) {
  check('探针执行未抛异常', false, String(e.message).slice(0, 240));
} finally {
  const pass = results.filter((r) => r.ok).length;
  console.log(`\n全模块建数据巡检汇总：通过 ${pass} / ${results.length}`);
  await browser.close();
  process.exit(pass === results.length ? 0 : 1);
}
