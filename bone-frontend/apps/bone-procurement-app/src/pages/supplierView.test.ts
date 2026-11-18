// @vitest-environment node
import { describe, it, expect } from 'vitest';

/**
 * 供应商页面可测逻辑（不依赖 jsdom）。
 * 覆盖 spec：双角色状态映射、未知码原样、失败降级分类、分页归一、PII 白名单、历史格式。
 */
import {
  allowedSupplierEvents,
  allowedVendorEvents,
  degradeMessage,
  fromApiError,
  pageCount,
  safeStateProjection,
  formatHistory,
  SupplierStateDTO,
} from './supplierView';

const base: SupplierStateDTO = {
  partyId: 'SUP-1',
  supplierState: 'SUSPENDED',
  supplierVersion: 3,
  vendorState: 'ACTIVE_VENDOR',
  vendorVersion: 5,
  supplierHistory: ['ACTIVE->SUSPENDED (risk_raised)'],
  vendorHistory: ['PENDING_VENDOR->ACTIVE_VENDOR (activation_passed)'],
};

describe('allowedSupplierEvents（只读投影，裁决仍在后端）', () => {
  it('ONBOARDING 显示 onboarding_approved', () => {
    expect(allowedSupplierEvents('ONBOARDING').map((e) => e.event)).toContain('onboarding_approved');
  });
  it('PROSPECT 显示 onboarding_submitted', () => {
    expect(allowedSupplierEvents('PROSPECT').map((e) => e.event)).toContain('onboarding_submitted');
  });
  it('EXITED 终态无任何事件', () => {
    expect(allowedSupplierEvents('EXITED')).toEqual([]);
  });
  it('未知状态不臆造事件（空集合，后端仍裁决）', () => {
    expect(allowedSupplierEvents('ZZ_UNKNOWN')).toEqual([]);
  });
});

describe('allowedVendorEvents（付款冻结解禁不在前端绕过）', () => {
  it('ACTIVE_VENDOR 不提供域内冻结解除入口', () => {
    const evs = allowedVendorEvents('ACTIVE_VENDOR');
    expect(evs.some((e) => e.event === 'payment_block_released' && e.source === 'DOMAIN')).toBe(false);
  });
  it('PAYMENT_BLOCKED 的解禁入口仅 EXTERNAL_FINANCE 来源', () => {
    const evs = allowedVendorEvents('PAYMENT_BLOCKED');
    const releases = evs.filter((e) => e.event === 'payment_block_released');
    expect(releases.length).toBeGreaterThan(0);
    expect(releases.every((e) => e.source === 'EXTERNAL_FINANCE')).toBe(true);
  });
});

describe('degradeMessage（能力开关与权限可观察降级）', () => {
  it('404/未注册 → 能力未启用', () => {
    expect(degradeMessage(404, 'no handler')).toContain('未启用');
  });
  it('403 → 无权限', () => {
    expect(degradeMessage(403, 'forbidden')).toMatch(/无权限|权限不足/);
  });
  it('其余业务失败 → 后端 message 原样', () => {
    expect(degradeMessage(500, 'NO_ACTIVE_VENDOR')).toBe('NO_ACTIVE_VENDOR');
  });
});

describe('pageCount（字符串 total 归一）', () => {
  it('total="23" size=10 → 3 页', () => {
    expect(pageCount('23', 10)).toBe(3);
  });
  it('total="0" → 0 页，无类型转换错误', () => {
    expect(pageCount('0', 10)).toBe(0);
  });
});

describe('safeStateProjection（PII 白名单，未知码原样）', () => {
  it('剔除意外字段 phone/uscc，保留白名单字段', () => {
    const dirty = { ...base, phone: '13800009527', uscc: '91310000MA1FL0AB2C', legalName: '某某公司' } as never;
    const safe = safeStateProjection(dirty);
    expect(safe.supplierState).toBe('SUSPENDED');
    expect(JSON.stringify(safe)).not.toContain('13800009527');
    expect(JSON.stringify(safe)).not.toContain('91310000MA1FL0AB2C');
    expect(JSON.stringify(safe)).not.toContain('某某公司');
  });
  it('未知状态码原样保留，不改写成已知态', () => {
    const unknown = { ...base, supplierState: 'ZZ_UNKNOWN' };
    expect(safeStateProjection(unknown).supplierState).toBe('ZZ_UNKNOWN');
  });
});

describe('fromApiError（失败信封/HTTP 错误归一化，供 try/catch 消费）', () => {
  it('success=false 被拦截器转成带 displayMessage 的 Error', () => {
    const err = Object.assign(new Error('NO_ACTIVE_VENDOR'), { displayMessage: 'NO_ACTIVE_VENDOR' });
    expect(fromApiError(err)).toEqual({ code: 0, message: 'NO_ACTIVE_VENDOR' });
  });
  it('HTTP 403 提取 status 与后端 message', () => {
    const err = { response: { status: 403, data: { message: 'forbidden' } }, displayMessage: 'forbidden' };
    expect(fromApiError(err)).toEqual({ code: 403, message: 'forbidden' });
  });
  it('HTTP 404 提取 status（能力未启用）', () => {
    const err = { response: { status: 404 }, message: 'no handler' };
    expect(fromApiError(err)).toEqual({ code: 404, message: 'no handler' });
  });
  it('未知错误给出兜底文案，不吞掉', () => {
    expect(fromApiError('boom').message).toBeTruthy();
  });
  it('degradeMessage(fromApiError(404)) 命中能力未启用降级', () => {
    const err = { response: { status: 404 }, message: 'not found' };
    const info = fromApiError(err);
    expect(degradeMessage(info.code, info.message)).toContain('未启用');
  });
});

describe('formatHistory（摘要形状) ', () => {
  it('保留后端摘要原文（from->to (event)）', () => {
    expect(formatHistory(['ACTIVE->SUSPENDED (risk_raised)'])).toEqual(['ACTIVE->SUSPENDED (risk_raised)']);
  });
  it('摘要缺失或畸形时显示原始值，不推断为正常', () => {
    expect(formatHistory([undefined as never, 42 as never])).toEqual(['(无摘要)', '42']);
  });
  it('对象摘要不显示 [object Object]，降级为占位', () => {
    expect(formatHistory([{ bad: 1 } as never])).toEqual(['(摘要格式异常)']);
  });
});
