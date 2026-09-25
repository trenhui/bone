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

// 主数据实体相关API
export const masterDataEntityApi = {
  page: (params: MasterDataEntityPageQry): Promise<ApiResponse<PageResult<MasterDataEntity>>> => {
    return apiClient.get(`${MD}/entities`, { params });
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
  page: (params: DataQualityRuleListQry): Promise<ApiResponse<PageResult<DataQualityRule>>> => {
    return apiClient.get(`${MD}/quality/rules`, { params });
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
  page: (params: MasterDataRecordListQry): Promise<ApiResponse<PageResult<MasterDataRecord>>> => {
    return apiClient.get(`${MD}/records`, { params });
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
};

export const qualityResultApi = {
  listByRecordId: (masterDataRecordId: number): Promise<ApiResponse<DataQualityResult[]>> => {
    return apiClient.get(`${MD}/quality-results`, { params: { recordId: masterDataRecordId } });
  },
};

export default {
  masterDataEntity: masterDataEntityApi,
  masterDataField: masterDataFieldApi,
  dataQualityRule: dataQualityRuleApi,
  masterDataRecord: masterDataRecordApi,
  qualityCheck: qualityCheckApi,
  qualityResult: qualityResultApi
};
