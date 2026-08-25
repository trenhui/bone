/**
 * 元数据领域类型（对齐 metadata-app 真实模型）
 */

export interface MetaEntity {
  id: number;
  name: string;
  code: string;
  displayName: string;
  description?: string;
  tableName: string;
  type: number;
  /** 0-GENERATIVE 1-RUNTIME */
  deliveryMode?: number;
  deliveryModeLabel?: string;
  status: number;
  statusLabel?: string;
  sortOrder?: number;
  icon?: string;
}

export interface MetaField {
  id: number;
  entityId: number;
  name: string;
  code: string;
  displayName: string;
  type: string;
  length?: number;
  required?: boolean;
  unique?: boolean;
  sortOrder?: number;
  /** 字段注释（后端 comment 语义） */
  comment?: string;
  /** 乐观锁版本 */
  version?: number;
  /** 创建时间（展示用） */
  createdAt?: string;
}

export interface MetaRelation {
  id: number;
  name: string;
  sourceEntityId: number;
  targetEntityId: number;
  type: string;
  sourceFieldId?: number;
  targetFieldId?: number;
  foreignKeyField?: string;
  required?: boolean;
  cascadeType?: string;
}

export interface CreateMetaEntityReq {
  name: string;
  code: string;
  displayName: string;
  description?: string;
  tableName: string;
  type?: number;
  deliveryMode?: number;
  icon?: string;
}

export interface UpdateMetaEntityReq {
  name: string;
  displayName: string;
  description?: string;
  tableName: string;
  sortOrder?: number;
  deliveryMode?: number;
  icon?: string;
}

export interface CreateMetaFieldReq {
  name: string;
  code: string;
  displayName: string;
  type: string;
  length?: number;
  required?: boolean;
  unique?: boolean;
  sortOrder?: number;
  /** 字段注释（后端 comment 语义） */
  comment?: string;
}

export interface UpdateMetaFieldReq {
  displayName: string;
  type: string;
  length?: number;
  required?: boolean;
  unique?: boolean;
  sortOrder?: number;
  /** 字段注释（后端 comment 语义） */
  comment?: string;
}

export interface CreateMetaRelationReq {
  name: string;
  sourceEntityId: number;
  targetEntityId: number;
  type: string;
  foreignKeyField?: string;
  required?: boolean;
  cascadeType?: string;
}

export interface UpdateMetaRelationReq {
  name: string;
  type: string;
  foreignKeyField?: string;
  required?: boolean;
  cascadeType?: string;
}

export const ENTITY_STATUS: Record<number, string> = {
  0: '草稿',
  1: '已发布',
  2: '已归档',
};

export const DELIVERY_MODE: Record<number, string> = {
  0: '生成式 (A)',
  1: '运行时 (B)',
};

export const RELATION_TYPES = ['OneToOne', 'OneToMany', 'ManyToOne', 'ManyToMany'];

export const FIELD_TYPES = ['STRING', 'INTEGER', 'LONG', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'TEXT'];

/** 字段类型 → 展示元数据（图标/描述/颜色），用于字段表单与列表 Tag 展示 */
export const FIELD_TYPE_MAP: Record<
  string,
  { icon?: string; desc: string; color: string }
> = {
  STRING: { icon: 'Aa', desc: '字符串', color: 'green' },
  INTEGER: { icon: '#', desc: '整数', color: 'blue' },
  LONG: { icon: '#', desc: '长整数', color: 'cyan' },
  DECIMAL: { icon: '.5', desc: '小数', color: 'purple' },
  BOOLEAN: { icon: 'T/F', desc: '布尔', color: 'orange' },
  DATE: { icon: '📅', desc: '日期', color: 'magenta' },
  DATETIME: { icon: '🕐', desc: '日期时间', color: 'volcano' },
  TEXT: { icon: 'T', desc: '文本', color: 'geekblue' },
};

/** 模式 B 动态行（物理表列名 → 值） */
export type RuntimeRecord = Record<string, unknown>;

/** 运行时 API 不可在表单中编辑的系统列 */
export const RUNTIME_READONLY_FIELDS = new Set([
  'id',
  'tenant_id',
  'created_at',
  'updated_at',
  'created_by',
  'updated_by',
  'deleted',
  'version',
]);

export const META_ENTITY_PUBLISHED = 1;
export const META_DELIVERY_RUNTIME = 1;
