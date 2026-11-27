// @vitest-environment node
import { describe, expect, it } from 'vitest';
import {
  bidStatusTextOrRaw,
  toQuotationRows,
  awardConfirmedByText,
} from './sourcingView';
import type { QuotationComparisonDTO } from '../services/sourcingApi';

/**
 * 寻源展示映射：未知码保留原始码、不可比报价带原因、AI 建议只作建议字段。
 */
describe('sourcingView 纯映射', () => {
  it('未知投标状态保留原始码，绝不压成「已提交」', () => {
    expect(bidStatusTextOrRaw('WEIRD_STATE')).toContain('WEIRD_STATE');
    expect(bidStatusTextOrRaw('SUBMITTED')).toContain('已提交');
    expect(bidStatusTextOrRaw(null)).toContain('无法确认');
  });

  it('不可比报价带原因，不进入有效排序', () => {
    const quotes: QuotationComparisonDTO[] = [
      { quoteId: 'Q1', amount: '100', comparable: true },
      { quoteId: 'Q2', amount: '50', comparable: false, reasonCode: 'CURRENCY_MISMATCH' },
      { quoteId: 'Q3', amount: '80', comparable: true },
    ];
    const rows = toQuotationRows(quotes);
    expect(rows.valid).toHaveLength(2);
    expect(rows.valid.map((r) => r.quoteId)).toEqual(['Q1', 'Q3']);
    expect(rows.invalid).toHaveLength(1);
    expect(rows.invalid[0].reasonCode).toBe('CURRENCY_MISMATCH');
    expect(rows.invalid[0].reasonText).toContain('币种');
  });

  it('AI 建议只作建议字段，绝不展示为已授标', () => {
    const rows = toQuotationRows([
      { quoteId: 'Q1', comparable: true, aiScore: '0.9', aiRecommendation: '建议授标' },
    ]);
    expect(rows.valid[0].aiSuggestion).toBe('建议授标');
    expect(rows.valid[0].awarded).toBe(false);
  });

  it('授标确认人缺失显示无法确认，不伪造人工终决', () => {
    expect(awardConfirmedByText(null)).toContain('无法确认');
    expect(awardConfirmedByText('zhangsan')).toBe('zhangsan');
  });
});
