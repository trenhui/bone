import axios, { type AxiosResponse } from 'axios';
import type { ApiResponse, DataSource, DatabaseTable, PageResult } from './types';

type Resp<T = unknown> = Promise<AxiosResponse<ApiResponse<T>>>;

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
  getList: (params: { page: number; size: number; name?: string; type?: string; status?: string }): Resp<PageResult<DataSource>> =>
    api.get(`${G}/data-sources`, { params }),

  getById: (id: string): Resp<DataSource> => api.get(`${G}/data-sources/${id}`),

  create: (data: Record<string, unknown>): Resp<DataSource> => api.post(`${G}/data-sources`, data),

  update: (id: string, data: Record<string, unknown>): Resp<DataSource> => api.put(`${G}/data-sources/${id}`, data),

  delete: (id: string): Resp<void> => api.delete(`${G}/data-sources/${id}`),

  testConnection: (id: string): Resp<{ success?: boolean; message?: string }> =>
    api.post(`${G}/data-sources/${id}:test-connection`),

  listTables: (id: string): Resp<DatabaseTable[]> => api.get(`${G}/data-sources/${id}/tables`),

  syncTables: (id: string, data?: { tableNames?: string[] }): Resp<{ syncedCount?: number }> =>
    api.post(`${G}/data-sources/${id}/tables:sync`, {
      dataSourceId: id,
      tableNames: data?.tableNames,
    }),

  listSyncedTables: (id: string): Resp<DatabaseTable[]> => api.get(`${G}/data-sources/${id}/synced-tables`),
};

export const metadataEntitySnapshotApi = {
  list: (params?: {
    page?: number;
    size?: number;
    tenantId?: number;
    entityCodes?: string;
    keyword?: string;
  }): Resp<PageResult<Record<string, unknown>>> =>
    api.get(`${G}/metadata-entity-snapshots`, { params }),
};

export const templateApi = {
  getList: (params: { page: number; size: number; type?: string; status?: string }): Resp<PageResult<Record<string, unknown>>> =>
    api.get(`${G}/templates`, { params }),

  getById: (id: number): Resp<Record<string, unknown>> => api.get(`${G}/templates/${id}`),

  create: (data: Record<string, unknown>): Resp<Record<string, unknown>> => api.post(`${G}/templates`, data),

  update: (id: number, data: Record<string, unknown>): Resp<Record<string, unknown>> =>
    api.put(`${G}/templates/${id}`, data),

  delete: (id: number): Resp<void> => api.delete(`${G}/templates/${id}`),

  publish: (id: number): Resp<void> => api.post(`${G}/templates/${id}:publish`),
};

/** @deprecated 使用 templateApi */
export const codeTemplateApi = templateApi;

export type GeneratorOperation = {
  operationId?: string;
  type?: string;
  done: boolean;
  progress?: number;
  result?: Record<string, unknown>;
  error?: { errorCode?: string; detail?: string };
};

const sleep = (ms: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, ms));

export async function getGeneratorOperation(operationId: string): Promise<GeneratorOperation> {
  const res = await api.get(`${G}/operations/${operationId}`);
  return res.data?.data as GeneratorOperation;
}

export async function pollGeneratorOperationUntilDone(
  operationId: string,
  opts?: { intervalMs?: number; timeoutMs?: number; onProgress?: (p: number) => void },
): Promise<GeneratorOperation> {
  const intervalMs = opts?.intervalMs ?? 500;
  const deadline = Date.now() + (opts?.timeoutMs ?? 120_000);
  let last = -1;
  while (Date.now() < deadline) {
    const op = await getGeneratorOperation(operationId);
    const progress = op.progress ?? (op.done ? 100 : 0);
    if (opts?.onProgress && progress !== last) {
      last = progress;
      opts.onProgress(progress);
    }
    if (op.done) {
      if (op.error?.detail) {
        throw new Error(op.error.detail);
      }
      return op;
    }
    await sleep(intervalMs);
  }
  throw new Error('代码生成超时');
}

export type CodeGenerationOptions = {
  sync?: boolean;
  onProgress?: (progress: number) => void;
};

export const codeGenerationApi = {
  async generate(
    data: Record<string, unknown>,
    opts?: CodeGenerationOptions,
  ): Resp<string | Record<string, unknown>> {
    const params = opts?.sync != null ? { sync: opts.sync } : undefined;
    const res = await api.post<ApiResponse<{ operationId?: string; taskId?: string }>>(
      `${G}/code-generation`,
      data,
      {
        params,
        validateStatus: (s: number) => s === 200 || s === 202,
      },
    );
    if (res.status === 202) {
      const operationId =
        res.data?.data?.operationId ??
        res.data?.data?.taskId ??
        (res.headers.location ? String(res.headers.location).split('/').pop() : undefined);
      if (!operationId) {
        throw new Error('缺少 operationId');
      }
      opts?.onProgress?.(0);
      await pollGeneratorOperationUntilDone(operationId, { onProgress: opts?.onProgress });
      return {
        ...res,
        data: { ...res.data, data: operationId },
      } as AxiosResponse<ApiResponse<string>>;
    }
    return res as AxiosResponse<ApiResponse<Record<string, unknown>>>;
  },

  getTaskStatus: (taskId: string): Resp<{ status?: string; progress?: number }> =>
    api.get(`${G}/code-generation/tasks/${taskId}/status`),

  downloadCode: (taskId: string): Promise<AxiosResponse<Blob>> =>
    api.get(`${G}/code-generation/tasks/${taskId}/download`, { responseType: 'blob' }),
};

export const generationTaskApi = {
  create: (data: Record<string, unknown>): Resp<{ taskId?: string }> =>
    api.post(`${G}/generation-tasks`, data),
};

export const tableMetadataApi = {
  sync: (data: { dataSourceId: string; tableNames?: string[] }): Resp<{ syncedCount?: number }> =>
    dataSourceApi.syncTables(data.dataSourceId, { tableNames: data.tableNames }),

  getDataSourceTables: (dataSourceId: string): Resp<DatabaseTable[]> =>
    dataSourceApi.listTables(dataSourceId),
};

export const codeGeneratorApi = {
  loadPhysicalTables: (dataSourceId: string): Resp<DatabaseTable[]> =>
    dataSourceApi.listTables(dataSourceId),

  loadCatalogEntities: (
    params?: { tenantId?: number; entityCodes?: string },
  ): Resp<PageResult<Record<string, unknown>>> =>
    metadataEntitySnapshotApi.list({ page: 1, size: 500, ...params }),

  generate: (data: Record<string, unknown>): Resp<{ taskId?: string }> =>
    generationTaskApi.create(data),
};

export default api;
