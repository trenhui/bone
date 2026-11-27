/**
 * Bone 网关配置（小程序端）。
 *
 * 真机 / 发布前必须把 GATEWAY 改成微信后台 request 合法域名里的实际地址（https）。
 * 本机开发可保持 127.0.0.1:8888，但需在微信开发者工具勾选「不校验合法域名、web-view、TLS 版本以及 HTTPS 证书」。
 * 运维可改 config.json（与本项目同目录）覆盖，避免改代码。
 */
module.exports = {
  GATEWAY: 'http://127.0.0.1:8888',
  API_BASE: '/api/v1',
  TOKEN_KEY: 'bone_claim_token',
  ACCOUNT_KEY: 'bone_claim_account',
};
