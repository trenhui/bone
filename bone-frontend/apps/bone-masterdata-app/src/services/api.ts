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
  executeCheck: (masterDataEntityId: number): Promise<ApiResponse<QualityCheck>> => {
    return apiClient.post(`${MD}/quality/check`, null, { params: { masterDataEntityId } });
  },
  getCheckResult: (qualityCheckId: number): Promise<ApiResponse<QualityReport>> => {
    return apiClient.get(`${MD}/quality/reports/${qualityCheckId}`);
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
/** [Vision] 质量检查分页/结果 API 尚未在 masterdata 服务落地 */
export const qualityCheckApi = {
  page: (params: { pageNum?: number; pageSize?: number; masterDataEntityId?: number }): Promise<ApiResponse<PageResult<QualityCheck>>> => {
    return apiClient.get(`${MD}/quality/reports`, { params });
  },
  detail: (id: number): Promise<ApiResponse<QualityCheck>> => {
    return apiClient.get(`${MD}/quality/reports/${id}`);
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
