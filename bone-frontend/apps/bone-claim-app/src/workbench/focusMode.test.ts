import { describe, expect, it } from 'vitest';
import {
  FOCUS_PATHS, STANDALONE_PATH, LEGACY_FOCUS_PATHS, buildFocusHref, isFocusPath,
} from './focusMode';

describe('focusMode', () => {
  it('独立页路径契约为 /standalone，旧 /claim-detail 仅作别名', () => {
    expect(STANDALONE_PATH).toBe('/standalone');
    expect(LEGACY_FOCUS_PATHS).toContain('/claim-detail');
    expect(FOCUS_PATHS).toEqual(['/standalone', '/claim-detail']);
  });

  it('微应用内链接拼成无壳独立页的完整 URL', () => {
    expect(buildFocusHref('/case?claimId=776102367333449728&tab=overview')).toBe(
      `${window.location.origin}/standalone#/case?claimId=776102367333449728&tab=overview`,
    );
    expect(buildFocusHref('/policy?policyNo=POL-2026-0001')).toBe(
      `${window.location.origin}/standalone#/policy?policyNo=POL-2026-0001`,
    );
  });

  it('非应用内绝对路径、协议相对 URL 或空值 fail-closed', () => {
    expect(buildFocusHref(null)).toBeNull();
    expect(buildFocusHref('')).toBeNull();
    expect(buildFocusHref('https://evil.example/x')).toBeNull();
    expect(buildFocusHref('//evil.example/x')).toBeNull();
    expect(buildFocusHref('../case')).toBeNull();
  });

  it('isFocusPath 认新旧路径但不误判相似前缀', () => {
    expect(isFocusPath('/standalone')).toBe(true);
    expect(isFocusPath('/standalone/')).toBe(true);
    expect(isFocusPath('/claim-detail')).toBe(true);
    expect(isFocusPath('/claim-detail/')).toBe(true);
    expect(isFocusPath('/claim')).toBe(false);
    expect(isFocusPath('/standalone-x')).toBe(false);
    expect(isFocusPath('/claim-detail-x')).toBe(false);
    expect(isFocusPath('')).toBe(false);
    expect(isFocusPath(null)).toBe(false);
  });
});
