/**
 * 元数据应用类型统一收敛到 @bone/shared-types
 * 本地领域类型已上提至 packages/shared-types/src/metadata.ts
 */
export * from '@bone/shared-types';
export type { ApiResponse, PageResult, NormalizedPageResult } from '@bone/shared-types';
export type EntityStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

import type { MetaField, RuntimeRecord } from '@bone/shared-types';
export type { RuntimeRecord };

// ===== 建模工作台闭环（2b 方案）新增类型 =====

/** 平台模型模板（G3/ADR-0038，tenant_id=0 平台资产，租户只读） */
export interface MetaTemplate {
  id: string;
  code: string;
  name: string;
  domain?: string;
  description?: string;
  currentVersion?: string;
  /** 1=已发布可实例化 */
  status?: number;
}

/** 平台模板默认字段 */
export interface MetaTemplateField {
  code: string;
  name: string;
  displayName: string;
  fieldType?: string;
  length?: number;
  required?: boolean;
  unique?: boolean;
  defaultValue?: string | null;
  comment?: string | null;
  sortOrder?: number;
}

/** 建模期静态校验问题（ERROR 阻断发布 / WARNING / INFO） */
export interface EntityValidationIssue {
  level: 'ERROR' | 'WARNING' | 'INFO';
  code: string;
  message: string;
  fieldId?: string | null;
  fieldName?: string | null;
}

/** 发布摘要预览（摘要级 dry-run，先行于 ADR-0039 R1 发布包） */
export interface PublishPreview {
  entityId: string;
  entityCode: string;
  status: number;
  deliveryMode: number;
  fields: MetaField[];
  relationCount: number;
  validationIssues: EntityValidationIssue[];
  hasBlockingErrors: boolean;
  runnable: boolean;
  physical: {
    entityCode: string;
    tableName: string;
    createTable: boolean;
    statements: string[];
    executed: number;
    status: string;
    message: string;
  } | null;
}

export interface CopyEntityReq {
  code: string;
  tableName: string;
  name?: string;
  displayName?: string;
  description?: string;
  /** ⚠ 雪花 ID 以字符串透传，禁止 Number()（2^53 截断） */
  targetModuleId?: string | string;
}

/**
 * 逆向建模请求（UC-IMP）：把存量物理表采集为目录实体草稿。
 * dryRun=true 时后端只回统计、不落库，用于导入向导的预览步骤。
 */
export interface ImportFromTableReq {
  tableName: string;
  code?: string;
  name?: string;
  displayName?: string;
  description?: string;
  /** 0=GENERATIVE / 1=RUNTIME；缺省 1（存量表已物理存在，纳管后走运行时元数据面） */
  deliveryMode?: number;
  /** 是否把平台保留列（id/tenant_id/version/deleted/审计列）也建模为业务字段，默认 false */
  includeReserved?: boolean;
  dryRun?: boolean;
}

export interface ImportMetaEntityResult {
  entityId: string | string | null;
  entityCode: string;
  tableName: string;
  importedFields: number;
  /** 被跳过的平台保留列 */
  skippedColumns: string[];
  dryRun: boolean;
}

export interface InstantiateTemplateReq {
  name: string;
  code: string;
  displayName: string;
  description?: string;
  tableName: string;
  /** ⚠ 雪花 ID 以字符串透传，禁止 Number()（2^53 截断） */
  moduleId?: string | string;
  icon?: string;
  deliveryMode?: number;
}

// ===== 运行时状态机动作（RuntimeActionController） =====

/** 动作入参声明（meta_entity_action_def.params_schema 元素）。 */
export interface RuntimeActionParamSpec {
  name: string;
  type: string;
  required: boolean;
  values?: string[];
  label?: string;
}

/** 状态机动作定义（GET /api/v1/runtime/entities/{code}/actions）。 */
export interface RuntimeActionDef {
  /** 后端 Long→string 序列化（雪花 ID 超 2^53）；前端禁止做数值运算 */
  id: string;
  entityCode: string;
  actionCode: string;
  label: string;
  fromStatus: string | null;
  toStatus: string | null;
  statusField: string;
  escape: boolean;
  handlerBean: string | null;
  params: RuntimeActionParamSpec[];
  permissionCode: string | null;
  requiresIdempotency: boolean;
  sortOrder: number;
}

/** 触发动作后的响应体（POST .../records/{id}/actions/{code}）。 */
export interface RuntimeActionResult {
  record: RuntimeRecord;
  result: Record<string, unknown>;
  idempotent: boolean;
  /** 动作码（回显，便于前端日志/埋点对齐） */
  action?: string;
  /** 状态机审计：流转前状态 */
  fromStatus?: string | null;
  /** 状态机审计：流转后状态 */
  toStatus?: string | null;
}

// ===== 运行时关系（G10：REFERENCE / CHILD_TABLE / ATTACHMENT） =====

/** REFERENCE 下拉选项（GET .../records/lookup-options，含 labelField 翻译值）。 */
export interface RuntimeReferenceOption {
  /** 目标实体记录主键（Long→string 序列化） */
  id: string;
  /** labelField 翻译值（如类别名） */
  label: string;
  [key: string]: unknown;
}

/** 子表（CHILD_TABLE）明细行 —— 与 RuntimeRecord 同构，行内键为子实体字段 code。 */
export type RuntimeChildRow = RuntimeRecord;

/** 子表批量替换响应（POST .../children/{childEntityCode}）。 */
export interface RuntimeChildrenReplaceResult {
  count: number;
  replaced: boolean;
}
