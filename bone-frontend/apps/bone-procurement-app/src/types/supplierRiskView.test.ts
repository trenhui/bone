// @vitest-environment node
import { describe, expect, it } from 'vitest';
import {
  severityTextOrRaw,
  toPerformanceRows,
  stripPii,
  FETCH_FAILED_TEXT,
} from './supplierRiskView';

/**
 * 风险展示映射：未知严重度保留原始码、绩效缺失显示无法确认、PII 白名单剔除。
 */
describe('supplierRiskView 纯映射', () => {
  it('未知严重度保留原始码，绝不显示「无风险」', () => {
    expect(severityTextOrRaw('WEIRD_SEVERITY')).toContain('WEIRD_SEVERITY');
    expect(severityTextOrRaw(null)).toContain('无法确认');
    expect(FETCH_FAILED_TEXT).toContain('无法确认');
  });

  it('绩效缺失显示无法确认，不伪造 0 分', () => {
    const rows = toPerformanceRows([
      { supplierId: 'S1', score: '90', grade: 'A' },
      { supplierId: 'S2' },
    ]);
    expect(rows[0].scoreText).toBe('90');
    expect(rows[1].scoreText).toContain('无法确认');
    expect(rows[1].scoreText).not.toContain('0');
  });

  it('PII 白名单剔除证件号与电话，只保留业务标识', () => {
    const raw = {
      supplierId: 'S1',
      displayName: '某供应商',
      maskedCertNo: '110101********1234',
      phone: '13800000000',
      score: '90',
    };
    const clean = stripPii(raw as unknown as Record<string, unknown>);
    expect(clean).toHaveProperty('supplierId');
    expect(clean).toHaveProperty('score');
    expect(JSON.stringify(clean)).not.toContain('110101');
    expect(JSON.stringify(clean)).not.toContain('13800000000');
  });
});
