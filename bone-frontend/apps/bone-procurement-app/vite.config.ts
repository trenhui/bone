import { createQiankunViteConfig } from '@bone/shared-config';

/**
 * 采购平台微应用（Qiankun 子应用）。
 *
 * 端口 3014（3003–3013 已被既有微应用占用）；代理目标为网关 8888。
 * API 前缀 `/api/v1/procurement/*` 由 services/purchaseApi.ts 收敛。
 */
export default createQiankunViteConfig('bone-procurement-app', 3014, {
  proxyTarget: 'http://localhost:8888',
});
