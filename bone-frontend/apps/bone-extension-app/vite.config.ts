import { createQiankunViteConfig } from '@bone/shared-config';

export default createQiankunViteConfig('bone-extension-app', 3008, {
  proxyTarget: 'http://localhost:8888',
  config: {
    server: {
      port: 3008,
      host: '0.0.0.0',
      cors: true,
      origin: 'http://localhost:3008',
      hmr: { overlay: false },
      proxy: {
        // 全部 /api 统一走网关（8888）：租户绑定（X-Tenant-Id）由网关从 JWT 注入，
        // 直连 extension-studio（8088）会缺租户上下文 / CORS → 403。与 metadata-app 一致。
        '/api': { target: 'http://localhost:8888', changeOrigin: true },
      },
    },
  },
});
