// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 同意撤回 API 契约测试。
 *
 * 后端真源：`POST /api/v1/claims/{id}/consent/revoke`，权限 claim:claims:write，
 * 幂等返回 ApiResponse<boolean>（true=本次撤回 / false=本来就已撤回）。
 * 这里锁定：路径正确、无请求体、布尔原样透传（false 不能被当成失败吞掉）。
 */
const { mockPost } = vi.hoisted(() => ({ mockPost: vi.fn() }));

vi.mock('@bone/shared-services', () => ({
  createApiClient: () => ({ post: mockPost, get: vi.fn(), put: vi.fn(), delete: vi.fn() }),
}));

import { claimApi } from './claim';

describe('claimApi.revokeConsent', () => {
  beforeEach(() => mockPost.mockReset());

  it('调用案件级撤回端点，不带请求体', async () => {
    mockPost.mockResolvedValue({ code: 200, success: true, data: true });
    await claimApi.revokeConsent('CL-123');
    expect(mockPost).toHaveBeenCalledTimes(1);
    expect(mockPost).toHaveBeenCalledWith('/CL-123/consent/revoke');
  });

  it('幂等重复撤回返回 false 时原样透传，不抛错、不改写成成功文案', async () => {
    mockPost.mockResolvedValue({ code: 200, success: true, data: false });
    const res = await claimApi.revokeConsent('CL-123');
    expect(res.data).toBe(false);
  });

});
