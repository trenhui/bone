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
  type: 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'TEXT';
  length?: number;
  required: boolean;
  defaultValue?: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DataQualityRule {
  id: number;
  masterDataEntityId: number;
  name: string;
  type: 'UNIQUE' | 'FORMAT' | 'RANGE' | 'REFERENCE' | 'CUSTOM';
  expression: string;
  severity: 'ERROR' | 'WARNING' | 'INFO';
  description?: string;
  createdAt: string;
  updatedAt: string;
}

export interface QualityCheck {
  id: number;
  masterDataEntityId: number;
  startTime: string;
  endTime?: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  issueCount: number;
  createdAt: string;
}

export interface QualityReport {
  id: number;
  qualityCheckId: number;
  reportData: Record<string, unknown>;
  issueCount: number;
  createdAt: string;
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
