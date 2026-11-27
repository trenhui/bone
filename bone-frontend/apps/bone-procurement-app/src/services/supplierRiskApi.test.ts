// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 准入与风险 API 客户端降级测试。
 *
 * 覆盖四类失败：403 无权限、404 能力未启用、网络失败、success=false（2xx）。
 * 所有失败都必须带明确降级原因，且绝不带 PII（证件号/电话）。
 */

type HttpErr = { response?: { status?: number } };

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

import {
  supplierRiskApi,
  PROCUREMENT_BASE,
  QUALIFICATION_PATH,
  RISK_PATH,
} from './supplierRiskApi';

describe('supplierRiskApi 四类失败降级', () => {
  beforeEach(() => {
    calls.get.length = 0;
    calls.post.length = 0;
    calls.rejectNext = null;
    calls.nextEnvelope = null;
  });

  it('BASE 与后端 RequestMapping 一致', () => {
    expect(PROCUREMENT_BASE).toBe('/api/v1/procurement');
    expect(QUALIFICATION_PATH).toBe('/qualifications');
    expect(RISK_PATH).toBe('/performance-scores');
  });

  it('读端点走真实路径 GET /performance-scores', async () => {
    await supplierRiskApi.getPerformance('S-1');
    expect(calls.get).toHaveLength(1);
    expect(calls.get[0].url).toBe(`${PROCUREMENT_BASE}${RISK_PATH}`);
  });

  it('写端点走真实路径 POST /{id}/ocr-review 与 /risk-alerts', async () => {
    await supplierRiskApi.reviewOcr({ qualificationId: 'Q-1', accurate: true, reason: '无误' });
    await supplierRiskApi.disposeAlert({ alertId: 'A-1', decision: '人工升级' });
    expect(calls.post[0].url).toBe(`${PROCUREMENT_BASE}${QUALIFICATION_PATH}/Q-1/ocr-review`);
    expect(calls.post[1].url).toBe(`${PROCUREMENT_BASE}/risk-alerts`);
    for (const call of calls.post) {
      const body = call.body as Record<string, unknown>;
      expect(body).not.toHaveProperty('tenantId');
      expect(body).not.toHaveProperty('operator');
      expect(body).not.toHaveProperty('reviewer');
    }
  });

  it('403 → 无权限降级，不显示成功', async () => {
    calls.rejectNext = { response: { status: 403 } } as HttpErr;
    const r = await supplierRiskApi.getPerformance('S-1');
    expect(r.success).toBe(false);
    expect(r.degradedReason).toMatch(/权限/);
  });

  it('404 → 能力未启用降级', async () => {
    calls.rejectNext = { response: { status: 404 } } as HttpErr;
    const r = await supplierRiskApi.getPerformance('S-1');
    expect(r.success).toBe(false);
    expect(r.degradedReason).toMatch(/未启用|无法确认/);
  });

  it('网络失败 → 无法确认', async () => {
    calls.rejectNext = new Error('network down');
    const r = await supplierRiskApi.getPerformance('S-1');
    expect(r.success).toBe(false);
    expect(r.degradedReason).toMatch(/无法确认/);
  });

  it('success=false 的 2xx → 业务失败降级', async () => {
    calls.nextEnvelope = { success: false, code: 200, message: '业务失败', data: null };
    const r = await supplierRiskApi.getPerformance('S-1');
    expect(r.success).toBe(false);
    expect(r.degradedReason).toBeTruthy();
  });

});
