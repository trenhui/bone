// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 案件签收 API 契约测试。
 *
 * 守两类回归：
 *   1. 端点/请求体契约漂移（尤其 signChannel 三维字段与 remark 二次确认意见）；
 *   2. 写动作幂等键退化成 UUID —— 网络超时重试会重复签收，线上是真金白银的重复赔付风险。
 *      这里钉死「同业务标识 → 同键」「长度 ≤64（对齐 DB varchar(64)）」。
 */
const { mockPost, mockGet } = vi.hoisted(() => ({ mockPost: vi.fn(), mockGet: vi.fn() }));

vi.mock('@bone/shared-services', () => ({
  createApiClient: () => ({ post: mockPost, get: mockGet, put: vi.fn(), delete: vi.fn() }),
}));

import { claimSignApi } from './claimSign';
import { stableClaimActionKey } from './claim';

const ok = <T,>(data: T) => ({ code: 200, message: 'ok', success: true, data });

describe('claimSign API 契约', () => {
  beforeEach(() => {
    mockPost.mockReset();
    mockGet.mockReset();
  });

  it('线上回执：端点/请求体正确，同 businessKey 重试复用同一稳定幂等键', async () => {
    mockPost.mockResolvedValue(ok({ batchNo: 'SIGN-1', batchId: '1001', created: true }));

    const res = await claimSignApi.acknowledge('RP20261125ABCDEFGH1234');

    expect(mockPost).toHaveBeenCalledTimes(1);
    const [url, body, config] = mockPost.mock.calls[0];
    expect(url).toBe('/sign');
    expect(body).toEqual({
      businessKey: 'RP20261125ABCDEFGH1234',
      signChannel: 'API_PUSH',
      claimNo: null,
      claimAmount: null,
    });
    const key = config.headers['Idempotency-Key'];
    expect(key).toBe(stableClaimActionKey('RP20261125ABCDEFGH1234', 'sign-ack'));
    expect(key.length).toBeLessThanOrEqual(64);
    expect(res.data.batchNo).toBe('SIGN-1');

    await claimSignApi.acknowledge('RP20261125ABCDEFGH1234');
    expect(mockPost).toHaveBeenCalledTimes(2);
    expect(mockPost.mock.calls[1][2].headers['Idempotency-Key']).toBe(key);
  });

  it('超长 businessKey 的幂等键仍确定性、≤64，且不同标识不碰撞', () => {
    const long = 'K'.repeat(200);
    const a = stableClaimActionKey(long, 'sign-ack');
    expect(a).toBe(stableClaimActionKey(long, 'sign-ack'));
    expect(a.length).toBeLessThanOrEqual(64);
    expect(stableClaimActionKey(`${long}X`, 'sign-ack')).not.toBe(a);
    // 动作名区分键空间：同一标识的回执与确认不能互相去重
    expect(stableClaimActionKey(long, 'sign-confirm')).not.toBe(a);
  });

  it('二次确认：端点带 batchId，remark 原样透传，键由 batchId 派生', async () => {
    mockPost.mockResolvedValue(ok(null));

    await claimSignApi.confirm('776102367333449728', '影像齐全，确认完成签收');

    const [url, body, config] = mockPost.mock.calls[0];
    expect(url).toBe('/sign/batches/776102367333449728/confirm');
    expect(body).toEqual({ remark: '影像齐全，确认完成签收' });
    expect(config.headers['Idempotency-Key']).toBe(
      stableClaimActionKey('776102367333449728', 'sign-confirm'),
    );
  });

  it('批次列表：筛选条件与真分页参数原样下发，字符串 total 不被解包吞掉', async () => {
    const page = { records: [], total: '2', page: 2, size: 50 };
    mockGet.mockResolvedValue(ok(page));

    const res = await claimSignApi.list({
      source: 'OFFLINE',
      signStatus: 'WAIT_FOR_IMAGE',
      page: 2,
      size: 50,
    });

    expect(mockGet).toHaveBeenCalledWith('/sign/batches', {
      params: { source: 'OFFLINE', signStatus: 'WAIT_FOR_IMAGE', page: 2, size: 50 },
    });
    expect(res.data).toEqual(page);
    expect(res.data.total).toBe('2');
  });
});
