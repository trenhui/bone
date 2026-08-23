/* eslint-disable @typescript-eslint/explicit-module-boundary-types */
import { createApiClient, setQiankunToken } from '@bone/shared-services';
import type {
  ApiResponse,
  PageResult,
  PageResultIamCompat,
  MetaEntity,
  MetaField,
  MetaRelation,
  CreateMetaEntityReq,
  UpdateMetaEntityReq,
  CreateMetaFieldReq,
  UpdateMetaFieldReq,
  CreateMetaRelationReq,
  UpdateMetaRelationReq,
  RuntimeRecord,
} from '../types';

export { setQiankunToken };

const META = '/api/v1/metadata';
const RUNTIME = '/api/v1/runtime';

/** 兼容 catalog（list）与 engine 原始分页（records） */
export function normalizePage<T>(raw: PageResult<T> | PageResultIamCompat<T>): PageResult<T> {
  return {
    list: raw.list ?? raw.records ?? [],
    total: raw.total,
    pageNum: raw.pageNum ?? raw.page ?? 1,
    pageSize: raw.pageSize ?? raw.size ?? 10,
  };
}

const api = createApiClient('', { timeout: 15000 });

export const metadataEntityApi = {
  page: (params: { pageNum?: number; pageSize?: number; keyword?: string; status?: number }) =>
    api.get<never, ApiResponse<PageResult<MetaEntity>>>(`${META}/entities`, { params }),

  detail: (id: number) =>
    api.get<never, ApiResponse<MetaEntity>>(`${META}/entities/${id}`),

  create: (data: CreateMetaEntityReq) =>
    api.post<never, ApiResponse<number>>(`${META}/entities`, data),

  update: (id: number, data: UpdateMetaEntityReq) =>
    api.put<never, ApiResponse<void>>(`${META}/entities/${id}`, data),

  publish: (id: number) =>
    api.post<never, ApiResponse<void>>(`${META}/entities/${id}/publish`),

  // TODO: 后端 batchPublish / batchDelete API 尚未在 MetaEntityCatalogController 中实现，以下为预留封装
  batchPublish: (ids: number[]) =>
    api.post<never, ApiResponse<{ successCount: number; failCount: number; errors: unknown[] }>>(`${META}/entities/batch-publish`, { ids }),

  batchDelete: (ids: number[]) =>
    api.post<never, ApiResponse<{ successCount: number; failCount: number; errors: unknown[] }>>(`${META}/entities/batch-delete`, { ids }),

  delete: (id: number) =>
    api.delete<never, ApiResponse<void>>(`${META}/entities/${id}`),
};

export const metadataFieldApi = {
  page: (entityId: number, params: { pageNum?: number; pageSize?: number; keyword?: string }) =>
    api.get<never, ApiResponse<PageResult<MetaField>>>(`${META}/entities/${entityId}/fields`, {
      params,
    }),

  create: (entityId: number, data: CreateMetaFieldReq) =>
    api.post<never, ApiResponse<number>>(`${META}/entities/${entityId}/fields`, data),

  update: (entityId: number, fieldId: number, data: UpdateMetaFieldReq) =>
    api.put<never, ApiResponse<void>>(`${META}/entities/${entityId}/fields/${fieldId}`, data),

  delete: (entityId: number, fieldId: number) =>
    api.delete<never, ApiResponse<void>>(`${META}/entities/${entityId}/fields/${fieldId}`),
};

export const metadataRelationApi = {
  page: (params: {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    sourceEntityId?: number;
    targetEntityId?: number;
  }) => api.get<never, ApiResponse<PageResult<MetaRelation>>>(`${META}/relationships`, { params }),

  create: (data: CreateMetaRelationReq) =>
    api.post<never, ApiResponse<number>>(`${META}/relationships`, data),

  update: (id: number, data: UpdateMetaRelationReq) =>
    api.put<never, ApiResponse<void>>(`${META}/relationships/${id}`, data),

  delete: (id: number) =>
    api.delete<never, ApiResponse<void>>(`${META}/relationships/${id}`),
};

export const runtimeRecordApi = {
  page: (entityCode: string, params?: { page?: number; size?: number }) =>
    api.get<never, ApiResponse<PageResult<RuntimeRecord>>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records`,
      { params: { page: params?.page ?? 1, size: params?.size ?? 20 } },
    ),

  get: (entityCode: string, id: string) =>
    api.get<never, ApiResponse<RuntimeRecord>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}`,
    ),

  create: (entityCode: string, body: RuntimeRecord) =>
    api.post<never, ApiResponse<RuntimeRecord>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records`,
      body,
    ),

  update: (entityCode: string, id: string, body: RuntimeRecord) =>
    api.put<never, ApiResponse<RuntimeRecord>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}`,
      body,
    ),

  delete: (entityCode: string, id: string) =>
    api.delete<never, ApiResponse<void>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}`,
    ),
};
