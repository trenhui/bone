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

const api = axios.create({
  baseURL: '/api',
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
    api.get<never, ApiResponse<PageResult<MetaEntity>>>('/v1/metadata/entities', { params }),

  detail: (id: number) =>
    api.get<never, ApiResponse<MetaEntity>>(`/v1/metadata/entities/${id}`),

  create: (data: CreateMetaEntityReq) =>
    api.post<never, ApiResponse<number>>('/v1/metadata/entities', data),

  update: (id: number, data: UpdateMetaEntityReq) =>
    api.put<never, ApiResponse<void>>(`/v1/metadata/entities/${id}`, data),

  publish: (id: number) =>
    api.post<never, ApiResponse<void>>(`/v1/metadata/entities/${id}/publish`),

  delete: (id: number) =>
    api.delete<never, ApiResponse<void>>(`/v1/metadata/entities/${id}`),
};

export const metadataFieldApi = {
  page: (entityId: number, params: { pageNum?: number; pageSize?: number; keyword?: string }) =>
    api.get<never, ApiResponse<PageResult<MetaField>>>(
      `/v1/metadata/entities/${entityId}/fields`,
      { params },
    ),

  create: (entityId: number, data: CreateMetaFieldReq) =>
    api.post<never, ApiResponse<number>>(`/v1/metadata/entities/${entityId}/fields`, data),

  update: (entityId: number, fieldId: number, data: UpdateMetaFieldReq) =>
    api.put<never, ApiResponse<void>>(
      `/v1/metadata/entities/${entityId}/fields/${fieldId}`,
      data,
    ),

  delete: (entityId: number, fieldId: number) =>
    api.delete<never, ApiResponse<void>>(
      `/v1/metadata/entities/${entityId}/fields/${fieldId}`,
    ),
};

export const metadataRelationApi = {
  page: (params: {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    sourceEntityId?: number;
    targetEntityId?: number;
  }) => api.get<never, ApiResponse<PageResult<MetaRelation>>>('/v1/metadata/relationships', { params }),

  create: (data: CreateMetaRelationReq) =>
    api.post<never, ApiResponse<number>>('/v1/metadata/relationships', data),

  update: (id: number, data: UpdateMetaRelationReq) =>
    api.put<never, ApiResponse<void>>(`/v1/metadata/relationships/${id}`, data),

  delete: (id: number) =>
    api.delete<never, ApiResponse<void>>(`/v1/metadata/relationships/${id}`),
};
