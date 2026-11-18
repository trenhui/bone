// @vitest-environment node
import { describe, it, expect } from 'vitest';
import {
  canAck,
  canCreateAsn,
  canSupplement,
  canWrite,
  degradeMessage,
  fromApiError,
  invoiceStateProjection,
  safeStateProjection,
} from './fulfillmentView';

/**
 * 履约纯逻辑测试：错误码降级、错误对象归一、状态投影、按钮可见性。
 * 前端不内置业务裁决，只按后端状态/权限快照投影。
 */
describe('错误码降级', () => {
  it('履约 31501–31506 全部映射为可读提示', () => {
    for (const code of ['31501', '31502', '31503', '31504', '31505', '31506']) {
      const text = degradeMessage(code);
      expect(text).not.toBe(code);
      expect(text.length).toBeGreaterThan(0);
    }
  });

  it('31504 给出保留表单语义的提示', () => {
    expect(degradeMessage('31504')).toMatch(/未开票余额/);
  });

  it('未知码原样返回，不臆造中文', () => {
    expect(degradeMessage('39999')).toBe('39999');
  });
});

describe('错误对象归一', () => {
  it('优先取 response.data.errorCode（履约 315xx 在此字段）', () => {
    expect(fromApiError({ response: { data: { errorCode: '31504' }, status: 409 } })).toBe('31504');
  });

  it('无 errorCode 时回退到 code / status', () => {
    expect(fromApiError({ response: { status: 403 } })).toBe('403');
    expect(fromApiError({ code: 500 })).toBe('500');
  });

  it('空错误返回 UNKNOWN', () => {
    expect(fromApiError(null)).toBe('UNKNOWN');
  });
});

describe('状态投影', () => {
  it('MATCH_PENDING 明确标注语法回执不代表通过（997 不得显示已匹配）', () => {
    const label = invoiceStateProjection('MATCH_PENDING');
    expect(label).toMatch(/语法回执/);
    expect(label).not.toMatch(/^已匹配$/);
  });

  it('只有 MATCHED 才显示财务裁决匹配', () => {
    expect(invoiceStateProjection('MATCHED')).toMatch(/财务裁决/);
  });

  it('未知码原样、空值显示无法确认', () => {
    expect(invoiceStateProjection('WEIRD_STATE')).toBe('WEIRD_STATE');
    expect(safeStateProjection('')).toBe('无法确认');
  });
});

describe('按钮可见性', () => {
  it('只有 SENT 态展示确认按钮', () => {
    expect(canAck('SENT')).toBe(true);
    expect(canAck('ACKNOWLEDGED')).toBe(false);
    expect(canAck('CANCELLED')).toBe(false);
    expect(canAck(undefined)).toBe(false);
  });

  it('只有已确认 PO 展示 ASN 登记', () => {
    expect(canCreateAsn(true)).toBe(true);
    expect(canCreateAsn(false)).toBe(false);
    expect(canCreateAsn(undefined)).toBe(false);
  });

  it('例外只有 OPEN/SUPPLEMENTED 可补料', () => {
    expect(canSupplement('OPEN')).toBe(true);
    expect(canSupplement('SUPPLEMENTED')).toBe(true);
    expect(canSupplement('UNDER_REVIEW')).toBe(false);
    expect(canSupplement('RESOLVED')).toBe(false);
  });

  it('只读/不可用态隐藏写按钮', () => {
    expect(canWrite('readwrite')).toBe(true);
    expect(canWrite('readonly')).toBe(false);
    expect(canWrite('unavailable')).toBe(false);
  });
});
