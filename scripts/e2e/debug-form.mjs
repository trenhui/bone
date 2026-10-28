/**
 * 调试探针：针对单个模块，打开新建弹窗，逐字段打印
 *   label / 控件类型 / 探针将填入的值，提交后打印 antd 校验错误与 toast。
 * 用法：node scripts/e2e/debug-form.mjs "租户管理"
 */
/* 共享 client：playwright 加载 / STAMP / API 登录与带 token fetch / 登录凭证（见 e2e_lib/client.cjs） */
import {
  UI_BASE as BASE,
  stamp,
  USER as E2E_USER,
  PWD as E2E_PWD,
  loadPlaywright,
  api,
  tryLogin,
} from './e2e_lib/client.cjs';
const { chromium } = loadPlaywright();

const STAMP = stamp(6);
const TARGET = process.argv[2] || '租户管理';

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.setDefaultTimeout(20000);

// —— 登录 ——
await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('input[type="password"]', { timeout: 20000 });
await page.locator('input[placeholder*="用户名"], input[name="username"], input#username').first().fill(E2E_USER);
const pwd = page.locator('input[type="password"]').first();
await pwd.fill(E2E_PWD);
await pwd.press('Enter');
await page.waitForTimeout(1500);
await page.waitForFunction(() => !document.querySelector('input[type="password"]'), { timeout: 30000 });

// —— 菜单真源 ——（登录与带 token fetch 见 e2e_lib/client.cjs；取不到 token 时保持原口径继续）
const token = await tryLogin();
const { data: mj } = token ? await api('GET', '/iam/menus/tree', undefined, { token }) : { data: null };
const out = [];
const walk = (ns, parent) => { for (const n of ns || []) { if (n.path && n.path.includes('#')) out.push({ name: `${parent ? parent + ' · ' : ''}${n.name}`, path: n.path }); walk(n.children || [], n.name); } };
walk(mj.data || [], '');

const hit = out.find((m) => m.name.includes(TARGET)) || out.find((m) => TARGET.split('·').pop().trim() && m.name.endsWith(TARGET.split('·').pop().trim()));
if (!hit) { console.log('未找到菜单匹配:', TARGET, '\n候选:', out.map((o) => o.name).join(' | ')); await browser.close(); process.exit(1); }
console.log('命中菜单:', hit.name, '->', hit.path);

