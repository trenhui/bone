import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios';
import type { ApiResponse } from '@bone/shared-types';

/**
 * Token 来源优先级：
 * 1. qiankun props 注入的内存 token
 * 2. window.__BONE_GLOBAL_CONTEXT__.token（Shell 下发的全局上下文）
 * 3. window.__BONE_TOKEN__（兼容旧代码）
 * 4. localStorage
 * 5. URL 参数 token
 */
let _qiankunToken: string | null = null;

export function setQiankunToken(token: string | null) {
  _qiankunToken = token;
  if (token) {
    localStorage.setItem('token', token);
  }
}

export function getToken(): string | null {
  // 优先从全局上下文获取
  const globalCtx = (window as unknown as { __BONE_GLOBAL_CONTEXT__?: { token?: string | null } }).__BONE_GLOBAL_CONTEXT__;
  return (
    _qiankunToken ||
    globalCtx?.token ||
    (window as unknown as Record<string, string>).__BONE_TOKEN__ ||
    localStorage.getItem('token') ||
    new URLSearchParams(window.location.search).get('token')
  );
}

/** 获取全局上下文（Shell 通过 props / window 下发） */
export function getGlobalContext() {
  return (window as unknown as { __BONE_GLOBAL_CONTEXT__?: Record<string, unknown> }).__BONE_GLOBAL_CONTEXT__;
}

/**
 * 创建配置化的 axios 实例
 *
 * 各微应用用法：
 * ```ts
 * import { createApiClient } from '@bone/shared-services';
 * const api = createApiClient('/api/v1/iam');
 * export default api;
 * ```
 */
export function createApiClient(baseURL: string, config?: AxiosRequestConfig): AxiosInstance {
  const instance = axios.create({
    baseURL: (import.meta.env.VITE_API_BASE_URL || '') + baseURL,
    timeout: 10000,
    ...config,
  });

  // 请求拦截器：自动注入 Token
  instance.interceptors.request.use((req) => {
    const token = getToken();
    if (token) {
      req.headers.Authorization = `Bearer ${token}`;
    }
    return req;
  });

  // 响应拦截器：解包 ApiResponse + 统一错误处理
  instance.interceptors.response.use(
    (response) => response.data,
    (error) => {
      if (error.response?.status === 401) {
        // 微应用不直接跳转，通知主应用处理
        window.dispatchEvent(new CustomEvent('bone:auth:expired', { detail: { status: 401 } }));
      }
      // 优先使用后端返回的 ApiResponse 中的错误信息
      const backendMessage = error.response?.data?.message;
      if (backendMessage) {
        error.displayMessage = backendMessage;
      }
      return Promise.reject(error);
    }
  );

  return instance;
}

/**
 * 类型安全的 API 调用辅助函数
 * 配合 createApiClient 使用，确保返回类型正确
 *
 * ```ts
 * const data = await apiCall(api.get<never, ApiResponse<User>>('/users/1'));
 * // data 类型为 User
 * ```
 */
export async function apiCall<T>(promise: Promise<ApiResponse<T>>): Promise<T> {
  const response = await promise;
  return response.data;
}
