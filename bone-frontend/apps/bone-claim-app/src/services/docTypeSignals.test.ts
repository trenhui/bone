import { describe, expect, it } from 'vitest';
import { signalsFromGray } from './docTypeSignals';

const W = 160;
const H = 120;

/** 构造灰底 + 中央小簇深色墨迹（签名版式）。 */
function signatureGray(): Uint8Array {
  const g = new Uint8Array(W * H).fill(178); // 中位背景=灰
  for (let y = 48; y < 74; y++) {
    for (let x = 60; x < 100; x++) {
      if ((x + y) % 3 !== 0) g[y * W + x] = 20; // 中央一小簇黑墨
    }
  }
  return g;
}

/** 构造白底 + 多行长横线 + 分散文字（票据/清单版式）。 */
function invoiceGray(): Uint8Array {
  const g = new Uint8Array(W * H).fill(255);
  for (let y = 10; y < H; y += 14) {
    for (let x = 6; x < W - 6; x++) g[y * W + x] = 30; // 通栏表格线
    for (let x = 12; x < W - 12; x += 9) g[(y + 4) * W + x] = 60; // 分散文字
  }
  return g;
}

describe('docTypeSignals · 版式特征判别力', () => {
  it('签名：低墨迹 + 高集中 + 无表格线', () => {
    const s = signalsFromGray(signatureGray(), W, H);
    expect(s.inkRatio).toBeLessThan(0.16);
    expect(s.inkConcentration).toBeGreaterThanOrEqual(0.5);
    expect(s.tableLineRatio).toBeLessThan(0.04);
  });

  it('票据/清单：表格线明显 + 墨迹分散（集中度低于签名）', () => {
    const s = signalsFromGray(invoiceGray(), W, H);
    expect(s.tableLineRatio).toBeGreaterThanOrEqual(0.05);
    expect(s.inkConcentration).toBeLessThan(0.5);
  });

  it('两种版式不会互相串：签名集中度显著高于票据', () => {
    const sig = signalsFromGray(signatureGray(), W, H);
    const inv = signalsFromGray(invoiceGray(), W, H);
    expect(sig.inkConcentration!).toBeGreaterThan(inv.inkConcentration!);
    expect(sig.tableLineRatio!).toBeLessThan(inv.tableLineRatio!);
  });
});
