import { describe, expect, it, vi } from 'vitest';
import {
  SCLAIM_SYSTEM, STANDALONE_PATHS, ALL_STANDALONE_PATHS,
  CLAIM_MENU_BASENAME, buildClaimMenuPath,
  buildSclaimPath, isStandalonePath, standaloneSearch,
  buildClaimDetailHref, buildPolicyDetailHref,
} from './sclaimRoutes';

describe('sclaimRoutes · SynapClaim URL 规范', () => {
  it('系统缩写与三级路径规范', () => {
    expect(SCLAIM_SYSTEM).toBe('sclaim');
    expect(buildSclaimPath('claim', 'list')).toBe('/sclaim/claim/list');
    expect(buildSclaimPath('review', 'detail')).toBe('/sclaim/review/detail');
    expect(buildSclaimPath('ocr', 'recognition')).toBe('/sclaim/ocr/recognition');
  });

  it('独立详情页路径符合 /sclaim/{module}/detail', () => {
    expect(STANDALONE_PATHS.CLAIM_DETAIL).toBe('/sclaim/claim/detail');
    expect(STANDALONE_PATHS.POLICY_DETAIL).toBe('/sclaim/policy/detail');
    expect(ALL_STANDALONE_PATHS).toEqual(['/sclaim/claim/detail', '/sclaim/policy/detail']);
  });

  it('内嵌菜单页 basename 与短路径构造无 hash 规范 URL', () => {
    // 此常量同时被 shell activeRule 与数据库 iam_menu.path 依赖，漂移即整域菜单失效。
    expect(CLAIM_MENU_BASENAME).toBe('/sclaim/claim');
    expect(buildClaimMenuPath('/pre-exam')).toBe('/sclaim/claim/pre-exam');
    expect(buildClaimMenuPath('/review')).toBe('/sclaim/claim/review');
    expect(buildClaimMenuPath('adjust')).toBe('/sclaim/claim/adjust');
    expect(buildClaimMenuPath('/pre-exam')).not.toContain('#');
  });

  it('isStandalonePath 认规范路径但不误判相似前缀', () => {
    expect(isStandalonePath('/sclaim/claim/detail')).toBe(true);
    expect(isStandalonePath('/sclaim/policy/detail')).toBe(true);
    expect(isStandalonePath('/sclaim/claim/detail/')).toBe(true);
    expect(isStandalonePath('/sclaim/claim/list')).toBe(false);
    expect(isStandalonePath('/sclaim/claim/detail-x')).toBe(false);
    expect(isStandalonePath('/standalone')).toBe(false);
    expect(isStandalonePath('/claim-detail')).toBe(false);
    expect(isStandalonePath('')).toBe(false);
    expect(isStandalonePath(null)).toBe(false);
  });

  it('构造规范详情 URL：干净 path + query、无 hash、非法值 fail-closed', () => {
    const claimHref = buildClaimDetailHref('776102367333449728', 'material');
    expect(claimHref).toContain(`${STANDALONE_PATHS.CLAIM_DETAIL}?claimId=776102367333449728&tab=material`);
    expect(claimHref).not.toContain('#');

    const policyHref = buildPolicyDetailHref('POL-2026-0001');
    expect(policyHref).toContain(`${STANDALONE_PATHS.POLICY_DETAIL}?policyNo=POL-2026-0001`);
    expect(policyHref).not.toContain('#');

    expect(buildClaimDetailHref('abc/../evil?x=1')).toBeNull();
    expect(buildPolicyDetailHref('')).toBeNull();
  });

  it('standaloneSearch 仅在独立页读浏览器 query', () => {
    const spy = vi.spyOn(window, 'location', 'get');
    try {
      spy.mockReturnValue({ pathname: '/sclaim/claim/detail', search: '?claimId=123' } as Location);
      expect(standaloneSearch()).toBe('?claimId=123');

      spy.mockReturnValue({ pathname: '/sclaim/claim/review', search: '?foo=bar' } as Location);
      expect(standaloneSearch()).toBe('');
    } finally {
      spy.mockRestore();
    }
  });
});
