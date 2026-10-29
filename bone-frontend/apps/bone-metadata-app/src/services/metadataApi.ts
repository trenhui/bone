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
  ModelingWorkspaceData,
  RuntimeRecord,
  RuntimeActionDef,
  RuntimeActionResult,
  RuntimeActionExecution,
  RuntimeReferenceOption,
  RuntimeChildRow,
  RuntimeChildrenReplaceResult,
  EnumOption,
  EnumSourceDiff,
  EnumSourceRefreshResult,
  DictMigrationPlan,
  DictMigrationReq,
  DictMigrationResult,
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

  /**
   * 删除实体（软删定义 + 级联动作定义）。
   * dropPhysicalTable=true 时联动 DROP RUNTIME 物理表（破坏性，须用户勾选确认后传 true）；
   * 缺省 false = 仅软删，物理表与历史数据保留。
   */
  delete: (id: SnowflakeId, dropPhysicalTable = false) =>
    api.delete<never, ApiResponse<void>>(`${META}/entities/${id}`, {
      params: { dropPhysicalTable },
    }),

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

/**
 * 建模工作台聚合（P0-1：消除模型设计页 N+5 次串行请求）。
 *
 * 契约：GET /api/v1/metadata/modeling/workspace → 统计 + 待发布草稿前 6 + 应用→模块结构。
 * 统计口径与「业务对象」页同源（后端同一 pageEntities 读模型），不再前端三次 size=1 分页凑 total。
 */
