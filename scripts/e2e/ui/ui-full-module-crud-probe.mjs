/**
 * 全模块「新建 → 落库 → 可见」通用联调探针（模拟人工填表）。
 *
 * 与 scripts/e2e/ui/create-data-probe.mjs 的分工：
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
 * 用法：node scripts/e2e/ui/full-module-crud-probe.mjs
 * 依赖：playwright（经共享 client 的 loadPlaywright 加载，见 e2e_lib/client.cjs）
 */
/* 共享 client：playwright 加载 / SHOT_DIR / API 登录与带 token fetch / 断言收集（见 e2e_lib/client.cjs） */
import {
  UI_BASE as BASE,
  SHOT_DIR,
  stamp,
  loadPlaywright,
  api,
  tryLogin,
  makeChecker,
} from '../e2e_lib/client.cjs';
import { uiLogin } from '../e2e_lib/ui.cjs';
const { chromium } = loadPlaywright();

const STAMP = stamp(6);

/** 真实存在的订单 ID（登录后在 try 里回填）。发货物流等页面要求引用已存在订单。 */
let REAL_ORDER_ID = null;
/**
 * 已发布且有库存的商品 ID（登录后在 try 里回填）。
 * 下单会同步预校验「商品已发布 + 库存充足」，填随机雪花 ID 会被 409
 * BP_ORDER_PRODUCT_NOT_PUBLISHED 拒 —— 那是产品做对了，探针不能靠造假蒙过去。
 */
let REAL_PRODUCT_ID = null;
/** 真实客户 ID（同上，取自既有订单），避免把客户维度填成随机雪花。 */
let REAL_CUSTOMER_ID = null;
/** 当前巡检到的菜单 path（autoFill 前赋值），用于按页面区分同名字段的取值口径。 */
let CUR_PATH = '';
/** API 会话 token（登录时回填），供「先查库再选」类前置换位用。 */
let API_TOKEN = null;

/**
 * 挑一个**已经定义过字段**的主数据模型，返回其名称。
 * 为什么必须挑：记录管理的动态表单项由模型字段生成，新模型零字段 ⇒ 弹窗里除了
 * 业务编码/名称/生效期再没有可填项 ⇒ 提交的 data={} ⇒ 400「记录数据不能为空」。
 * 建出来的空模型恰好被选中时就会翻车，这是场景依赖，不是产品缺陷。
 */
const pickEntityWithFields = async () => {
  try {
    const { data: j } = await api('GET', '/masterdata/entities?page=1&size=50', undefined, { token: API_TOKEN });
    for (const e of j?.data?.records || []) {
      const fr = await api('GET', `/masterdata/entities/${e.id}/fields`, undefined, { token: API_TOKEN }).catch(() => null);
      if (!fr || !fr.ok) continue;
      const fj = fr.data;
      const arr = Array.isArray(fj?.data) ? fj.data : fj?.data?.records || [];
      if (arr.length && e.name) return String(e.name);
    }
  } catch {
    /* 查不到就保持页面默认选择 */
  }
  return null;
};

// 只跑这些模块（空数组 = 全跑）。用 BONE_E2E_ONLY=/iam#/accounts,/system#/dict 局部复跑。
const ONLY = (process.env.BONE_E2E_ONLY || '')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);

