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
  /** 所属 IAM 模块（应用→模块→模型分层归属；未归属为空） */
  moduleId?: number;
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

  // ===== 业界元数据 / 数据治理属性 =====
  /** 数据分级：PUBLIC/INTERNAL/CONFIDENTIAL/SECRET/TOP_SECRET */
  dataClassification?: string;
  /** 是否个人敏感信息(PII) */
  pii?: boolean;
  /** 敏感级别：L1/L2/L3/L4 */
  sensitivityLevel?: string;
  /** 数据管家/责任人 */
  dataSteward?: string;
  /** 业务术语/数据标准 */
  businessTerm?: string;
  /** 来源系统（血缘） */
  sourceSystem?: string;
  /** 枚举值/标准码表（JSON 文本） */
  enumValues?: string;
  /** 校验规则/质量规则（JSON 文本） */
  validationRules?: string;
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
  /**
   * 归属的 IAM 模块（模块上下文中创建时由前端自动携带）。
   * ⚠ 雪花 ID 禁止 Number() 转换（19 位超出 2^53 静默截断）；JSON 中以字符串透传，Jackson 自动转 Long。
   */
  moduleId?: number | string;
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

  // ===== 业界元数据 / 数据治理属性 =====
  dataClassification?: string;
  pii?: boolean;
  sensitivityLevel?: string;
  dataSteward?: string;
  businessTerm?: string;
  sourceSystem?: string;
  enumValues?: string;
  validationRules?: string;
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

  // ===== 业界元数据 / 数据治理属性 =====
  dataClassification?: string;
  pii?: boolean;
  sensitivityLevel?: string;
  dataSteward?: string;
  businessTerm?: string;
  sourceSystem?: string;
  enumValues?: string;
  validationRules?: string;
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

// ===== 业界元数据 / 数据治理枚举（对齐后端 MetaField 治理属性） =====

/** 数据分级（业界通用：公开/内部/秘密/机密/绝密） */
export const DATA_CLASSIFICATIONS = [
  { value: 'PUBLIC', label: '公开', color: 'green' },
  { value: 'INTERNAL', label: '内部', color: 'blue' },
  { value: 'CONFIDENTIAL', label: '秘密', color: 'orange' },
  { value: 'SECRET', label: '机密', color: 'volcano' },
  { value: 'TOP_SECRET', label: '绝密', color: 'red' },
] as const;

/** 敏感级别（业界通用：L1 一般/L2 较敏感/L3 敏感/L4 极敏感） */
export const SENSITIVITY_LEVELS = [
  { value: 'L1', label: 'L1 一般', color: 'green' },
  { value: 'L2', label: 'L2 较敏感', color: 'blue' },
  { value: 'L3', label: 'L3 敏感', color: 'orange' },
  { value: 'L4', label: 'L4 极敏感', color: 'red' },
] as const;

/** 数据分级 → 展示元数据（用于列表/详情 Tag） */
export const DATA_CLASSIFICATION_MAP: Record<
  string,
  { label: string; color: string }
> = Object.fromEntries(
  DATA_CLASSIFICATIONS.map((c) => [c.value, { label: c.label, color: c.color }]),
);

/** 敏感级别 → 展示元数据（用于列表/详情 Tag） */
export const SENSITIVITY_LEVEL_MAP: Record<
  string,
  { label: string; color: string }
> = Object.fromEntries(
  SENSITIVITY_LEVELS.map((s) => [s.value, { label: s.label, color: s.color }]),
);
