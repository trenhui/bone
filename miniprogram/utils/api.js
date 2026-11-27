/**
 * Bone 网关请求封装（原生 wx.request，无 npm、无 fetch）。
 * 统一：JWT 注入、401 跳登录、业务码解包、幂等键、错误文案。
 */
const config = require('../config.js');

function gateway() {
  return String(config.GATEWAY).replace(/\/$/, '');
}

function token() {
  return wx.getStorageSync(config.TOKEN_KEY) || '';
}

function saveSession(data) {
  wx.setStorageSync(config.TOKEN_KEY, data.token || '');
  wx.setStorageSync(config.ACCOUNT_KEY, {
    username: data.account && data.account.username,
    realName: data.account && data.account.realName,
    tenantName: data.account && data.account.tenantName,
    requirePasswordChange: data.requirePasswordChange,
  });
}

function clearSession() {
  wx.removeStorageSync(config.TOKEN_KEY);
  wx.removeStorageSync(config.ACCOUNT_KEY);
}

function account() {
  return wx.getStorageSync(config.ACCOUNT_KEY) || null;
}

/** UUID v4（小程序无 crypto.randomUUID 的兼容实现：时间戳 + 随机，未做密码学强度要求，仅作幂等键）。 */
function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    var r = Math.floor(Math.random() * 16);
    var v = c === 'x' ? r : ((r & 0x3) | 0x8);
    return v.toString(16);
  });
}

/**
 * 发起请求。opts: { method, path, data, auth, idempotent }
 * 返回 Promise，resolve 的是后端 data 字段，reject 的是 { code, message, status }。
 */
function request(opts) {
  var o = opts || {};
  var method = o.method || 'GET';
  var path = o.path || '';
  var header = { 'Content-Type': 'application/json' };
  if (o.auth !== false && token()) header['Authorization'] = 'Bearer ' + token();
  if (o.idempotent) header['Idempotency-Key'] = uuidv4();
  var url = gateway() + config.API_BASE + path;
  return new Promise(function (resolve, reject) {
    wx.request({
      url: url,
      method: method,
      data: o.data || undefined,
      header: header,
      timeout: 20000,
      success: function (res) {
        var status = res.statusCode;
        var body = res.data;
        if (status === 401) {
          clearSession();
          wx.showToast({ title: '登录已过期，请重新登录', icon: 'none' });
          wx.navigateTo({ url: '/pages/login/login' });
          reject({ code: 'GW_UNAUTHORIZED', message: '登录已过期', status: status });
          return;
        }
        if (status < 200 || status >= 300) {
          var b = body || {};
          reject({
            code: b.errorCode || b.code || ('HTTP_' + status),
            message: b.message || ('请求失败（' + status + '）'),
            status: status,
          });
          return;
        }
        var payload = body || {};
        // 业务码按 2xx 判成功：报案创建返回 201（code=201），不能只认 200，
        // 否则后端已落库、前端却误报「报案提交失败」= 假红。
        var bizCode = payload.code;
        var bizFail = payload.success === false
          || (bizCode !== undefined && bizCode !== null && (Number(bizCode) < 200 || Number(bizCode) >= 300));
        if (bizFail) {
          reject({
            code: payload.errorCode || payload.code,
            message: payload.message || '请求失败',
            status: status,
          });
          return;
        }
        resolve(payload.data);
      },
      fail: function (err) {
        reject({ code: 'NETWORK_ERROR', message: err.errMsg || '网络异常，请检查网关地址', status: 0 });
      },
    });
  });
}

/** 登录：POST /iam/login，成功写入本地会话。 */
function login(username, password) {
  return request({
    method: 'POST',
    path: '/iam/login',
    data: { username: username, password: password },
    auth: false,
  }).then(function (data) {
    saveSession(data || {});
    return data;
  });
}

/** 报案提交：POST /claims，必带幂等键。 */
function submitClaim(payload) {
  return request({ method: 'POST', path: '/claims', data: payload, idempotent: true });
}

module.exports = {
  request: request,
  login: login,
  submitClaim: submitClaim,
  token: token,
  account: account,
  clearSession: clearSession,
  uuidv4: uuidv4,
  gateway: gateway,
};
