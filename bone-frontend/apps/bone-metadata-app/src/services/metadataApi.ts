/* eslint-disable @typescript-eslint/explicit-module-boundary-types */
import axios from 'axios';
import type {
  ApiResponse,
  PageResult,
  MetaEntity,
  MetaField,
  MetaRelation,
  CreateMetaEntityReq,
  UpdateMetaEntityReq,
  CreateMetaFieldReq,
  UpdateMetaFieldReq,
  CreateMetaRelationReq,
  UpdateMetaRelationReq,
  RuntimeRecord,
} from '../types';

const META = '/api/v1/metadata';
const RUNTIME = '/api/v1/runtime';

// 模块级内存 token，由 qiankun mount 生命周期写入，优先于 localStorage
let _qiankunToken: string | null = null;

/** 供 main.tsx 在 qiankun mount 时调用，将 props.token 写入内存 */
export function setQiankunToken(token: string | null) {
  _qiankunToken = token;
  if (token) {
    localStorage.setItem('token', token);
  }
}

/** 兼容 catalog（list）与 engine 原始分页（records） */
export function normalizePage<T>(raw: PageResult<T> & { records?: T[]; page?: number; size?: number }): PageResult<T> {
  return {
    list: raw.list ?? raw.records ?? [],
    total: raw.total,
    pageNum: raw.pageNum ?? raw.page ?? 1,
    pageSize: raw.pageSize ?? raw.size ?? 10,
  };
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 15000,
});

// 请求拦截器 - 添加Token
api.interceptors.request.use((config) => {
  const key = import.meta.env.VITE_API_KEY;
  if (key) {
    config.headers['X-API-Key'] = key;
  }
  // 优先级：qiankun 内存 token > window 全局 token > localStorage
  const token = _qiankunToken
    || (window as unknown as Record<string, string>).__BONE_TOKEN__
    || localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      // qiankun 微应用中不直接跳转 /login，而是通知主应用处理
      const event = new CustomEvent('bone:auth:expired', { detail: { status: 401 } });
      window.dispatchEvent(event);
    }
    return Promise.reject(error);
  },
);

export const metadataEntityApi = {
  page: (params: { pageNum?: number; pageSize?: number; keyword?: string; status?: number }) =>
    api.get<never, ApiResponse<PageResult<MetaEntity>>>(`${META}/entities`, { params }),

  detail: (id: number) =>
    api.get<never, ApiResponse<MetaEntity>>(`${META}/entities/${id}`),

  create: (data: CreateMetaEntityReq) =>
    api.post<never, ApiResponse<number>>(`${META}/entities`, data),

  update: (id: number, data: UpdateMetaEntityReq) =>
    api.put<never, ApiResponse<void>>(`${META}/entities/${id}`, data),

  publish: (id: number) =>
    api.post<never, ApiResponse<void>>(`${META}/entities/${id}/publish`),

  delete: (id: number) =>
    api.delete<never, ApiResponse<void>>(`${META}/entities/${id}`),
};

export const metadataFieldApi = {
  page: (entityId: number, params: { pageNum?: number; pageSize?: number; keyword?: string }) =>
    api.get<never, ApiResponse<PageResult<MetaField>>>(`${META}/entities/${entityId}/fields`, {
      params,
    }),

  create: (entityId: number, data: CreateMetaFieldReq) =>
    api.post<never, ApiResponse<number>>(`${META}/entities/${entityId}/fields`, data),

  update: (entityId: number, fieldId: number, data: UpdateMetaFieldReq) =>
    api.put<never, ApiResponse<void>>(`${META}/entities/${entityId}/fields/${fieldId}`, data),

  delete: (entityId: number, fieldId: number) =>
    api.delete<never, ApiResponse<void>>(`${META}/entities/${entityId}/fields/${fieldId}`),
};

export const metadataRelationApi = {
  page: (params: {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    sourceEntityId?: number;
    targetEntityId?: number;
  }) => api.get<never, ApiResponse<PageResult<MetaRelation>>>(`${META}/relationships`, { params }),

  create: (data: CreateMetaRelationReq) =>
    api.post<never, ApiResponse<number>>(`${META}/relationships`, data),

  update: (id: number, data: UpdateMetaRelationReq) =>
    api.put<never, ApiResponse<void>>(`${META}/relationships/${id}`, data),

  delete: (id: number) =>
    api.delete<never, ApiResponse<void>>(`${META}/relationships/${id}`),
};

export const runtimeRecordApi = {
  page: (entityCode: string, params?: { page?: number; size?: number }) =>
    api.get<never, ApiResponse<PageResult<RuntimeRecord>>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records`,
      { params: { page: params?.page ?? 1, size: params?.size ?? 20 } },
    ),

  get: (entityCode: string, id: string) =>
    api.get<never, ApiResponse<RuntimeRecord>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}`,
    ),

  create: (entityCode: string, body: RuntimeRecord) =>
    api.post<never, ApiResponse<RuntimeRecord>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records`,
      body,
    ),

  update: (entityCode: string, id: string, body: RuntimeRecord) =>
    api.put<never, ApiResponse<RuntimeRecord>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}`,
      body,
    ),

  delete: (entityCode: string, id: string) =>
    api.delete<never, ApiResponse<void>>(
      `${RUNTIME}/entities/${encodeURIComponent(entityCode)}/records/${encodeURIComponent(id)}`,
    ),
};
