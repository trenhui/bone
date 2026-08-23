import type { ApiResponse, ChangePasswordRequest, LoginRequest, LoginResponse, RefreshTokenRequest } from '@bone/shared-types';
import { createApiClient, setQiankunToken, getToken } from './apiClient';

const AUTH_BASE = '/api/v1/iam';

/**
 * 认证服务（纯 axios，D1）。
 * - token 本地读写沿用 apiClient 的 token 来源机制。
 * - login / logout / refreshToken 对接后端认证接口，返回解包后的业务数据。
 */
export const authService = {
  /** 读取当前 token（多来源） */
  getToken: getToken,

  setToken: (token: string | null): void => {
    setQiankunToken(token);
  },

  clearToken: (): void => {
    setQiankunToken(null);
  },

  /** 登录：POST /api/v1/auth/login */
  async login(payload: LoginRequest): Promise<LoginResponse> {
    const api = createApiClient(AUTH_BASE);
    const response = await api.post<never, ApiResponse<LoginResponse>>('/login', payload);
    if (response.data?.token) {
      setQiankunToken(response.data.token);
    }
    return response.data as LoginResponse;
  },

  /** 刷新令牌：POST /api/v1/auth/refresh */
  async refreshToken(payload: RefreshTokenRequest): Promise<LoginResponse> {
    const api = createApiClient(AUTH_BASE);
    const response = await api.post<never, ApiResponse<LoginResponse>>('/refresh', payload);
    if (response.data?.token) {
      setQiankunToken(response.data.token);
    }
    return response.data as LoginResponse;
  },

  /** 登出：POST /api/v1/auth/logout（服务端清除会话，本地清理 token） */
  async logout(): Promise<void> {
    try {
      const api = createApiClient(AUTH_BASE);
      await api.post<never, void>('/logout');
    } finally {
      setQiankunToken(null);
      try {
        localStorage.removeItem('token');
      } catch {
        /* ignore */
      }
    }
  },

  /** 修改密码：POST /api/v1/iam/me/change-password（IAM MeController 已提供；契约 ChangePasswordRequest{oldPassword,newPassword}） */
  async changePassword(payload: ChangePasswordRequest): Promise<void> {
    const api = createApiClient(AUTH_BASE);
    await api.post<never, void>('/me/change-password', payload);
  },
};
