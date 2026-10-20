/**
 * 系统管理领域类型（对齐 system-app 真实模型）
 */

export interface SystemConfig {
  id?: string;
  key: string;
  value: string;
  description?: string;
  type: 'SYSTEM' | 'SERVICE' | 'FEATURE';
  createdAt?: string;
  updatedAt?: string;
}

export interface ConfigHistory {
  id?: string;
  configId: string;
  oldValue?: string;
  newValue: string;
  operator?: string;
  tenantId?: string;
  createdAt?: string;
}

export interface AlertRule {
  id?: string;
  name: string;
  /** 对齐后端 CreateAlertRuleReq.metricName */
  metricName: string;
  threshold: number;
  /** 对齐后端 CreateAlertRuleReq.alertLevel */
  alertLevel: 'CRITICAL' | 'WARNING' | 'INFO';
  notificationChannels: string[];
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface AlertRecord {
  id?: string;
  alertRuleId: string;
  value: number;
  message: string;
  status: 'TRIGGERED' | 'RESOLVED';
  createdAt?: string;
  resolveTime?: string;
}

export interface SystemLog {
  id?: string;
  level: 'ERROR' | 'WARN' | 'INFO' | 'DEBUG' | 'TRACE';
  service: string;
  content: string;
  traceId?: string;
  createdAt?: string;
}

export interface Metrics {
  cpu: number;
  memory: number;
  disk: number;
  apiResponseTime: number;
  errorRate: number;
  qps: number;
  dbConnections: number;
}

export interface SystemInfo {
  version: string;
  uptime: string;
  healthStatus: 'HEALTHY' | 'UNHEALTHY' | 'DEGRADED';
  services: string[];
}

/* ==================== 数据字典（两级模型，见 doc/design/modules/7a） ==================== */

/** 值域分类：ENUM 绑定代码枚举 / LIST 扁平列表 / CASCADE 可挂层级 */
export type DictCategory = 'ENUM' | 'LIST' | 'CASCADE';

/** 展示语义色，与 antd Tag 色板对齐 */
export type DictTagType = 'default' | 'info' | 'success' | 'warning' | 'error';

/** 值的技术类型（SAP Domain 口径） */
export type DictValueType = 'STRING' | 'INT' | 'DECIMAL' | 'BOOLEAN';

/** 层级视图编码（DEFAULT 为默认视图） */
export type DictHierarchyCode = string;

/** 字典类型（定义层，对齐 DictTypeResp） */
export interface DictType {
  id?: string;
  tenantId?: string;
  code: string;
  name: string;
  category?: DictCategory;
  moduleCode?: string;
  /** category=ENUM 时绑定的枚举全限定名 */
  enumClass?: string;
  /** CASCADE 层级上限，0 表示不限 */
  maxDepth?: number;
  /** 值的技术类型（SAP Domain 口径） */
  valueType?: DictValueType;
  /** 值格式正则 */
  valueRegex?: string;
  /** 层级编码分段，如 2,2,2（GB/T 2260 风格：由编码前缀推导父级） */
  codeSegments?: string;
  description?: string;
  /** 1=平台内置（禁删、编码不可改） */
  builtin?: number;
  /** 1=租户可改其项 */
  editable?: number;
  sort?: number;
  status?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** 字典项（值层，对齐 DictItemResp） */
export interface DictItem {
  id?: string;
  tenantId?: string;
  typeCode: string;
  code: string;
  label: string;
  value?: string;
  enumName?: string;
  tagType?: DictTagType;
  i18nKey?: string;
  /** 外部标准码（GB/T 2260 / ISO 4217），仅用于对接 */
  externalCode?: string;
  /** 生效区间（ISO-8601）；为空表示不限 */
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  isDefault?: number;
  sort?: number;
  status?: number;
  description?: string;
  // ---- 层级视图字段（随视图投影，不属于项本身）----
  hierarchyCode?: string;
  parentCode?: string | null;
  level?: number;
  path?: string;
  hasChildren?: boolean;
  /** 仅树形接口填充 */
  children?: DictItem[];
  createdAt?: string;
  updatedAt?: string;
}

/** 字典项译文（SAP T005T 风格：值语言无关，译文按 (code, language) 存） */
export interface DictItemText {
  typeCode?: string;
  code?: string;
  language: string;
  label: string;
  description?: string;
}

/** 层级关系（哪套视图、谁挂谁） */
export interface DictHierarchy {
  typeCode?: string;
  hierarchyCode: string;
  code: string;
  parentCode?: string | null;
  sort?: number;
}

/**
 * 下拉数据源（平台 + 租户覆盖合并，ACTIVE + DEPRECATED，DISABLED 不返回）。
 *
 * <p>三态口径（界面设计器字典打通 v1.1 §4.2/§4.5）：渲染侧按 {@link state} 过滤 ACTIVE，
 * 写校验侧接受两态。state 缺省按 ACTIVE 兼容存量响应。
 */
export interface DictOption {
  code: string;
  label: string;
  value?: string;
  tagType?: DictTagType;
  isDefault?: boolean;
  sort?: number;
  state?: 'ACTIVE' | 'DEPRECATED' | 'DISABLED';
}

/** 枚举与字典的漂移报告 */
export interface DictEnumDiff {
  typeCode: string;
  enumClass?: string;
  /** 枚举有、字典无 */
  missingInDict: string[];
  /** 字典有、枚举无（代码已删而字典未清，危险） */
  missingInEnum: string[];
  /** value 与 ordinal 不一致 */
  valueDrift: string[];
  consistent: boolean;
}

/**
 * 字典引用方清单项（界面设计器字典打通 v1.1 §5.4，对齐 metadata-server DictBinding）。
 *
 * <p>回答「这个值域被哪些实体/字段绑定」——改值域前的变更影响预览；
 * {@link drift} = 该字段的物化快照已落后于真源（待刷新）。
 */
export interface DictBinding {
  fieldId: string;
  fieldCode: string;
  fieldName: string;
  entityId: string;
  entityCode?: string;
  entityName?: string;
  /** 物化快照的选项数 */
  snapshotCount: number;
  /** 真源是否可达（false = 字典服务不可达，漂移状态无从判定） */
  resolved: boolean;
  /** 快照与真源是否有差异（新增/移除/改标签/状态变迁任一非空） */
  drift: boolean;
  /** 人类可读摘要：「新增 1 项」/「无差异」/「真源不可达」 */
  driftSummary: string;
}

/** 值域快照（导出结果 / 导入载荷同构）：类型定义 + 项 + 层级关系 + 译文 */
export interface DictExport {
  type?: DictType;
  items: DictItem[];
  hierarchies?: DictHierarchy[];
  texts?: DictItemText[];
}

/** 系统定时任务（对齐 ScheduleTaskResp） */
export interface ScheduleTask {
  id?: string;
  name: string;
  cron: string;
  handler: string;
  status: 'ENABLED' | 'DISABLED';
  lastRunAt?: string;
  nextRunAt?: string;
  createdAt?: string;
  updatedAt?: string;
}
