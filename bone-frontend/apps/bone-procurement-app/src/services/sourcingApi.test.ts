// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 寻源 API 客户端契约测试。
 *
 * 锁定三件事：① 路径/方法与后端 BusinessController 严格一致；
 * ② 顶层 success=false（即便 HTTP 2xx）必须判为失败，不许乐观当成功；
 * ③ 授标请求体不含前端自报 tenantId/operator/confirmedBy（身份只由后端登录态裁决）。
 */

const calls = vi.hoisted(() => ({
  get: [] as Array<{ url: string; params?: unknown }>,
  post: [] as Array<{ url: string; body?: unknown }>,
  rejectNext: null as unknown | null,
  nextEnvelope: null as Record<string, unknown> | null,
}));

vi.mock('@bone/shared-services', () => ({
  createApiClient: () => ({
    get: (url: string, config?: { params?: unknown }) => {
      calls.get.push({ url, params: config?.params });
      if (calls.rejectNext !== null) {
        const err = calls.rejectNext;
        calls.rejectNext = null;
        return Promise.reject(err);
      }
      if (calls.nextEnvelope !== null) {
        const env = calls.nextEnvelope;
        calls.nextEnvelope = null;
        return Promise.resolve(env);
      }
      return Promise.resolve({ success: true, code: 200, message: 'ok', data: {} });
    },
    post: (url: string, body?: unknown) => {
      calls.post.push({ url, body });
      if (calls.rejectNext !== null) {
        const err = calls.rejectNext;
        calls.rejectNext = null;
        return Promise.reject(err);
      }
      if (calls.nextEnvelope !== null) {
        const env = calls.nextEnvelope;
        calls.nextEnvelope = null;
        return Promise.resolve(env);
      }
      return Promise.resolve({ success: true, code: 200, message: 'ok', data: {} });
    },
  }),
}));

import { sourcingApi, SOURCING_BASE } from './sourcingApi';

describe('sourcingApi 请求形状与 fail-closed 契约', () => {
  beforeEach(() => {
    calls.get.length = 0;
    calls.post.length = 0;
    calls.rejectNext = null;
    calls.nextEnvelope = null;
  });

  it('BASE 与后端 RequestMapping 一致', () => {
    expect(SOURCING_BASE).toBe('/api/v1/procurement/business');
  });

  it('getAward 走 GET /{rfxNo}/award 并带只读查询参数', async () => {
    await sourcingApi.getAward('RFX-1');
    expect(calls.get).toHaveLength(1);
    expect(calls.get[0].url).toBe(`${SOURCING_BASE}/RFX-1/award`);
  });

  it('listQuotations 走 GET /{rfxNo}/quotations 并带预算币种', async () => {
    await sourcingApi.listQuotations('RFX-1', 'CNY');
    expect(calls.get).toHaveLength(1);
    expect(calls.get[0].url).toBe(`${SOURCING_BASE}/RFX-1/quotations`);
    expect(calls.get[0].params).toMatchObject({ budgetCurrency: 'CNY' });
  });

  it('submitBid / recordQuotation 走 POST 且 body 不含自报身份字段', async () => {
    await sourcingApi.submitBid({
      bidId: 'B-1',
      rfxNo: 'RFX-1',
      supplierId: 'S-1',
      sourceRef: 'SRC-1',
      dueAt: '2026-12-01T00:00:00Z',
    });
    await sourcingApi.recordQuotation({
      quoteId: 'Q-1',
      rfxNo: 'RFX-1',
      supplierId: 'S-1',
      amount: '100',
      currency: 'CNY',
      validUntil: '2026-12-01T00:00:00Z',
    });
    expect(calls.post).toHaveLength(2);
    expect(calls.post[0].url).toBe(`${SOURCING_BASE}/bids`);
    expect(calls.post[1].url).toBe(`${SOURCING_BASE}/quotations`);
    for (const call of calls.post) {
      const body = call.body as Record<string, unknown>;
      expect(body).not.toHaveProperty('tenantId');
      expect(body).not.toHaveProperty('operator');
      expect(body).not.toHaveProperty('confirmedBy');
    }
  });

  it('award 走 POST /{rfxNo}/award，body 只含 supplierId 与 reason', async () => {
    await sourcingApi.award('RFX-1', { supplierId: 'S-1', reason: '最低价且资质合规' });
    expect(calls.post).toHaveLength(1);
    expect(calls.post[0].url).toBe(`${SOURCING_BASE}/RFX-1/award`);
    const body = calls.post[0].body as Record<string, unknown>;
    expect(Object.keys(body).sort()).toEqual(['reason', 'supplierId']);
  });

  it('success=false 的 2xx 响应判为失败，不返回成功数据', async () => {
    calls.nextEnvelope = { success: false, code: 200, message: '业务失败', data: null };
    const result = await sourcingApi.getAward('RFX-1');
    expect(result.success).toBe(false);
    expect(result.data).toBeNull();
    expect(result.degradedReason).toBeTruthy();
  });

  it('网络/HTTP 失败映射为明确降级原因而非成功', async () => {
    calls.rejectNext = new Error('network down');
    const result = await sourcingApi.getAward('RFX-1');
    expect(result.success).toBe(false);
    expect(result.degradedReason).toMatch(/无法确认/);
  });
});
