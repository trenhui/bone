Page({
  data: {
    status: 'fail',
    title: '报案失败',
    description: '请返回重试或联系管理员',
    reportNo: '',
    claimNo: '',
    claimId: '',
    amount: '',
    statusText: '',
  },

  onLoad: function (query) {
    var status = query && query.status === 'success' ? 'success' : 'fail';
    var data = {
      status: status,
      reportNo: query && query.reportNo ? decodeURIComponent(query.reportNo) : '',
      claimNo: query && query.claimNo ? decodeURIComponent(query.claimNo) : '',
      claimId: query && query.claimId ? decodeURIComponent(query.claimId) : '',
      amount: query && query.amount ? decodeURIComponent(query.amount) : '',
      statusText: query && query.statusText ? decodeURIComponent(query.statusText) : '',
    };
    if (status === 'success') {
      data.title = '报案已受理';
      data.description = '赔案已建案并受理签收，请在 PC 工作台补充材料并推进核赔。';
    }
    this.setData(data);
  },

  goHome: function () {
    wx.reLaunch({ url: '/pages/index/index' });
  },

  continueSubmit: function () {
    wx.redirectTo({ url: '/pages/report/report' });
  },
});
