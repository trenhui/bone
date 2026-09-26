/** 治理域类型（G4/G5/G10/G11/G15/G16/G17 · 3a 设计方案落地） */

export interface DomainTemplate {
  id: number;
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
  id: number;
  templateId: number;
  versionNumber: string;
  changeLog?: string;
  fieldSchema?: string;
  createdAt?: string;
}

export interface MasterDataCategory {
  id: number;
  masterDataEntityId: number;
  code: string;
  name: string;
  description?: string;
  parentCategoryId?: number;
  level: number;
  sortOrder: number;
}

export interface EntitySubscription {
  id: number;
  masterDataEntityId: number;
  appId: number;
  subscribeMode: string;
  status: string;
  requestedBy?: number;
  approvedBy?: number;
  approvedAt?: string;
}

export interface StewardAssignment {
  id: number;
  masterDataEntityId: number;
  accountId: number;
  roleType: string;
  createdAt?: string;
}

export interface QualityIssue {
  id: number;
  masterDataEntityId: number;
  recordId?: number;
  checkId?: number;
  ruleId?: number;
  issueDesc: string;
  severity: string;
  status: string;
  assigneeId?: number;
  dueAt?: string;
  resolvedAt?: string;
}

export interface ReferenceSet {
  id: number;
  setCode: string;
  setName: string;
  externalStandard?: string;
  description?: string;
  status: string;
}

export interface ReferenceValue {
  id: number;
  setId: number;
  valueCode: string;
  valueName: string;
  externalCode?: string;
  sortOrder: number;
  enabled: boolean;
  /** 来源层（2026-09-26 overlay 拆分）：PLATFORM 平台标准值 / TENANT 当前租户私有扩展值 */
  scope?: 'PLATFORM' | 'TENANT';
}

export interface ModelDrift {
  id: number;
  masterDataEntityId: number;
  metaEntityId: number;
  driftType: string;
  fieldCode?: string;
  oldValue?: string;
  newValue?: string;
  destructive: boolean;
  status: string;
  detectedAt?: string;
}

export interface DataFeedback {
  id: number;
  masterDataEntityId: number;
  recordId?: number;
  appId?: number;
  feedbackType: string;
  content: string;
  status: string;
  handledResult?: string;
  createdAt?: string;
}

export interface RecordVersion {
  id: number;
  recordId: number;
  versionNumber: number;
  data: string;
  status: string;
  changeDescription?: string;
  approvedBy?: number;
  approvedAt?: string;
  createdAt?: string;
}
