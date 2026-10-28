/**
 * P1-5 大 schema 渲染层性能基线（活体）。
 *
 * 为什么纯函数基准不够：bench 证明 lintSchema/firstDiffPath 在 400 控件下都 <0.15ms，
 * 纯算法不是瓶颈。真正的成本在 React 渲染（每个控件一套 antd 组件 + 检视器 DOM）。
 * 本探针把 N 个控件的真实 schema PUT 进后端，在**真机浏览器**量三条用户可感知路径：
 *
 *   T1 首屏：进设计器 → 结构面板控件全部渲染可见（domcontentloaded → 控件行数稳定）
 *   T2 编辑：点某个控件的齿轮（选中）→ 检视器出现；改一次属性 → UI 反映
 *   T3 切面板：结构 → 规则 → 结构（每次都触发右栏 memo 失效与面板重挂）
 *
 * 每档规模跑一次，最后对比「规模翻倍时各耗时是否线性」。**只量不改**：优化前后各跑一次。
 *
 * 用法：node scripts/e2e/ui-designer-perf-probe.mjs [控件数,默认 120]
 * finally 必恢复原草稿（不留下性能实验残留）。
 */
/* 共享 client：playwright 加载 / API 登录与带 token fetch / 登录凭证（见 e2e_lib/client.cjs） */
import {
  UI_BASE as BASE,
  USER,
  PWD,
  loadPlaywright,
  api,
  login,
} from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

// SIZES 已定义（见下）
/** 多档规模（默认 10/40/80/160），用于看增长曲线；传单个数字则只跑该档 */
const SIZES = process.argv[2] ? [Number(process.argv[2])] : [10, 40, 80, 160];

/* ---------------- API（登录与带 token fetch 见 e2e_lib/client.cjs） ---------------- */
const getEntities = async (tok) => {
  const { data: d } = await api('GET', '/metadata/entities?page=1&size=500', undefined, { token: tok });
  return ((d.data || {}).records || []).filter((x) => x.displayName === '报销单')
    .sort((a, b) => String(b.id).localeCompare(String(a.id)));
};
const getSchema = async (tok, eid) => {
  const { data: d } = await api('GET', `/metadata/entities/${eid}/ui-schema`, undefined, { token: tok });
  const data = d.data || {};
  if (typeof data.json === 'string') data.json = JSON.parse(data.json);
  return data;
};
const putSchema = async (tok, eid, schema, lock) => {
  const { status, data: body } = await api('PUT', `/metadata/entities/${eid}/ui-schema`, { json: schema }, {
    token: tok,
    headers: { 'If-Match': String(lock) },
  });
  return { status, body };
};
const getFields = async (tok, eid) => {
  const { data: d } = await api('GET', `/metadata/entities/${eid}/fields`, undefined, { token: tok });
  const dd = d.data || {};
  // 端点返 PageResult（records/total/...），不是裸数组
  return Array.isArray(dd) ? dd : (dd.records || []);
};

const KINDS = ['INPUT', 'NUMBER', 'MONEY', 'DATE', 'DATETIME', 'ENUM', 'SWITCH', 'SLIDER', 'TEXTAREA', 'REFERENCE'];

/**
 * 用**实体的真实字段**造 N 控件 schema。
 *
 * 字段池只有 10~11 个（实测全库最多的实体也只有 11），N > 池大小必然重复引用同一
 * fieldCode ⇒ lint 会报「字段重复出现」error。**这是有意的**：本探针测的是渲染成本，
 * 该error 只多出一条恒定的横幅（不随 N 增长），不影响控件行数与重渲染量；
 * 而若改用不存在的字段，会引入另一类 error 并可能触发门控短路，噪声更大。
 */
function buildLargeSchema(base, fields, n) {
  const per = 8;
  const pool = fields.filter((f) => f && f.code);
  const sections = [];
  for (let s = 0; s * per < n; s++) {
    const controls = [];
    for (let j = 0; j < per && s * per + j < n; j++) {
      const i = s * per + j;
      const f = pool[i % pool.length];
      controls.push({
        fieldCode: f.code,
        controlKind: KINDS[i % KINDS.length],
        labelOverride: `${f.name || f.code}_P${i}`,
        props: {},
      });
    }
    sections.push({ id: `sec_perf_${s + 1}`, title: `性能分区 ${s + 1}`, controls });
  }
  const next = JSON.parse(JSON.stringify(base || {}));
  next.version = next.version || '1.2';
  next.views = { ...(next.views || {}), form: { ...((next.views || {}).form || {}), sections } };
  // 清掉引用旧控件的联动/变体，避免 lint error 淹没渲染量测
  next.linkageRules = [];
  next.submitRules = [];
  next.variants = [];
  return next;
}

