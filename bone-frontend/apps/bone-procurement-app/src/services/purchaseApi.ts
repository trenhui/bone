import { createApiClient, setQiankunToken } from '@bone/shared-services';
import { normalizeTotal } from '@bone/shared-utils';
import type {
  ApiEnvelope,
  CreatePurchaseOrderPayload,
  NormalizedPage,
  PageQuery,
  PurchaseOrderDTO,
  RequisitionDTO,
} from '../types/purchase';

export { setQiankunToken };

/** 与后端 Controller 的 @RequestMapping 严格一致。 */
export const REQUISITIONS_BASE = '/api/v1/procurement/requisitions';
export const PURCHASE_ORDERS_BASE = '/api/v1/procurement/purchase-orders';

const apiClient = createApiClient('');

/** 分页归一化：只读 records/total，total 用 normalizeTotal 处理 Long→String。 */
export function normalizePurchasePage<T>(
  resp: ApiEnvelope<{ records?: T[]; total?: string | number; page?: number; size?: number } | null>,
): NormalizedPage<T> {
  const data = resp.data ?? {};
  return {
    records: Array.isArray(data.records) ? data.records : [],
    total: normalizeTotal(data.total),
    page: data.page ?? 1,
    size: data.size ?? 10,
  };
}

export const purchaseApi = {
  createRequisition: (cmd: {
    tenantId: number;
    orgId: number;
    requesterId: number;
    totalAmount: string;
    currency: string;
    budgetRef?: string;
  }): Promise<ApiEnvelope<RequisitionDTO>> => apiClient.post(REQUISITIONS_BASE, cmd),

  convertRequisition: (
    no: string,
    cmd: { tenantId: number; vendorPartyId: string; legalEntityId: number },
  ): Promise<ApiEnvelope<RequisitionDTO>> =>
    apiClient.post(`${REQUISITIONS_BASE}/${encodeURIComponent(no)}/convert`, cmd),

  createPurchaseOrder: (cmd: CreatePurchaseOrderPayload): Promise<ApiEnvelope<PurchaseOrderDTO>> =>
    apiClient.post(PURCHASE_ORDERS_BASE, cmd),

  submitPurchaseOrder: (no: string, tenantId: number): Promise<ApiEnvelope<PurchaseOrderDTO>> =>
    apiClient.post(`${PURCHASE_ORDERS_BASE}/${encodeURIComponent(no)}/submit`, null, {
      params: { tenantId },
    }),

  sendPurchaseOrder: (no: string, tenantId: number): Promise<ApiEnvelope<PurchaseOrderDTO>> =>
    apiClient.post(`${PURCHASE_ORDERS_BASE}/${encodeURIComponent(no)}/send`, null, {
      params: { tenantId },
    }),

  getPurchaseOrder: (no: string, tenantId: number): Promise<ApiEnvelope<PurchaseOrderDTO>> =>
    apiClient.get(`${PURCHASE_ORDERS_BASE}/${encodeURIComponent(no)}`, {
      params: { tenantId },
    }),

  listPurchaseOrders: async (
    query: PageQuery,
    tenantId: number,
  ): Promise<ApiEnvelope<NormalizedPage<PurchaseOrderDTO>>> => {
    const resp = (await apiClient.get(PURCHASE_ORDERS_BASE, {
      params: {
        tenantId,
        page: query.page ?? 1,
        size: query.size ?? 10,
        status: query.status,
      },
    })) as unknown as ApiEnvelope<NormalizedPage<PurchaseOrderDTO>>;
    return { ...resp, data: normalizePurchasePage(resp) };
  },
};
