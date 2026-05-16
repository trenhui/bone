import { createMicroAppViteConfig } from '../../config/createMicroAppViteConfig';

export default createMicroAppViteConfig({
  appName: 'bone-metadata-app',
  port: 3004,
  apiProxyTarget: 'http://localhost:8081',
});
