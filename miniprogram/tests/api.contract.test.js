/** API 层契约单测（mock wx.request：node tests/api.contract.test.js）。 */
const assert = require('assert');
let captured = [], storage = {}, nav = [];
global.wx = {
  request(o) {
    captured.push(o);
    const isLogin = o.url.indexOf('/iam/login') >= 0;
    const data = isLogin
      ? { token: 'JWT.TOKEN.XYZ', refreshToken: 'R', account: { username: 'admin', realName: '管理员', tenantName: '平台' } }
      : { claimId: '1', claimNo: 'CL-TEST' };
    o.success({ statusCode: 200, data: { success: true, code: 200, data } });
  },
  getStorageSync(k) { return storage[k] || ''; },
  setStorageSync(k, v) { storage[k] = v; },
  removeStorageSync(k) { delete storage[k]; },
  showToast() {}, navigateTo(o) { nav.push(o.url); },
};
const api = require('../utils/api.js');
(async () => {
  let pass = 0, fail = 0;
  const t = (n, f) => { try { f(); console.log('  PASS', n); pass++; } catch (e) { console.log('  FAIL', n, e.message); fail++; } };

  const loginData = await api.login('admin', '123456');
  t('登录 URL 正确', () => assert.strictEqual(captured[0].url, 'http://127.0.0.1:8888/api/v1/iam/login'));
  t('登录 POST 且无 auth 头', () => { assert.strictEqual(captured[0].method, 'POST'); assert.ok(!captured[0].header.Authorization); });
  t('登录写入 token 与账号', () => { assert.strictEqual(storage.bone_claim_token, 'JWT.TOKEN.XYZ'); assert.strictEqual(api.account().tenantName, '平台'); });
  t('登录解包 data', () => assert.strictEqual(loginData.token, 'JWT.TOKEN.XYZ'));

  const submitData = await api.submitClaim({ planId: 9001 });
  const r = captured[1];
  t('报案 URL 正确', () => assert.strictEqual(r.url, 'http://127.0.0.1:8888/api/v1/claims'));
  t('报案带 JWT', () => assert.strictEqual(r.header.Authorization, 'Bearer JWT.TOKEN.XYZ'));
  t('报案带 UUID v4 幂等键', () => assert.match(r.header['Idempotency-Key'], /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/));
  t('报案解包 data', () => assert.strictEqual(submitData.claimNo, 'CL-TEST'));

  // 真实报案创建返回 HTTP 201 / code=201：必须判成功（假红回归钉子）
  const origRequest = global.wx.request;
  global.wx.request = (o) => {
    o.success({ statusCode: 201, data: { code: 201, message: '操作成功', data: { claimNo: 'CL-201', claimId: 2 } } });
  };
  let created;
  try { created = await api.submitClaim({ planId: 9001 }); } catch (e) { created = { error: e }; }
  t('HTTP201/code201 报案判成功（非假红）', () => assert.strictEqual(created && created.claimNo, 'CL-201'));
  global.wx.request = origRequest;

  captured = []; nav = [];
  global.wx.request = (o) => { o.success({ statusCode: 401, data: { code: 401, message: 'unauthorized' } }); };
  let rejected = false;
  try { await api.request({ path: '/claims' }); } catch (e) { rejected = true; }
  t('401 清会话+跳登录+reject', () => { assert.ok(rejected); assert.ok(nav.some((u) => u.includes('/pages/login/login'))); assert.ok(!storage.bone_claim_token); });

  console.log(`\napi 契约结果 ${pass}/${pass + fail}`);
  process.exit(fail ? 1 : 0);
})();
