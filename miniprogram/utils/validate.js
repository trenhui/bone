/**
 * 报案表单校验（与后端 SubmitClaimReq 约束对齐，前端只做「必填/格式」层，业务真源在服务端）。
 * 后端必填：planId(Long)、insuredIdHash、outInsureTime(Instant)、items(>=1，每项 amount>=0)。
 * policyNo 与 insurerPolicyNo 至少填一个。
 */
function isBlank(v) {
  return v === null || v === undefined || String(v).trim() === '';
}

/** 校验日期 YYYY-MM-DD（出险时间由日期 + 当日 00:00:00Z 合成 Instant）。 */
function isDate(v) {
  return /^\d{4}-\d{2}-\d{2}$/.test(String(v || '')) && !isNaN(Date.parse(String(v) + 'T00:00:00Z'));
}

/** 校验金额：数字字符串、>=0、最多两位小数。 */
function isAmount(v) {
  var s = String(v == null ? '' : v).trim();
  if (s === '') return false;
  return /^\d+(\.\d{1,2})?$/.test(s);
}

/**
 * 校验整个表单。返回 { valid, summary, fieldErrors, itemsErrors }
 * itemsErrors 是与 items 同长度的数组，每项 { itemName, amount }。
 */
function validateReport(form) {
  var f = form || {};
  var fieldErrors = {
    policyNo: '',
    insurerPolicyNo: '',
    planId: '',
    insuredIdHash: '',
    outInsureTime: '',
  };
  if (isBlank(f.policyNo) && isBlank(f.insurerPolicyNo)) {
    fieldErrors.policyNo = '保单号与保司保单号至少填一个';
    fieldErrors.insurerPolicyNo = '保单号与保司保单号至少填一个';
  }
  if (!/^\d+$/.test(String(f.planId || '').trim())) fieldErrors.planId = '计划ID必须为正整数';
  if (isBlank(f.insuredIdHash)) fieldErrors.insuredIdHash = '请输入被保人标识';
  if (!isDate(f.outInsureTime)) fieldErrors.outInsureTime = '请选择出险日期';

  var items = Array.isArray(f.items) ? f.items : [];
  var itemsErrors = [];
  for (var i = 0; i < items.length; i++) {
    var it = items[i] || {};
    var e = { itemName: '', amount: '' };
    if (isBlank(it.itemName)) e.itemName = '请填项目名称';
    if (!isAmount(it.amount)) e.amount = '金额需为≥0且最多两位小数';
    itemsErrors.push(e);
  }
  if (items.length === 0) {
    fieldErrors.items = '至少添加一项费用明细';
  }

  var summaries = [];
  Object.keys(fieldErrors).forEach(function (k) {
    if (fieldErrors[k] && summaries.indexOf(fieldErrors[k]) === -1) summaries.push(fieldErrors[k]);
  });
  itemsErrors.forEach(function (e) {
    if (e.itemName && summaries.indexOf(e.itemName) === -1) summaries.push(e.itemName);
    if (e.amount && summaries.indexOf(e.amount) === -1) summaries.push(e.amount);
  });
  return { valid: summaries.length === 0, summary: summaries.slice(0, 3).join('；'), fieldErrors: fieldErrors, itemsErrors: itemsErrors };
}

/** 把 YYYY-MM-DD 转成服务端 Instant（当日 00:00:00Z）。 */
function toInstant(dateStr) {
  return String(dateStr) + 'T00:00:00Z';
}

module.exports = { validateReport: validateReport, toInstant: toInstant, isAmount: isAmount };
