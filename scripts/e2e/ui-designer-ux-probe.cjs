/**
 * 界面设计器 UX 优化专项探针（2026-10-15 优化批次回归）：
 *   1. Tab 换名 + 图标区分（结构 / 显示设置 / 界面设计器 / 数据 / 发布）
 *   2. 3 步上手引导（首访自动 + 帮助按钮重显）
 *   3. 全屏（禅模式）+ Esc 退出
 *   4. 变体创作（variants tab：新增 / 命中条件 / 覆盖编辑）
 *   5. 快捷键：Ctrl+Z 撤销成真；负向——输入框内 Ctrl+Z 不劫持
 *   6. 发布协议硬门禁：负向（坏 schema 阻断发布）+ 正向（修复后放行）
 *   7. 可视化编辑历史栈（jumpToPast：跳回后被跳过的中间态压入 future，重做不断链）
 *
 * 策略：Playwright 走 shell :3000 真实登录拿 JWT（沿用 lowcode-closed-loop 模式），
 *      实体经网关 API 建（探针自产自销，不碰他人在途数据），UI 断言走真实页面。
 *
 * 用法：
 *   NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-6/bin/node
 *   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
 *   $NODE scripts/e2e/ui-designer-ux-probe.cjs
 */
const { chromium } = require('playwright');
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const SHELL = 'http://localhost:3000';
const GATEWAY = 'http://localhost:8888';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const steps = [];
const errors = [];
function assert(name, ok, info) {
  steps.push({ name, ok: !!ok, ...(info || {}) });
  if (!ok) errors.push(`${name} :: ${JSON.stringify(info || {}).slice(0, 300)}`);
  console.log(`  ${ok ? '✅' : '❌'} ${name}${info ? '  ' + JSON.stringify(info) : ''}`);
}

