import { createMicroAppViteConfig } from '../../config/createMicroAppViteConfig';

export default createMicroAppViteConfig({
  appName: 'bone-system-app',
  port: 3007,
  apiProxyTarget: 'http://localhost:8083',
  pathAlias: true,
});
