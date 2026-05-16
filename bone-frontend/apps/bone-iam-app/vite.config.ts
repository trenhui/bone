import { createMicroAppViteConfig } from '../../config/createMicroAppViteConfig';

export default createMicroAppViteConfig({
  appName: 'bone-iam-app',
  port: 3003,
  apiProxyTarget: 'http://localhost:8081',
});
