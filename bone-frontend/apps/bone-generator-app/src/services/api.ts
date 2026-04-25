import axios from 'axios';

const API_BASE_URL = 'http://localhost:8085';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 数据源相关API
export const dataSourceApi = {
  // 获取数据源列表
  getList: (params: { page: number; size: number; name?: string; type?: string; status?: string }) =>
    api.get('/api/data-sources', { params }),
  
  // 根据ID获取数据源
  getById: (id: string) =>
    api.get(`/api/data-sources/${id}`),
  
  // 创建数据源
  create: (data: any) =>
    api.post('/api/data-sources', data),
  
  // 更新数据源
  update: (id: string, data: any) =>
    api.put(`/api/data-sources/${id}`, data),
  
  // 删除数据源
  delete: (id: string) =>
    api.delete(`/api/data-sources/${id}`),
  
  // 测试数据源连接
  testConnection: (id: string) =>
    api.post(`/api/data-sources/${id}/test`),
};

// 表元数据相关API
export const tableMetadataApi = {
  // 同步表元数据
  sync: (data: { dataSourceId: string; tableNames?: string[] }) =>
    api.post('/api/table-metadata/sync', data),
  
  // 获取数据源表列表
  getDataSourceTables: (dataSourceId: string) =>
    api.get(`/api/table-metadata/data-source/${dataSourceId}`),
};

// 代码模板相关API
export const codeTemplateApi = {
  // 获取模板列表
  getList: (params: { page: number; size: number; type?: string; status?: string }) =>
    api.get('/api/code-templates', { params }),
  
  // 根据ID获取模板
  getById: (id: number) =>
    api.get(`/api/code-templates/${id}`),
  
  // 创建模板
  create: (data: any) =>
    api.post('/api/code-templates', data),
  
  // 更新模板
  update: (id: number, data: any) =>
    api.put(`/api/code-templates/${id}`, data),
  
  // 删除模板
  delete: (id: number) =>
    api.delete(`/api/code-templates/${id}`),
  
  // 发布模板
  publish: (id: number) =>
    api.post(`/api/code-templates/${id}/publish`),
};

// 代码生成相关API
export const codeGenerationApi = {
  // 生成代码
  generate: (data: any) =>
    api.post('/api/code-generation', data),
  
  // 获取任务状态
  getTaskStatus: (taskId: string) =>
    api.get(`/api/code-generation/tasks/${taskId}/status`),
  
  // 下载生成的代码
  downloadCode: (taskId: string) =>
    api.get(`/api/code-generation/tasks/${taskId}/download`, { responseType: 'blob' }),
};

export default api;