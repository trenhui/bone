import { webcrypto } from 'node:crypto';
import { afterEach, describe, expect, it } from 'vitest';
import { hashFile } from './hashFile';

const OriginalWorker = globalThis.Worker;
const OriginalCrypto = globalThis.crypto;

describe('hashFile · Worker 不可达时主线程兜底', () => {
  afterEach(() => {
    globalThis.Worker = OriginalWorker;
    Object.defineProperty(globalThis, 'crypto', { value: OriginalCrypto, configurable: true });
  });

  it('跨源 Worker 构造抛错时仍返回与主线程一致的 SHA-256', async () => {
    class BrokenWorker {
      constructor() {
        throw new Error("Failed to construct 'Worker': cross-origin");
      }
    }
    globalThis.Worker = BrokenWorker as unknown as typeof Worker;
    Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true });

    const bytes = new Uint8Array([1, 2, 3, 4]);
    const file = { arrayBuffer: async () => bytes.buffer } as File;
    const digest = await webcrypto.subtle.digest('SHA-256', bytes);
    const expected = Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');

    await expect(hashFile(file)).resolves.toBe(expected);
  });
});
