import { createQiankunViteConfig } from '@bone/shared-config';

export default createQiankunViteConfig('bone-metadata-app', 3004, {
  proxyTarget: 'http://localhost:9001',
  config: {
    server: {
      port: 3004,
      host: '0.0.0.0',
      cors: true,
      origin: 'http://localhost:3004',
      hmr: { overlay: false },
      proxy: {
        // 全部 /api 统一走网关（8888）：租户绑定（X-Tenant-Id）由网关从 JWT 注入，
        // 直连 metadata-server（9001）会缺租户上下文 → MissingTenantContextException 500
        // （实体详情页实证）。此前「/api 直连 9001」的拆分已废弃。
        '/api': { target: 'http://localhost:8888', changeOrigin: true },
      },
    },
  },
});
