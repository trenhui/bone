/**
 * 主数据领域类型（对齐 masterdata-app 真实模型）
 */

export interface MasterDataEntity {
  id: number;
  name: string;
  description?: string;
  category?: string;
  status: 'DRAFT' | 'PUBLISHED';
  createdAt: string;
  updatedAt: string;
}

export interface MasterDataField {
  id: number;
  masterDataEntityId: number;
  name: string;
  /** 字段编码：记录 data 的 JSON 以 code 为键；存量数据可能缺失，取值时需回退到 name。 */
  code?: string;
  type: 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'TEXT';
  length?: number;
  required: boolean;
  defaultValue?: string;
  description?: string;
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
  id: number;
  masterDataEntityId: number;
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
  id: number;
  masterDataEntityId: number;
  startedAt: string;
  endedAt?: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  totalRecords?: number;
  passedRecords?: number;
  failedRecords?: number;
}

export interface QualityReport {
  id: number;
  qualityCheckId: number;
  reportData: Record<string, unknown>;
  issueCount: number;
  createdAt: string;
}

/** 报告 reportData 的结构（后端 buildReportData 产出）。 */
export interface QualityReportData {
  entityId: number;
  totalRecords: number;
  rules: Array<{
    ruleId: number;
    ruleName: string;
    type: string;
    violations: number;
    samples: Array<{ recordId: number; field: string; message: string }>;
  }>;
  unsupportedRules: Array<{ ruleId: number; ruleName: string; type: string; reason: string }>;
}

export interface DataQualityResult {
  id: number;
  masterDataRecordId: number;
  dataQualityRuleId: number;
  passed: boolean;
  message: string;
  timestamp: string;
}

export interface MasterDataRecord {
  id: number;
  masterDataEntityId: number;
  data: Record<string, unknown>;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
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
  masterDataEntityId: number;
  name: string;
  type: string;
  length?: number;
  required: boolean;
  defaultValue?: string;
  description?: string;
}

export interface UpdateMasterDataFieldReq {
  name?: string;
  type?: string;
  length?: number;
  required?: boolean;
  defaultValue?: string;
  description?: string;
}

export interface CreateDataQualityRuleReq {
  masterDataEntityId: number;
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

export interface UpdateMasterDataRecordReq {
  data: Record<string, unknown>;
}

export interface MasterDataEntityPageQry {
  pageNum?: number;
  pageSize?: number;
  name?: string;
  category?: string;
  status?: string;
}

export interface MasterDataRecordListQry {
  pageNum?: number;
  pageSize?: number;
  masterDataEntityId: number;
  status?: string;
  keyword?: string;
}

export interface DataQualityRuleListQry {
  pageNum?: number;
  pageSize?: number;
  masterDataEntityId?: number;
  type?: string;
  severity?: string;
}

export interface ImportResult {
  successCount: number;
  failCount: number;
  errors: string[];
}
