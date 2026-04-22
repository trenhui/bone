import axios from 'axios';
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

// 创建axios实例
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
});

// 请求拦截器
apiClient.interceptors.request.use(
  config => {
    // 可以在这里添加认证信息
    // const token = localStorage.getItem('token');
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`;
    // }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

// 响应拦截器
apiClient.interceptors.response.use(
  response => {
    return response.data;
  },
  error => {
    console.error('API Error:', error);
    return Promise.reject(error);
  }
);

// 主数据实体相关API
export const masterDataEntityApi = {
  // 分页查询
  page: (params: MasterDataEntityPageQry): Promise<ApiResponse<PageResult<MasterDataEntity>>> => {
    return apiClient.get('/master-data/entity/page', { params });
  },
  // 详情
  detail: (id: number): Promise<ApiResponse<MasterDataEntity>> => {
    return apiClient.get(`/master-data/entity/${id}`);
  },
  // 创建
  create: (data: CreateMasterDataEntityReq): Promise<ApiResponse<MasterDataEntity>> => {
    return apiClient.post('/master-data/entity', data);
  },
  // 更新
  update: (id: number, data: UpdateMasterDataEntityReq): Promise<ApiResponse<MasterDataEntity>> => {
    return apiClient.put(`/master-data/entity/${id}`, data);
  },
  // 删除
  delete: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.delete(`/master-data/entity/${id}`);
  },
  // 发布
  publish: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.post(`/master-data/entity/${id}/publish`);
  },
  // 停用
  disable: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.post(`/master-data/entity/${id}/disable`);
  }
};

// 主数据字段相关API
export const masterDataFieldApi = {
  // 根据实体ID查询字段列表
  listByEntityId: (masterDataEntityId: number): Promise<ApiResponse<MasterDataField[]>> => {
    return apiClient.get(`/master-data/field/entity/${masterDataEntityId}`);
  },
  // 详情
  detail: (id: number): Promise<ApiResponse<MasterDataField>> => {
    return apiClient.get(`/master-data/field/${id}`);
  },
  // 创建
  create: (data: CreateMasterDataFieldReq): Promise<ApiResponse<MasterDataField>> => {
    return apiClient.post('/master-data/field', data);
  },
  // 更新
  update: (id: number, data: UpdateMasterDataFieldReq): Promise<ApiResponse<MasterDataField>> => {
    return apiClient.put(`/master-data/field/${id}`, data);
  },
  // 删除
  delete: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.delete(`/master-data/field/${id}`);
  }
};

// 数据质量规则相关API
export const dataQualityRuleApi = {
  // 分页查询
  page: (params: DataQualityRuleListQry): Promise<ApiResponse<PageResult<DataQualityRule>>> => {
    return apiClient.get('/master-data/quality-rule/page', { params });
  },
  // 详情
  detail: (id: number): Promise<ApiResponse<DataQualityRule>> => {
    return apiClient.get(`/master-data/quality-rule/${id}`);
  },
  // 创建
  create: (data: CreateDataQualityRuleReq): Promise<ApiResponse<DataQualityRule>> => {
    return apiClient.post('/master-data/quality-rule', data);
  },
  // 更新
  update: (id: number, data: UpdateDataQualityRuleReq): Promise<ApiResponse<DataQualityRule>> => {
    return apiClient.put(`/master-data/quality-rule/${id}`, data);
  },
  // 删除
  delete: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.delete(`/master-data/quality-rule/${id}`);
  },
  // 执行质量检查
  executeCheck: (masterDataEntityId: number): Promise<ApiResponse<QualityCheck>> => {
    return apiClient.post(`/master-data/quality-rule/execute/${masterDataEntityId}`);
  },
  // 获取质量检查结果
  getCheckResult: (qualityCheckId: number): Promise<ApiResponse<QualityReport>> => {
    return apiClient.get(`/master-data/quality-rule/check-result/${qualityCheckId}`);
  }
};

// 主数据记录相关API
export const masterDataRecordApi = {
  // 分页查询
  page: (params: MasterDataRecordListQry): Promise<ApiResponse<PageResult<MasterDataRecord>>> => {
    return apiClient.get('/master-data/record/page', { params });
  },
  // 详情
  detail: (id: number): Promise<ApiResponse<MasterDataRecord>> => {
    return apiClient.get(`/master-data/record/${id}`);
  },
  // 创建
  create: (masterDataEntityId: number, data: Record<string, any>): Promise<ApiResponse<MasterDataRecord>> => {
    return apiClient.post(`/master-data/record/entity/${masterDataEntityId}`, data);
  },
  // 更新
  update: (id: number, data: UpdateMasterDataRecordReq): Promise<ApiResponse<MasterDataRecord>> => {
    return apiClient.put(`/master-data/record/${id}`, data);
  },
  // 删除
  delete: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.delete(`/master-data/record/${id}`);
  },
  // 发布
  publish: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.post(`/master-data/record/${id}/publish`);
  },
  // 归档
  archive: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.post(`/master-data/record/${id}/archive`);
  },
  // 导入
  import: (masterDataEntityId: number, file: File): Promise<ApiResponse<ImportResult>> => {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient.post(`/master-data/record/import/${masterDataEntityId}`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
  },
  // 导出
  export: (masterDataEntityId: number): Promise<Blob> => {
    return apiClient.get(`/master-data/record/export/${masterDataEntityId}`, {
      responseType: 'blob'
    });
  }
};

// 质量检查相关API
export const qualityCheckApi = {
  // 分页查询
  page: (params: { pageNum?: number; pageSize?: number; masterDataEntityId?: number }): Promise<ApiResponse<PageResult<QualityCheck>>> => {
    return apiClient.get('/master-data/quality-check/page', { params });
  },
  // 详情
  detail: (id: number): Promise<ApiResponse<QualityCheck>> => {
    return apiClient.get(`/master-data/quality-check/${id}`);
  }
};

// 质量结果相关API
export const qualityResultApi = {
  // 根据记录ID查询质量结果
  listByRecordId: (masterDataRecordId: number): Promise<ApiResponse<DataQualityResult[]>> => {
    return apiClient.get(`/master-data/quality-result/record/${masterDataRecordId}`);
  }
};

export default {
  masterDataEntity: masterDataEntityApi,
  masterDataField: masterDataFieldApi,
  dataQualityRule: dataQualityRuleApi,
  masterDataRecord: masterDataRecordApi,
  qualityCheck: qualityCheckApi,
  qualityResult: qualityResultApi
};
