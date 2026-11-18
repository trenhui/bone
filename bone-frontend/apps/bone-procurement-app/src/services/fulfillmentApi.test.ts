// @vitest-environment node
import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * 履约 API 客户端契约测试：锁定前端请求形状与 FulfillmentController 六端点一致。
 * 任一 path / method / body / params 漂移必须转红。
 */
const calls = vi.hoisted(() => ({
  get: [] as Array<{ url: string; params?: unknown }>,
  post: [] as Array<{ url: string; body?: unknown; config?: unknown }>,
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
    post: (url: string, body?: unknown, config?: { params?: unknown }) => {
      calls.post.push({ url, body, config });
      if (calls.rejectNext !== null) {
        const err = calls.rejectNext;
        calls.rejectNext = null;
        return Promise.reject(err);
      }
      return Promise.resolve({ success: true, code: 200, message: 'ok', data: {} });
    },
  }),
  setQiankunToken: () => undefined,
}));

import { fulfillmentApi, FULFILLMENT_BASE, normalizeFulfillmentPage } from './fulfillmentApi';

describe('fulfillmentApi 请求形状契约', () => {
  beforeEach(() => {
    calls.get.length = 0;
    calls.post.length = 0;
    calls.rejectNext = null;
  });

  it('BASE 与后端 RequestMapping 一致', () => {
    expect(FULFILLMENT_BASE).toBe('/api/v1/procurement');
  });

  it('ack 走 POST /purchase-orders/{no}/ack，poNo 由 path 提供，body 不含伪造 vendor', async () => {
    await fulfillmentApi.ack('PO-1', { tenantId: 42, poVersion: 3, ackType: 'FULL', lines: [] });
    expect(calls.post).toHaveLength(1);
    expect(calls.post[0].url).toBe(`${FULFILLMENT_BASE}/purchase-orders/PO-1/ack`);
    const body = calls.post[0].body as Record<string, unknown>;
    expect(body.ackType).toBe('FULL');
    expect(body.poVersion).toBe(3);
    expect(body.vendorPartyId).toBeUndefined();
  });

  it('createAsn 走 POST /asns 且 body 携带命令字段', async () => {
    await fulfillmentApi.createAsn({
      tenantId: 42,
      asnNo: 'ASN-1',
      poNo: 'PO-1',
      lines: [{ lineNo: 1, poLineNo: 1, itemId: 'I-1', qty: '2.000000' }],
    });
    expect(calls.post[0].url).toBe(`${FULFILLMENT_BASE}/asns`);
    const body = calls.post[0].body as Record<string, unknown>;
    expect(body.asnNo).toBe('ASN-1');
    expect(Array.isArray(body.lines)).toBe(true);
  });

  it('submitInvoice 走 POST /invoices 且 body 携带金额/品类', async () => {
    await fulfillmentApi.submitInvoice({
      tenantId: 42,
      invoiceNo: 'INV-1',
      amount: '100.0000',
      currency: 'CNY',
      category: 'SERVICES',
      lines: [],
    });
    expect(calls.post[0].url).toBe(`${FULFILLMENT_BASE}/invoices`);
    const body = calls.post[0].body as Record<string, unknown>;
    expect(body.amount).toBe('100.0000');
    expect(body.category).toBe('SERVICES');
  });

  it('supplement 走 POST /ir-exceptions/{no}/supplement，参数在 query 而非 body', async () => {
    await fulfillmentApi.supplement('EXC-1', { tenantId: 42, supplementRef: 'DOC-9' });
    expect(calls.post[0].url).toBe(`${FULFILLMENT_BASE}/ir-exceptions/EXC-1/supplement`);
    expect(calls.post[0].config).toEqual({ params: { tenantId: 42, supplementRef: 'DOC-9' } });
  });

  it('fulfillmentView 走 GET /purchase-orders/{no}/fulfillment?tenantId=', async () => {
    await fulfillmentApi.fulfillmentView('PO-1', 42);
    expect(calls.get[0].url).toBe(`${FULFILLMENT_BASE}/purchase-orders/PO-1/fulfillment`);
    expect(calls.get[0].params).toEqual({ tenantId: 42 });
  });

  it('listExceptions 走 GET /ir-exceptions 且传 tenantId/page/size', async () => {
    await fulfillmentApi.listExceptions(42, 2, 5);
    expect(calls.get[0].url).toBe(`${FULFILLMENT_BASE}/ir-exceptions`);
    expect(calls.get[0].params).toEqual({ tenantId: 42, page: 2, size: 5 });
  });
});

describe('fulfillmentApi 响应归一化与失败语义', () => {
  beforeEach(() => {
    calls.get.length = 0;
    calls.post.length = 0;
    calls.rejectNext = null;
  });

  it('分页把字符串 total 归一为 number 且只读 records', () => {
    const out = normalizeFulfillmentPage({
      success: true,
      code: 200,
      message: 'ok',
      data: {
        records: [{ exceptionNo: 'EXC-1' }],
        total: '23',
        page: 1,
        size: 20,
        list: [{ exceptionNo: 'STALE' }],
      } as never,
    });
    expect(out.total).toBe(23);
    expect(out.records).toEqual([{ exceptionNo: 'EXC-1' }]);
  });

  it('31504 以 reject 透传 errorCode，调用方必须捕获并保留表单', async () => {
    calls.rejectNext = { response: { status: 409, data: { errorCode: '31504' } } };
    await expect(
      fulfillmentApi.submitInvoice({
        tenantId: 42,
        invoiceNo: 'INV-1',
        amount: '999.0000',
        category: 'SERVICES',
        lines: [],
      }),
    ).rejects.toMatchObject({ response: { data: { errorCode: '31504' } } });
  });

  it('403（跨供应商越权）以 reject 透传 status', async () => {
    calls.rejectNext = { response: { status: 403 } };
    await expect(fulfillmentApi.listExceptions(42, 1, 20)).rejects.toMatchObject({
      response: { status: 403 },
    });
  });
});
