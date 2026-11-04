// @vitest-environment node
import { describe, it, expect } from 'vitest';
import {
  INVOLVEMENT_META,
  involvementMeta,
  rightLabel,
  RIGHTS_LABELS,
} from './aiDisclosure';

/**
 * AI 参与决策告知字典的负向护栏。
 * 核心：三态不可压平——「无法确认」永不被翻译成「AI 未参与」，未知码必须降级显示原始码。
 */
describe('involvementMeta 三态', () => {
  it('三态编号齐全且与后端受控码一致', () => {
    expect(Object.keys(INVOLVEMENT_META).sort()).toEqual([
      'AI_ASSISTED',
      'AI_UNAVAILABLE',
      'UNDETERMINED',
    ]);
  });

  it('UNDETERMINED 文案明确「无法确认」，不写成 AI 未参与', () => {
    const meta = involvementMeta('UNDETERMINED');
    expect(meta.label).toContain('无法确认');
    expect(meta.label).not.toContain('AI 未参与');
    expect(meta.tone).toBe('warning');
    expect(meta.hint).toContain('不得据此认定');
  });

  it('AI_UNAVAILABLE 是降级态，不得映射成无风险结论', () => {
    const meta = involvementMeta('AI_UNAVAILABLE');
    expect(meta.tone).toBe('warning');
    expect(meta.hint).toContain('不代表风险已排除');
  });

  it('AI_ASSISTED 是显著标识，措辞不得把 AI 说成决定者', () => {
    const meta = involvementMeta('AI_ASSISTED');
    expect(meta.label).toContain('AI 辅助生成内容');
    expect(meta.hint).toContain('最终决定仍由人工作出');
    expect(meta.label).not.toContain('AI 决定');
  });

  it('负向探针：未知受控码降级显示原始码并告警，绝不乐观映射', () => {
    const meta = involvementMeta('SOME_NEW_CODE');
    expect(meta.label).toContain('SOME_NEW_CODE');
    expect(meta.tone).toBe('warning');
    expect(meta.hint).toContain('未登记');
    expect(meta.label).not.toBe(INVOLVEMENT_META.AI_UNAVAILABLE.label);
  });
});

describe('rightLabel 权利字典', () => {
  it('PIPL 三项权利均有中文映射', () => {
    expect(Object.keys(RIGHTS_LABELS).sort()).toEqual([
      'APPEAL_WITHIN_48H',
      'EXPLANATION_OF_DECISION',
      'REQUEST_HUMAN_REVIEW',
    ]);
    expect(rightLabel('REQUEST_HUMAN_REVIEW')).toBe('要求人工复核');
    expect(rightLabel('APPEAL_WITHIN_48H')).toBe('48 小时内申诉');
  });

  it('负向探针：未知权利码显示原始码，不静默丢弃', () => {
    expect(rightLabel('UNKNOWN_RIGHT')).toContain('UNKNOWN_RIGHT');
  });
});
