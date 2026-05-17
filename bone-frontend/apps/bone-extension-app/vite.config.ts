import { createMicroAppViteConfig } from '../../config/createMicroAppViteConfig';

/**
 * 开发代理：默认经 bone-gateway（8888）转发至 extension-studio（8088）。
 * 直连 Studio：`BONE_API_PROXY_TARGET=http://localhost:8088 npm run dev`
 */
const apiProxyTarget =
  process.env.BONE_API_PROXY_TARGET ??
  process.env.VITE_API_PROXY_TARGET ??
  'http://localhost:8888';

export default createMicroAppViteConfig({
  appName: 'bone-extension-app',
  port: 3008,
  apiProxyTarget,
  pathAlias: true,
});
