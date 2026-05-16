import { createMicroAppViteConfig } from '../../config/createMicroAppViteConfig';

export default createMicroAppViteConfig({
  appName: 'bone-masterdata-app',
  port: 3005,
  apiProxyTarget: 'http://localhost:8081',
  pathAlias: true,
});
