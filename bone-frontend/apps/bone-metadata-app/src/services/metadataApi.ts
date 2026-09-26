/* eslint-disable @typescript-eslint/explicit-module-boundary-types */
import { createApiClient, setQiankunToken } from '@bone/shared-services';
import type {
  ApiResponse,
  PageResult,
  PageResultIamCompat,
  MetaEntity,
  MetaField,
  MetaRelation,
  MetaTemplate,
  MetaTemplateField,
  EntityValidationIssue,
  PublishPreview,
  CopyEntityReq,
  InstantiateTemplateReq,
  CreateMetaEntityReq,
  UpdateMetaEntityReq,
  CreateMetaFieldReq,
  UpdateMetaFieldReq,
  CreateMetaRelationReq,
  UpdateMetaRelationReq,
  RuntimeRecord,
} from '../types';

export { setQiankunToken };

/**
 * 把后端统一错误响应转换为前端提示文案。
 * 后端错误体为 ApiResponse<ProblemDetail>，业务码在 data.errorCode（如 META_RUNTIME_DUPLICATE），
 * 与 message（可读详情）一并展示，便于用户/运维定位（对齐 P0-5/P0-6 服务端校验码）。
 */
export function errorMessage(res: ApiResponse<unknown>): string {
  const code = (res.data as { errorCode?: string } | null | undefined)?.errorCode;
  return code ? `[${code}] ${res.message}` : res.message;
}

const META = '/api/v1/metadata';
const RUNTIME = '/api/v1/runtime';

/** 兼容 catalog（list）与 engine 原始分页（records） */
export function normalizePage<T>(raw: PageResult<T> | PageResultIamCompat<T>): PageResult<T> {
  const compat = raw as PageResultIamCompat<T>;
  const legacy = raw as unknown as { page?: number; size?: number };
  return {
    list: raw.list ?? compat.records ?? [],
    total: raw.total,
    pageNum: raw.pageNum ?? legacy.page ?? 1,
    pageSize: raw.pageSize ?? legacy.size ?? 10,
  };
}

const api = createApiClient('', { timeout: 15000 });

/**
 * 雪花 ID 一律以字符串透传（后端全局 Long→String 序列化）。
 * 禁止 Number() 转换：18~19 位超出 2^53 会被静默截断（实体详情 500 实证）。
 */
type SnowflakeId = number | string;

export const metadataEntityApi = {
  page: (params: {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    status?: number;
    type?: number;
    deliveryMode?: number;
    moduleId?: string | number;
  }) => api.get<never, ApiResponse<PageResult<MetaEntity>>>(`${META}/entities`, { params }),

  detail: (id: SnowflakeId) =>
    api.get<never, ApiResponse<MetaEntity>>(`${META}/entities/${id}`),

  create: (data: CreateMetaEntityReq) =>
    api.post<never, ApiResponse<number>>(`${META}/entities`, data),

  update: (id: SnowflakeId, data: UpdateMetaEntityReq) =>
    api.put<never, ApiResponse<void>>(`${META}/entities/${id}`, data),

  publish: (id: SnowflakeId) =>
    api.post<never, ApiResponse<void>>(`${META}/entities/${id}/publish`),

  // 批量发布：后端 MetaEntityCatalogController.batchPublish 已实现（部分成功语义）
  batchPublish: (ids: number[]) =>
    api.post<never, ApiResponse<{ successCount: number; failCount: number; errors: unknown[] }>>(`${META}/entities/batch-publish`, { ids }),

  batchDelete: (ids: number[]) =>
    api.post<never, ApiResponse<{ successCount: number; failCount: number; errors: unknown[] }>>(`${META}/entities/batch-delete`, { ids }),

  delete: (id: SnowflakeId) =>
    api.delete<never, ApiResponse<void>>(`${META}/entities/${id}`),

  /** 建模期静态校验（UC-W5）：问题分级列表，ERROR 阻断发布 */
  validate: (id: SnowflakeId) =>
    api.get<never, ApiResponse<EntityValidationIssue[]>>(`${META}/entities/${id}/validate`),

  /** 发布摘要预览（UC-W7 摘要级 dry-run） */
  publishPreview: (id: SnowflakeId) =>
    api.get<never, ApiResponse<PublishPreview>>(`${META}/entities/${id}/publish-preview`),

  /** 实体复制（UC-W2 流程 B）：定义+字段随迁为新草稿 */
  copy: (id: SnowflakeId, data: CopyEntityReq) =>
    api.post<never, ApiResponse<number>>(`${META}/entities/${id}/copy`, data),
};

/** 平台模型模板（G3）：目录/字段为平台层只读资产；实例化=建模写 */
export const metadataTemplateApi = {
  list: (keyword?: string) =>
    api.get<never, ApiResponse<MetaTemplate[]>>(`${META}/templates`, {
      params: keyword ? { keyword } : undefined,
    }),

  fields: (templateId: SnowflakeId) =>
    api.get<never, ApiResponse<MetaTemplateField[]>>(`${META}/templates/${templateId}/fields`),

  instantiate: (templateId: SnowflakeId, data: InstantiateTemplateReq) =>
    api.post<never, ApiResponse<number>>(`${META}/templates/${templateId}/instantiate`, data),
};

export const metadataFieldApi = {
  page: (entityId: SnowflakeId, params: { pageNum?: number; pageSize?: number; keyword?: string }) =>
    api.get<never, ApiResponse<PageResult<MetaField>>>(`${META}/entities/${entityId}/fields`, {
      params,
    }),

  create: (entityId: SnowflakeId, data: CreateMetaFieldReq) =>
    api.post<never, ApiResponse<number>>(`${META}/entities/${entityId}/fields`, data),

  update: (entityId: SnowflakeId, fieldId: SnowflakeId, data: UpdateMetaFieldReq) =>
    api.put<never, ApiResponse<void>>(`${META}/entities/${entityId}/fields/${fieldId}`, data),

  delete: (entityId: SnowflakeId, fieldId: SnowflakeId) =>
    api.delete<never, ApiResponse<void>>(`${META}/entities/${entityId}/fields/${fieldId}`),
};

export const metadataRelationApi = {
  page: (params: {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    sourceEntityId?: SnowflakeId;
    targetEntityId?: SnowflakeId;
  }) => api.get<never, ApiResponse<PageResult<MetaRelation>>>(`${META}/relationships`, { params }),

  create: (data: CreateMetaRelationReq) =>
    api.post<never, ApiResponse<number>>(`${META}/relationships`, data),

  update: (id: SnowflakeId, data: UpdateMetaRelationReq) =>
    api.put<never, ApiResponse<void>>(`${META}/relationships/${id}`, data),

  delete: (id: SnowflakeId) =>
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
