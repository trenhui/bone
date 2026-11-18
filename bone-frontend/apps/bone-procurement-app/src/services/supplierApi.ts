import { createApiClient, getGlobalContext, setQiankunToken } from '@bone/shared-services';
import { normalizeTotal } from '@bone/shared-utils';
import type {
  SupplierApiResponse,
  SupplierStateDTO,
  SupplierRegistrationResultDTO,
  SupplierTransitionResultDTO,
  SupplierPageQuery,
  NormalizedSupplierPage,
  RegisterSupplierCommand,
} from '../types/supplier';

export { setQiankunToken };

/** 与后端 SupplierController 的 @RequestMapping 严格一致。 */
export const SUPPLIERS_BASE = '/api/v1/masterdata/suppliers';

const apiClient = createApiClient('');

/**
 * 读取当前登录租户，仅用于命令体 tenantId 字段（与后端登录态做一致性比对，fail-closed）。
 * 租户隔离的真源仍是登录态 JWT，不是这里的自报值；本值只用于「自报与登录态不一致即拒绝」的校验。
 */
export function resolveTenantId(): number {
  const ctx = getGlobalContext() as { tenantId?: string | number } | undefined;
  const raw =
    ctx?.tenantId ??
    (typeof localStorage !== 'undefined' ? localStorage.getItem('tenantId') : null);
  const n = Number(raw);
  return Number.isFinite(n) ? n : 0;
}

/**
 * 分页归一化：只读权威字段 `records`（`getList()` 已 @JsonIgnore），
 * `total` 用 normalizeTotal 处理 Long→String，不读兼容 getter `list`。
 */
export function normalizeSupplierPage<T>(
  resp: SupplierApiResponse<{
    records?: T[];
    total?: string | number;
    page?: number;
    size?: number;
  } | null>,
): NormalizedSupplierPage<T> {
  const data = resp.data ?? {};
  return {
    records: Array.isArray(data.records) ? data.records : [],
    total: normalizeTotal(data.total),
    page: data.page ?? 1,
    size: data.size ?? 10,
  };
}

function withTenant<T extends { tenantId: number }>(cmd: T): T {
  // 命令体 tenantId 必须与登录态一致；若调用方未填或填错，用登录态值覆盖，
  // 使请求不会因前端拿不到租户上下文而误发跨租户命令（真正的隔离仍在后端比对）。
  return { ...cmd, tenantId: resolveTenantId() || cmd.tenantId };
}

export const supplierApi = {
  register: (
    cmd: RegisterSupplierCommand,
  ): Promise<SupplierApiResponse<SupplierRegistrationResultDTO>> =>
    apiClient.post(`${SUPPLIERS_BASE}/registrations`, withTenant(cmd)),

  transitionSupplier: (
    partyId: string,
    cmd: { tenantId: number; event: string; occurredAt?: string },
  ): Promise<SupplierApiResponse<SupplierTransitionResultDTO>> =>
    apiClient.post(`${SUPPLIERS_BASE}/${encodeURIComponent(partyId)}/supplier-transitions`, withTenant(cmd)),

  transitionVendor: (
    partyId: string,
    cmd: { tenantId: number; event: string; source: string; occurredAt?: string },
  ): Promise<SupplierApiResponse<SupplierTransitionResultDTO>> =>
    apiClient.post(`${SUPPLIERS_BASE}/${encodeURIComponent(partyId)}/vendor-transitions`, withTenant(cmd)),

  current: (partyId: string): Promise<SupplierApiResponse<SupplierStateDTO | null>> =>
    apiClient.get(`${SUPPLIERS_BASE}/${encodeURIComponent(partyId)}`),

  page: async (
    query: SupplierPageQuery,
  ): Promise<SupplierApiResponse<NormalizedSupplierPage<SupplierStateDTO>>> => {
    // 运行时 axios 响应拦截器已解包 response.data 为统一信封（success/code/message/data），
    // 但 createApiClient 的静态类型仍是 AxiosResponse，故此处显式收窄到实际形状。
    const resp = (await apiClient.get(SUPPLIERS_BASE, {
      params: { page: query.page ?? 1, size: query.size ?? 10 },
    })) as unknown as SupplierApiResponse<NormalizedSupplierPage<SupplierStateDTO>>;
    return { ...resp, data: normalizeSupplierPage(resp) };
  },
};
