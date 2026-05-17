import React from 'react';

// 主数据实体相关类型
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

// 数据质量规则相关类型
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
  reportData: Record<string, any>;
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

// 主数据记录相关类型
export interface MasterDataRecord {
  id: number;
  masterDataEntityId: number;
  data: Record<string, any>;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  createdAt: string;
  updatedAt: string;
  publishTime?: string;
}

// API 响应类型
export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T = any> {
  list: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

// 表单相关类型
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
  data: Record<string, any>;
}

// 查询参数类型
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

// 导入导出相关类型
export interface ImportResult {
  successCount: number;
  failCount: number;
  errors: string[];
}

// 导航菜单类型
export interface MenuItem {
  key: string;
  label: string;
  icon: React.ReactNode;
  path: string;
}
