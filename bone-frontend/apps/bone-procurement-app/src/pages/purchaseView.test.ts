import { describe, expect, it } from 'vitest';
import {
  allowedPoEvents,
  allowedPrEvents,
  degradeMessage,
  formatMoney,
  fromApiError,
  safeStateProjection,
} from './purchaseView';

describe('purchaseView 纯逻辑', () => {
  it('PO 状态只暴露后端允许的迁移事件', () => {
    expect(allowedPoEvents('DRAFT').map((e) => e.event)).toEqual(['SUBMIT']);
    expect(allowedPoEvents('APPROVED').map((e) => e.event)).toEqual(['SEND']);
    expect(allowedPoEvents('SENT')).toEqual([]);
    expect(allowedPoEvents('CLOSED')).toEqual([]);
    expect(allowedPoEvents('UNKNOWN_X')).toEqual([]);
  });

  it('PR 状态只暴露后端允许的迁移事件', () => {
    expect(allowedPrEvents('APPROVED').map((e) => e.event)).toEqual(['CONVERT']);
    expect(allowedPrEvents('CONVERTED')).toEqual([]);
  });

  it('错误码可读降级，未知码原样返回', () => {
    expect(degradeMessage('31003')).toContain('未开通');
    expect(degradeMessage('31402')).toContain('不一致');
    expect(degradeMessage('99999', '后端原文')).toBe('后端原文');
    expect(degradeMessage('88888')).toBe('88888');
  });

  it('从信封与 HTTP 错误提取稳定码', () => {
    expect(fromApiError({ response: { data: { errorCode: '31004' } } })).toBe('31004');
    expect(fromApiError({ response: { status: 404 } })).toBe('404');
    expect(fromApiError(null)).toBe('UNKNOWN');
  });

  it('未知状态原样展示，空值显示无法确认', () => {
    expect(safeStateProjection('PARTIAL_RECEIVED')).toBe('PARTIAL_RECEIVED');
    expect(safeStateProjection(undefined)).toBe('无法确认');
  });

  it('金额按币种四位小数格式化', () => {
    expect(formatMoney('12.5', 'CNY')).toBe('12.5000 CNY');
    expect(formatMoney(undefined, 'CNY')).toBe('无法确认');
  });
});
