import { createMicroAppViteConfig } from '../../config/createMicroAppViteConfig';

export default createMicroAppViteConfig({
  appName: 'bone-extension-app',
  port: 3008,
  apiProxyTarget: 'http://localhost:8088',
  pathAlias: true,
});
