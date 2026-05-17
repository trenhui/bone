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
} from '../types';

const META = '/api/v1/metadata';

const api = axios.create({
  baseURL: '',
  timeout: 15000,
});

api.interceptors.request.use((config) => {
  const key = import.meta.env.VITE_API_KEY;
  if (key) {
    config.headers['X-API-Key'] = key;
  }
  return config;
});

api.interceptors.response.use((response) => response.data);

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
