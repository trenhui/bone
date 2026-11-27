var api = require('../../utils/api.js');
var validator = require('../../utils/validate.js');

var CATEGORIES = [
  { label: '服务', value: 'SERVICE' },
  { label: '药品', value: 'DRUG' },
  { label: '检查', value: 'EXAM' },
  { label: '材料', value: 'MATERIAL' },
];
var CATEGORY_LABELS = CATEGORIES.map(function (c) { return c.label; });

function newItem() {
  return {
    key: api.uuidv4(),
    itemName: '',
    itemCode: '',
    categoryIndex: 0,
    amount: '',
    quantity: '',
    unitPrice: '',
  };
}

function fmtAmount(n) {
  return Number(n || 0).toFixed(2);
}

Page({
  data: {
    accountName: '',
    tenantName: '',
    today: '',
    policyNo: '',
    insurerPolicyNo: '',
    planId: '',
    insuredIdHash: '',
    outInsureTime: '',
    consentRecordId: '',
    items: [newItem()],
    categoryLabels: CATEGORY_LABELS,
    itemsErrors: [{ itemName: '', amount: '' }],
    formError: '',
    itemsError: '',
    fieldErrors: { policyNo: '', insurerPolicyNo: '', planId: '', insuredIdHash: '', outInsureTime: '' },
    totalAmount: '0.00',
    submitting: false,
  },

  onLoad: function () {
    var acc = api.account();
    if (!api.token()) {
      wx.redirectTo({ url: '/pages/login/login' });
      return;
    }
    var d = new Date();
    var today = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    this.setData({
      today: today,
      accountName: (acc && (acc.realName || acc.username)) || '',
      tenantName: (acc && acc.tenantName) || '',
    });
  },

  handleInput: function (event) {
    var field = event.currentTarget.dataset.field;
    if (!field) return;
    var update = {};
    update[field] = event.detail.value;
    update['fieldErrors.' + field] = '';
    update.formError = '';
    this.setData(update);
  },

  handleDateChange: function (event) {
    this.setData({ outInsureTime: event.detail.value, 'fieldErrors.outInsureTime': '', formError: '' });
  },

  handleItemInput: function (event) {
    var ds = event.currentTarget.dataset;
    var idx = Number(ds.index);
    var field = ds.field;
    if (!field || isNaN(idx)) return;
    var update = {};
    update['items[' + idx + '].' + field] = event.detail.value;
    update['itemsErrors[' + idx + '].' + field] = '';
    update.itemsError = '';
    update.formError = '';
    this.setData(update, this.recalcTotal);
  },

  handleCategoryChange: function (event) {
    var idx = Number(event.currentTarget.dataset.index);
    if (isNaN(idx)) return;
    var update = {};
    update['items[' + idx + '].categoryIndex'] = Number(event.detail.value);
    this.setData(update);
  },

  handleAddItem: function () {
    var items = this.data.items.slice();
    var errs = this.data.itemsErrors.slice();
    items.push(newItem());
    errs.push({ itemName: '', amount: '' });
    this.setData({ items: items, itemsErrors: errs });
  },

  handleRemoveItem: function (event) {
    var idx = Number(event.currentTarget.dataset.index);
    if (isNaN(idx) || this.data.items.length <= 1) return;
    var items = this.data.items.slice();
    var errs = this.data.itemsErrors.slice();
    items.splice(idx, 1);
    errs.splice(idx, 1);
    this.setData({ items: items, itemsErrors: errs }, this.recalcTotal);
  },

  recalcTotal: function () {
    var sum = 0;
    this.data.items.forEach(function (it) {
      var n = Number(it.amount);
      if (!isNaN(n) && n > 0) sum += n;
    });
    this.setData({ totalAmount: fmtAmount(sum) });
  },

  handleLogout: function () {
    api.clearSession();
    wx.reLaunch({ url: '/pages/login/login' });
  },

  buildPayload: function () {
    var f = this.data;
    var items = f.items.map(function (it) {
      var row = {
        itemName: String(it.itemName).trim(),
        category: CATEGORIES[it.categoryIndex].value,
        amount: Number(it.amount),
      };
      if (String(it.itemCode || '').trim()) row.itemCode = String(it.itemCode).trim();
      if (String(it.quantity || '').trim()) row.quantity = Number(it.quantity);
      if (String(it.unitPrice || '').trim()) row.unitPrice = Number(it.unitPrice);
      return row;
    });
    var payload = {
      planId: Number(f.planId),
      insuredIdHash: String(f.insuredIdHash).trim(),
      outInsureTime: validator.toInstant(f.outInsureTime),
      claimType: 'OUTPATIENT',
      items: items,
    };
    if (String(f.policyNo).trim()) payload.policyNo = String(f.policyNo).trim();
    if (String(f.insurerPolicyNo).trim()) payload.insurerPolicyNo = String(f.insurerPolicyNo).trim();
    if (String(f.consentRecordId).trim()) payload.consentRecordId = String(f.consentRecordId).trim();
    return payload;
  },

  handleSubmit: function () {
    if (this.data.submitting) return;
    var validation = validator.validateReport(this.data);
    if (!validation.valid) {
      this.setData({
        formError: validation.summary,
        fieldErrors: validation.fieldErrors,
        itemsErrors: validation.itemsErrors,
        itemsError: validation.fieldErrors.items || '',
      });
      return;
    }
    var payload = this.buildPayload();
    var that = this;
    this.setData({ submitting: true, formError: '', itemsError: '' });
    api.submitClaim(payload).then(
      function (data) {
        var d = data || {};
        var query = 'status=success'
          + '&reportNo=' + encodeURIComponent(d.reportNo || '')
          + '&claimNo=' + encodeURIComponent(d.claimNo || '')
          + '&claimId=' + encodeURIComponent(String(d.claimId || ''))
          + '&amount=' + encodeURIComponent(that.data.totalAmount)
          + '&statusText=' + encodeURIComponent(d.status || '');
        wx.redirectTo({
          url: '/pages/report-result/report-result?' + query,
          fail: function () {
            that.setData({ formError: '报案已受理，请返回查看结果', submitting: true });
          },
        });
      },
      function (err) {
        that.setData({
          submitting: false,
          formError: (err && err.message) || '报案提交失败，请稍后重试',
        });
      }
    );
  },
});
