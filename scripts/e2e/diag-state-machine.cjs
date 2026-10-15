/**
 * 快速实证：0040 状态机语义（针对已发布的 insp_1792042722480）
 *   1. POST 不带 status → 是否自动注入入口态（OPEN）
 *   2. POST 带 status → 是否 422 META_RUNTIME_FIELD_PROTECTED（负向）
 *   3. POST /records/{id}/actions/close → 状态是否推进到 CLOSED
 *   4. GET /api/v1/iam/menus/tree → 侧栏菜单现状（实体/关系管理）
 */
const { chromium } = require('playwright');
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const SHELL = 'http://localhost:3000';
const GATEWAY = 'http://localhost:8888';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const ctx = await browser.newContext();
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
  if (!token) { await browser.close(); throw new Error('login failed'); }
  const api = async (method, path, { body, etag } = {}) => {
    const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
    if (etag) headers['If-Match'] = etag;
    const res = await fetch(GATEWAY + path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    return { status: res.status, data, etag: res.headers.get('etag') || undefined };
  };
  const EC = 'insp_1792042722480';

  // 1. 不带 status 插入
  const r1 = await api('POST', `/api/v1/runtime/entities/${EC}/records`, {
    body: { insp_no: `VF-${Date.now()}`, device_code: 'DEV-VF', inspector: '实证', insp_date: '2026-10-15', result: 'NORMAL', remark: '状态机语义实证' },
  });
  console.log('1) 不带 status 插入:', r1.status, 'status=', r1.data?.data?.status, 'id=', r1.data?.data?.id, 'err=', r1.data?.data?.errorCode ?? '');

  // 2. 负向：带 status 插入
  const r2 = await api('POST', `/api/v1/runtime/entities/${EC}/records`, {
    body: { insp_no: `VF-${Date.now()}`, device_code: 'DEV-VF', inspector: '实证', insp_date: '2026-10-15', result: 'NORMAL', status: 'CLOSED' },
  });
  console.log('2) 负向 带 status 插入:', r2.status, 'err=', r2.data?.data?.errorCode ?? '', 'msg=', (r2.data?.data?.message ?? '').slice(0, 80));

  // 3. 状态机动作 close
  if (r1.data?.data?.id) {
    const r3 = await api('POST', `/api/v1/runtime/entities/${EC}/records/${r1.data.data.id}/actions/close`, { body: { params: {} } });
    console.log('3) 动作 close:', r3.status, 'status=', r3.data?.data?.status, 'err=', r3.data?.data?.errorCode ?? '');
    const r3b = await api('GET', `/api/v1/runtime/entities/${EC}/records/${r1.data.data.id}`);
    console.log('   回读 status=', r3b.data?.data?.status, 'etag=', !!r3b.etag);
  }

  // 4. 菜单树
  const m = await api('GET', '/api/v1/iam/menus/tree');
  const walk = (nodes, depth = 0, out = []) => {
    for (const n of nodes || []) { out.push(`${'  '.repeat(depth)}${n.name ?? n.title ?? '?'} [${n.path ?? ''}]`); walk(n.children, depth + 1, out); }
    return out;
  };
  const tree = m.data?.data ?? m.data;
  console.log('4) 菜单树:');
  console.log(walk(Array.isArray(tree) ? tree : tree?.children ?? []).join('\n'));
  await browser.close();
})().catch((e) => { console.error('FATAL', e); process.exit(2); });
