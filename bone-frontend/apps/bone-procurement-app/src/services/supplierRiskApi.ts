import { createApiClient } from '@bone/shared-services';

/** 与后端 QualificationController / RiskController 的 @RequestMapping 严格一致。 */
export const PROCUREMENT_BASE = '/api/v1/procurement';
export const QUALIFICATION_PATH = '/qualifications';
export const RISK_PATH = '/performance-scores';

const apiClient = createApiClient('');

/** 统一结果：失败态必须带降级原因，绝不让调用方误当成功或「无风险」。 */
export interface RiskResult<T> {
  success: boolean;
  data: T | null;
  degradedReason: string | null;
}

export interface PerformanceScoreDTO {
  supplierId?: string;
  score?: string;
  grade?: string;
  period?: string;
}

export interface ReviewOcrPayload {
  qualificationId: string;
  accurate: boolean;
  reason: string;
}

export interface DisposeAlertPayload {
  alertId: string;
  decision: string;
}

function unwrap<T>(envelope: unknown): RiskResult<T> {
  const env = envelope as { success?: boolean; message?: string; data?: T } | null;
  if (!env || env.success === false) {
    return { success: false, data: null, degradedReason: env?.message?.trim() || '无法确认' };
  }
  return { success: true, data: env.data ?? null, degradedReason: null };
}

function fail(reason: string): RiskResult<never> {
  return { success: false, data: null, degradedReason: reason };
}

function degrade(error: unknown): string {
  const status = (error as { response?: { status?: number } })?.response?.status;
  if (status === 403) return '无权限';
  if (status === 404) return '能力未启用';
  return '无法确认';
}

/**
 * 准入与风险 API。写操作只含业务标识与结论，不含前端自报 tenantId/operator/reviewer；
 * 人工终决身份只由后端登录态裁决，前端不参与身份判定（fail-closed）。
 */
export const supplierRiskApi = {
  getPerformance: async (supplierId: string): Promise<RiskResult<PerformanceScoreDTO[]>> => {
    try {
      return unwrap<PerformanceScoreDTO[]>(
        await apiClient.get(`${PROCUREMENT_BASE}${RISK_PATH}`, {
          params: { supplierId },
        }),
      );
    } catch (error) {
      return fail(degrade(error));
    }
  },

  reviewOcr: async (payload: ReviewOcrPayload): Promise<RiskResult<null>> => {
    try {
      return unwrap<null>(
        await apiClient.post(
          `${PROCUREMENT_BASE}${QUALIFICATION_PATH}/${encodeURIComponent(payload.qualificationId)}/ocr-review`,
          { accurate: payload.accurate, reason: payload.reason },
        ),
      );
    } catch (error) {
      return fail(degrade(error));
    }
  },

  disposeAlert: async (payload: DisposeAlertPayload): Promise<RiskResult<null>> => {
    try {
      return unwrap<null>(
        await apiClient.post(`${PROCUREMENT_BASE}/risk-alerts`, payload),
      );
    } catch (error) {
      return fail(degrade(error));
    }
  },
};
