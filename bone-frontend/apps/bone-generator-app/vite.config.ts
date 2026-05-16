import { createMicroAppViteConfig } from '../../config/createMicroAppViteConfig';

export default createMicroAppViteConfig({
  appName: 'bone-generator-app',
  port: 3009,
  apiProxyTarget: 'http://localhost:8085',
});
