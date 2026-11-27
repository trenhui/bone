var api = require('../../utils/api.js');

Page({
    data: {
        loggedIn: false,
        accountName: '',
        tenantName: '',
        gateway: api.gateway(),
    },

    onShow: function () {
        var acc = api.account();
        this.setData({
            loggedIn: !!api.token(),
            accountName: (acc && (acc.realName || acc.username)) || '',
            tenantName: (acc && acc.tenantName) || '',
        });
    },

    goReport: function () {
        var url = api.token() ? '/pages/report/report' : '/pages/login/login';
        wx.navigateTo({ url: url });
    },

    goLogin: function () {
        wx.navigateTo({ url: '/pages/login/login' });
    },

    handleLogout: function () {
        var that = this;
        wx.showModal({
            title: '退出登录',
            content: '确定退出当前账号？',
            success: function (res) {
                if (res.confirm) {
                    api.clearSession();
                    that.setData({ loggedIn: false, accountName: '', tenantName: '' });
                }
            },
        });
    },
});