/* ---------------- 浏览器 ---------------- */
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1680, height: 1000 } });
page.setDefaultTimeout(30000);

const shots = [];
const uiLogin = async () => {
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"]', { timeout: 20000 });
  const u = page.locator('input[placeholder*="用户名"], input[name="username"], input#username').first();
  if (await u.count()) await u.fill(USER);
  const p = page.locator('input[type="password"]').first();
  await p.fill(PWD); await p.press('Enter');
  await page.waitForTimeout(1500);
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('button').filter({ hasText: /登\s*录/ }).first().click().catch(() => {});
  }
  await page.waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 });
};
const dismissTour = async () => {
  const s = page.locator('button, a').filter({ hasText: '跳过引导' }).first();
  if (await s.count()) { await s.click({ timeout: 4000 }).catch(() => {}); await page.waitForTimeout(400); }
};

/** 打开设计器并等结构面板稳定；返回 { 首屏ms, 控件行数 } */
async function openDesigner(eid) {
  const t0 = Date.now();
  await page.goto(`${BASE}/metadata#/entities/${eid}`, { waitUntil: 'domcontentloaded' });
  await page.reload({ waitUntil: 'domcontentloaded' });
  const tab = page.locator('.ant-tabs-tab').filter({ hasText: '界面设计器' }).first();
  await tab.waitFor({ state: 'visible', timeout: 30000 });
  await tab.click();
  await page.waitForSelector('.uis-root', { timeout: 30000 });
  await dismissTour();
  // 先起一个节拍器把「文本稳定」信号持续写进 window（waitForFunction 读它）
  const beat = setInterval(() => { markStableText().catch(() => {}); }, 250);
  await new Promise((r) => setTimeout(r, 500));
  // 等结构面板控件行全部出现（设计器渲染完成的可观测信号），再要求文本稳定两拍。
  // 早期版本这里比对 `window.__lastT` —— 该变量从未被赋值，条件恒假 ⇒ 每档都白等满
  // 40s 超时，「首屏」列出来的是超时上限而非真实耗时（教训：waitForFunction 的
  // 谓词必须只引用**确实会被写入**的来源）。
  await page.waitForFunction(() => {
    const gears = document.querySelectorAll('[aria-label^="控件属性 "]').length;
    const rows = document.querySelectorAll('[data-tour="control-row"]').length;
    const t = document.querySelector('.uis-root')?.innerText || '';
    return gears > 0 && rows > 0 && t.length > 50 && t === window.__stableT;
  }, null, { timeout: 40000, polling: 300 }).catch(() => {});
  const gears = await page.locator('[aria-label^="控件属性 "]').count();
  clearInterval(beat);
  return { ms: Date.now() - t0, gears };
}

/** 采样「文本稳定」信号：连续两拍文本一致才置位，供 openDesigner 的 waitForFunction 读取 */
async function markStableText() {
  await page.evaluate(() => {
    const el = document.querySelector('.uis-root');
    const cur = el?.innerText || '';
    if (window.__lastT === cur) window.__stableT = cur;
    else window.__lastT = cur;
  });
}

/** T2：点最后一个（最靠下、最考验渲染的）控件的齿轮 → 检视器出现 */
async function measureSelect() {
  const gears = page.locator('[aria-label^="控件属性 "]');
  const n = await gears.count();
  if (!n) return null;
  const last = gears.nth(n - 1);
  const t0 = Date.now();
  await last.scrollIntoViewIfNeeded();
  await last.click();
  // 等检视器真正渲染（用 data-tour 锚点；此前等的是不存在的 .uis-insp 类
  // ⇒ 每档都白等满 15s 超时，「选中控件」列出来的是超时上限而非真实耗时）
  await page.waitForSelector('[data-tour="control-inspector"]', { timeout: 10000 })
    .catch(() => {});
  return Date.now() - t0;
}

/**
 * T2b：一次真实编辑的端到端延迟（**最关键的用户感知指标**）。
 *
 * 前置：检视器必须已展开（measureSelect 里点了最后一个齿轮），否则检视器输入框不存在。
 * 度「按键 → 预览区 label 文本出现新字符」的墙钟时间，这条路径覆盖：
 * commit → firstDiffPath → setSchema → issues useMemo（lintSchema 全量重跑）
 * → StructurePanel 重渲 → MemoSchemaPreview 失效重渲。
 */
