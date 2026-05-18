import { createMicroAppViteConfig } from '../../config/createMicroAppViteConfig';

/**
 * 开发代理：默认经 bone-gateway（8888）转发至 studio-generator（8085）。
 * 直连：`BONE_API_PROXY_TARGET=http://localhost:8085 npm run dev`
 */
const apiProxyTarget =
  process.env.BONE_API_PROXY_TARGET ??
  process.env.VITE_API_PROXY_TARGET ??
  'http://localhost:8888';

export default createMicroAppViteConfig({
  appName: 'bone-generator-app',
  port: 3009,
  apiProxyTarget,
});
