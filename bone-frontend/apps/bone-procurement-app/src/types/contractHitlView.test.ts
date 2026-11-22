import { describe, expect, it } from 'vitest';
import {
  buildOverridePayload,
  canFinalize,
  displayRiskLevel,
  FINAL_RISK_LEVELS,
  type ContractReviewReportDTO,
} from './contractHitlView';

const baseReport: ContractReviewReportDTO = {
  reviewId: 'rv-1',
  contractNo: 'CT-1',
  status: 'HUMAN_REVIEW',
  aiAvailable: 'AVAILABLE',
  riskLevel: 'LOW',
  humanFinalized: false,
  humanReason: null,
};

describe('合同人工终决视图', () => {
  it('只接受非空理由与登记风险等级，其他值不构造 payload', () => {
    expect(buildOverridePayload('  人工判定  ', 'HIGH')).toEqual({
      reason: '人工判定',
      finalRiskLevel: 'HIGH',
    });
    expect(buildOverridePayload('', 'HIGH')).toBeNull();
    expect(buildOverridePayload('理由', 'AUTO_APPROVE')).toBeNull();
    expect(buildOverridePayload('理由', null)).toBeNull();
    expect(FINAL_RISK_LEVELS).not.toContain('AUTO_APPROVE');
  });

  it('未知风险码原样透传，不压成低风险', () => {
    expect(displayRiskLevel('UNKNOWN')).toBe('无法确认');
    expect(displayRiskLevel('SOME_NEW_CODE')).toBe('未知码：SOME_NEW_CODE');
    expect(displayRiskLevel(null)).toBe('无法确认');
  });

  it('已终决或空报告不可再次终决', () => {
    expect(canFinalize(baseReport)).toBe(true);
    expect(canFinalize({ ...baseReport, humanFinalized: true })).toBe(false);
    expect(canFinalize(null)).toBe(false);
  });
});
