/** 报案表单校验单测（Node 直跑：node tests/validate.test.js）。 */
const assert = require('assert');
const v = require('../utils/validate.js');
let pass = 0, fail = 0;
function t(name, fn) {
  try { fn(); console.log('  PASS', name); pass++; }
  catch (e) { console.log('  FAIL', name, e.message); fail++; }
}
const BASE = { policyNo: 'E2E-POLICY-0001', insurerPolicyNo: '', planId: '9001', insuredIdHash: 'h1', outInsureTime: '2026-11-24', items: [{ itemName: '手术费', amount: '120.50' }] };

t('空表单判无效且给出汇总', () => {
  const r = v.validateReport({ items: [] });
  assert.strictEqual(r.valid, false);
  assert.ok(r.summary.length > 0);
});
t('合法表单（平台保单号）判有效', () => assert.strictEqual(v.validateReport(BASE).valid, true));
t('合法表单（仅保司保单号）判有效', () => {
  const f = Object.assign({}, BASE, { policyNo: '', insurerPolicyNo: 'INS-123', items: [{ itemName: '检查费', amount: '0' }] });
  assert.strictEqual(v.validateReport(f).valid, true);
});
t('两个保单号都空判无效', () => {
  const f = Object.assign({}, BASE, { policyNo: '', insurerPolicyNo: '' });
  const r = v.validateReport(f);
  assert.strictEqual(r.valid, false);
  assert.ok(r.fieldErrors.policyNo.includes('至少填一个'));
});
t('planId 非数字判无效', () => {
  const r = v.validateReport(Object.assign({}, BASE, { planId: 'abc' }));
  assert.strictEqual(r.valid, false); assert.ok(r.fieldErrors.planId);
});
t('金额为负或三位小数判无效', () => {
  assert.strictEqual(v.validateReport(Object.assign({}, BASE, { items: [{ itemName: 'x', amount: '-1' }] })).valid, false);
  assert.strictEqual(v.validateReport(Object.assign({}, BASE, { items: [{ itemName: 'x', amount: '1.234' }] })).valid, false);
});
t('明细项名为空判无效', () => {
  const r = v.validateReport(Object.assign({}, BASE, { items: [{ itemName: '', amount: '1' }] }));
  assert.strictEqual(r.valid, false); assert.ok(r.itemsErrors[0].itemName);
});
t('出险日期格式错误判无效', () => {
  const r = v.validateReport(Object.assign({}, BASE, { outInsureTime: '2026/11/24' }));
  assert.strictEqual(r.valid, false); assert.ok(r.fieldErrors.outInsureTime);
});
t('toInstant 合成 Z 结尾', () => assert.strictEqual(v.toInstant('2026-11-24'), '2026-11-24T00:00:00Z'));

console.log(`\nvalidate 结果 ${pass}/${pass + fail}`);
process.exit(fail ? 1 : 0);
