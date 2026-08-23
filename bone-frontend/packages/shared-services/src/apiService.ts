import type { AxiosInstance, AxiosRequestConfig } from 'axios';
import { createApiClient } from './apiClient';

/**
 * 通用业务 API 调用封装（纯 axios，D1）。
 * 基于 createApiClient 组合统一响应解析，返回 Promise<ApiResponse<T>>。
 * 各微应用可传入自定义 baseURL（默认根路径，配合 VITE_API_BASE_URL）。
 */
export const apiService = {
  create(baseURL = '', config?: AxiosRequestConfig): AxiosInstance {
    return createApiClient(baseURL, config);
  },

  /**
   * 通用请求（返回解包后的 data）。
   * @param baseURL API 前缀，如 '/api/v1/iam'
   */
  async request<T>(url: string, options: { baseURL?: string; config?: AxiosRequestConfig } = {}): Promise<T> {
    const api = createApiClient(options.baseURL ?? '', options.config);
    const response = await api.get<unknown, T>(url);
    return response;
  },

  /**
   * 通用 GET，返回 ApiResponse<T> 包装（由调用方读取 data）。
   */
  get<T>(url: string, baseURL = '', config?: AxiosRequestConfig): Promise<T> {
    const api = createApiClient(baseURL, config);
    return api.get<unknown, T>(url);
  },

  post<T>(url: string, data?: unknown, baseURL = '', config?: AxiosRequestConfig): Promise<T> {
    const api = createApiClient(baseURL, config);
    return api.post<unknown, T>(url, data);
  },

  put<T>(url: string, data?: unknown, baseURL = '', config?: AxiosRequestConfig): Promise<T> {
    const api = createApiClient(baseURL, config);
    return api.put<unknown, T>(url, data);
  },

  delete<T>(url: string, baseURL = '', config?: AxiosRequestConfig): Promise<T> {
    const api = createApiClient(baseURL, config);
    return api.delete<unknown, T>(url, config);
  },
};
