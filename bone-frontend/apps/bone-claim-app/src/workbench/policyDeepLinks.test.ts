import { describe, expect, it } from 'vitest';
import { buildPolicyDetailLink, isSafePolicyNo, parsePolicyNoFromSearch } from './policyDeepLinks';

describe('policyDeepLinks', () => {
  it('合法保单号生成 /policy?policyNo= 深链', () => {
    expect(buildPolicyDetailLink('POL-2026-0001')).toBe('/policy?policyNo=POL-2026-0001');
    expect(buildPolicyDetailLink('  POL-2026-0001  ')).toBe('/policy?policyNo=POL-2026-0001');
  });

  it('非法保单号 fail-closed，不生成链接', () => {
    for (const bad of ['', 'POL/../evil', 'POL?x=1', 'PO L', 'POL#frag']) {
      expect(isSafePolicyNo(bad)).toBe(false);
      expect(buildPolicyDetailLink(bad)).toBeNull();
    }
  });

  it('从 hash query 读保单号，非法值返回 null', () => {
    expect(parsePolicyNoFromSearch('?policyNo=POL-2026-0001&x=1')).toBe('POL-2026-0001');
    expect(parsePolicyNoFromSearch('?policyNo=../evil')).toBeNull();
    expect(parsePolicyNoFromSearch('')).toBeNull();
  });
});
