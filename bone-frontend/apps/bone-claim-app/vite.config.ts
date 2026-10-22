import { createQiankunViteConfig } from '@bone/shared-config';

/**
 * synapClaim 智能理赔微应用（Qiankun 子应用）。
 *
 * 端口 3013（3003–3012 已被既有微应用占用）；代理目标为网关 8888（与其他子应用一致），
 * 前端不直连后端实例。API 前缀 `/api/v1/claims` 由 `services/claimApi.ts` 收敛。
 */
export default createQiankunViteConfig('bone-claim-app', 3013, {
  proxyTarget: 'http://localhost:8888',
});
