// 元数据最小闭环联调：Playwright 真实 UI 登录（取 JWT）→ 在 metadata-app 源下用 fetch 直连网关
// 跑完整闭环（建 RUNTIME 实体→加字段→发布→录记录→ETag→If-Match→412→T4 模板实例化）。
// 规避 qiankun 深层 UI 点击不稳定，同时保证登录态与前端同源。
// 用法：
//   NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-6/bin/node
//   export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
//   $NODE scripts/e2e/probe-metadata-loop-api.cjs > /tmp/md-api.json
const { chromium } = require('playwright');
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const SHELL = 'http://localhost:3000';
const META = 'http://localhost:3004';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 900 } });
  const page = await ctx.newPage();
  // 真实 UI 登录
  await page.goto(`${SHELL}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await sleep(3500);
  const u = page.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if (await u.count() && await u.isVisible().catch(() => false)) {
    await u.fill('admin');
    await page.locator('input[type="password"], input[placeholder="密码"]').first().fill('123456');
    await page.locator('button[type="submit"], button:has-text("登")').first().click();
    await sleep(4000);
  }
  const token = await page.evaluate(() => localStorage.getItem('token'));
  if (!token) { console.log(JSON.stringify({ fatal: 'no token after login' })); await browser.close(); process.exit(1); }

  // 在 metadata-app 源注入 token，确保同源 fetch /api 走代理→网关
  const metaPage = await ctx.newPage();
  await metaPage.goto(`${META}/metadata#/entities`, { waitUntil: 'domcontentloaded' });
  await sleep(1500);
  await metaPage.evaluate((t) => {
    localStorage.setItem('token', t);
    window.__BONE_TOKEN__ = t;
  }, token);

  const result = await metaPage.evaluate(async (t) => {
    const out = { steps: [], errors: [] };
    const log = (name, ok, info) => out.steps.push({ name, ok, ...(info || {}) });
    async function api(method, path, { body, etag, expectStatus } = {}) {
      const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${t}` };
      if (etag) headers['If-Match'] = etag;
      const res = await fetch(path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
      const text = await res.text();
      let data = null; try { data = text ? JSON.parse(text) : null; } catch { data = text; }
      const etagOut = res.headers.get('etag') || undefined;
      return { status: res.status, data, etag: etagOut, ok: expectStatus ? res.status === expectStatus : res.status >= 200 && res.status < 300 };
    }
    try {
      const list = await api('GET', '/api/v1/metadata/entities?size=50');
      const recs = list.data?.data?.records || [];
      const moduleId = (recs.find((e) => e.moduleId != null) || recs[0])?.moduleId;
      log('获取 moduleId', moduleId != null, { moduleId, entityCount: recs.length });
      if (moduleId == null) out.errors.push('无可用 moduleId');

      const code = `e2eloop${Date.now()}`;
      const name = `E2E闭环${Date.now()}`;
      const create = await api('POST', '/api/v1/metadata/entities', { body: { name, code, displayName: name, tableName: `md_${code}`, moduleId, deliveryMode: 1 } });
      const entityId = create.data?.data ?? create.data;
      log('创建 RUNTIME 实体', create.ok && entityId, { status: create.status, entityId, code });
      if (!create.ok || !entityId) out.errors.push('创建实体失败: ' + JSON.stringify(create.data).slice(0, 200));

      const field = await api('POST', `/api/v1/metadata/entities/${entityId}/fields`, { body: { name: 'fullName', code: 'full_name', displayName: '姓名', type: 'STRING', required: false, sortOrder: 1 } });
      log('加字段(STRING)', field.ok, { status: field.status, detail: JSON.stringify(field.data?.data ?? field.data).slice(0, 160) });
      if (!field.ok) out.errors.push('加字段失败: ' + JSON.stringify(field.data).slice(0, 200));

      const pub = await api('POST', `/api/v1/metadata/entities/${entityId}/publish`);
      log('发布实体', pub.ok, { status: pub.status, detail: JSON.stringify(pub.data?.data ?? pub.data).slice(0, 160) });
      if (!pub.ok) out.errors.push('发布失败: ' + JSON.stringify(pub.data).slice(0, 200));

      const rec = await api('POST', `/api/v1/runtime/entities/${code}/records`, { body: { full_name: '张三' } });
      const recordId = rec.data?.data?.id ?? rec.data?.id;
      log('录首条记录', rec.ok && recordId, { status: rec.status, recordId, detail: JSON.stringify(rec.data?.data ?? rec.data).slice(0, 160) });
      if (!rec.ok) out.errors.push('录记录失败: ' + JSON.stringify(rec.data).slice(0, 200));

      const got = await api('GET', `/api/v1/runtime/entities/${code}/records/${recordId}`);
      log('读取记录带回 ETag', got.ok && !!got.etag, { status: got.status, etag: got.etag });

      const upd = await api('PUT', `/api/v1/runtime/entities/${code}/records/${recordId}`, { body: { full_name: '李四' }, etag: got.etag });
      log('If-Match 更新(200)', upd.status === 200, { status: upd.status, detail: JSON.stringify(upd.data?.data ?? upd.data).slice(0, 160) });

      const stale = await api('PUT', `/api/v1/runtime/entities/${code}/records/${recordId}`, { body: { full_name: '王五' }, etag: got.etag, expectStatus: 412 });
      log('陈旧 ETag → 412 并发保护', stale.status === 412, { status: stale.status, detail: JSON.stringify(stale.data?.data ?? stale.data).slice(0, 160) });

      // T3 直接验证：发布后「继续加字段」加 ENUM 字段（带 enumValues），重新发布对齐物理表，再录枚举记录
      const enumValues = JSON.stringify([{ value: 'L1', label: '低' }, { value: 'L2', label: '中' }, { value: 'L3', label: '高' }]);
      const eField = await api('POST', `/api/v1/metadata/entities/${entityId}/fields`, { body: { name: 'level', code: 'level', displayName: '等级', type: 'ENUM', required: false, sortOrder: 2, enumValues } });
      const eFieldId = eField.data?.data ?? eField.data;
      log('加字段(ENUM)', eField.ok, { status: eField.status, eFieldId });
      if (eField.ok && eFieldId) {
        const flds = await api('GET', `/api/v1/metadata/entities/${entityId}/fields?size=100`);
        const arr = flds.data?.data?.records ?? flds.data?.records ?? [];
        const ef = arr.find((f) => String(f.id) === String(eFieldId));
        const persisted = ef && ef.enumValues != null && ef.enumValues !== '';
        log('ENUM enumValues 持久化(T3)', !!persisted, { enumValues: ef?.enumValues });
        if (!persisted) out.errors.push('ENUM enumValues 未持久化');
      }
      const pub2 = await api('POST', `/api/v1/metadata/entities/${entityId}/publish`);
      log('加字段后重新发布', pub2.ok, { status: pub2.status });
      const eRec = await api('POST', `/api/v1/runtime/entities/${code}/records`, { body: { full_name: '王二', level: 'L2' } });
      const eRecId = eRec.data?.data?.id ?? eRec.data?.id;
      log('ENUM 记录录入(重新发布后)', eRec.ok && eRecId, { status: eRec.status, level: eRec.data?.data?.level, detail: JSON.stringify(eRec.data?.data ?? eRec.data).slice(0, 140) });
      if (!eRec.ok) out.errors.push('ENUM 记录录入失败: ' + JSON.stringify(eRec.data).slice(0, 200));

      const tpls = await api('GET', '/api/v1/metadata/templates');
      const tplList = tpls.data?.data ?? tpls.data ?? [];
      if (Array.isArray(tplList) && tplList.length) {
        const tpl = tplList[0];
        const inst = await api('POST', `/api/v1/metadata/templates/${tpl.id}/instantiate`, { body: { name: `${tpl.name}-e2e`, code: `tpl_e2e${Date.now()}`, displayName: `${tpl.name} E2E`, tableName: `md_tpl_e2e${Date.now()}`, moduleId, deliveryMode: 1 } });
        const tplEntityId = inst.data?.data ?? inst.data;
        if (inst.ok && tplEntityId) {
          const flds = await api('GET', `/api/v1/metadata/entities/${tplEntityId}/fields?size=100`);
          const fieldsArr = flds.data?.data?.records ?? flds.data?.records ?? [];
          log('T4 模板实例化', true, { tpl: tpl.code, tplEntityId, fieldCount: fieldsArr.length });
        } else {
          log('T4 模板实例化', false, { detail: JSON.stringify(inst.data).slice(0, 160) });
        }
      } else {
        log('T4 模板实例化', false, { detail: '无平台模板（0029 未落库）— 跳过' });
      }
    } catch (e) { out.errors.push(String(e && e.message || e)); }
    const failed = out.steps.filter((s) => !s.ok).length;
    out.summary = { total: out.steps.length, failed, pass: out.steps.length - failed };
    return out;
  }, token);

  await browser.close();
  console.log(JSON.stringify(result, null, 1));
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