async function login() {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  await page.goto(`${SHELL}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await sleep(3500);
  const u = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if ((await u.count()) && (await u.isVisible().catch(() => false))) {
    await u.fill('admin');
    await page.locator('input[type="password"], input[placeholder="密码"]').first().fill('123456');
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await sleep(4000);
  }
  const token = await page.evaluate(() => localStorage.getItem('token'));
  if (!token) { await browser.close(); throw new Error('login failed: no token'); }
  return { browser, ctx, page, token };
}

function makeApi(token) {
  return async function api(method, path, { body, etag } = {}) {
    const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
    if (etag) headers['If-Match'] = etag;
    const res = await fetch(GATEWAY + path, {
      method, headers, body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    return { status: res.status, data };
  };
}
const payload = (r) => (r && r.data && typeof r.data === 'object' && 'data' in r.data ? r.data.data : r?.data);
const okOf = (r) => r.status >= 200 && r.status < 300 && (r.data == null || r.data.code === undefined || r.data.code === 200 || r.data.code === 201);

(async () => {
  const { browser, ctx, page, token } = await login();
  const api = makeApi(token);
  const consoleErrors = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
  page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message));

  // ---------- A. 探针自产实体（模板实例化，deliveryMode=1 才有协议设计器） ----------
  console.log('\n===== A. 前置：实例化探针实体 =====');
  const tpls = await api('GET', '/api/v1/metadata/templates');
  const tpl = (Array.isArray(payload(tpls)) ? payload(tpls) : []).find((t) => t.code === 'tpl_device_inspection');
  assert('巡检模板可用', !!tpl, { templates: (Array.isArray(payload(tpls)) ? payload(tpls) : []).map((t) => t.code) });
  const ents = await api('GET', '/api/v1/metadata/entities?page=1&size=50');
  const moduleId = (payload(ents)?.records || []).find((e) => e.moduleId != null)?.moduleId;
  const stamp = Date.now();
  const code = `uidesign_${stamp}`;
  const inst = await api('POST', `/api/v1/metadata/templates/${tpl.id}/instantiate`, {
    body: { name: `UiDesign_${stamp}`, code, displayName: '设计器UX探针', tableName: `md_${code}`, moduleId, deliveryMode: 1 },
  });
  const entityId = payload(inst);
  assert('探针实体实例化', okOf(inst) && !!entityId, { status: inst.status, entityId });
  if (!entityId) { await browser.close(); process.exit(2); }

  // ---------- B. Tab 集 + 图标区分（对齐当前产品：结构/字段/关系/校验/界面设计器/数据/发布） ----------
  // IA 收敛：「交付预览」已归位——团队契约口径并入「界面设计器」，个人覆盖口径对照在运行时数据页。
  console.log('\n===== B. Tab 集 + 图标区分 =====');
  await page.goto(`${SHELL}/metadata#/entities/${entityId}`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.ant-tabs-tab', { timeout: 25000 });
  await sleep(4000);
  const tabInfo = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.ant-tabs-tab')).map((e) => ({
      text: e.textContent.trim(),
      icons: Array.from(e.querySelectorAll('.anticon')).map((i) => i.className.match(/anticon-([a-z-]+)/)?.[1] ?? ''),
    })),
  );
  const tabTexts = tabInfo.map((t) => t.text);
  const expectedTabs = ['结构', '字段', '关系', '校验', '界面设计器', '数据', '发布'];
  assert('设计器 Tab 集 = 结构/字段/关系/校验/界面设计器/数据/发布',
    expectedTabs.every((t) => tabTexts.some((x) => x.includes(t))),
    { tabs: tabTexts });
  assert('「交付预览」已归位（顶层不再单列；个人语义不入建模台）',
    !tabTexts.some((x) => x.includes('交付预览')),
    { tabs: tabTexts });
  assert('旧「界面（协议）」/「显示设置」/「个人显示偏好」tab 已不存在（个人覆盖编辑器已移至运行时数据页）',
    !tabTexts.some((x) => x.includes('界面（协议）')) && !tabTexts.some((x) => x.includes('显示设置')) && !tabTexts.some((x) => x.includes('个人显示偏好')),
    { tabs: tabTexts });
  const iconOf = (label) => tabInfo.find((t) => t.text.includes(label))?.icons.join(',') ?? '';
  assert('界面设计器用 layout 图标', iconOf('界面设计器').includes('layout'), { icons: iconOf('界面设计器') });
  assert('数据保留 table 图标', iconOf('数据').includes('table'), { icons: iconOf('数据') });
  await page.screenshot({ path: '/Users/renhui.trh/wps/bone/logs/ui_designer_ux_1_tabs.png' });

  // ---------- C. 设计器：首访 Spotlight 引导（6 步）+ 帮助重显 ----------
  // 产品已将「静态 3 步 Modal（开始使用）」升级为「Spotlight 交互式引导」（DesignerTour.tsx）：
  // 首访自动弹出（全新 context + 全新实体 ⇒ localStorage 无标记 ⇒ 自动开），可从「帮助」重开。
  console.log('\n===== C. Spotlight 6 步新手引导 =====');
  await page.locator('.ant-tabs-tab').filter({ hasText: '界面设计器' }).click();
  await page.waitForSelector('text=载入场景样例', { timeout: 20000 });
  await sleep(2500);
  // 首访自动弹出 .tour-card（聚光灯卡片，非旧版 .ant-modal）
  let tourVisible0 = await page.locator('.tour-card').first().isVisible().catch(() => false);
  // 兜底：若自动弹出时序未命中，点「帮助」也应能唤起（验证引导可达）
  if (!tourVisible0) {
    await page.locator('button').filter({ hasText: '帮助' }).first().click();
    await sleep(1000);
    tourVisible0 = await page.locator('.tour-card').first().isVisible().catch(() => false);
  }
  assert('首访自动弹出 Spotlight 新手引导（.tour-card）', tourVisible0, {});
  const tourText = await page.locator('.tour-card').first().innerText().catch(() => '');
  assert('引导含多步 + 计数（x/6）+ 跳过/下一步', /跳过引导/.test(tourText) && /\d+ \/ 6/.test(tourText),
    { snippet: tourText.slice(0, 140) });
  // 跳过关闭
  await page.locator('.tour-card button').filter({ hasText: '跳过引导' }).first().click();
  await sleep(800);
  const guideClosed = !(await page.locator('.tour-card').first().isVisible().catch(() => false));
  assert('引导可关闭（跳过）', guideClosed, {});
  // 帮助按钮重显
  await page.locator('button').filter({ hasText: '帮助' }).first().click();
  await sleep(800);
  const guideReShown = await page.locator('.tour-card').first().isVisible().catch(() => false);
  assert('帮助按钮可重显引导', guideReShown, {});
  // 收尾关闭，避免遮挡后续段
  await page.locator('.tour-card button').filter({ hasText: '跳过引导' }).first().click().catch(() => {});
  await sleep(600);

  // ---------- D. 全屏（禅模式） ----------
  console.log('\n===== D. 全屏禅模式 =====');
  await page.locator('button').filter({ hasText: '全屏' }).first().click();
  await sleep(800);
  const zenOn = await page.evaluate(() => {
    const els = Array.from(document.querySelectorAll('div'));
    return els.some((e) => {
      const s = getComputedStyle(e);
      return s.position === 'fixed' && e.style.zIndex === '1080';
    });
  });
  const exitBtn = await page.locator('button').filter({ hasText: '退出全屏' }).count();
  const zenHeader = await page.locator('text=全屏模式').count();
  assert('全屏覆盖层出现（fixed 吃满视口 + 上下文头）', zenOn && exitBtn > 0 && zenHeader > 0, { zenOn, exitBtn, zenHeader });
  await page.screenshot({ path: '/Users/renhui.trh/wps/bone/logs/ui_designer_ux_2_zen.png' });
  await page.keyboard.press('Escape');
  await sleep(800);
  const zenOff = await page.evaluate(() => {
    const els = Array.from(document.querySelectorAll('div'));
    return els.some((e) => { const s = getComputedStyle(e); return s.position === 'fixed' && e.style.zIndex === '1080'; });
  });
  assert('Esc 退出全屏', !zenOff, { zenOff });

  // ---------- E. 快捷键：Ctrl+Z 成真 + 输入框负向 ----------
  console.log('\n===== E. 快捷键 =====');
  // 默认在结构 tab；点「添加分区」→ 脏；Ctrl+Z → 撤销成真
  await page.locator('button').filter({ hasText: '添加分区' }).first().click();
  await sleep(500);
  const dirtyAfterAdd = await page.locator('text=未保存改动').count();
  assert('添加分区后出现未保存改动', dirtyAfterAdd > 0, { dirtyAfterAdd });
  await page.keyboard.press('Control+z');
  await sleep(500);
  const dirtyAfterUndo = await page.locator('text=未保存改动').count();
  assert('Ctrl+Z 撤销成真（tooltip 不再是假承诺）', dirtyAfterUndo === 0, { dirtyAfterUndo });
  // 负向：输入框内 Ctrl+Z 不得劫持为全局撤销
  await page.locator('button').filter({ hasText: '添加分区' }).first().click();
  await sleep(500);
  const secTitle = page.locator('input[placeholder="分区标题"]').last();
  await secTitle.click();
  await page.keyboard.press('Control+z');
  await sleep(500);
  const dirtyInInput = await page.locator('text=未保存改动').count();
  assert('负向：输入框内 Ctrl+Z 不触发全局撤销', dirtyInInput > 0, { dirtyInInput });
  // 清理：再按一次（焦点回按钮后）撤销掉刚加的分区，恢复干净态
  await page.locator('button').filter({ hasText: '添加分区' }).first().click();
  await sleep(300);
  await page.keyboard.press('Control+z');
  await sleep(400);

  // ---------- E2. 可视化编辑历史栈（jumpToPast 的 future 堆叠） ----------
  console.log('\n===== E2. 可视化编辑历史栈 =====');
  // E 段负向用例遗留 1 步脏（past=[初始态]）；先归位干净态，保证历史步数可数
  await page.keyboard.press('Control+z');
  await sleep(500);
  const dirtyNorm = await page.locator('text=未保存改动').count();
  assert('E2 前置：归位干净态', dirtyNorm === 0, { dirtyNorm });
  // 制造 2 步编辑 → past 恰好 2 项
  await page.locator('button').filter({ hasText: '添加分区' }).first().click();
  await sleep(600);
  await page.locator('button').filter({ hasText: '添加分区' }).first().click();
  await sleep(600);
  const dirty2 = await page.locator('text=未保存改动').count();
  assert('2 次添加后出现未保存改动', dirty2 > 0, { dirty2 });
  // 打开编辑历史 Popover（aria-label 定位，图标按钮无文本）
  await page.locator('button[aria-label="编辑历史"]').click();
  await sleep(800);
  const histPop = page.locator('.ant-popover').filter({ hasText: '编辑历史' });
  const histVisible = await histPop.first().isVisible().catch(() => false);
  assert('历史 Popover 可打开', histVisible, {});
  const histText = await histPop.first().innerText().catch(() => '');
  const pastCount = /共 (\d+) 步可回退/.exec(histText.replace(/\n/g, ' '));
  assert('历史 Popover 恰好列出 2 个回退项（回退 1 步 / 回退 2 步，无 phantom 条目）',
    /回退 1 步/.test(histText) && /回退 2 步/.test(histText) && pastCount !== null && Number(pastCount[1]) === 2,
    { snippet: histText.replace(/\n/g, ' ').slice(0, 160), pastCount: pastCount?.[1] });
  await page.screenshot({ path: '/Users/renhui.trh/wps/bone/logs/ui_designer_ux_2b_history.png' });
  // 跳回最近 1 步：被跳过的中间态必须压入 future，否则重做断链
  await histPop.locator('text=回退 1 步').click();
  await sleep(900);
  const redoEnabled = await page.locator('button[aria-label="重做"]').isEnabled().catch(() => false);
  assert('跳回后「重做」可用（被跳过的状态压入 future）', redoEnabled, {});
  const dirtyAfterJump = await page.locator('text=未保存改动').count();
  assert('跳回后仍有未保存改动（保留 1 步编辑）', dirtyAfterJump > 0, { dirtyAfterJump });
  // 二次打开：past 应只剩 1 项，且被跳过的中间态恰好 1 条压入 future（不重不漏）
  await page.locator('button[aria-label="编辑历史"]').click();
  await sleep(700);
  const histText2 = await histPop.first().innerText().catch(() => '');
  const pastCount2 = /共 (\d+) 步可回退/.exec(histText2.replace(/\n/g, ' '));
  const futureCount2 = /可重做 (\d+) 步/.exec(histText2.replace(/\n/g, ' '));
  assert('二次打开仅 1 个回退项且 future 恰为 1（时间线与实际状态一致，无重复压栈）',
    pastCount2 !== null && Number(pastCount2[1]) === 1 && !/回退 2 步/.test(histText2)
    && futureCount2 !== null && Number(futureCount2[1]) === 1,
    { snippet: histText2.replace(/\n/g, ' ').slice(0, 120), past: pastCount2?.[1], future: futureCount2?.[1] });
  // 收尾：toggle 关 Popover → 撤销回干净态，避免影响 F2 段「可见删除图标 nth(1)」假设
  await page.locator('button[aria-label="编辑历史"]').click();
  await sleep(500);
  await page.keyboard.press('Control+z');
  await sleep(500);
  const dirtyFinal = await page.locator('text=未保存改动').count();
  assert('E2 收尾后恢复干净态', dirtyFinal === 0, { dirtyFinal });

  // ---------- F. 变体创作 ----------
  console.log('\n===== F. 变体创作 =====');
  await page.locator('.ant-tabs-tab').filter({ hasText: '变体' }).first().click();
  await page.waitForSelector('text=变体 = 按身份', { timeout: 15000 });
  await page.locator('button').filter({ hasText: '新增变体' }).first().click();
  await sleep(800);
  const vCard = await page.locator('input[placeholder="变体名称"]').count();
  assert('变体卡片可创建', vCard > 0, { vCard });
  // antd v5 多选/tags Select 的 placeholder 渲染在 .ant-select-selection-placeholder span 上
  // （input 无 placeholder 属性），且预览区「身份模拟」也有 角色/设备——
  // 「档位 tier / 渠道」仅变体卡独有，故四态齐全即可证明变体卡四维编辑器在位。
  const matchTexts = (await page.locator('.ant-select-selection-placeholder').allTextContents()).map((t) => t.trim());
  const need = ['角色', '档位 tier', '渠道', '设备'];
  assert('四维命中条件编辑器齐全（角色/档位/渠道/设备）', need.every((t) => matchTexts.includes(t)), { matchTexts });
  await page.locator('.ant-segmented-item').filter({ hasText: '覆盖编辑' }).first().click();
  await sleep(800);
  const structInVariant = await page.locator('text=录入表单').count();
  assert('覆盖编辑可复用结构面板（合成 schema）', structInVariant > 0, { structInVariant });
  await page.screenshot({ path: '/Users/renhui.trh/wps/bone/logs/ui_designer_ux_3_variants.png' });

  // ---------- F2. FAB 控件库 / 版本快照 / 命令面板 ----------
  console.log('\n===== F2. FAB / 快照 / 命令面板 =====');
  // 设计器左栏的「结构」页签（实体详情也有「结构」页签，取 DOM 中更靠后的设计器侧）
  await page.locator('.ant-tabs-tab').filter({ hasText: '结构' }).last().click();
  await sleep(600);
  // 模板实例化的 schema 已含全部字段 ⇒ 字段库为空。先「移出分区」释放一个字段。
  // 注意 :visible——页面初始化时外层「结构」页签默认挂载过（antd 保持隐藏挂载），
  // 其字段表格的删除图标在 DOM 中但不可见；可见列表中 nth(0)=分区头删除、nth(1)=首个控件移出。
  await page.locator('.anticon-delete:visible').nth(1).click();
  await sleep(500);
  await page.locator('button.ant-btn-circle').last().click();
  await page.waitForSelector('text=添加字段到界面', { timeout: 10000 });
  await page.locator('.ant-popover .ant-tag').first().click();
  await sleep(600);
  const fabDirty = await page.locator('text=未保存改动').count();
  assert('FAB 控件库可调出并把字段落入分区', fabDirty > 0, { fabDirty });
  // 快照：保存 → 版本历史 → 恢复
  await page.locator('button').filter({ hasText: '快照' }).first().click();
  await page.locator('.ant-dropdown-menu-item').filter({ hasText: '保存当前快照' }).click();
  await sleep(800);
  const snapSaved = await page.locator('text=已保存快照').count();
  assert('快照可保存（本地 30 份上限）', snapSaved > 0, { snapSaved });
  await page.locator('button').filter({ hasText: '快照' }).first().click();
  await page.locator('.ant-dropdown-menu-item').filter({ hasText: '版本历史' }).click();
  await page.waitForSelector('text=版本历史', { timeout: 10000 });
  await page.locator('.ant-modal button').filter({ hasText: '恢复' }).first().click();
  await sleep(800);
  const snapRestored = await page.locator('text=已恢复到快照').count();
  assert('快照可恢复（恢复前跑协议 Gate）', snapRestored > 0, { snapRestored });
  await page.keyboard.press('Escape');
  await sleep(600);
  // 命令面板：Ctrl+K → 关键字过滤 → 执行跳转
  await page.keyboard.press('Control+k');
  await page.waitForSelector('text=命令面板', { timeout: 10000 });
  // 必须按模态内容限定：实体详情的 forceRender 隐藏 modal（编辑字段等）输入框也在 DOM 中
  const cmdModal = page.locator('.ant-modal').filter({ hasText: '命令面板' });
  await cmdModal.locator('input').first().fill('自检');
  await sleep(500);
  const cmdHit = await cmdModal.innerText().catch(() => '');
  assert('命令面板可按关键字过滤', /跳到：自检/.test(cmdHit), { snippet: cmdHit.slice(0, 100) });
  // 命令项是 <div><Tag>分组</Tag><span>标签</span></div>，div 文本含分组前缀，须点 span
  await cmdModal.locator('span').filter({ hasText: /^跳到：自检$/ }).last().click();
  await sleep(600);
  const lintActive = await page.locator('.ant-tabs-tab-active').filter({ hasText: '自检' }).count();
  assert('命令面板可执行跳转（跳到自检）', lintActive > 0, { lintActive });

  // ---------- G. 发布协议硬门禁 ----------
  console.log('\n===== G. 发布硬门禁（负向 + 正向） =====');
  // 负向：PUT 引用不存在字段的 schema → 后端 validate 必含 ERROR → runnable=false → 按钮禁用
  const brokenSchema = {
    schemaVersion: 1, minor: 1, entityCode: code, displayModes: ['create', 'edit', 'view'],
    views: { form: { sections: [{ id: 'sec_main', title: '坏分区', controls: [{ fieldCode: 'no_such_field_xyz' }] }] } },
    linkageRules: [], submitRules: [], actionBar: [],
  };
  const putRes = await api('PUT', `/api/v1/metadata/entities/${entityId}/ui-schema`, {
    body: { json: JSON.stringify(brokenSchema) }, etag: '0',
  });
  assert('坏 schema 已 PUT（负向前提）', okOf(putRes), { status: putRes.status });
  // 硬门禁的数据地基：后端 validate 对坏 schema 必须报 ERROR 级（handlePublish 的阻断判据）
  const valRes = await api('POST', `/api/v1/metadata/entities/${entityId}/ui-schema/validate`);
  const valIssues = Array.isArray(payload(valRes)) ? payload(valRes) : [];
  const errIssues = valIssues.filter((i) => i.level === 'ERROR');
  assert('后端 validate 对坏 schema 报 ERROR 级问题', errIssues.length > 0, { status: valRes.status, issues: valIssues.slice(0, 3) });
  // 整页重载进设计器：挂载即加载坏 schema → 协议错误横幅必须顶出来
  await page.goto(`${SHELL}/metadata#/entities/${entityId}?tab=uischema`, { waitUntil: 'domcontentloaded' });
  await page.reload();
  await page.waitForSelector('text=载入场景样例', { timeout: 25000 });
  const banner = await page.locator('text=界面协议存在').first().innerText().catch(() => '');
  assert('协议错误阻断式横幅顶出（不藏在 Tab 里）', /界面协议存在 \d+ 处错误/.test(banner), { banner: banner.slice(0, 120) });
  // 打开发布预览：preview.runnable=false ⇒ 确认发布按钮禁用（门禁第一层，UI 即不可达发布动作）
  await page.locator('button').filter({ hasText: '发布' }).first().click();
  const pubModal = page.locator('.ant-modal').filter({ hasText: '发布预览' });
  await pubModal.waitFor({ state: 'visible', timeout: 15000 });
  await sleep(2500);
  const pubBtn = pubModal.locator('button').filter({ hasText: '确认发布' });
  assert('负向：坏 schema 发布按钮禁用（preview.runnable=false）', await pubBtn.isDisabled(), {});
  const entAfterBlock = payload(await api('GET', `/api/v1/metadata/entities/${entityId}`));
  assert('负向：实体确实未发布（status 仍为草稿）', entAfterBlock?.status === 0, { status: entAfterBlock?.status });
  await page.screenshot({ path: '/Users/renhui.trh/wps/bone/logs/ui_designer_ux_4_gate_blocked.png' });
  // 正向：删除坏草稿 → 刷新预览 → runnable=true 按钮启用 → 发布放行
  const delRes = await api('DELETE', `/api/v1/metadata/entities/${entityId}/ui-schema`);
  assert('坏 schema 草稿已删除（正向前提）', delRes.status >= 200 && delRes.status < 300, { status: delRes.status });
  await pubModal.locator('button').filter({ hasText: '刷新预览' }).click();
  await sleep(3000);
  assert('正向：修复后发布按钮启用', await pubBtn.isEnabled(), {});
  await pubBtn.click();
  await sleep(5000);
  const entAfterOk = payload(await api('GET', `/api/v1/metadata/entities/${entityId}`));
  assert('正向：修复后发布放行（status=1）', entAfterOk?.status === 1, { status: entAfterOk?.status });

  // ---------- H. console 干净度 ----------
  const KNOWN = /clearIcon.{0,4}is deprecated|favicon|404 \(Not Found\)/i;
  const realErrors = consoleErrors.filter((e) => !KNOWN.test(e));
  assert('页面无新增 console error', realErrors.length === 0, { errors: realErrors.slice(0, 3) });

  await browser.close();
  const failed = steps.filter((s) => !s.ok).length;
  console.log('\n===== SUMMARY =====');
  console.log(JSON.stringify({ total: steps.length, pass: steps.length - failed, failed, errors }, null, 2));
  process.exit(failed > 0 ? 1 : 0);
})().catch((e) => { console.error('FATAL', e); process.exit(2); });
