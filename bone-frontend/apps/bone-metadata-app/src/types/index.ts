/**
 * 元数据应用类型统一收敛到 @bone/shared-types
 * 本地领域类型已上提至 packages/shared-types/src/metadata.ts
 */
export * from '@bone/shared-types';
export type { ApiResponse, PageResult, NormalizedPageResult } from '@bone/shared-types';
export type EntityStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

import type { MetaEntity, MetaField, RuntimeRecord } from '@bone/shared-types';
export type { RuntimeRecord };

// ===== 建模工作台闭环（2b 方案）新增类型 =====

/**
 * 建模工作台聚合数据（P0-1：GET /api/v1/metadata/modeling/workspace）。
 *
 * 一次返回统计 + 待发布草稿 + 应用→模块结构，替代此前的 N+5 次串行请求。
 * 注意：雪花 ID 一律字符串透传（后端全局 Long→String 序列化），禁止 Number()。
 */
export interface ModelingWorkspaceData {
  entityTotal: number;
  draftTotal: number;
  publishedTotal: number;
  recentDrafts: MetaEntity[];
  appModules: WorkspaceAppModules[];
}

/** 应用→模块分组（聚合端点的模块最小读模型：id/name/code） */
export interface WorkspaceAppModules {
  appId: string;
  appName: string;
  modules: { id: string; name: string; code: string }[];
}

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
  /** 交付模式（0041）：0=生成式(GENERATIVE) 1=运行时(RUNTIME)；实例化默认继承，可改选 */
  deliveryMode?: number;
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
  /**
   * 待授权权限清单（P1 onboarding）：逃逸动作（资金/外部副作用）的动作级细码。
   * 元数据服务不感知 IAM 授权态——这是「应付清单」而非「缺失清单」，已授过的码用户自行忽略。
   */
  pendingAuthorizations?: PendingAuthorization[];
  /** 交付模式后果明示：GENERATIVE 不建物理表 / RUNTIME 建表对齐（把运行期惊讶前移为创建期决策） */
  deliveryModeNotice?: string;
}

/** 待授权权限项：动作中文名 + 需授予的权限码（复制后去 IAM 授权管理粘贴） */
export interface PendingAuthorization {
  code: string;
  actionCode: string;
  actionLabel: string;
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

/**
 * 动作执行日志行（meta_runtime_action_execution，GET .../records/{id}/action-executions）。
 * P0-2 半事务时序：副作用执行前先落 PENDING 行 ⇒ 任何一次执行（含崩溃/孤儿）都留有痕迹。
 */
export interface RuntimeActionExecution {
  id: string;
  actionCode: string;
  /** 1=成功 2=失败 3=执行中(PENDING) 4=孤儿（副作用已发出、状态流转未完成，禁自动重放） */
  status: number;
  resultJson: string | null;
  operatorId: string | null;
  createdAt: string;
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
