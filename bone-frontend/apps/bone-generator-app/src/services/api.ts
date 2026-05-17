import axios from 'axios';
import type { PageResult } from './types';

/** 空字符串时使用相对路径，由 vite.config 代理到 studio-generator :8085 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

const G = '/api/v1/generator';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/** 解析分页体（规范 records；兼容过渡 list） */
export function pageRecords<T>(page?: PageResult<T> | { list?: T[]; records?: T[] } | null): T[] {
  if (!page) return [];
  return page.records ?? page.list ?? [];
}

export const dataSourceApi = {
  getList: (params: { page: number; size: number; name?: string; type?: string; status?: string }) =>
    api.get(`${G}/data-sources`, { params }),

  getById: (id: string) => api.get(`${G}/data-sources/${id}`),

  create: (data: Record<string, unknown>) => api.post(`${G}/data-sources`, data),

  update: (id: string, data: Record<string, unknown>) => api.put(`${G}/data-sources/${id}`, data),

  delete: (id: string) => api.delete(`${G}/data-sources/${id}`),

  testConnection: (id: string) => api.post(`${G}/data-sources/${id}:test-connection`),

  listTables: (id: string) => api.get(`${G}/data-sources/${id}/tables`),

  syncTables: (id: string, data: { tableNames?: string[] }) =>
    api.post(`${G}/data-sources/${id}/tables:sync`, {
      dataSourceId: id,
      ...data,
    }),
};

export const metadataEntitySnapshotApi = {
  list: (params?: {
    page?: number;
    size?: number;
    tenantId?: number;
    entityCodes?: string;
    keyword?: string;
  }) => api.get(`${G}/metadata-entity-snapshots`, { params }),
};

export const templateApi = {
  getList: (params: { page: number; size: number; type?: string; status?: string }) =>
    api.get(`${G}/templates`, { params }),

  getById: (id: number) => api.get(`${G}/templates/${id}`),

  create: (data: Record<string, unknown>) => api.post(`${G}/templates`, data),

  update: (id: number, data: Record<string, unknown>) => api.put(`${G}/templates/${id}`, data),

  delete: (id: number) => api.delete(`${G}/templates/${id}`),

  publish: (id: number) => api.post(`${G}/templates/${id}:publish`),
};

/** @deprecated 使用 templateApi */
export const codeTemplateApi = templateApi;

export const codeGenerationApi = {
  generate: (data: Record<string, unknown>) => api.post(`${G}/code-generation`, data),

  getTaskStatus: (taskId: string) => api.get(`${G}/code-generation/tasks/${taskId}/status`),

  downloadCode: (taskId: string) =>
    api.get(`${G}/code-generation/tasks/${taskId}/download`, { responseType: 'blob' }),
};

export const generationTaskApi = {
  create: (data: Record<string, unknown>) => api.post(`${G}/generation-tasks`, data),
};

export const tableMetadataApi = {
  sync: (data: { dataSourceId: string; tableNames?: string[] }) =>
    dataSourceApi.syncTables(data.dataSourceId, { tableNames: data.tableNames }),

  getDataSourceTables: (dataSourceId: string) => dataSourceApi.listTables(dataSourceId),
};

export const codeGeneratorApi = {
  loadPhysicalTables: (dataSourceId: string) => dataSourceApi.listTables(dataSourceId),

  loadCatalogEntities: (params?: { tenantId?: number; entityCodes?: string }) =>
    metadataEntitySnapshotApi.list({ page: 1, size: 500, ...params }),

  generate: (data: Record<string, unknown>) => generationTaskApi.create(data),
};

export default api;
