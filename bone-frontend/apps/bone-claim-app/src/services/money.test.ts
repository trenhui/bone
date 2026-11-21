// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { formatMoney } from './money';

/**
 * 金额缺值语义的判别力测试。
 *
 * 旧写法 `¥${Number(v ?? 0).toFixed(2)}` 把 null/NaN 全变成 `¥0.00`，
 * 在「待核定金额」「可用额度」处等于把未知说成零。这里用双向对照钉住：
 * 显式 0 仍是 `¥0.00`，缺值/非法值一律不是金额。
 */
describe('formatMoney · 缺值不伪装成零金额', () => {
  it('显式 0 是合法金额', () => {
    expect(formatMoney(0)).toBe('¥0.00');
  });

  it('null / undefined / 空串 / NaN 显示未知，不是 ¥0.00', () => {
    for (const value of [null, undefined, '', NaN, 'not-a-number']) {
      expect(formatMoney(value)).toBe('—');
      expect(formatMoney(value)).not.toBe('¥0.00');
    }
  });

  it('业务语义可覆盖未知文案', () => {
    expect(formatMoney(null, { unknownText: '待核定' })).toBe('待核定');
    expect(formatMoney(12.1)).toBe('¥12.10');
  });

  it('大额金额固定千分位，避免审核时看错位', () => {
    expect(formatMoney(1234567.89)).toBe('¥1,234,567.89');
    expect(formatMoney('10000')).toBe('¥10,000.00');
  });
});
