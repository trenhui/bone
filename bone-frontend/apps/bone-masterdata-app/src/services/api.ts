import { createApiClient, setQiankunToken } from '@bone/shared-services';
import type {
  ApiResponse,
  PageResult,
  MasterDataEntity,
  MasterDataField,
  DataQualityRule,
  MasterDataRecord,
  QualityCheck,
  QualityReport,
  DataQualityResult,
  CreateMasterDataEntityReq,
  UpdateMasterDataEntityReq,
  CreateMasterDataFieldReq,
  UpdateMasterDataFieldReq,
  CreateDataQualityRuleReq,
  UpdateDataQualityRuleReq,
  UpdateMasterDataRecordReq,
  MasterDataEntityPageQry,
  MasterDataRecordListQry,
  DataQualityRuleListQry,
  ImportResult
} from '../types';

export { setQiankunToken };

const MD = '/api/v1/masterdata';

// 创建 axios 实例（开发走 Vite 代理 → 网关 :8888）
const apiClient = createApiClient('');

/**
 * 分页结果归一化：后端 PageResult 真源字段为 records/total/page/size（bone-core PageResult），
 * 前端 PageResult<T> 声明为 list/total。此处在 api 层统一适配，调用方一律读 data.list。
 */
function normalizePage<T>(resp: ApiResponse<PageResult<T>>): ApiResponse<PageResult<T>> {
  if (resp.code === 200 && resp.data && !Array.isArray((resp.data as unknown as { list?: T[] }).list)) {
    const raw = resp.data as unknown as { records?: T[]; total?: number; page?: number; size?: number };
    return {
      ...resp,
      data: {
        list: raw.records ?? [],
        total: raw.total ?? 0,
        pageNum: raw.page ?? 1,
        pageSize: raw.size ?? 10,
      },
    };
  }
  return resp;
}

// 主数据模型相关API
export const masterDataEntityApi = {
  page: async (params: MasterDataEntityPageQry): Promise<ApiResponse<PageResult<MasterDataEntity>>> => {
    return normalizePage(await apiClient.get(`${MD}/entities`, { params }));
  },
  detail: (id: number): Promise<ApiResponse<MasterDataEntity>> => {
    return apiClient.get(`${MD}/entities/${id}`);
  },
  create: (data: CreateMasterDataEntityReq): Promise<ApiResponse<MasterDataEntity>> => {
    return apiClient.post(`${MD}/entities`, data);
  },
  update: (id: number, data: UpdateMasterDataEntityReq): Promise<ApiResponse<MasterDataEntity>> => {
    return apiClient.put(`${MD}/entities/${id}`, data);
  },
  delete: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.delete(`${MD}/entities/${id}`);
  },
  publish: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.post(`${MD}/entities/${id}/publish`);
  },
  disable: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.post(`${MD}/entities/${id}/disable`);
  },
};

// 主数据字段相关API
export const masterDataFieldApi = {
  listByEntityId: (masterDataEntityId: number): Promise<ApiResponse<MasterDataField[]>> => {
    return apiClient.get(`${MD}/entities/${masterDataEntityId}/fields`);
  },
  detail: (id: number): Promise<ApiResponse<MasterDataField>> => {
    return apiClient.get(`${MD}/fields/${id}`);
  },
  create: (data: CreateMasterDataFieldReq): Promise<ApiResponse<MasterDataField>> => {
    const entityId = data.masterDataEntityId;
    return apiClient.post(`${MD}/entities/${entityId}/fields`, data);
  },
  update: (id: number, data: UpdateMasterDataFieldReq): Promise<ApiResponse<MasterDataField>> => {
    return apiClient.put(`${MD}/fields/${id}`, data);
  },
  delete: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.delete(`${MD}/fields/${id}`);
  },
};

