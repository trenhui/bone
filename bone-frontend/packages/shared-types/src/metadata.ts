/**
 * 元数据领域类型（对齐 metadata-app 真实模型）
 */

export interface MetaEntity {
  id: string;
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
  moduleId?: string;
  /** 最后修改时间（发布脏态判定：updatedAt 晚于上次发布时刻 ⇒ 待重新发布） */
  updatedAt?: string;
  /**
   * 自动编号配置 JSON（如 {"field":"claim_no","prefix":"CLM","reset":"daily"}）；
   * 空 = 未启用。仅对自动生成界面（RUNTIME）实体生效，业务数据写入时平台生成单据号。
   */
  autoNumber?: string;
}

export interface MetaField {
  id: string;
  entityId: string;
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
  /** 最后修改时间（脏态计数：晚于上次发布时刻 ⇒ 该字段待重新发布） */
  updatedAt?: string;

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
  /** 枚举选项源声明 JSON（空 = inline 本地枚举；dict 时 enumValues 为物化快照） */
  enumSource?: string;
  /** 校验规则/质量规则（JSON 文本） */
  validationRules?: string;
}

export interface MetaRelation {
  id: string;
  name: string;
  sourceEntityId: string;
  targetEntityId: string;
  type: string;
  sourceFieldId?: string;
  targetFieldId?: string;
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
  moduleId?: string | string;
  /**
   * 自动编号配置 JSON（如 {"field":"claim_no","prefix":"CLM","reset":"daily"}）；空 = 不启用。
   * 仅对自动生成界面（RUNTIME）实体生效。
   */
  autoNumber?: string;
}

export interface UpdateMetaEntityReq {
  name: string;
  displayName: string;
  description?: string;
  tableName: string;
  sortOrder?: number;
  deliveryMode?: number;
  icon?: string;
  /**
   * 自动编号配置 JSON（如 {"field":"claim_no","prefix":"CLM","reset":"daily"}）；空 = 不启用。
   * 仅草稿实体可改；已发布实体结构锁死，更新请求中的该值被后端忽略。
   */
  autoNumber?: string;
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
  /**
   * 枚举选项源声明 JSON（doc/design/modules/11 §4.2）。
   * P0 形态：{"type":"dict","mode":"snapshot","ref":{"typeCode":"xxx"}}；空 = inline 本地枚举。
   * resolvedAt/resolvedCount/resolvedBy 为服务端物化元数据，建模者只填 type/mode/ref。
   */
  enumSource?: string;
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
  /** 枚举选项源声明 JSON（空 = inline；形态见 CreateMetaFieldReq.enumSource） */
  enumSource?: string;
  validationRules?: string;
}

export interface CreateMetaRelationReq {
  name: string;
  sourceEntityId: string;
  targetEntityId: string;
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
  0: '手写界面',
  1: '自动生成界面',
};

export const RELATION_TYPES = ['OneToOne', 'OneToMany', 'ManyToOne', 'ManyToMany'];

export const FIELD_TYPES = ['STRING', 'INTEGER', 'LONG', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'TEXT', 'ENUM'];

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
  ENUM: { icon: '◉', desc: '枚举/码表', color: 'gold' },
};

/**
 * 枚举选项（ENUM 控件：标准码表）。
 *
 * <p>{@link state} 为三态生命周期标记（界面设计器字典打通 v1.1 §4.5）：仅 dict 物化快照对 DEPRECATED
 * 项携带；缺省 = ACTIVE（inline 本地枚举与存量快照）。渲染（新表单下拉）只出现 ACTIVE； 写校验放行
 * ACTIVE + DEPRECATED；DISABLED 不进快照（拒绝）。
 */
export interface EnumOption {
  code: string;
  label: string;
  state?: 'ACTIVE' | 'DEPRECATED' | 'DISABLED';
}

/**
 * 枚举选项源声明（doc/design/modules/11 §4.2，meta_field.enum_source 的 JSON 文本）。
 * P0 仅 dict 一种非 inline 类型；reference/external 为分期预留。
 */
export interface EnumSource {
  /** inline（缺省，兼容存量）/ dict（P0）/ reference（P1）/ external（P2） */
  type: 'inline' | 'dict' | 'reference' | 'external';
  /** snapshot（默认）/ live；external 强制 live */
  mode?: 'snapshot' | 'live';
  /** 真源定位：dict={typeCode} / reference={setCode} / external={connectorId,path,...} */
  ref?: Record<string, unknown>;
  /** 服务端物化元数据（建模者只读不改） */
  resolvedAt?: string;
  resolvedCount?: number;
  resolvedBy?: string;
}

/** 选项源物化结果（手动刷新返回值） */
export interface EnumSourceRefreshResult {
  fieldId: string;
  beforeCount: number;
  afterCount: number;
}

/** 快照 vs 真源差异（option-diff 端点） */
export interface EnumSourceDiff {
  added: string[];
  removed: string[];
  labelChanged: string[];
  /** 状态变迁（如 ACTIVE→DEPRECATED 的 code 列表；v1.1 三态：让设计者提前感知选项正在下线） */
  stateChanged: string[];
  /** false = 真源不可达（前端按 ENUM_SOURCE_UNRESOLVED 语义展示） */
  resolved: boolean;
}

/**
 * 解析 enum_source JSON 文本为声明对象；空/非法返回 null（inline 语义，调用方按无绑定处理）。
 * 与 parseEnumValues 同姿态：不抛异常，脏数据静默按 inline 兼容存量。
 */
export function parseEnumSource(raw?: string | null): EnumSource | null {
  if (!raw) return null;
  try {
    const obj = JSON.parse(raw) as EnumSource;
    if (obj && typeof obj === 'object' && typeof obj.type === 'string') return obj;
    return null;
  } catch {
    return null;
  }
}

/**
 * 解析 ENUM 字段的 enumValues（JSON 文本）为选项数组。
 * 兼容 [{code,label}] 与 [{value,label}] 两种形态；可选携带 state（三态生命周期，仅 DEPRECATED 会写出）；
 * 非法输入返回 []，不抛异常。
 */
export function parseEnumValues(raw?: string | null): EnumOption[] {
  if (!raw) return [];
  try {
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr
      .map((it) => {
        if (it && typeof it === 'object') {
          const o = it as Record<string, unknown>;
          const code = o.code ?? o.value;
          const label = o.label ?? code;
          if (code == null) return null;
          const option: EnumOption = { code: String(code), label: String(label) };
          const state = o.state;
          if (state === 'DEPRECATED' || state === 'DISABLED') {
            option.state = state;
          }
          return option;
        }
        return null;
      })
      .filter((x): x is EnumOption => x !== null);
  } catch {
    return [];
  }
}

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
/** 手写界面（GENERATIVE）· PRD §0.7 双界面模式：页面在业务仓手写；显式选择时使用本常量，禁止裸写 0 */
export const META_DELIVERY_GENERATIVE = 0;

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
