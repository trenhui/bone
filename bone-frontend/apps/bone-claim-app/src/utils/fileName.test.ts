// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { isSafeFileName } from './fileName';

/** 双向对照：后端拒绝的同款输入必须拒绝，后端接受的不误报。 */
describe('isSafeFileName · 与后端 assertSafeName 同口径', () => {
  it('拒绝路径遍历、绝对路径、反斜杠与控制字符', () => {
    for (const name of [
      '', '   ', '../evil.pdf', 'a..b.pdf', '/abs/path.pdf',
      'C:\\evil.pdf', 'a\0b.pdf', 'a\u0007b.pdf', 'a\u007fb.pdf',
    ]) {
      expect(isSafeFileName(name), name).toBe(false);
    }
  });

  it('不误报后端允许的普通字符（含 # & % 与中文括号）', () => {
    for (const name of ['发票_2026.pdf', 'a#b&c%d.pdf', '检查（一）.jpg', 'report.v2.final.pdf']) {
      expect(isSafeFileName(name), name).toBe(true);
    }
  });
});
