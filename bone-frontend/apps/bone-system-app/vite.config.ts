import path from 'path';
import { createQiankunViteConfig } from '@bone/shared-config';

export default createQiankunViteConfig('bone-system-app', 3007, {
  aliases: { '@': path.resolve(process.cwd(), 'src') },
});
