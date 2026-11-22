import { describe, expect, it } from 'vitest';
import { CLAIM_FOCUS_PATH, buildFocusHref } from './focusMode';

describe('focusMode', () => {
  it('微应用内链接拼成无壳焦点路径的完整 URL', () => {
    expect(buildFocusHref('/case?claimId=776102367333449728&tab=overview')).toBe(
      `${window.location.origin}${CLAIM_FOCUS_PATH}#/case?claimId=776102367333449728&tab=overview`,
    );
    expect(buildFocusHref('/policy?policyNo=POL-2026-0001')).toBe(
      `${window.location.origin}${CLAIM_FOCUS_PATH}#/policy?policyNo=POL-2026-0001`,
    );
  });

  it('非应用内绝对路径或空值 fail-closed', () => {
    expect(buildFocusHref(null)).toBeNull();
    expect(buildFocusHref('')).toBeNull();
    expect(buildFocusHref('https://evil.example/x')).toBeNull();
    expect(buildFocusHref('../case')).toBeNull();
  });
});
