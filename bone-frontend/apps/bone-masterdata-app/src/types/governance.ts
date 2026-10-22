/** 治理域类型（G4/G5/G10/G11/G15/G16/G17 · 3a 设计方案落地） */

export interface DomainTemplate {
  id: string;
  domainCode: string;
  domainName: string;
  description?: string;
  currentVersion: string;
  defaultGovernanceTier: string;
  fieldSchema?: string;
  ruleSchema?: string;
  categorySchema?: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TemplateVersion {
  id: string;
  templateId: string;
  versionNumber: string;
  changeLog?: string;
  fieldSchema?: string;
  createdAt?: string;
}

/**
 * 主数据分类（mdm_category）。
 *
 * id / masterDataEntityId / parentCategoryId 一律声明为 string：这些都是雪花 ID（如
 * 910000000000000121），超出 2^53，Jackson 为保精度序列化成字符串。声明成 number 会
 * 与实际运行时不符，导致 Tree 的 key 比较、Map key 命中行为不可预期。
 */
export interface MasterDataCategory {
  id: string;
  masterDataEntityId: string;
  code: string;
  name: string;
  description?: string;
  parentCategoryId?: string | null;
  level: number;
  sortOrder: number;
}

export interface EntitySubscription {
  id: string;
  masterDataEntityId: string;
  appId: string;
  subscribeMode: string;
  status: string;
  requestedBy?: number;
  approvedBy?: number;
  approvedAt?: string;
}

export interface StewardAssignment {
  id: string;
  masterDataEntityId: string;
  accountId: string;
  roleType: string;
  createdAt?: string;
}

export interface QualityIssue {
  id: string;
  masterDataEntityId: string;
  recordId?: string;
  checkId?: string;
  ruleId?: string;
  issueDesc: string;
  severity: string;
  status: string;
  assigneeId?: string;
  dueAt?: string;
  resolvedAt?: string;
}

export interface ReferenceSet {
  id: string;
  setCode: string;
  setName: string;
  externalStandard?: string;
  description?: string;
  status: string;
}

export interface ReferenceValue {
  id: string;
  setId: string;
  valueCode: string;
  valueName: string;
  externalCode?: string;
  sortOrder: number;
  enabled: boolean;
  /** 来源层（2026-09-26 overlay 拆分）：PLATFORM 平台标准值 / TENANT 当前租户私有扩展值 */
  scope?: 'PLATFORM' | 'TENANT';
}

export interface ModelDrift {
  id: string;
  masterDataEntityId: string;
  metaEntityId: string;
  driftType: string;
  fieldCode?: string;
  oldValue?: string;
  newValue?: string;
  destructive: boolean;
  status: string;
  detectedAt?: string;
}

export interface DataFeedback {
  id: string;
  masterDataEntityId: string;
  recordId?: string;
  appId?: string;
  feedbackType: string;
  content: string;
  status: string;
  handledResult?: string;
  createdAt?: string;
}

/**
 * 数据标准（对齐后端 DataStandardResp / StandardRuleType）。
 *
 * id 是雪花 ID，后端 Long→String 序列化，声明为 string；ruleType 是 Integer 保持 number：
 * 1=ENCODING 编码规则（pattern 为正则）· 2=REFERENCE 引用值域（refCode 为参考数据值域编码）。
 * entityCode / fieldCode 是业务编码（非雪花 ID），创建后不可改（UpdateDataStandardReq 只接受
 * ruleType/pattern/refCode/description）。
 */
export interface DataStandard {
  id: string;
  entityCode: string;
  fieldCode: string;
  ruleType: number;
  pattern?: string;
  refCode?: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateDataStandardReq {
  entityCode: string;
  fieldCode: string;
  ruleType?: number;
  pattern?: string;
  refCode?: string;
  description?: string;
}

export interface UpdateDataStandardReq {
  ruleType?: number;
  pattern?: string;
  refCode?: string;
  description?: string;
}

/** 数据血缘边（对齐后端 LineageRecordDTO）：sourceEntity.field →[transformType]→ targetEntity.field。 */
export interface LineageRecord {
  id: string;
  sourceEntity: string;
  sourceField?: string;
  transformType?: string;
  targetEntity: string;
  targetField?: string;
  schemaName?: string;
  createdAt?: string;
}

export interface RecordLineageReq {
  sourceEntity: string;
  sourceField?: string;
  transformType?: string;
  targetEntity: string;
  targetField?: string;
  schemaName?: string;
}

export interface RecordVersion {
  id: string;
  recordId: string;
  versionNumber: number;
  data: string;
  status: string;
  changeDescription?: string;
  approvedBy?: number;
  approvedAt?: string;
  createdAt?: string;
}
