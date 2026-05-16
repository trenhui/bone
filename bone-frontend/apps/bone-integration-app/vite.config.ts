import { createMicroAppViteConfig } from '../../config/createMicroAppViteConfig';

export default createMicroAppViteConfig({
  appName: 'bone-integration-app',
  port: 3006,
  apiProxyTarget: 'http://localhost:8081',
});
