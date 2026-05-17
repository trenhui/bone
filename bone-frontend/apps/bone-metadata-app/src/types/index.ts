export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  list: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

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
}

export interface UpdateMetaFieldReq {
  displayName: string;
  type: string;
  length?: number;
  required?: boolean;
  unique?: boolean;
  sortOrder?: number;
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

export const RELATION_TYPES = [
  'OneToOne',
  'OneToMany',
  'ManyToOne',
  'ManyToMany',
];

export const FIELD_TYPES = ['STRING', 'INTEGER', 'LONG', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'TEXT'];

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