// 数据质量规则相关API
export const dataQualityRuleApi = {
  page: async (params: DataQualityRuleListQry): Promise<ApiResponse<PageResult<DataQualityRule>>> => {
    return normalizePage(await apiClient.get(`${MD}/quality/rules`, { params }));
  },
  detail: (id: number): Promise<ApiResponse<DataQualityRule>> => {
    return apiClient.get(`${MD}/quality/rules/${id}`);
  },
  create: (data: CreateDataQualityRuleReq): Promise<ApiResponse<DataQualityRule>> => {
    return apiClient.post(`${MD}/quality/rules`, data);
  },
  update: (id: number, data: UpdateDataQualityRuleReq): Promise<ApiResponse<DataQualityRule>> => {
    return apiClient.put(`${MD}/quality/rules/${id}`, data);
  },
  delete: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.delete(`${MD}/quality/rules/${id}`);
  },
  /** 检查为同步执行，返回的是检查任务 ID（不是任务对象）。 */
  executeCheck: (masterDataEntityId: number): Promise<ApiResponse<number>> => {
    return apiClient.post(`${MD}/quality/check`, null, { params: { masterDataEntityId } });
  },
};

// 主数据记录相关API
export const masterDataRecordApi = {
  page: async (params: MasterDataRecordListQry): Promise<ApiResponse<PageResult<MasterDataRecord>>> => {
    return normalizePage(await apiClient.get(`${MD}/records`, { params }));
  },
  detail: (id: number): Promise<ApiResponse<MasterDataRecord>> => {
    return apiClient.get(`${MD}/records/${id}`);
  },
  create: (masterDataEntityId: number, data: Record<string, unknown>): Promise<ApiResponse<MasterDataRecord>> => {
    return apiClient.post(`${MD}/records/entity/${masterDataEntityId}`, data);
  },
  update: (id: number, data: UpdateMasterDataRecordReq): Promise<ApiResponse<MasterDataRecord>> => {
    return apiClient.put(`${MD}/records/${id}`, data);
  },
  delete: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.delete(`${MD}/records/${id}`);
  },
  publish: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.post(`${MD}/records/${id}/publish`);
  },
  archive: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.post(`${MD}/records/${id}/archive`);
  },
  import: (masterDataEntityId: number, file: File): Promise<ApiResponse<ImportResult>> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('masterDataEntityId', String(masterDataEntityId));
    return apiClient.post(`${MD}/records`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      params: { masterDataEntityId },
    });
  },
  export: (masterDataEntityId: number): Promise<Blob> => {
    return apiClient.get(`${MD}/records/export`, {
      params: { masterDataEntityId },
      responseType: 'blob',
    });
  },
};

// 质量检查相关API
export const qualityCheckApi = {
  /** 后端 GET /quality/checks 返回 List（非分页），故用数组接收后自行分页展示。 */
  list: (params: { masterDataEntityId?: number }): Promise<ApiResponse<QualityCheck[]>> => {
    return apiClient.get(`${MD}/quality/checks`, { params });
  },
  detail: (id: number): Promise<ApiResponse<QualityCheck>> => {
    return apiClient.get(`${MD}/quality/checks/${id}`);
  },
};

// 质量报告API：报告按 checkId 唯一（uk_mdm_qrpt_check），报告 ID ≠ 检查 ID
export const qualityReportApi = {
  listByCheckId: (qualityCheckId: number): Promise<ApiResponse<QualityReport[]>> => {
    return apiClient.get(`${MD}/quality/reports`, { params: { qualityCheckId } });
  },
  /** 按检查任务 ID 取单份报告：/reports/{id} 的 id 是报告主键，不能拿 checkId 去打。 */
  detailByCheckId: (qualityCheckId: number): Promise<ApiResponse<QualityReport>> => {
    return apiClient.get(`${MD}/quality/checks/${qualityCheckId}/report`);
  },
};

export const qualityResultApi = {
  listByRecordId: (masterDataRecordId: number): Promise<ApiResponse<DataQualityResult[]>> => {
    return apiClient.get(`${MD}/quality-results`, { params: { recordId: masterDataRecordId } });
  },
};

// ============================================================
// 治理域 API（G4/G5/G9/G10/G11/G15/G16/G17 · 3a 设计方案落地）
// ============================================================