const { results, check, report } = makeChecker();

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
  let items = await modal.locator('.ant-form-item').all();

  // 弹窗内带 Tabs 时（集成「流程编排」的「新建流程」默认落在画布 Tab，
  // 流程名称/描述在「基本信息」Tab 里不可见），可见表单项为 0 ⇒ 「填 0 项 → 提交必失败」，
  // 看着像功能坏了，其实是探针没翻到那一页。先逐个点一遍 Tab 让表单项显露。
  if (!(await modal.locator('.ant-form-item:visible').count())) {
    for (const t of await modal.locator('.ant-tabs-tab').all()) {
      if (await modal.locator('.ant-form-item:visible').count()) break;
      await t.click().catch(() => {});
      await page.waitForTimeout(400);
    }
    items = await modal.locator('.ant-form-item').all();
  }

  // 自定义弹窗（向导/画布）没有 .ant-form-item 包裹时，退化为直接填裸控件。
  if (items.length === 0) {
    const ctrls = await modal.locator('input:not([type=hidden]):visible, textarea:visible').all();
    for (const c of ctrls) {
      const ph = (await c.getAttribute('placeholder').catch(() => '')) || '';
      const s = ph.toLowerCase();
      let v = `E2E${STAMP}`;
      if (/cron|调度|定时/.test(s)) v = '0 0 12 * * ?';
      else if (/表达式|规则/.test(s)) v = 'status=active';
      else if (/\bid\b|编号|序号|雪花/.test(s))
        v = String(760000000000000000n + BigInt(Math.floor(Math.random() * 1e9)));
      else if (/编码|code|标识/.test(s)) v = `e2e${STAMP}`;
      else if (/名称|name/.test(s)) v = `E2E${STAMP}`;
      else if (/描述|remark|说明/.test(s)) v = `E2E 自动建数据 ${STAMP}`;
      await c.fill(v).catch(() => {});
      filled.push(ph || 'raw');
    }
    return { filled, skipped };
  }

  for (const item of items) {
    try {
      if (!(await item.isVisible().catch(() => false))) continue;
      const label = (await item.locator('label').first().innerText().catch(() => '')).replace(/[:：*\s]/g, '');
      const ph = (await item.locator('input,textarea').first().getAttribute('placeholder').catch(() => '')) || '';
      const sem = (label + ' ' + ph).toLowerCase();
      const closeDropdown = async () =>
        modal.locator('.ant-modal-title, .ant-drawer-title, .ant-modal-body').first().click({ timeout: 2000 }).catch(() => {});

      // ★ 无条件 Esc 会冒泡到 Modal/Drawer，把整个新建弹窗一起关掉（antd Modal 默认 keyboard=true）。
      //   实测「权限管理 / 业务数据」的『填表后表单仍然打开』假红就来自这里：下拉已被 Enter 收起，
      //   多出来的那次 Esc 直接关掉了新建弹窗，后面自然找不到提交按钮。只在下拉真还开着时才按。
      const escIfOpen = async () => {
        const open = await page
          .locator(
            '.ant-select-dropdown:not(.ant-select-dropdown-hidden), .ant-picker-dropdown:not(.ant-picker-dropdown-hidden)'
          )
          .first()
          .isVisible()
          .catch(() => false);
        if (open) await page.keyboard.press('Escape').catch(() => {});
      };

      // 0) 跳过表单已预设的合法默认值（如数据源 port=3306 / host=localhost / 字典 category=LIST），
      //    不要覆盖成脏值，否则会把能成功的创建打成 400（如把端口填成 "E2E312314" 导致 Integer 解析失败）。
      const presetVal = await item.locator('input:not([type=hidden])').first().inputValue().catch(() => '');
      // ★ 预设判定必须看「文本」，不能只数节点：antd 的 Select 在**空值**时也会渲染
      //   一个 .ant-select-selection-item（空文本）。只数个数 ⇒ 所有空 ENUM 下拉都被当成
      //   「已有合法预设值」跳过 ⇒ 必填枚举以空值提交 ⇒ 400「取值 不在允许枚举内」
      //   （2026-10-18「业务数据」模块假红：设备类型/巡检结果 被这条误跳）。
      //   判定改为「存在且文本非空」才跳过。
      const presetSelTxt = (await item.locator('.ant-select-selection-item').first().innerText().catch(() => '')) || '';
      if ((presetVal && presetVal.trim()) || presetSelTxt.trim()) {
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
          // 用「点标题收起下拉」而不是 Esc：AutoComplete 在无匹配项时下拉仍会开着一个
          // 「暂无数据」层，escIfOpen 判定为「还开着」就按 Esc，Esc 冒泡到 Modal 会把
          // 整个新建弹窗关掉（权限管理 / 业务数据 的假红就是这么来的）。
          await closeDropdown();
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
        // 「长度/上限/大小」类填 255 而不是 1：建字段时若把长度填成 1，
        // 之后在「记录管理」里给该字段填任何正常值都会被
        // MD_RECORD_FIELD_VALIDATION_FAILED「长度 N 超过上限 1」拒掉，
        // 看起来像记录管理坏了，根因其实在建模这一步。
        await numWrap.locator('input').first().fill(/长度|上限|max|大小|容量/.test(sem) ? '255' : '1');
        filled.push(label || 'number');
        continue;
      }

      // 4) DatePicker / TimePicker —— 点开选今天，不填文本
      const picker = item.locator('.ant-picker').first();
      const isPicker = (await picker.count()) > 0 && (await picker.isVisible().catch(() => false));
      if (isPicker) {
        await picker.click({ timeout: 2000 }).catch(() => {});
        await page.waitForTimeout(400);
        // 必须真的落下值，不能只按 Enter 就当完成：日期是建模里常见的必填字段，
        // 空着提交会被 400 META_RUNTIME_VALIDATION_FAILED「字段「xxx」为必填」拒，
        // 看起来像后端坏了，其实是探针没选日期。
        const today = page
          .locator('.ant-picker-dropdown:not(.ant-picker-dropdown-hidden) .ant-picker-cell-today')
          .first();
        if (await today.count()) await today.click({ timeout: 2000 }).catch(() => {});
        else await page.keyboard.press('Enter').catch(() => {});
        await page.waitForTimeout(300);
        await closeDropdown();
        const picked = await picker.locator('input').first().inputValue().catch(() => '');
        skipped.push(`${label || 'datepicker'}${picked ? `=${picked}` : '(未落值!)'}`);
        continue;
      }

      // 5) 文本域：结构化字段给最小合法值，自由文本给描述
      const ta = item.locator('textarea').first();
      if ((await ta.count()) > 0 && (await ta.isVisible().catch(() => false))) {
        // 字段 schema（field_schema / 字段结构 / 字段集）必须填 JSON 数组，而非对象，否则后端校验为非法。
        if (/field[\s_-]?schema|字段结构|字段集|字段定义|schema/i.test(sem)) {
          await ta.fill('[{"key":"value"}]');
        } else if (/表达式|规则/i.test(sem)) {
          // 与 input 分支同口径：质量规则表达式必须含 `field=<字段编码>`，
          // 否则 400 MD_RULE_EXPRESSION_INVALID「规则表达式缺少 field=<字段编码>」。
          await ta.fill(/rules|quality|质量|质检/i.test(CUR_PATH) ? `field=e2e${STAMP}` : 'status=active');
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
      // 路由路径：必须是 `path#hash`（微应用前缀 + 子应用 HashRouter 路由）。
      // 旧逻辑走到兜底分支填成 `E2E<STAMP>` —— 无 `#` 的裸串会被壳层 `<Route path="*">`
      // 静默送回首页（「点了没反应」），且污染菜单真源。这里显式给合法值。
      else if (/路由|路径|route|path/i.test(sem)) v = '/iam#/menus';
      // 分支顺序即优先级，调错一个位置就会把「能建成功」打成 400：
      //  · cron 必须排在「表达式」之前 —— cron 字段的 label 常是「cron表达式」，
      //    否则先命中表达式分支，cron 被填成 status=active。
      //  · 「表达式|规则」必须排在「编码」之前 —— 质量规则「规则表达式」的 placeholder
      //    含「字段编码」字样，反过来会被编码分支先命中填成 e2e，后端报
      //    MD_RULE_EXPRESSION_INVALID「片段不是 k=v 形式」。（这段顺序注释原本写在编码分支
      //    下面但代码顺序是反的，属于「注释声称 X、代码没做 X」，此处按注释落地。）
      else if (/cron|调度|定时/.test(sem)) v = '0 0 12 * * ?';
      else if (/表达式|规则|condition|rule/i.test(sem)) {
        // 质量规则：RuleExpressionEvaluator#validate 强制要求 k=v 片段里必须含 `field=<字段编码>`，
        // 写 status=active 会被 400 MD_RULE_EXPRESSION_INVALID「缺少 field=<字段编码>」拒。
        v = /rules|quality|质量|质检/i.test(CUR_PATH) ? `field=e2e${STAMP}` : 'status=active';
      }
      // ID 类字段是雪花 ID（Long），必须填数字字符串。判定只认 label 里的独立「id/编号/序号/雪花」，
      // 用 \bid\b 词边界，避免误伤「租户名称/租户编码」等命名字段（中文「单」与拉丁 i 之间恰是词边界）。
      // ★ 无 label 的行（商品ID 只有 placeholder）必须回退到 placeholder 再判定，
      //   否则会掉到兜底分支被填成 E2E249582 → 后端 Long 反序列化失败。
      else if (/\bid\b|编号|序号|雪花/.test(labelLower || ph.toLowerCase())) {
        // 订单类 ID 必须是**真实存在**的订单：发货物流「订单ID」填随机雪花会被 404
        // BP_ORDER_NOT_FOUND 拒 —— 那是产品做对了，探针不能靠造假蒙过去。
        v =
          /订单|order/i.test(sem) && REAL_ORDER_ID
            ? REAL_ORDER_ID
            : /商品|product/i.test(sem) && REAL_PRODUCT_ID
              ? REAL_PRODUCT_ID
              : /客户|customer/i.test(sem) && REAL_CUSTOMER_ID
                ? REAL_CUSTOMER_ID
                : String(760000000000000000n + BigInt(Math.floor(Math.random() * 1e9)));
      } else if (/编码|code|标识/i.test(sem)) v = `e2e${STAMP}`;
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

/**
 * 收尾清理：删掉本轮在「菜单管理」页建出来的 E2E 菜单。
 *
 * ★ 为什么必须清：菜单是本探针自己的运行时真源。巡检到 `/iam#/menus` 时会真的建一条菜单，
 *   即便 path 已填成合法值，它仍是**指向既有页面的重复入口**（噪音），而且一旦填值分支
 *   失效退化成 `E2E<STAMP>`，就是一条 path 不含 `#` 的非法菜单 —— 壳层 `<Route path="*">`
 *   会静默送回首页（用户看到「点了没反应」），并让下游 `ui-all-menu-regression` 因
 *   `goto` 非法 URL 直接抛异常（实测 233/235 的那 1 个失败即来源于此）。
 *   留着 = 每跑一轮污染一次真源，故必须自净。
 */
const cleanupE2EMenus = async () => {
  try {
    // 取不到 token 时按原口径跳过清理（不抛错）
    const token = await tryLogin();
    if (!token) {
      console.log('E2E 菜单清理跳过：取 token 失败');
      return;
    }
    const { data: mj } = await api('GET', '/iam/menus/tree', undefined, { token });
    const junk = [];
    const walk = (ns) => {
      for (const n of ns || []) {
        if (/^E2E\d+$/.test(String(n.name || ''))) junk.push(n);
        walk(n.children || []);
      }
    };
    walk(mj.data || []);
    let removed = 0;
    for (const m of junk) {
      const r = await api('DELETE', `/iam/menus/${m.id}`, undefined, { token });
      if (r.ok) removed++;
    }
    check(`E2E 菜单自净（残留 ${junk.length} 条，删除 ${removed} 条）`, junk.length === removed,
      `剩 ${junk.length - removed} 条未删`);
  } catch (e) {
    check('E2E 菜单自净', false, String(e.message).slice(0, 120));
  }
};

try {
  // ---------- 登录取菜单真源 ----------
  // 登录：统一走共享 uiLogin（禁止在探针内重抄登录选择器；凭据见 client.cjs）
  const logged = await uiLogin(page);
  check('UI 登录成功', logged, '登录表单未消失');
  if (!logged) throw new Error('登录失败，后续用例无意义');

  // 菜单树：走独立 API 会话拿运行时真源（页面内 fetch 不一定带上网关要求的所有头）
  let menus = [];
  try {
    const token = await tryLogin();
    API_TOKEN = token;
    const { data: mj } = await api('GET', '/iam/menus/tree', undefined, { token });
    const out = [];
    const walk = (ns, parent) => {
      for (const n of ns || []) {
        if (n.path && n.path.includes('#')) out.push({ name: `${parent ? parent + ' · ' : ''}${n.name}`, path: n.path });
        walk(n.children || [], n.name);
      }
    };
    walk(mj.data || [], '');
    menus = out;

    // 预取真实业务主键（三项都必须是「库里真实存在且状态合法」的，不能用随机雪花蒙）：
    //   · 订单 ID：发货物流要求订单存在（随机 → 404 BP_ORDER_NOT_FOUND）；
    //     ★ 且必须 PAID —— Order#ship 只接受已支付态，CREATED/CANCELLED/SHIPPED 都会被
    //       409 BP_ORDER_STATUS_CONFLICT 拒。库里 CANCELLED 占比最高，直接取首条必翻车。
    //   · 客户 ID / 商品 ID：下单会同步预校验「商品已发布 + 库存充足」（随机 → 409）。
    const or = await api('GET', '/orders?page=1&size=50', undefined, { token }).catch(() => null);
    if (or && or.ok) {
      const oj = or.data;
      const recs = oj?.data?.records || [];
      const pick = recs.find((r) => r.status === 'PAID') || recs[0];
      if (recs.length) {
        REAL_ORDER_ID = String(pick.id);
        if (pick.customerId) REAL_CUSTOMER_ID = String(pick.customerId);
        // 和 vessels 同理：商品 ID 必须是一个**主数据里已发布**的商品。
        // 取若干既有订单的行项目做候选，并剔除 900xxx —— 那一段是渠道 E2E 脚本
        // （multichannel.py / channel-buyer-broadcast.py）的保留商品，只走渠道拉单、
        // 不在 MD_PRODUCT 主数据里，走普通下单会被 409 BP_ORDER_PRODUCT_NOT_PUBLISHED 拦。
        const candidates = [];
        for (const o of recs.slice(0, 8)) {
          const dr = await api('GET', `/orders/${o.id}`, undefined, { token }).catch(() => null);
          if (!dr || !dr.ok) continue;
          const dj = dr.data;
          for (const it of dj?.data?.items || []) {
            if (it.productId != null) candidates.push(String(it.productId));
          }
          const seeded = candidates.find((p) => /^\d+$/.test(p) && Number(p) < 900000);
          if (seeded) {
            REAL_PRODUCT_ID = seeded;
            break;
          }
        }
        if (!REAL_PRODUCT_ID && candidates.length) REAL_PRODUCT_ID = candidates[0];
      }
    }
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
      // 每个模块记录 console 错误基线，失败时能指认到**具体页面**，而不是甩一个全局汇总
      const errStart = consoleErrors.length;
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

      // 记录管理：先把左上角「选择模型」切到一个有字段的模型，再进新建
      // （否则表单没有动态数据项，提交的 data 为空 ⇒ 400 记录数据不能为空）
      if (/masterdata#\/records/.test(m.path)) {
        const want = await pickEntityWithFields();
        if (want) {
          const es = page.locator('#subapp-viewport .ant-select').first();
          if (await es.count()) {
            await es.click({ timeout: 3000 }).catch(() => {});
            await page.waitForTimeout(500);
            const opt = page
              .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
              .filter({ hasText: want })
              .first();
            if (await opt.count()) {
              await opt.click({ timeout: 3000 }).catch(() => {});
              await page.waitForTimeout(1800);
            } else {
              await page.keyboard.press('Escape').catch(() => {});
            }
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
      // ★ 抓住**这一个**弹窗的 DOM 句柄，别再用 `.ant-modal:visible` 这类重解析选择器判存亡：
      //   那会在创建成功后把顺弹出的「创建成功」信息框也算成「弹窗没关」，
      //   把真正闭环的模块（租户管理：创建成功后弹 Modal.success 展示初始密码）打假红。
      const modalHandle = await modal.elementHandle().catch(() => null);

      CUR_PATH = m.path;
      const { filled, skipped } = await autoFill(modal);
      await shot(slug(m.path) + '_form');

      if (!(await modalHandle?.isVisible().catch(() => false))) {
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
      await page.waitForTimeout(1500);

      const toast = await page.locator('.ant-message, .ant-notification').innerText().catch(() => '');
      // 表单内 Alert 也必须采集：提交规则（submitRules）拦截的反馈走的是**表单内 Alert**，
      // 不是 toast。只读 toast 会把「有明确提示的规则拦截」与「点了没反应的真静默」
      // 混为一谈 —— 前者是产品行为，后者才是缺陷（采购订单 2026-10-31 实证：8 条 Alert
      // 里有 4 条带【规则名】前缀的拦截说明，而 toast 为空 ⇒ 曾被误判为静默失败）。
      const alertTexts = await modal.locator('.ant-alert:visible').allInnerTexts().catch(() => []);
      const alertText = alertTexts.join(' | ').replace(/\s+/g, ' ');
      // 强信号：runSubmit 的每条提示都带【规则名/校验名】前缀
      const ruleNames = [...new Set([...alertText.matchAll(/【([^】]{2,30})】/g)].map((x) => x[1]))];
      const ruleBlocked = ruleNames.length > 0;
      // 轮询等弹窗收起，而不是固定 sleep：下单这类要过库存/发布预校验的接口偶尔慢一拍，
      // 固定等待会把「其实已经成功」误判成「弹窗没关」。
      for (let i = 0; i < 10; i++) {
        if (!(await modalHandle?.isVisible().catch(() => false))) break;
        await page.waitForTimeout(500);
      }
      // 判存亡只看「刚才那个弹窗」，不受成功提示框干扰
      const stillOpen = (await modalHandle?.isVisible().catch(() => false)) ? 1 : 0;
      const listAfter = await page.locator('#subapp-viewport').innerText().catch(() => '');
      const changed = listAfter !== listBefore;
      const bad = /失败|错误|异常|error|500|400|已存在|重复|请先修正/i.test(toast);
      // 判据：弹窗关闭 + 无失败类 toast +（列表真的刷新出变化 或 出现成功类 toast）。
      // 之所以接受「成功 toast」作为第二判据：列表多为分页，新建的记录可能落在第 N 页，
      // 首屏文本不变化不代表没落库——但「弹窗关闭 + 成功 toast」已经足以证明提交成功。
      const okSubmit = stillOpen === 0 && !bad && (changed || /成功|已创建|已保存|已添加|已新增/.test(toast));
      if (okSubmit) {
        check(
          `${m.name}：新建提交闭环（填${filled.length}项/跳${skipped.length}项，按钮「${btnText}」）`,
          true,
          `弹窗关闭 列表变化=${changed}`
        );
      } else if (ruleBlocked) {
        // 拦截成立且**有可见说明** ⇒ 产品行为，不判缺陷；但必须留下规则名，
        // 否则「被规则拦住」与「静默无反馈」在日志里长得一样，下次还得重查一遍。
        check(
          `${m.name}：被提交规则拦截（非缺陷）`,
          true,
          `规则=${ruleNames.join('/')} 无HTTP=${badResponses.length === 0} Alert已提示=${alertText.length > 0}`
        );
      } else {
        check(
          `${m.name}：新建提交闭环（填${filled.length}项/跳${skipped.length}项，按钮「${btnText}」）`,
          false,
          `弹窗仍开=${stillOpen} toast=${toast.replace(/\s+/g, ' ').slice(0, 60)} Alert=${alertText.slice(0, 80)} 列表变化=${changed}`
        );
      }
      if (stillOpen && !ruleBlocked) {
        await shot(slug(m.path) + '_fail');
        for (const b of badResponses.slice(0, 3)) {
          console.log(`    ↳ HTTP ${b.status} ${b.url} :: ${b.body.replace(/\s+/g, ' ')}`);
        }
        // 失败时打印「哪些字段被填、哪些被跳」——光有「填5项/跳3项」两个数字无法定位
        // 是哪个字段取了非法值（本轮 equipment_type 枚举被拒就是靠这份明细定位）。
        console.log(`    ↳ 填值明细 filled=[${filled.join(' | ')}]`);
        console.log(`    ↳ 跳过明细 skipped=[${skipped.join(' | ')}]`);
        await page.keyboard.press('Escape').catch(() => {});
      }
      // 本模块新增的 console 错误（定位「页面上到底哪个组件在报警」的唯一依据）
      const pageErrs = consoleErrors.slice(errStart);
      if (pageErrs.length) {
        console.log(`    ↳ 本页 console ${pageErrs.length} 条：${pageErrs.join(' ~ ').replace(/\s+/g, ' ').slice(0, 300)}`);
      }
    } catch (e) {
      check(`${m.name}：建数据流程未抛异常`, false, String(e.message).slice(0, 160));
    }
  }
  console.log(`\n实际触发新建闭环：${attempted} 个模块`);

  // 自净：清掉本轮建出来的 E2E 菜单，避免污染下游探针的菜单真源
  await cleanupE2EMenus();

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
  const { ok } = report('全模块建数据巡检汇总');
  await browser.close();
  process.exit(ok ? 0 : 1);
}
