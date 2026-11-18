// @vitest-environment node
import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * 供应商 API 客户端契约测试。
 *
 * 锁定前端请求形状与后端 SupplierController 五个端点一致：
 * path / method / body（含 tenantId 比对值与事件来源声明）/ 分页 records+total 归一。
 * 任一形状漂移必须让本测试转红。
 */

const calls = vi.hoisted(() => ({
  get: [] as Array<{ url: string; params?: unknown }>,
  post: [] as Array<{ url: string; body?: unknown }>,
  // 模拟 apiClient 响应拦截器：失败（success=false 或 HTTP 错误）一律 reject，
  // 成功信封的 success 恒为 true。这样任何依赖 `if (!resp.success)` 的实现都会在此假绿下现形。
  rejectNext: null as unknown | null,
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
      return Promise.resolve({ success: true, code: 200, message: 'ok', data: {} });
    },
    post: (url: string, body?: unknown) => {
      calls.post.push({ url, body });
      if (calls.rejectNext !== null) {
        const err = calls.rejectNext;
        calls.rejectNext = null;
        return Promise.reject(err);
      }
      return Promise.resolve({ success: true, code: 200, message: 'ok', data: {} });
    },
  }),
  setQiankunToken: () => undefined,
  getGlobalContext: () => ({ tenantId: 42 }),
}));

// 全局上下文在 node 环境下不存在 window；API 客户端用 getGlobalContext() 读 tenantId（已 mock）。
// localStorage 由 setup.ts 提供 mock。

import {
  supplierApi,
  SUPPLIERS_BASE,
  resolveTenantId,
  normalizeSupplierPage,
} from './supplierApi';

describe('supplierApi 请求形状契约', () => {
  beforeEach(() => {
    calls.get.length = 0;
    calls.post.length = 0;
    calls.rejectNext = null;
    localStorage.clear();
  });

  it('BASE 与后端 RequestMapping 一致', () => {
    expect(SUPPLIERS_BASE).toBe('/api/v1/masterdata/suppliers');
  });

  it('resolveTenantId 从全局上下文读取（命令体 tenantId 仅用于与登录态比对）', () => {
    expect(resolveTenantId()).toBe(42);
  });

  it('register 走 POST /registrations 且 body 携带命令字段', async () => {
    await supplierApi.register({
      mergeId: 'merge-1',
      tenantId: 42,
      sources: [{ recordId: 'r1', sourceSystem: 'ERP', values: { uscc: 'X' } }],
    });
    expect(calls.post).toHaveLength(1);
    expect(calls.post[0].url).toBe(`${SUPPLIERS_BASE}/registrations`);
    const body = calls.post[0].body as Record<string, unknown>;
    expect(body.mergeId).toBe('merge-1');
    expect(body.tenantId).toBe(42);
    expect(Array.isArray(body.sources)).toBe(true);
  });

  it('transitionSupplier 走 POST /{partyId}/supplier-transitions 且 body 含 event', async () => {
    await supplierApi.transitionSupplier('SUP-1', {
      tenantId: 42,
      event: 'onboarding_approved',
    });
    expect(calls.post[0].url).toBe(`${SUPPLIERS_BASE}/SUP-1/supplier-transitions`);
    expect((calls.post[0].body as Record<string, unknown>).event).toBe('onboarding_approved');
  });

  it('transitionVendor 走 POST /{partyId}/vendor-transitions 且 body 含 event 与来源声明', async () => {
    await supplierApi.transitionVendor('SUP-1', {
      tenantId: 42,
      event: 'payment_block_released',
      source: 'EXTERNAL_FINANCE',
    });
    expect(calls.post[0].url).toBe(`${SUPPLIERS_BASE}/SUP-1/vendor-transitions`);
    const body = calls.post[0].body as Record<string, unknown>;
    expect(body.event).toBe('payment_block_released');
    expect(body.source).toBe('EXTERNAL_FINANCE');
  });

  it('current 走 GET /{partyId}', async () => {
    await supplierApi.current('SUP-1');
    expect(calls.get[0].url).toBe(`${SUPPLIERS_BASE}/SUP-1`);
  });

  it('page 走 GET 根路径且传 page/size', async () => {
    await supplierApi.page({ page: 2, size: 5 });
    expect(calls.get[0].url).toBe(SUPPLIERS_BASE);
    expect(calls.get[0].params).toEqual({ page: 2, size: 5 });
  });
});

describe('supplierApi 响应归一化', () => {
  beforeEach(() => {
    calls.get.length = 0;
    calls.post.length = 0;
    calls.rejectNext = null;
  });

  it('分页把字符串 total 归一为 number 且只读 records（不读 list）', () => {
    const out = normalizeSupplierPage({
      success: true,
      code: 200,
      message: 'ok',
      data: {
        records: [{ partyId: 'SUP-1' }],
        total: '23',
        page: 1,
        size: 10,
        // 兼容 getter list 即便存在也必须被忽略
        list: [{ partyId: 'STALE' }],
      } as never,
    });
    expect(out.total).toBe(23);
    expect(out.records).toEqual([{ partyId: 'SUP-1' }]);
    expect(out.page).toBe(1);
    expect(out.size).toBe(10);
  });

  it('空列表 records=[] total="0" 归一为 0，无类型转换错误', () => {
    const out = normalizeSupplierPage({
      success: true,
      code: 200,
      message: 'ok',
      data: { records: [], total: '0', page: 1, size: 10 },
    });
    expect(out.total).toBe(0);
    expect(out.records).toEqual([]);
  });
});

describe('supplierApi 失败语义（拦截器 reject 路径）', () => {
  beforeEach(() => {
    calls.get.length = 0;
    calls.post.length = 0;
    calls.rejectNext = null;
  });

  it('success=false 被拦截器转成 reject 时，supplierApi 不吞错（调用方必须 try/catch）', async () => {
    const err = Object.assign(new Error('NO_ACTIVE_VENDOR'), { displayMessage: 'NO_ACTIVE_VENDOR' });
    calls.rejectNext = err;
    await expect(
      supplierApi.transitionSupplier('SUP-1', { tenantId: 42, event: 'onboarding_approved' }),
    ).rejects.toMatchObject({ displayMessage: 'NO_ACTIVE_VENDOR' });
  });

  it('HTTP 404（能力未启用）以 reject 透传 status，供降级判定', async () => {
    calls.rejectNext = { response: { status: 404 }, message: 'not found' };
    await expect(supplierApi.page({ page: 1, size: 10 })).rejects.toMatchObject({
      response: { status: 404 },
    });
  });

  it('HTTP 403（无权限）以 reject 透传 status', async () => {
    calls.rejectNext = { response: { status: 403 }, message: 'forbidden' };
    await expect(supplierApi.current('SUP-1')).rejects.toMatchObject({
      response: { status: 403 },
    });
  });
});
