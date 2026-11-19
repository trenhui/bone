import { describe, expect, it } from 'vitest';
import {
  disclosureErrorMessage,
  disclosureText,
  resolveDisclosureText,
} from '../types/contractReview';

/** DISCLOSE-01/02 前端侧：未知态显示「无法确认」，版本快照不作证据。 */
describe('合同审查披露文案', () => {
  it('UNDETERMINED 不渲染成 AI 未参与', () => {
    expect(disclosureText('UNDETERMINED')).toBe('无法确认');
    expect(disclosureText('UNDETERMINED')).not.toBe('AI 未参与');
  });

  it('UNKNOWN 显示无法确认且保留原始码由页面透出', () => {
    expect(disclosureText('UNKNOWN')).toBe('无法确认');
  });

  it('版本快照不影响参与判定（纯按码）', () => {
    expect(disclosureText('UNKNOWN')).toBe('无法确认');
    expect(disclosureText('AVAILABLE')).toBe('AI 已参与');
    expect(disclosureText('UNAVAILABLE')).toBe('AI 未参与');
  });

  it('缺失码 fail-closed 为无法确认', () => {
    expect(disclosureText(null)).toBe('无法确认');
    expect(disclosureText(undefined)).toBe('无法确认');
  });
});

/** SUGGESTION-5：后端文本单一真源；前端仅在与码一致时透传，否则 fail-closed 兜底。 */
describe('披露文案单一真源（后端优先 + 一致性守卫）', () => {
  it('后端文本与码一致时透传后端文本', () => {
    expect(resolveDisclosureText('AVAILABLE', 'AI 已参与')).toBe('AI 已参与');
    expect(resolveDisclosureText('UNAVAILABLE', 'AI 未参与')).toBe('AI 未参与');
    expect(resolveDisclosureText('UNKNOWN', '无法确认')).toBe('无法确认');
  });

  it('后端文本与码冲突时不采信，按码兜底（不得把 UNKNOWN 渲染成 AI 未参与）', () => {
    expect(resolveDisclosureText('UNKNOWN', 'AI 未参与')).toBe('无法确认');
    expect(resolveDisclosureText('UNDETERMINED', 'AI 已参与')).toBe('无法确认');
    expect(resolveDisclosureText('AVAILABLE', 'AI 未参与')).toBe('AI 已参与');
  });

  it('后端文本缺失时按码兜底', () => {
    expect(resolveDisclosureText('AVAILABLE', '')).toBe('AI 已参与');
    expect(resolveDisclosureText('UNKNOWN', null)).toBe('无法确认');
    expect(resolveDisclosureText(undefined, undefined)).toBe('无法确认');
  });
});

/** SUGGESTION-2：403/404/网络错误映射为可展示的降级文案，杜绝永久 loading。 */
describe('披露页错误态降级文案', () => {
  it('403 权限不足', () => {
    expect(disclosureErrorMessage({ response: { status: 403 } })).toMatch(/权限|无权|403/);
  });

  it('404 记录不存在', () => {
    expect(disclosureErrorMessage({ response: { status: 404 } })).toMatch(/不存在|404/);
  });

  it('未知错误保留原始码，便于排障', () => {
    expect(disclosureErrorMessage({ response: { status: 500 } })).toBe('500');
    expect(disclosureErrorMessage(null)).toBe('UNKNOWN');
  });
});