// 域模板（G9 / UC-P1 P2 T1）
export const domainTemplateApi = {
  page: (params?: { pageNum?: number; pageSize?: number; status?: string }): Promise<ApiResponse<any>> =>
    apiClient.get(`${MD}/templates`, { params }),
  detail: (id: number): Promise<ApiResponse<any>> => apiClient.get(`${MD}/templates/${id}`),
  create: (data: Record<string, unknown>): Promise<ApiResponse<number>> => apiClient.post(`${MD}/templates`, data),
  update: (id: number, data: Record<string, unknown>): Promise<ApiResponse<void>> =>
    apiClient.put(`${MD}/templates/${id}`, data),
  publishVersion: (id: number, data: { versionNumber: string; changeLog?: string }): Promise<ApiResponse<number>> =>
    apiClient.post(`${MD}/templates/${id}/versions`, data),
  archive: (id: number): Promise<ApiResponse<void>> => apiClient.post(`${MD}/templates/${id}/archive`),
  instantiate: (data: {
    templateId: number;
    entityCode?: string;
    name: string;
    description?: string;
    governanceTier?: string;
    owningAppId?: number;
    withFields?: boolean;
  }): Promise<ApiResponse<number>> => apiClient.post(`${MD}/templates/instantiate`, data),
  versions: (id: number): Promise<ApiResponse<any[]>> => apiClient.get(`${MD}/templates/${id}/versions`),
};

// 分类体系（G4 / UC-T3）
export const categoryApi = {
  tree: (masterDataEntityId: number): Promise<ApiResponse<any[]>> =>
    apiClient.get(`${MD}/categories`, { params: { masterDataEntityId } }),
  create: (data: Record<string, unknown>): Promise<ApiResponse<number>> => apiClient.post(`${MD}/categories`, data),
  update: (id: number, data: Record<string, unknown>): Promise<ApiResponse<void>> =>
    apiClient.put(`${MD}/categories/${id}`, data),
  delete: (id: number): Promise<ApiResponse<void>> => apiClient.delete(`${MD}/categories/${id}`),
  assignRecord: (recordId: number, categoryId: number): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/categories/assign`, { recordId, categoryId }),
  unassignRecord: (recordId: number, categoryId: number): Promise<ApiResponse<void>> =>
    apiClient.delete(`${MD}/categories/assign`, { params: { recordId, categoryId } }),
  recordCategories: (recordId: number): Promise<ApiResponse<number[]>> =>
    apiClient.get(`${MD}/categories/record/${recordId}`),
};

// 治理角色（G3 / UC-T2）
export const governanceRoleApi = {
  byEntity: (masterDataEntityId: number): Promise<ApiResponse<any[]>> =>
    apiClient.get(`${MD}/governance-roles`, { params: { masterDataEntityId } }),
  assign: (data: { masterDataEntityId: number; accountId: number; roleType: string }): Promise<ApiResponse<number>> =>
    apiClient.post(`${MD}/governance-roles`, data),
  unassign: (id: number): Promise<ApiResponse<void>> => apiClient.delete(`${MD}/governance-roles/${id}`),
};

// 消费订阅（G10 / UC-C1 C3）
export const subscriptionApi = {
  byEntity: (masterDataEntityId: number): Promise<ApiResponse<any[]>> =>
    apiClient.get(`${MD}/subscriptions`, { params: { masterDataEntityId } }),
  request: (data: { masterDataEntityId: number; appId: number; subscribeMode?: string }): Promise<ApiResponse<number>> =>
    apiClient.post(`${MD}/subscriptions`, data),
  approve: (id: number): Promise<ApiResponse<void>> => apiClient.post(`${MD}/subscriptions/${id}/approve`),
  revoke: (id: number): Promise<ApiResponse<void>> => apiClient.post(`${MD}/subscriptions/${id}/revoke`),
};

// 质量整改工单（G11 / UC-T9）
export const qualityIssueApi = {
  byEntity: (masterDataEntityId: number, status?: string): Promise<ApiResponse<any[]>> =>
    apiClient.get(`${MD}/quality-issues`, { params: { masterDataEntityId, status } }),
  openCount: (masterDataEntityId: number): Promise<ApiResponse<number>> =>
    apiClient.get(`${MD}/quality-issues/open-count`, { params: { masterDataEntityId } }),
  create: (data: Record<string, unknown>): Promise<ApiResponse<number>> =>
    apiClient.post(`${MD}/quality-issues`, data),
  fix: (id: number): Promise<ApiResponse<void>> => apiClient.post(`${MD}/quality-issues/${id}/fix`),
  close: (id: number): Promise<ApiResponse<void>> => apiClient.post(`${MD}/quality-issues/${id}/close`),
  ignore: (id: number): Promise<ApiResponse<void>> => apiClient.post(`${MD}/quality-issues/${id}/ignore`),
};

// 参考数据（G15 / §2.3）
export const referenceApi = {
  sets: (): Promise<ApiResponse<any[]>> => apiClient.get(`${MD}/reference-sets`),
  createSet: (data: Record<string, unknown>): Promise<ApiResponse<number>> =>
    apiClient.post(`${MD}/reference-sets`, data),
  archiveSet: (id: number): Promise<ApiResponse<void>> => apiClient.post(`${MD}/reference-sets/${id}/archive`),
  values: (setId: number): Promise<ApiResponse<any[]>> => apiClient.get(`${MD}/reference-sets/${setId}/values`),
  createValue: (setId: number, data: Record<string, unknown>): Promise<ApiResponse<number>> =>
    apiClient.post(`${MD}/reference-sets/${setId}/values`, data),
  disableValue: (valueId: number): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/reference-sets/values/${valueId}/disable`),
};