/**
 * T2b：一次真实编辑的端到端延迟（**最关键的用户感知指标**）。
 *
 * 口径 = 「敲一个字符 → 浏览器把这一帧画出来」，用**双requestAnimationFrame** 度。
 *
 * 为什么不用「等预览文本出现新字符」当判据：那个条件依赖 labelOverride 必须被
 * 渲染进预览区，控件类型/场景/重复字段一变化就不成立（实测 40+ 档恒等超时 ⇒
 * 量到的是超时上限而非耗时，是典型的**探针假数据**）。而双 rAF 直接量
 * 「本次 setState 引发的重渲染 + 布局 + 绘制是否已在屏幕上落地」，
 * 不依赖任何特定文案，是与用户手感一致、且不会假红的口径。
 *
 * 定位「显示名覆盖」输入框不能按 placeholder：它是**动态的**（沿用元数据字段
 * displayName，实测值为「状态」）。用检视器内「以『显示名覆盖』开头的单元格里的 input」。
 */
async function measureEdit() {
  return page.evaluate(async () => {
    const insp = document.querySelector('[data-tour="control-inspector"]');
    if (!insp) return null;
    const cell = Array.from(insp.children).find((d) => d.textContent?.trim().startsWith('显示名覆盖'));
    const inp = cell?.querySelector('input');
    if (!inp) return null;

    // 用原生 setter 写入并派发 input 事件，走 React 的 onChange（与真实键入等价；
    // 直接改 inp.value 不会触发 React 受控更新）。
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
    const t0 = performance.now();
    setter?.call(inp, `${inp.value}X`);
    inp.dispatchEvent(new Event('input', { bubbles: true }));
    // 双 rAF：第一帧回调在 React commit + 布局之后，第二帧确认已绘制
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    const ms = performance.now() - t0;
    setter?.call(inp, inp.value.slice(0, -1));
    inp.dispatchEvent(new Event('input', { bubbles: true }));
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    return Math.round(ms);
  });
}

/** T3：切面板（结构→规则→结构），量两次往返 */
async function measureTabSwitch() {
  const go = async (re) => {
    const t = page.locator('.ant-tabs-tab').filter({ hasText: re }).first();
    const t0 = Date.now();
    await t.click();
    await page.waitForTimeout(900); // 含稳定等待，反映用户感知
    return Date.now() - t0;
  };
  const a = await go(/^规\s*则$/);
  const b = await go(/^结\s*构$/);
  return { toRules: a, toStructure: b };
}

/* ---------------- 主流程 ---------------- */
const tok = await login();
const ent = (await getEntities(tok))[0];
if (!ent) { console.error('✗ 找不到「报销单」实体'); await browser.close(); process.exit(1); }
const eid = ent.id;
const orig = await getSchema(tok, eid);
const fields = await getFields(tok, eid);
console.log(`实体 ${ent.displayName} (${eid})，真实字段 ${fields.length} 个，目标规模档 ${SIZES.join('/')}`);

await uiLogin();
const table = [];
let restored = false;
try {
  const cur0 = await getSchema(tok, eid);
  let lock = cur0.lockVersion;
  for (const n of SIZES) {
    const big = buildLargeSchema(orig.json || {}, fields, n);
    const put = await putSchema(tok, eid, big, lock);
    if (put.status !== 200) throw new Error(`注入 ${n} 控件失败 ${put.status} ${JSON.stringify(put.body).slice(0, 200)}`);
    lock = (await getSchema(tok, eid)).lockVersion;
    console.log(`已注入 ${n} 控件（JSON ${Math.round(JSON.stringify(big).length / 1024)}KB）`);

    const open = await openDesigner(eid);
    const sel = await measureSelect();
    const edit = await measureEdit();
    const sw = await measureTabSwitch();
    const previewFields = await page.locator('.uis-fld').count();
    const gears = await page.locator('[aria-label^="控件属性 "]').count();
    console.log(`  ${n} 控件 → 首屏 ${open.ms}ms / 结构行 ${gears} / 选中 ${sel}ms / 编辑 ${edit}ms / 切规则 ${sw.toRules}ms / 回结构 ${sw.toStructure}ms`);
    table.push({
      控件数: n, KB: Math.round(JSON.stringify(big).length / 1024),
      首屏ms: open.ms, 结构行: gears, 预览行: previewFields,
      选中控件ms: sel ?? -1, 单次编辑ms: edit ?? -1,
      切规则ms: sw.toRules, 回结构ms: sw.toStructure,
    });
    await page.screenshot({ path: `/Users/renhui.trh/wps/bone/logs/ui_designer_perf_${n}.png` }).catch(() => {});
  }
} catch (e) {
  console.error('✗ 度量失败：', e.message);
} finally {
  if (!restored) {
    const cur = await getSchema(tok, eid);
    const back = await putSchema(tok, eid, orig.json || {}, cur.lockVersion);
    restored = true;
    console.log(`\n草稿已恢复：PUT ${back.status}`);
  }
  await browser.close();
}

console.log('\n===== P1-5 渲染层性能基线（浏览器实测）=====');
console.table(table);
console.log('JSON:' + JSON.stringify(table));
process.exit(table.length ? 0 : 1);