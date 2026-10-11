/* eslint-disable @typescript-eslint/explicit-module-boundary-types */
import { createApiClient, setQiankunToken } from '@bone/shared-services';
import { normalizeTotal } from '@bone/shared-utils';
import type {
  ApiResponse,
  PageResult,
  NormalizedPageResult,
  PageResultIamCompat,
  MetaEntity,
  MetaField,
  MetaRelation,
  MetaTemplate,
  MetaTemplateField,
  EntityValidationIssue,
  PublishPreview,
  CopyEntityReq,
  ImportFromTableReq,
  ImportMetaEntityResult,
  InstantiateTemplateReq,
  CreateMetaEntityReq,
  UpdateMetaEntityReq,
  CreateMetaFieldReq,
  UpdateMetaFieldReq,
  CreateMetaRelationReq,
  UpdateMetaRelationReq,
  RuntimeRecord,
  RuntimeActionDef,
  RuntimeActionResult,
  RuntimeReferenceOption,
  RuntimeChildRow,
  RuntimeChildrenReplaceResult,
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

/**
 * 兼容 catalog（list）与engine 原始分页（records）。
 *
 * 返回 `NormalizedPageResult` 而非 `PageResult`：`total` 在本函数内已归一为
 * `number`，故对外承诺 number 类型，调用方可直接喂给 `useState<number>` / `Math.ceil`。
 */
export function normalizePage<T>(
  raw: PageResult<T> | PageResultIamCompat<T>,
): NormalizedPageResult<T> {
  const compat = raw as PageResultIamCompat<T>;
  const legacy = raw as unknown as { page?: number; size?: number };
  return {
    // 权威当前页字段是 records；list 为后端 @Deprecated 兼容 getter 的产物（规范 §5.3）。
    // 兼容读取 list 是为过渡期双键并存，@JsonIgnore 落地后可去掉。
    records: raw.records ?? compat.records ?? [],
    // 后端 total 是 java.lang.Long，运行期被全局 Long→String 序列化器输出为字符串
    // （见 doc/architecture/Bone-API-规范.md §5.3）。必须归一，否则调用方
    // `Math.ceil(total / pageSize)` 会抛 TypeError。此处 Number() 安全：
    // total 是行数计数而非雪花 ID。
    total: normalizeTotal(raw.total),
    page: raw.page ?? legacy.page ?? 1,
    size: raw.size ?? legacy.size ?? 10,
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
    page?: number;
    size?: number;
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
  batchPublish: (ids: string[]) =>
    api.post<never, ApiResponse<{ successCount: number; failCount: number; errors: string[] }>>(`${META}/entities/batch-publish`, { ids }),

  batchDelete: (ids: string[]) =>
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

  /**
   * 逆向建模（UC-IMP）：从存量物理表采集为实体草稿。
   * 先以 dryRun=true 预览（列数 / 跳过的保留列），确认后再正式导入。
   */
  importFromTable: (data: ImportFromTableReq) =>
    api.post<never, ApiResponse<ImportMetaEntityResult>>(
      `${META}/entities/import-from-table`,
      data,
    ),
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
  page: (entityId: SnowflakeId, params: { page?: number; size?: number; keyword?: string }) =>
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
    page?: number;
    size?: number;
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
  /**
   * 运行时分页查询。
   *
   * 后端契约（RuntimePageQuery.parse）：
   *   · fields = 逗号分隔列名（白名单校验）→ 此处由**列投影**产出，保证「界面配置的列」=「实际请求的列」
   *   · sort   = 逗号分隔列名，`-` 前缀降序（如 `-updated_at,insp_no`）
   *   · q      = `field:value` 单条件过滤
   * 三者此前前端均未消费（后端已实现 ⇒ 属 W0a2「能力悬空」，此处补齐接线）。
   */
  page: (
    entityCode: string,
    params?: { page?: number; size?: number; fields?: string; sort?: string; q?: string },
  ) => {
    const query: Record<string, unknown> = {
      page: params?.page ?? 1,
      size: params?.size ?? 20,
    };
    if (params?.fields) query.fields = params.fields;
    if (params?.sort) query.sort = params.sort;
    if (params?.q) query.q = params.q;
    return api.get<never, ApiResponse<PageResult<RuntimeRecord>>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records`,
      { params: query },
    );
  },

  get: (entityCode: string, id: string) =>
    api.get<never, ApiResponse<RuntimeRecord>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}`,
    ),

  create: (entityCode: string, body: RuntimeRecord) =>
    api.post<never, ApiResponse<RuntimeRecord>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records`,
      body,
    ),

  update: (entityCode: string, id: string, body: RuntimeRecord, etag?: string) =>
    api.put<never, ApiResponse<RuntimeRecord>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}`,
      body,
      etag ? { headers: { 'If-Match': etag } } : undefined,
    ),

  /**
   * 归档（软删）。
   *
   * `etag` 可选但生效（判据 R13）：带上后端按版本前置条件校验，冲突 412。
   * 归档记录进回收站，可经 {@link restore} 恢复 —— 但物理表无 deleted 列时是**物理删除**，
   * 故 UI 必须明示「不可恢复」，不能让用户以为「删了还能找回来」。
   */
  delete: (entityCode: string, id: string, etag?: string) =>
    api.delete<never, ApiResponse<void>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}`,
      etag ? { headers: { 'If-Match': etag } } : undefined,
    ),

  /** 回收站视图（只查 deleted=1 的归档记录）。 */
  trash: (
    entityCode: string,
    params?: { page?: number; size?: number; fields?: string; sort?: string; q?: string },
  ) => {
    const query: Record<string, unknown> = {
      page: params?.page ?? 1,
      size: params?.size ?? 20,
    };
    if (params?.fields) query.fields = params.fields;
    if (params?.sort) query.sort = params.sort;
    if (params?.q) query.q = params.q;
    return api.get<never, ApiResponse<PageResult<RuntimeRecord>>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/deleted`,
      { params: query },
    );
  },

  /** 恢复归档记录（回收站 → 在用）。 */
  restore: (entityCode: string, id: string) =>
    api.post<never, ApiResponse<RuntimeRecord>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}/restore`,
    ),

  /**
   * 列出某实体可用动作定义（状态机）。不带 status 返回全部（前端按 fromStatus 分组）；
   * 带 status 返回该状态下可触发的动作。
   */
  listActions: (entityCode: string, status?: string) =>
    api.get<never, ApiResponse<RuntimeActionDef[]>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/actions`,
      status ? { params: { status } } : undefined,
    ),

  /**
   * 触发某条记录的某个状态机动作。escape 动作（资金类）必须带 idempotencyKey 防重放二次记账。
   * 响应体为 { record, result, idempotent }。
   */
  invokeAction: (
    entityCode: string,
    id: string,
    actionCode: string,
    params: Record<string, unknown>,
    idempotencyKey?: string,
    etag?: string,
  ) => {
    const headers: Record<string, string> = {};
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
    // If-Match 与列表/详情返回的 ETag 对齐，避免基于陈旧快照推进状态（动作与 CRUD 同一套乐观并发语义）
    if (etag) headers['If-Match'] = etag;
    return api.post<never, ApiResponse<RuntimeActionResult>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}/actions/${encodeURIComponent(actionCode)}`,
      { params },
      Object.keys(headers).length ? { headers } : undefined,
    );
  },

  /**
   * REFERENCE 下拉选项（G10，RuntimeRelationController.lookupOptions）。
   * 契约：GET /api/v1/runtime/entities/{code}/lookup-options?field={field}&q=&limit=50
   * 返回目标实体满足条件的记录，每条含 value(id) + label(labelField 翻译)。
   * 服务端按 reference 声明过滤（filterField 生效），前端只负责渲染。
   */
  lookupOptions: (
    entityCode: string,
    field: string,
    q?: string,
    limit?: number,
  ): Promise<ApiResponse<RuntimeReferenceOption[]>> =>
    api.get<never, ApiResponse<RuntimeReferenceOption[]>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/lookup-options`,
      { params: { field, q: q ?? undefined, limit: limit ?? 50 } },
    ),

  /**
   * 子表明细列表（G10，RuntimeRelationController.listChildren）。
   * 契约：GET /api/v1/runtime/entities/{code}/records/{id}/children/{childEntityCode}
   * 返回按 orderColumn 排序的明细行，REFERENCE 字段已补 code__label（enrichReferenceLabels）。
   */
  listChildren: (
    entityCode: string,
    id: string,
    childEntityCode: string,
  ): Promise<ApiResponse<RuntimeChildRow[]>> =>
    api.get<never, ApiResponse<RuntimeChildRow[]>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}/children/${encodeURIComponent(childEntityCode)}`,
    ),

  /**
   * 子表批量替换（G10，RuntimeRelationController.replaceChildren）。
   * 契约：POST /api/v1/runtime/entities/{code}/records/{id}/children/{childEntityCode}
   * 整组替换语义：先删后插（同一事务），rows 为空即清空。行内键 = 子实体字段 code。
   */
  replaceChildren: (
    entityCode: string,
    id: string,
    childEntityCode: string,
    rows: RuntimeChildRow[],
  ): Promise<ApiResponse<RuntimeChildrenReplaceResult>> =>
    api.post<never, ApiResponse<RuntimeChildrenReplaceResult>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}/children/${encodeURIComponent(childEntityCode)}`,
      rows,
    ),
};
