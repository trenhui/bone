/**
 * 主数据领域类型（对齐 masterdata-app 真实模型）
 */

export interface MasterDataEntity {
  id: string;
  name: string;
  description?: string;
  category?: string;
  status: 'DRAFT' | 'PUBLISHED';
  createdAt: string;
  updatedAt: string;
}

export interface MasterDataField {
  id: string;
  masterDataEntityId: string;
  name: string;
  /** 字段编码：记录 data 的 JSON 以 code 为键；存量数据可能缺失，取值时需回退到 name。 */
  code?: string;
  type: 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'TEXT';
  length?: number;
  required: boolean;
  defaultValue?: string;
  description?: string;
  /** 显示顺序（建模期可排，列表按此升序渲染）。 */
  sortOrder?: number;
  /** 数值字段取值下限（NUMBER 类型生效），例如单价下限 0.01。 */
  minValue?: number;
  /** 数值字段取值上限（NUMBER 类型生效），例如折扣率上限 1。 */
  maxValue?: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * 数据质量规则。
 *
 * type / severity 的取值以后端为准（后端枚举是契约真源）：
 * - type：求值器支持 NOT_NULL / UNIQUE / FORMAT / RANGE / REFERENCE；CUSTOM 允许录入但检查时不求值。
 * - severity：RuleSeverity 枚举 LOW / MEDIUM / HIGH / CRITICAL（历史上前端误用 ERROR/WARNING/INFO，导致创建必 400）。
 */
export interface DataQualityRule {
  id: string;
  masterDataEntityId: string;
  name: string;
  type: 'NOT_NULL' | 'UNIQUE' | 'FORMAT' | 'RANGE' | 'REFERENCE' | 'CUSTOM';
  expression: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  description?: string;
  createdAt: string;
  updatedAt: string;
}

/** 质量检查任务（对齐后端 QualityCheckDTO）。 */
export interface QualityCheck {
  id: string;
  masterDataEntityId: string;
  startedAt: string;
  endedAt?: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  totalRecords?: number;
  passedRecords?: number;
  failedRecords?: number;
}

export interface QualityReport {
  id: string;
  qualityCheckId: string;
  reportData: Record<string, unknown>;
  issueCount: number;
  createdAt: string;
}

/** 报告 reportData 的结构（后端 buildReportData 产出）。 */
export interface QualityReportData {
  entityId: string;
  totalRecords: number;
  rules: Array<{
    ruleId: string;
    ruleName: string;
    type: string;
    violations: number;
    samples: Array<{ recordId: string; field: string; message: string }>;
  }>;
  unsupportedRules: Array<{ ruleId: string; ruleName: string; type: string; reason: string }>;
}

/**
 * 质量检查结果。
 *
 * 后端按入参返回两种粒度（见 QualityResultDTO#level），前端必须据此渲染，
 * 不能一律当"某条记录的结果"显示——汇总行里 recordId / ruleId 都是 null。
 */
export interface DataQualityResult {
  id: string;
  /** 所属质量检查任务 ID：两种粒度都有值。 */
  qualityCheckId: string;
  /** 汇总行必有值；明细行在限定实体查询时才有值。 */
  masterDataEntityId?: string;
  /** 明细行 = 记录 ID；汇总行 = null。 */
  masterDataRecordId: string | null;
  /** 明细行 = 规则 ID；汇总行 = null。 */
  dataQualityRuleId: string | null;
  /** 结果粒度：DETAIL（逐规则×记录）/ SUMMARY（任务汇总）。 */
  level: 'DETAIL' | 'SUMMARY';
  passed: boolean;
  message: string;
  timestamp: string;
}

export interface MasterDataRecord {
  id: string;
  masterDataEntityId: string;
  data: Record<string, unknown>;
  /** 六态状态机（UC-T7）：DRAFT→PENDING_APPROVAL→APPROVED→PUBLISHED→ARCHIVED；驳回退回 DRAFT */
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'PUBLISHED' | 'SUPERSEDED' | 'ARCHIVED';
  /** 业务唯一编码（租户+实体内唯一），下游按此定位记录。 */
  recordCode?: string;
  /** 显示名称：列表页/下拉框直接可读，不必再解 data JSON。 */
  displayName?: string;
  /** 生效开始时间，未开始则记录处于"待生效"。 */
  effectiveFrom?: string;
  /** 生效结束时间，已过期则记录处于"已失效"。 */
  effectiveTo?: string;
  /** 是否当前有效版本。 */
  current?: boolean;
  createdAt: string;
  updatedAt: string;
  publishTime?: string;
}

export interface CreateMasterDataEntityReq {
  name: string;
  description?: string;
  category?: string;
}

export interface UpdateMasterDataEntityReq {
  name?: string;
  description?: string;
  category?: string;
}

export interface CreateMasterDataFieldReq {
  masterDataEntityId: string;
  name: string;
  type: string;
  length?: number;
  required: boolean;
  defaultValue?: string;
  description?: string;
  sortOrder?: number;
  minValue?: number;
  maxValue?: number;
}

export interface UpdateMasterDataFieldReq {
  name?: string;
  type?: string;
  length?: number;
  required?: boolean;
  defaultValue?: string;
  description?: string;
  sortOrder?: number;
  minValue?: number;
  maxValue?: number;
}

export interface CreateDataQualityRuleReq {
  masterDataEntityId: string;
  name: string;
  type: string;
  expression: string;
  severity: string;
  description?: string;
}

export interface UpdateDataQualityRuleReq {
  name?: string;
  type?: string;
  expression?: string;
  severity?: string;
  description?: string;
}

export interface CreateMasterDataRecordReq {
  data: Record<string, unknown>;
  /** 业务唯一编码（租户+实体内唯一）。 */
  recordCode?: string;
  /** 显示名称。 */
  displayName?: string;
  /** 生效开始时间，格式 yyyy-MM-dd HH:mm:ss。 */
  effectiveFrom?: string;
  /** 生效结束时间，格式 yyyy-MM-dd HH:mm:ss。 */
  effectiveTo?: string;
}

export interface UpdateMasterDataRecordReq {
  data: Record<string, unknown>;
  recordCode?: string;
  displayName?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
}

export interface MasterDataEntityPageRequest {
  page?: number;
  size?: number;
  name?: string;
  category?: string;
  status?: string;
}

export interface MasterDataRecordListQry {
  page?: number;
  size?: number;
  masterDataEntityId: string;
  status?: string;
  keyword?: string;
  /** 仅返回当前生效记录（当前版本 + 生效窗口含此刻）。 */
  onlyCurrent?: boolean;
}

/** 导入重复策略：FAIL 重码行计入失败清单（默认）；UPDATE 按编码幂等更新。 */
export type ImportDuplicateStrategy = 'FAIL' | 'UPDATE';

export interface DataQualityRuleListQry {
  page?: number;
  size?: number;
  masterDataEntityId?: string;
  type?: string;
  severity?: string;
}

/** 导入失败明细：逐行隔离，成功行照常入库。 */
export interface ImportFailure {
  /** 1-based 行号（与 Excel 行号对齐，去掉表头后从 1 起算）。 */
  rowNumber: number;
  recordCode?: string;
  reason: string;
}

/**
 * 导入结果：部分成功语义。
 *
 * 真实场景：ERP 批量同步 1000 条里混进 2 条脏数据，若整批回滚业务方无从下手；
 * 返回"成功 N 条 + 失败行号与原因"，才能形成可执行的修正清单。
 */
export interface ImportResult {
  total: number;
  successCount: number;
  /** duplicateStrategy=UPDATE 时按编码命中并更新的行数。 */
  updatedCount: number;
  failureCount: number;
  recordIds: string[];
  failures: ImportFailure[];
}
