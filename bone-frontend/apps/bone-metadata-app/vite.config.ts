import { createQiankunViteConfig } from '@bone/shared-config';

export default createQiankunViteConfig('bone-metadata-app', 3004, {
  proxyTarget: 'http://localhost:9001',
});
