/**
 * 元数据应用类型统一收敛到 @bone/shared-types
 * 本地领域类型已上提至 packages/shared-types/src/metadata.ts
 */
export * from '@bone/shared-types';
export type { ApiResponse, PageResult } from '@bone/shared-types';
export type EntityStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

import type { MetaField } from '@bone/shared-types';

// ===== 建模工作台闭环（2b 方案）新增类型 =====

/** 平台模型模板（G3/ADR-0038，tenant_id=0 平台资产，租户只读） */
export interface MetaTemplate {
  id: number;
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
  fieldId?: number | null;
  fieldName?: string | null;
}

/** 发布摘要预览（摘要级 dry-run，先行于 ADR-0039 R1 发布包） */
export interface PublishPreview {
  entityId: number;
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
  targetModuleId?: number | string;
}

export interface InstantiateTemplateReq {
  name: string;
  code: string;
  displayName: string;
  description?: string;
  tableName: string;
  /** ⚠ 雪花 ID 以字符串透传，禁止 Number()（2^53 截断） */
  moduleId?: number | string;
  icon?: string;
  deliveryMode?: number;
}
