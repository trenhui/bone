var api = require('../../utils/api.js');

Page({
  data: {
    username: '',
    password: '',
    formError: '',
    fieldErrors: { username: '', password: '' },
    submitting: false,
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

  handleLogin: function () {
    if (this.data.submitting) return;
    var fieldErrors = { username: '', password: '' };
    if (this.data.username.trim() === '') fieldErrors.username = '请输入用户名';
    if (this.data.password === '') fieldErrors.password = '请输入密码';
    if (fieldErrors.username || fieldErrors.password) {
      this.setData({ fieldErrors: fieldErrors, formError: '请先填写用户名和密码' });
      return;
    }
    var that = this;
    this.setData({ submitting: true, formError: '', fieldErrors: fieldErrors });
    api.login(this.data.username.trim(), this.data.password).then(
      function () {
        wx.showToast({ title: '登录成功', icon: 'success' });
        wx.reLaunch({ url: '/pages/index/index' });
      },
      function (err) {
        that.setData({
          submitting: false,
          formError: (err && err.message) || '登录失败，请稍后重试',
        });
      }
    );
  },
});
