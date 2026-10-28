/**
 * 专项探针：已发布实体「加字段 → 再发布」后新字段能否写入（RC-002 热生效 / 预留列机制）。
 * 区分两种可能：(a) 发布后缓存未失效导致快照过时；(b) 预留列写路径本身断裂。
 */
/* 共享 client：playwright 加载 / CHROME / 登录凭证（见 e2e_lib/client.cjs） */
const { loadPlaywright, CHROME, USER, PWD } = require('./e2e_lib/client.cjs');
const { chromium } = loadPlaywright();
const SHELL = 'http://localhost:3000';
const GW = 'http://localhost:8888';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const b = await chromium.launch({ headless: true, executablePath: CHROME });
  const ctx = await b.newContext();
  const p = await ctx.newPage();
  await p.goto(`${SHELL}/iam#/accounts`, { waitUntil: 'domcontentloaded' });
  await sleep(3500);
  const u = p.locator('input[placeholder="用户名"], input[placeholder*="用户"], input[placeholder*="账号"]').first();
  if ((await u.count()) && (await u.isVisible().catch(() => false))) {
    await u.fill(USER);
    await p.locator('input[type="password"], input[placeholder="密码"]').first().fill(PWD);
    await p.locator('button[type="submit"], button:has-text("登")').first().click();
    await sleep(4000);
  }
  const t = await p.evaluate(() => localStorage.getItem('token'));
  if (!t) throw new Error('no token');

  const out = await p.evaluate(async ({ t, GW }) => {
    const r = { log: [] };
    const api = async (m, path, body) => {
      const res = await fetch(GW + path, {
        method: m, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${t}` },
        body: body ? JSON.stringify(body) : undefined,
      });
      const j = await res.json().catch(() => null);
      return { status: res.status, data: j?.data, raw: j };
    };
    const code = `hot_${Date.now()}`;
    const ents = await api('GET', '/api/v1/metadata/entities?page=1&size=20');
    const moduleId = (ents.data?.records || []).find((e) => e.moduleId != null)?.moduleId;
    await api('POST', '/api/v1/metadata/entities', {
      name: `Hot_${code}`, code, displayName: '热生效探针', tableName: `md_${code}`, moduleId, deliveryMode: 1,
    });
    const eid = (await api('GET', `/api/v1/metadata/entities?page=1&size=50&keyword=${code}`)).data?.records?.find((e) => e.code === code)?.id;
    await api('POST', `/api/v1/metadata/entities/${eid}/fields`, { name: 'title', code: 'title', displayName: '标题', type: 'STRING', sortOrder: 1 });
    await api('POST', `/api/v1/metadata/entities/${eid}/publish`);
    r.log.push({ step: 'publish#1', ...(await api('POST', `/api/v1/runtime/entities/${code}/records`, { title: 'A' })).status === 201 ? { ok: true } : { ok: false } });

    // 加第二个字段后再发布
    await api('POST', `/api/v1/metadata/entities/${eid}/fields`, { name: 'score', code: 'score', displayName: '分数', type: 'INTEGER', sortOrder: 2 });
    const pub2 = await api('POST', `/api/v1/metadata/entities/${eid}/publish`);
    const flds = await api('GET', `/api/v1/metadata/entities/${eid}/fields?page=1&size=50`);
    const score = (flds.data?.records || []).find((f) => f.code === 'score');
    r.publish2 = pub2.status;
    r.scoreField = { code: score?.code, physicalColumn: score?.physicalColumn };

    // 立即写（缓存应已失效）
    await new Promise((res) => setTimeout(res, 1500));
    const w1 = await api('POST', `/api/v1/runtime/entities/${code}/records`, { title: 'B', score: 88 });
    const g1 = await api('GET', `/api/v1/runtime/entities/${code}/records/${w1.data?.id}`);
    r.immediateWrite = { postStatus: w1.status, readScore: g1.data?.score ?? null };

    // 等 TTL(60s) 之后再写一次
    await new Promise((res) => setTimeout(res, 62000));
    const w2 = await api('POST', `/api/v1/runtime/entities/${code}/records`, { title: 'C', score: 99 });
    const g2 = await api('GET', `/api/v1/runtime/entities/${code}/records/${w2.data?.id}`);
    r.afterTtlWrite = { postStatus: w2.status, readScore: g2.data?.score ?? null };

    // 第三次：验证是否稳定
    const w3 = await api('POST', `/api/v1/runtime/entities/${code}/records`, { title: 'D', score: 55 });
    const g3 = await api('GET', `/api/v1/runtime/entities/${code}/records/${w3.data?.id}`);
    r.afterTtlWrite2 = { postStatus: w3.status, readScore: g3.data?.score ?? null };
    r.entityCode = code;
    return r;
  }, { t, GW });

  console.log(JSON.stringify(out, null, 2));
  await b.close();
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