export const metadataWorkspaceApi = {
  workspace: () =>
    api.get<never, ApiResponse<ModelingWorkspaceData>>(`${META}/modeling/workspace`),
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

/**
 * 枚举选项源端点（doc/design/modules/11 §9，字段级平级路径——设计器持 fieldId 调用）。
 * 读端点后端不挂 @PreAuthorize（同字典 /options 姿态）；refresh 需 metadata:model:write。
 */
export const metadataFieldOptionsApi = {
  /** 实时解析选项：dict 来源返实时选项，inline 返已物化快照 */
  options: (fieldId: SnowflakeId) =>
    api.get<never, ApiResponse<EnumOption[]>>(`${META}/fields/${fieldId}/options`),

  /** 快照 vs 真源差异（added/removed/labelChanged；resolved=false 表示真源不可达） */
  diff: (fieldId: SnowflakeId) =>
    api.get<never, ApiResponse<EnumSourceDiff>>(`${META}/fields/${fieldId}/option-diff`),

  /** 重新物化快照（失败时后端保留旧快照并报错） */
  refresh: (fieldId: SnowflakeId) =>
    api.post<never, ApiResponse<EnumSourceRefreshResult>>(`${META}/fields/${fieldId}/options/refresh`),

  /**
   * 绑定值域版本协商（v1.1 §4.3）：返回真源当前版本与字段快照版本。
   * 前端据此判定「快照是否落后于真源」——版本不一致即自动触发刷新，字典变更分钟级自动到达绑定字段。
   */
  version: (fieldId: SnowflakeId) =>
    api.get<never, ApiResponse<{ sourceVersion: number | null; snapshotVersion: number | null }>>(
      `${META}/fields/${fieldId}/options/version`,
    ),

  /**
   * L0→L1 迁移预览（v1.1 §5.3）：本地枚举 vs 目标值域的匹配/合并计划，**不产生任何变更**。
   *
   * 必须由用户先看到计划再确认——预览与执行在后端共用同一套比对，前端不能自行推算分类，
   * 否则「确认的是 A、系统做的是 B」。
   */
  migrationPreview: (fieldId: SnowflakeId, typeCode: string) =>
    api.get<never, ApiResponse<DictMigrationPlan>>(
      `${META}/fields/${fieldId}/migrate-to-dict/preview`,
      { params: { typeCode } },
    ),

  /** 执行 L0→L1 迁移（存在冲突时必须 confirmConflicts=true，否则后端 409）。 */
  migrateToDict: (fieldId: SnowflakeId, body: DictMigrationReq) =>
    api.post<never, ApiResponse<DictMigrationResult>>(
      `${META}/fields/${fieldId}/migrate-to-dict`,
      body,
    ),
};

/**
 * 字典绑定灰度开关状态（v1.1 §6.5）。
 *
 * 前端消费点：灰度关闭时「绑定平台字典 / 升级为平台字典」入口必须禁用并说明原因——
 * 只在后端拒绝会让用户填完表单才吃 400（UI 暴露的约束必须真消费）。
 */
export const dictBindingToggleApi = {
  state: () =>
    api.get<never, ApiResponse<{ tenantId: string; enabled: boolean; source: string }>>(
      `${META}/dict-binding/toggle`,
    ),
};

/**
 * 系统字典值域预览（字段向导 dict 模式）：经同一网关打 bone-system 的「全平台唯一取数契约」
 * {@code /api/v1/system/dict/items/options}。与 bone-system-app 的 dictApi 同端点——跨应用复用
 * 契约而非各起调用，正是 doc 11「UI 只引用不复制」纪律的前端侧体现。
 */
export function previewDictOptions(typeCode: string) {
  return api.get<never, ApiResponse<EnumOption[]>>(
    '/api/v1/system/dict/items/options',
    { params: { type: typeCode } },
  );
}

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

  /**
   * 记录的动作执行时间线（按时间正序）。
   * 数据源 meta_runtime_action_execution（幂等去重 + 审计同表），含 PENDING/成功/失败/孤儿全状态。
   */
  actionExecutions: (entityCode: string, id: string) =>
    api.get<never, ApiResponse<RuntimeActionExecution[]>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}/action-executions`,
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

/** 审批任务行（`wf_task` 投影 + 所属单据坐标/摘要）。 */
export interface ApprovalTaskRow {
  taskId: string;
  instanceId: string;
  nodeCode: string;
  assigneeId: string;
  status: string;
  decision?: string | null;
  comment?: string | null;
  createdAt?: string;
  decidedAt?: string | null;
  pending: boolean;
  entityCode?: string;
  recordId?: string;
  instancePhase?: string;
  currentNode?: string;
  instanceMissing?: boolean;
  /** 行级不可见时为 null（待办条目本身仍列出）。 */
  summary?: Record<string, unknown> | null;
}

/** 单据审批概况（实例 + 待办 + 轨迹）。 */
export interface ApprovalDescribe {
  entityCode: string;
  recordId: string;
  started: boolean;
  instance?: Record<string, unknown>;
  pendingTasks?: ApprovalTaskRow[];
  trail?: ApprovalTaskRow[];
  /**
   * 挂起原因（仅 `phase=SUSPENDED` 且服务端能读到告警正文时返回）。
   *
   * <p>挂起时引擎不派单，详情页只看到「挂起 + 零待办」；不带原因就无法区分
   * 「审批人未填」「审批人就是发起人本人（职责分离）」「组织信息缺失」——现场只能翻日志。
   * 服务端读不到时该字段缺省，前端降级为通用指引。
   */
  suspendReason?: string;
  /**
   * 驳回可退回的候选节点（引擎按**流程定义**给出，不是运行历史）。
   *
   * <p>为什么不能由前端从 `trail` 推：最需要退回的场景是单级审批（第一个审批人退回发起人），
   * 此时轨迹里只有发起一步，按轨迹推候选恒为空 ⇒ 退回入口不可用。候选的权威来源是定义。
   */
  returnableNodes?: ApprovalNodeOption[];
}

/** 退回候选节点（`code` 为引擎跳转标识，`label` 用于展示）。 */
export interface ApprovalNodeOption {
  code: string;
  label: string;
  type?: string;
}

/** 站内信行（`GET /approval/inbox` 投影，接收人恒为当前登录人）。 */
export interface ApprovalInboxRow {
  id: string;
  type?: string;
  instanceId?: string;
  entityCode?: string;
  recordId?: string;
  nodeCode?: string;
  title?: string;
  body?: string;
  read?: boolean;
  createdAt?: string;
}

/** 流程实例行（`GET /approval/instances?phase=` 问题单据投影）。 */
export interface ApprovalInstanceRow {
  instanceId: string;
  entityCode?: string;
  recordId?: string;
  definitionKey?: string;
  phase: string;
  currentNode?: string;
  startedBy?: string;
  startedAt?: string;
  updatedAt?: string;
  suspended?: boolean;
  terminated?: boolean;
}

/** 问题单据响应：只截断不计数（`truncated=true` 表示该阶段问题单不止这些，上限 200）。 */
export interface ApprovalProblemList {
  phase: string;
  truncated: boolean;
  instances: ApprovalInstanceRow[];
}

/**
 * 审批中心（2026-10-25，RuntimeApprovalController）。
 *
 * 待办真源是 `wf_task` 的指派快照，不是单据上的 `approver_id` 字段过滤——
 * 后者在「任务已改派 / 指派快照与单据字段不一致」时必然失真。
 */
export const runtimeApprovalApi = {
  pending: (page = 1, size = 20): Promise<ApiResponse<PageResult<ApprovalTaskRow>>> =>
    api.get<never, ApiResponse<PageResult<ApprovalTaskRow>>>(
      `${RUNTIME}/approval/tasks/pending`,
      { params: { page, size } },
    ),

  decided: (page = 1, size = 20): Promise<ApiResponse<PageResult<ApprovalTaskRow>>> =>
    api.get<never, ApiResponse<PageResult<ApprovalTaskRow>>>(
      `${RUNTIME}/approval/tasks/decided`,
      { params: { page, size } },
    ),

  describe: (entityCode: string, recordId: string): Promise<ApiResponse<ApprovalDescribe>> =>
    api.get<never, ApiResponse<ApprovalDescribe>>(
      `${RUNTIME}/approval/records/${encodeURIComponent(entityCode)}/${encodeURIComponent(recordId)}`,
    ),

  reassign: (taskId: string, assigneeId: string, reason?: string) =>
    api.post<unknown, ApiResponse<Record<string, unknown>>>(
      `${RUNTIME}/approval/tasks/${encodeURIComponent(taskId)}/reassign`,
      { assigneeId, reason },
    ),

  urge: (taskId: string, comment?: string) =>
    api.post<unknown, ApiResponse<Record<string, unknown>>>(
      `${RUNTIME}/approval/tasks/${encodeURIComponent(taskId)}/urge`,
      { comment },
    ),

  assign: (instanceId: string, assigneeId: string, nodeCode?: string, reason?: string) =>
    api.post<unknown, ApiResponse<Record<string, unknown>>>(
      `${RUNTIME}/approval/instances/${encodeURIComponent(instanceId)}/assign`,
      { assigneeId, nodeCode, reason },
    ),

  terminate: (instanceId: string, reason: string) =>
    api.post<unknown, ApiResponse<Record<string, unknown>>>(
      `${RUNTIME}/approval/instances/${encodeURIComponent(instanceId)}/terminate`,
      { reason },
    ),

  /**
   * 转办：办理人把**自己的**待办转给他人（引擎校验操作人 == 当前办理人）。
   * 与 reassign（管理员改派）语义区分：转办是自助交接，改派是干预。
   */
  transfer: (taskId: string, assigneeId: string, comment?: string) =>
    api.post<unknown, ApiResponse<Record<string, unknown>>>(
      `${RUNTIME}/approval/tasks/${encodeURIComponent(taskId)}/transfer`,
      { assigneeId, comment },
    ),

  /** 加签：拉人会办当前节点，被加签人拿到同节点 PENDING（节点完成判定自动计入）。 */
  addSign: (taskId: string, assigneeId: string, comment?: string) =>
    api.post<unknown, ApiResponse<Record<string, unknown>>>(
      `${RUNTIME}/approval/tasks/${encodeURIComponent(taskId)}/addSign`,
      { assigneeId, comment },
    ),

  /**
   * 恢复挂起实例：组织数据修复后重跑审批人解析（与 assign 分工——assign 直接注入人，resume 按原规则重解析）。
   * 解析仍不出人时引擎保持挂起并报错。
   */
  resume: (instanceId: string, reason?: string) =>
    api.post<unknown, ApiResponse<Record<string, unknown>>>(
      `${RUNTIME}/approval/instances/${encodeURIComponent(instanceId)}/resume`,
      { reason },
    ),

  /** 我的站内信（收件箱）；`unreadOnly=true` 只看未读。接收人恒为当前登录人。 */
  inbox: (unreadOnly = false): Promise<ApiResponse<ApprovalInboxRow[]>> =>
    api.get<never, ApiResponse<ApprovalInboxRow[]>>(`${RUNTIME}/approval/inbox`, {
      params: { unreadOnly },
    }),

  /** 问题单据：按阶段列举实例（SUSPENDED 挂起死单 / REJECTED 驳回单…），更新时间倒序，上限 200 只截断不计数。 */
  instancesByPhase: (phase: string, limit = 50): Promise<ApiResponse<ApprovalProblemList>> =>
    api.get<never, ApiResponse<ApprovalProblemList>>(`${RUNTIME}/approval/instances`, {
      params: { phase, limit },
    }),
};

/**
 * 界面列投影覆盖的用户级存取（0042 · P2-④ 服务端化）。
 *
 * 契约（MetaEntityCatalogController）：
 *   GET    /entities/{entityCode}/ui-overlay → ApiResponse<string | null>（UiOverlay JSON；null=从未配置）
 *   PUT    /entities/{entityCode}/ui-overlay → body 为 overlay 对象（JsonNode），UPSERT 幂等
 *   DELETE /entities/{entityCode}/ui-overlay → 重置为默认投影（服务端软删）
 *
 * 键由后端从 JWT 推导（tenantId + userId），前端不传 —— 避免前端伪造他人偏好。
 * localStorage 键降级为离线缓存（hydrate 策略见 utils/overlaySync.ts）。
 */
export const metadataUiOverlayApi = {
  get: (entityCode: string): Promise<ApiResponse<string | null>> =>
    api.get<never, ApiResponse<string | null>>(
      `${META}/entities/${encodeURIComponent(entityCode)}/ui-overlay`,
    ),

  save: (entityCode: string, overlay: unknown): Promise<ApiResponse<void>> =>
    api.put<unknown, ApiResponse<void>>(
      `${META}/entities/${encodeURIComponent(entityCode)}/ui-overlay`,
      overlay,
    ),

  reset: (entityCode: string): Promise<ApiResponse<void>> =>
    api.delete<never, ApiResponse<void>>(
      `${META}/entities/${encodeURIComponent(entityCode)}/ui-overlay`,
    ),
};

/**
 * 实体动作定义（建模期视图，对应后端 `ActionDefSpec`）。
 * 用于界面配置器动作条的动作码下拉，避免手填 code 与真源漂移。
 */
export interface ActionDefSpec {
  actionCode: string;
  label?: string;
  fromStatus?: string;
  toStatus?: string;
  statusField?: string;
  escape?: boolean;
  handlerBean?: string;
  paramsSchema?: string;
  requiresIdempotency?: boolean;
  sortOrder?: number;
}

/** 业务对象界面 schema 响应（团队级；null=从未配置，前端回落列投影） */
export interface UiSchemaResp {
  json: string;
  minor?: number;
  /** 乐观锁版本（P0-3 后端落盘后回填；缺省=0，前端 If-Match 透传；线上传字符串，读侧归一为 number） */
  lockVersion?: number | string;
  updatedAt?: string;
  /** 已发布快照 JSON（发布时冻结、线上运行真源；从未发布为 null）。用于「待发布/已发布」三态与线上对照预览。 */
  publishedSchemaJson?: string | null;
  /** 已发布时对应的实体版本（snapshotOnPublish 写入；从未发布为 null）。 */
  publishedVersion?: number | null;
}

/**
 * 业务对象界面 schema 校验问题（UI-REF-xx 码，与实体校验码正交）。
 *
 * 后端 `POST /entities/{id}/ui-schema/validate` 实际返回 `EntityValidationIssue`
 * （level: ERROR/WARNING/INFO）——ERROR 为发布硬门禁判据（与实体校验同语义），
 * WARNING/INFO 不阻断。level 缺省按非 ERROR 处理（前向兼容旧响应）。
 */
export interface UiSchemaIssue {
  level?: 'ERROR' | 'WARNING' | 'INFO';
  code: string;
  message: string;
  fieldCode?: string;
}

/** 模板更新检查（base-update：digest 只含计数，不返全量） */
export interface TemplateUpdateCheck {
  hasUpdate: boolean;
  reason?: string;
  baseVersion?: string;
  currentVersion?: string;
  /** 变更规模摘要（sections/rules/actions 增减数） */
  digest?: { sections?: number; rules?: number; actions?: number };
}

/** 三路归并预览项（base.old → base.new → instance.exclusive） */
export interface MergePreviewItem {
  path: string;
  kind: string;
  /** autoMerged=租户未改过自动取新版；conflict=租户改过保留实例值 + WARNING */
  outcome: 'autoMerged' | 'conflict';
  baseNew?: unknown;
  instance?: unknown;
}

export interface MergePreview {
  autoMerged: MergePreviewItem[];
  conflicts: MergePreviewItem[];
  templateVersion: string;
}

/**
 * 业务对象界面 schema（团队级，P0 协议驱动界面）。
 *
 * 契约（MetaEntityCatalogController，与 ui-overlay 同控制器内聚；键走实体主键 `{id}`）：
 *   GET    /entities/{id}/ui-schema            → ApiResponse<UiSchemaResp | null>
 *   PUT    /entities/{id}/ui-schema            → 头 If-Match: <lockVersion> + body {json}，全量替换幂等
 *            （锁版本走标准头不进 body，与 runtime invokeAction 的 If-Match/ETag/412 同语义，技术方案 §3.2）
 *   DELETE /entities/{id}/ui-schema            → 删草稿行（已发布快照保留）
 *   POST   /entities/{id}/ui-schema/validate   → UiSchemaIssue[]
 *   GET    /entities/{id}/ui-schema/base-update → TemplateUpdateCheck（模板跟随三步之一直查口）
 *   POST   /entities/{id}/ui-schema/merge-preview → MergePreview（三路归并预览，服务端算前端只确认）
 *   POST   /entities/{id}/ui-schema/merge-confirm → 头 If-Match + {acceptConflicts, templateVersion}
 * 运行时零新增端点：渲染数据继续走 runtime records/children/lookup/actions 通道。
 */
export const metadataUiSchemaApi = {
  get: (id: SnowflakeId): Promise<ApiResponse<UiSchemaResp | null>> =>
    api.get<never, ApiResponse<UiSchemaResp | null>>(
      `${META}/entities/${encodeURIComponent(String(id))}/ui-schema`,
    ),

  save: (id: SnowflakeId, json: string, lockVersion: number): Promise<ApiResponse<UiSchemaResp>> =>
    api.put<unknown, ApiResponse<UiSchemaResp>>(
      `${META}/entities/${encodeURIComponent(String(id))}/ui-schema`,
      { json },
      { headers: { 'If-Match': String(lockVersion) } },
    ),

  remove: (id: SnowflakeId): Promise<ApiResponse<void>> =>
    api.delete<never, ApiResponse<void>>(
      `${META}/entities/${encodeURIComponent(String(id))}/ui-schema`,
    ),

  validate: (id: SnowflakeId): Promise<ApiResponse<UiSchemaIssue[]>> =>
    api.post<never, ApiResponse<UiSchemaIssue[]>>(
      `${META}/entities/${encodeURIComponent(String(id))}/ui-schema/validate`,
    ),

  baseUpdate: (id: SnowflakeId): Promise<ApiResponse<TemplateUpdateCheck>> =>
    api.get<never, ApiResponse<TemplateUpdateCheck>>(
      `${META}/entities/${encodeURIComponent(String(id))}/ui-schema/base-update`,
    ),

  mergePreview: (id: SnowflakeId): Promise<ApiResponse<MergePreview>> =>
    api.post<never, ApiResponse<MergePreview>>(
      `${META}/entities/${encodeURIComponent(String(id))}/ui-schema/merge-preview`,
    ),

  mergeConfirm: (
    id: SnowflakeId, acceptConflicts: boolean, templateVersion: string, lockVersion: number,
  ): Promise<ApiResponse<UiSchemaResp>> =>
    api.post<unknown, ApiResponse<UiSchemaResp>>(
      `${META}/entities/${encodeURIComponent(String(id))}/ui-schema/merge-confirm`,
      { acceptConflicts, templateVersion },
      { headers: { 'If-Match': String(lockVersion) } },
    ),
};

/**
 * 实体动作定义建模接口（UI Schema actionBar 的动作码真源）。
 * 契约：GET/POST /entities/{id}/action-defs，DELETE /entities/{id}/action-defs/{actionCode}
 */
export const metadataActionDefApi = {
  list: (id: SnowflakeId): Promise<ApiResponse<ActionDefSpec[]>> =>
    api.get<never, ApiResponse<ActionDefSpec[]>>(
      `${META}/entities/${encodeURIComponent(String(id))}/action-defs`,
    ),
};
