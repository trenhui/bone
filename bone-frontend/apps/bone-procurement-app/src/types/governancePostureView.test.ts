import { describe, expect, it } from 'vitest';
import {
  GOVERNANCE_POSTURE_FETCH_ERROR,
  postureVerdictTextOrRaw,
  postureVerdictColor,
  type GovernancePostureDTO,
} from './governancePostureView';

describe('治理态势视图映射（反假绿）', () => {
  it('未知判词码显示原始码而非正常', () => {
    expect(postureVerdictTextOrRaw('SOME_NEW_VERDICT')).toContain('SOME_NEW_VERDICT');
    expect(postureVerdictTextOrRaw('SOME_NEW_VERDICT')).not.toContain('正常');
  });

  it('已知判词显示中文', () => {
    expect(postureVerdictTextOrRaw('UNDETERMINED')).toContain('无法确认');
    expect(postureVerdictTextOrRaw('ATTENTION_REQUIRED')).toContain('需关注');
    expect(postureVerdictTextOrRaw('ALL_CLEAR')).toContain('全部正常');
  });

  it('缺失判词显示无法确认', () => {
    expect(postureVerdictTextOrRaw(null)).toBe('无法确认');
    expect(postureVerdictTextOrRaw(undefined)).toBe('无法确认');
  });

  it('未知判词用告警色', () => {
    expect(postureVerdictColor('SOME_NEW_VERDICT')).toBe('red');
    expect(postureVerdictColor('ALL_CLEAR')).toBe('green');
  });

  it('取数失败文案为无法确认', () => {
    expect(GOVERNANCE_POSTURE_FETCH_ERROR).toBe('无法确认');
  });

  it('DTO 结构可承载能力明细与计数', () => {
    const dto: GovernancePostureDTO = {
      verdict: 'ALL_CLEAR',
      capabilities: [],
      attentionCount: 0,
      undeterminedCount: 0,
      modelHealthClaim: 'NONE',
    };
    expect(dto.capabilities).toHaveLength(0);
    expect(dto.modelHealthClaim).toBe('NONE');
  });
});