await page.goto(`${BASE}${hit.path}`, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(2500);
// 业务数据需先选实体
if (/业务数据|runtime/i.test(hit.name)) {
  const entSel = page.locator('#subapp-viewport .ant-select').first();
  if (await entSel.count()) { await entSel.click({ timeout: 3000 }).catch(() => {}); await page.waitForTimeout(500);
    const opt = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').first();
    if (await opt.count()) { await opt.click({ timeout: 3000 }).catch(() => {}); await page.waitForTimeout(1500); } }
}
const btn = page.locator('#subapp-viewport button:visible').filter({ hasText: /^(新建|新增|创建|添加)/ }).first();
if (!(await btn.count())) { console.log('无新建按钮'); await browser.close(); process.exit(1); }
await btn.click();
await page.waitForTimeout(1500);
const modal = page.locator('.ant-modal:visible, .ant-drawer:visible').first();
if (!(await modal.count())) { console.log('未出现弹窗'); await browser.close(); process.exit(1); }

// —— 逐字段 dump ——
const items = await modal.locator('.ant-form-item').all();
console.log(`\n=== 表单字段 (${items.length}) ===`);
for (const item of items) {
  if (!(await item.isVisible().catch(() => false))) continue;
  const label = (await item.locator('label').first().innerText().catch(() => '')).replace(/[:：*\s]/g, '');
  const ph = (await item.locator('input,textarea').first().getAttribute('placeholder').catch(() => '')) || '';
  const sem = (label + ' ' + ph).toLowerCase();
  let ctype = 'unknown';
  if (await item.locator('.ant-tree-select').count()) ctype = 'TreeSelect';
  else if (await item.locator('.ant-select').count()) ctype = 'Select/AutoComplete';
  else if (await item.locator('.ant-input-number').count()) ctype = 'InputNumber';
  else if (await item.locator('.ant-picker').count()) ctype = 'DatePicker';
  else if (await item.locator('textarea').count()) ctype = 'TextArea';
  else if (await item.locator('input:not([type=hidden])').count()) ctype = 'Input';
  let willFill = '';
  if (/编码|code|标识/.test(sem)) willFill = `e2e${STAMP} (code)`;
  else if (/cron|调度|表达式|规则/.test(sem)) willFill = '0 0 12 * * ? / status=active';
  else if (/json|schema|字段集|data|映射|配置|条件/.test(sem)) willFill = '{...}';
  else if (/邮箱|mail/.test(sem)) willFill = `e2e_${STAMP}@bone.local`;
  else if (/手机|电话/.test(sem)) willFill = '13800000001';
  else if (/名称|name|角色|username/.test(sem)) willFill = `E2E${STAMP}`;
  else if (/描述|remark|note/.test(sem)) willFill = `E2E ${STAMP}`;
  else if (/(^|[^a-z])id$|ID$|客户|买家|用户|商品|上级|父级|归属|关联|租户|组织|账号|订单/.test(sem)) willFill = '760..(snowflake)';
  else willFill = `E2E${STAMP}`;
  console.log(`  [${ctype}] "${label}" ph="${ph}" => ${willFill}`);
}

// —— 实际填充（复刻探针逻辑）并回读确认 ——
const closeDropdown = async () => modal.locator('.ant-modal-title, .ant-drawer-title, .ant-modal-body').first().click({ timeout: 2000 }).catch(() => {});
const items2 = await modal.locator('.ant-form-item').all();
for (const item of items2) {
  if (!(await item.isVisible().catch(() => false))) continue;
  const label = (await item.locator('label').first().innerText().catch(() => '')).replace(/[:：*\s]/g, '');
  const ph = (await item.locator('input,textarea').first().getAttribute('placeholder').catch(() => '')) || '';
  const sem = (label + ' ' + ph).toLowerCase();
  try {
    const treeSel = item.locator('.ant-tree-select').first();
    const sel = item.locator('.ant-select').first();
    const numWrap = item.locator('.ant-input-number').first();
    const picker = item.locator('.ant-picker').first();
    const ta = item.locator('textarea').first();
    if (await treeSel.count()) { await treeSel.click({timeout:3000}); await page.waitForTimeout(300); const n=page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-tree-node-content-wrapper, .ant-select-tree-node-content-wrapper').first(); if(await n.count()) await n.click({timeout:3000}); else await closeDropdown(); }
    else if (await sel.count()) { if(/编码|code|标识/.test(sem)){const i=sel.locator('input').first(); await i.click({timeout:3000}).catch(()=>{}); await i.fill(`e2e${STAMP}`); await page.keyboard.press('Enter').catch(()=>{}); await page.keyboard.press('Escape').catch(()=>{});} else {await sel.click({timeout:3000}); await page.waitForTimeout(300); const o=page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').first(); if(await o.count()) await o.click({timeout:3000}); else await closeDropdown();} }
    else if (await numWrap.count()) { await numWrap.locator('input').first().fill('1'); }
    else if (await picker.count()) { await picker.click({timeout:2000}).catch(()=>{}); await page.waitForTimeout(300); await page.keyboard.press('Enter').catch(()=>{}); await page.keyboard.press('Escape').catch(()=>{}); }
    else if (await ta.count()) { await ta.fill(/json|schema|data|表达式|规则|映射|配置|条件/.test(sem)?'{"key":"value"}':'E2E '+STAMP); }
    else { const c=item.locator('input:not([type=hidden])').first(); let v=`E2E${STAMP}`; if(/(^|[^a-z])id$|ID$|客户|买家|用户|商品|上级|父级|归属|关联|租户|组织|账号|订单/.test(sem)) v=String(760000000000000000n+BigInt(Math.floor(Math.random()*1e9))); else if(/密码/.test(sem)) v='E2e@12345'; else if(/邮箱|mail/.test(sem)) v=`e2e_${STAMP}@bone.local`; else if(/手机|电话/.test(sem)) v='13800000001'; else if(/编码|code|标识/.test(sem)) v=`e2e${STAMP}`; else if(/cron|调度|表达式|规则/.test(sem)) v='0 0 12 * * ?'; else if(/名称|name|角色|username/.test(sem)) v=`E2E${STAMP}`; else if(/描述|remark|note/.test(sem)) v=`E2E ${STAMP}`; await c.fill(v); }
  } catch (e) { console.log('  fill err', label, String(e.message).slice(0,40)); }
}
await page.waitForTimeout(800);
// 回读实际值
console.log('\n=== 填充后回读 ===');
for (const item of items2) {
  if (!(await item.isVisible().catch(() => false))) continue;
  const label = (await item.locator('label').first().innerText().catch(() => '')).replace(/[:：*\s]/g, '');
  const val = await item.locator('input:not([type=hidden]),textarea').first().inputValue().catch(() => '');
  console.log(`  "${label}" => "${val.slice(0,30)}"`);
}

// —— 提交看校验 ——
const submit = modal.locator('button:visible').filter({ hasText: /^(确\s*定|确\s*认|保\s*存|提\s*交|创\s*建|新\s*增|OK|Save)/ }).first();
await submit.click();
await page.waitForTimeout(2500);
const errs = await modal.locator('.ant-form-item-explain-error, .ant-form-item .ant-form-item-explain-error').allInnerTexts().catch(() => []);
const toast = await page.locator('.ant-message, .ant-notification').innerText().catch(() => '');
console.log('\n=== 提交后 ===');
console.log('校验错误:', JSON.stringify(errs));
console.log('toast:', toast.replace(/\s+/g, ' ').slice(0, 200));
await browser.close();