// 模型漂移（G16 / UC-T10）
export const driftApi = {
  byEntity: (masterDataEntityId: number): Promise<ApiResponse<any[]>> =>
    apiClient.get(`${MD}/model-drifts`, { params: { masterDataEntityId } }),
  reconcile: (masterDataEntityId: number): Promise<ApiResponse<any[]>> =>
    apiClient.post(`${MD}/model-drifts/reconcile`, null, { params: { masterDataEntityId } }),
  handle: (id: number, status: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/model-drifts/${id}/handle`, { status }),
};

// 下游反馈（G17 / UC-C4）
export const feedbackApi = {
  byEntity: (masterDataEntityId: number, status?: string): Promise<ApiResponse<any[]>> =>
    apiClient.get(`${MD}/feedbacks`, { params: { masterDataEntityId, status } }),
  submit: (data: Record<string, unknown>): Promise<ApiResponse<number>> => apiClient.post(`${MD}/feedbacks`, data),
  accept: (id: number): Promise<ApiResponse<void>> => apiClient.post(`${MD}/feedbacks/${id}/accept`),
  reject: (id: number, result?: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/feedbacks/${id}/reject`, { result }),
  complete: (id: number, result?: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/feedbacks/${id}/complete`, { result }),
};

// 记录审批（G5 / UC-T7）与模型治理配置（G3）
export const approvalApi = {
  submit: (id: number): Promise<ApiResponse<void>> => apiClient.post(`${MD}/records/${id}/submit`),
  approve: (id: number, comment?: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/records/${id}/approve`, { comment }),
  reject: (id: number, comment?: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/records/${id}/reject`, { comment }),
  versions: (id: number): Promise<ApiResponse<any[]>> => apiClient.get(`${MD}/records/${id}/versions`),
  changeGovernanceTier: (id: number, tier: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/entities/${id}/governance-tier`, { tier }),
  bindOwningApp: (id: number, owningAppId: number | null): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/entities/${id}/owning-app`, { owningAppId }),
};

export default {
  masterDataEntity: masterDataEntityApi,
  masterDataField: masterDataFieldApi,
  dataQualityRule: dataQualityRuleApi,
  masterDataRecord: masterDataRecordApi,
  qualityCheck: qualityCheckApi,
  qualityResult: qualityResultApi
};
