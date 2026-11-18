import { createApiClient, setQiankunToken } from '@bone/shared-services';
import { normalizeTotal } from '@bone/shared-utils';
import type {
  AckCommandPayload,
  AsnCommandPayload,
  FulfillmentViewDTO,
  InvoiceCommandPayload,
  IrExceptionDTO,
  NormalizedPage,
  SupplementPayload,
} from '../types/fulfillment';

export { setQiankunToken };

/** 与履约 FulfillmentController 的 @RequestMapping 严格一致。 */
export const FULFILLMENT_BASE = '/api/v1/procurement';

const apiClient = createApiClient('');

interface Envelope<T> {
  success?: boolean;
  code?: number;
  message?: string;
  data?: T;
}

/** 分页归一化：只读 records/total（PageResult），total 用 normalizeTotal 处理 Long→String。 */
export function normalizeFulfillmentPage<T>(
  resp: Envelope<{ records?: T[]; total?: string | number; page?: number; size?: number } | null>,
): NormalizedPage<T> {
  const data = resp.data ?? {};
  return {
    records: Array.isArray(data.records) ? data.records : [],
    total: normalizeTotal(data.total),
    page: data.page ?? 1,
    size: data.size ?? 20,
  };
}

export const fulfillmentApi = {
  /** POST /purchase-orders/{no}/ack — poNo 由 path 提供，命令体不带 poNo。 */
  ack: (
    no: string,
    cmd: Omit<AckCommandPayload, never>,
  ): Promise<Envelope<unknown>> =>
    apiClient.post(`${FULFILLMENT_BASE}/purchase-orders/${encodeURIComponent(no)}/ack`, cmd),

  /** POST /asns */
  createAsn: (cmd: AsnCommandPayload): Promise<Envelope<unknown>> =>
    apiClient.post(`${FULFILLMENT_BASE}/asns`, cmd),

  /** POST /invoices */
  submitInvoice: (cmd: InvoiceCommandPayload): Promise<Envelope<unknown>> =>
    apiClient.post(`${FULFILLMENT_BASE}/invoices`, cmd),

  /** POST /ir-exceptions/{no}/supplement?tenantId=&supplementRef= */
  supplement: (
    no: string,
    payload: SupplementPayload,
  ): Promise<Envelope<unknown>> =>
    apiClient.post(`${FULFILLMENT_BASE}/ir-exceptions/${encodeURIComponent(no)}/supplement`, null, {
      params: { tenantId: payload.tenantId, supplementRef: payload.supplementRef },
    }),

  /** GET /purchase-orders/{no}/fulfillment?tenantId= */
  fulfillmentView: (no: string, tenantId: number): Promise<Envelope<FulfillmentViewDTO>> =>
    apiClient.get(`${FULFILLMENT_BASE}/purchase-orders/${encodeURIComponent(no)}/fulfillment`, {
      params: { tenantId },
    }),

  /** GET /ir-exceptions?tenantId=&page=&size= */
  listExceptions: async (
    tenantId: number,
    page = 1,
    size = 20,
  ): Promise<Envelope<NormalizedPage<IrExceptionDTO>>> => {
    const resp = (await apiClient.get(`${FULFILLMENT_BASE}/ir-exceptions`, {
      params: { tenantId, page, size },
    })) as unknown as Envelope<NormalizedPage<IrExceptionDTO>>;
    return { ...resp, data: normalizeFulfillmentPage(resp) };
  },
};
