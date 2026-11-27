import { createApiClient } from '@bone/shared-services';

/** 与后端 BusinessController 的 @RequestMapping 严格一致。 */
export const SOURCING_BASE = '/api/v1/procurement/business';

const apiClient = createApiClient('');

/** 统一结果：失败态必须带降级原因，绝不让调用方误当成功。 */
export interface SourcingResult<T> {
  success: boolean;
  data: T | null;
  degradedReason: string | null;
}

export interface AwardDTO {
  tenantId?: string;
  rfxNo?: string;
  supplierId?: string;
  confirmedBy?: string;
  confirmedAt?: string;
  reason?: string;
}

export interface QuotationComparisonDTO {
  quoteId?: string;
  supplierId?: string;
  amount?: string;
  currency?: string;
  comparable?: boolean;
  reasonCode?: string;
  aiScore?: string;
  aiRecommendation?: string;
}

export interface SubmitBidPayload {
  bidId: string;
  rfxNo: string;
  supplierId: string;
  sourceRef: string;
  dueAt: string;
}

export interface RecordQuotationPayload {
  quoteId: string;
  rfxNo: string;
  supplierId: string;
  amount: string;
  currency: string;
  validUntil: string;
  aiScore?: string;
  aiRecommendation?: string;
}

export interface AwardPayload {
  supplierId: string;
  reason: string;
}

function unwrap<T>(envelope: unknown): SourcingResult<T> {
  const env = envelope as { success?: boolean; message?: string; data?: T } | null;
  if (!env || env.success === false) {
    return {
      success: false,
      data: null,
      degradedReason: env?.message?.trim() || '无法确认',
    };
  }
  return { success: true, data: env.data ?? null, degradedReason: null };
}

function fail(reason: string): SourcingResult<never> {
  return { success: false, data: null, degradedReason: reason };
}

function degrade(error: unknown): string {
  const status = (error as { response?: { status?: number } })?.response?.status;
  if (status === 403) return '无权限';
  if (status === 404) return '能力未启用';
  return '无法确认';
}

/**
 * 寻源/授标 API。请求体只含业务标识与理由，不含前端自报 tenantId/operator/confirmedBy；
 * 身份一律由后端登录态裁决，前端不参与身份判定（fail-closed）。
 */
export const sourcingApi = {
  getAward: async (rfxNo: string): Promise<SourcingResult<AwardDTO>> => {
    try {
      return unwrap<AwardDTO>(await apiClient.get(`${SOURCING_BASE}/${encodeURIComponent(rfxNo)}/award`));
    } catch (error) {
      return fail(degrade(error));
    }
  },

  listQuotations: async (
    rfxNo: string,
    budgetCurrency: string,
  ): Promise<SourcingResult<QuotationComparisonDTO[]>> => {
    try {
      return unwrap<QuotationComparisonDTO[]>(
        await apiClient.get(`${SOURCING_BASE}/${encodeURIComponent(rfxNo)}/quotations`, {
          params: { budgetCurrency },
        }),
      );
    } catch (error) {
      return fail(degrade(error));
    }
  },

  submitBid: async (payload: SubmitBidPayload): Promise<SourcingResult<null>> => {
    try {
      return unwrap<null>(await apiClient.post(`${SOURCING_BASE}/bids`, payload));
    } catch (error) {
      return fail(degrade(error));
    }
  },

  recordQuotation: async (payload: RecordQuotationPayload): Promise<SourcingResult<null>> => {
    try {
      return unwrap<null>(await apiClient.post(`${SOURCING_BASE}/quotations`, payload));
    } catch (error) {
      return fail(degrade(error));
    }
  },

  award: async (rfxNo: string, payload: AwardPayload): Promise<SourcingResult<AwardDTO>> => {
    try {
      return unwrap<AwardDTO>(
        await apiClient.post(`${SOURCING_BASE}/${encodeURIComponent(rfxNo)}/award`, payload),
      );
    } catch (error) {
      return fail(degrade(error));
    }
  },
};
