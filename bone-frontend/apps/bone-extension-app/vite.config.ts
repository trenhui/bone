import path from 'path';
import { createQiankunViteConfig } from '@bone/shared-config';

export default createQiankunViteConfig('bone-extension-app', 3008, {
  aliases: { '@': path.resolve(process.cwd(), 'src') },
});
