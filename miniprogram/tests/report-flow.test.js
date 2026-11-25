/**
 * 报案→结果页数据流测试：验证后端回执的 reportNo（报案号 RP）真正传到结果页并展示。
 * 用真实 report.js / report-result.js 的 Page 定义驱动，mock 只在 wx 边界。
 * 运行：node miniprogram/tests/report-flow.test.js
 */
const assert = require('assert');
const path = require('path');

const MP = path.resolve(__dirname, '..');
let redirects = [];
let submitResp = { claimId: 1, claimNo: 'CL-TEST', reportNo: 'RP-TEST-0001', status: 'PENDING_EVIDENCE' };

global.wx = {
  getStorageSync: () => '',
  setStorageSync: () => {},
  removeStorageSync: () => {},
  showToast: () => {},
  navigateTo: (o) => redirects.push(o.url),
  reLaunch: (o) => redirects.push(o.url || o),
  redirectTo: (o) => redirects.push(o.url || o),
  request: (o) => {
    o.success({ statusCode: 201, data: { code: 201, message: '操作成功', data: submitResp } });
  },
};

const pageDefs = [];
global.Page = (def) => pageDefs.push(def);
require(path.join(MP, 'pages/report/report.js'));
require(path.join(MP, 'pages/report-result/report-result.js'));
const reportDef = pageDefs[0];
const resultDef = pageDefs[1];

function instance(def, overrides) {
  const data = Object.assign({}, def.data, overrides || {});
  return Object.assign(Object.create(def), {
    data,
    setData(patch, cb) {
      Object.assign(data, patch);
      if (typeof cb === 'function') cb.call(this);
    },
  });
}

(async () => {
  let pass = 0, fail = 0;
  const t = (n, f) => {
    try { f(); console.log('  PASS', n); pass++; }
    catch (e) { console.log('  FAIL', n, e.message); fail++; }
  };

  const page = instance(reportDef, {
    policyNo: 'E2E-POLICY-0001',
    planId: '9001',
    insuredIdHash: 'e2e-insured-hash-0001',
    outInsureTime: '2026-11-15',
    items: [{ key: 'a', itemName: '门诊诊查费', itemCode: '', categoryIndex: 0, amount: '710.15', quantity: '1', unitPrice: '710.15' }],
  });
  page.handleSubmit();
  // handleSubmit 内 api.submitClaim 是 Promise，flush 一个微任务队列
  await new Promise((r) => setImmediate(r));

  const url = redirects.find((u) => u && u.indexOf('report-result') >= 0) || '';
  t('handleSubmit 跳转到结果页', () => assert.ok(url, '未捕获到结果页跳转'));
  const params = {};
  String(url).split('?')[1].split('&').forEach((kv) => {
    const i = kv.indexOf('=');
    params[kv.slice(0, i)] = decodeURIComponent(kv.slice(i + 1));
  });
  t('跳转 URL 携带报案号 reportNo=RP-TEST-0001', () => assert.strictEqual(params.reportNo, 'RP-TEST-0001'));
  t('跳转 URL 仍携带赔案号 claimNo=CL-TEST', () => assert.strictEqual(params.claimNo, 'CL-TEST'));

  const resultPage = instance(resultDef);
  resultPage.onLoad(params);
  t('结果页 onLoad 接收并保存 reportNo', () => assert.strictEqual(resultPage.data.reportNo, 'RP-TEST-0001'));
  t('结果页 onLoad 状态为 success', () => assert.strictEqual(resultPage.data.status, 'success'));

  const wxml = require('fs').readFileSync(path.join(MP, 'pages/report-result/report-result.wxml'), 'utf8');
  t('结果页 WXML 渲染报案号字段', () => {
    assert.ok(wxml.indexOf('报案号') >= 0, '缺「报案号」标签');
    assert.ok(wxml.indexOf('{{reportNo}}') >= 0, '未绑定 {{reportNo}}');
  });

  console.log(`\nreport-flow 结果 ${pass}/${pass + fail}`);
  process.exit(fail ? 1 : 0);
})().catch((e) => { console.error('[FATAL]', e && e.stack || e); process.exit(1); });
