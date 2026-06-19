/* eslint-disable @typescript-eslint/explicit-module-boundary-types */
import axios from 'axios';
import type {
  SystemConfig,
  ConfigHistory,
  AlertRule,
  AlertEvent,
  SystemLog,
  Metrics,
  SystemInfo,
  ApiResponse,
  PageResult,
} from '@/types';

// 模块级内存 token，由 qiankun mount 生命周期写入，优先于 localStorage
let _qiankunToken: string | null = null;

/** 供 main.tsx 在 qiankun mount 时调用，将 props.token 写入内存 */
export function setQiankunToken(token: string | null) {
  _qiankunToken = token;
  if (token) {
    localStorage.setItem('token', token);
  }
}

const api = axios.create({
  baseURL: (import.meta.env.VITE_API_BASE_URL || '') + '/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  (config) => {
    // 优先级：qiankun 内存 token > window 全局 token > localStorage
    const token = _qiankunToken
      || (window as unknown as Record<string, string>).__BONE_TOKEN__
      || localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // qiankun 微应用中不直接跳转 /login，而是通知主应用处理
      const event = new CustomEvent('bone:auth:expired', { detail: { status: 401 } });
      window.dispatchEvent(event);
    }
    return Promise.reject(error);
  }
);

// 系统配置 API
export const systemConfigApi = {
  getConfig: async (params?: { keyword?: string; pageNum?: number; pageSize?: number }) => {
    const p = { page: params?.pageNum ?? 1, size: params?.pageSize ?? 100, keyword: params?.keyword ?? '' };
    const response = await api.get<ApiResponse<PageResult<SystemConfig>>>('/system/config/page', { params: p });
    return response.data;
  },

  getConfigDetail: async (id: number) => {
    const response = await api.get<ApiResponse<SystemConfig>>(`/system/config/${id}`);
    return response.data;
  },

  createConfig: async (data: { configKey: string; configValue: string; configType: string; description?: string }) => {
    const response = await api.post<ApiResponse<number>>('/system/config', data);
    return response.data;
  },

  updateConfig: async (data: { id: number; configKey: string; configValue: string; configType: string }) => {
    const response = await api.put<ApiResponse<void>>('/system/config', data);
    return response.data;
  },

  deleteConfig: async (id: number) => {
    const response = await api.delete<ApiResponse<void>>(`/system/config/${id}`);
    return response.data;
  },
};

// 监控告警 API
export const monitorApi = {
  getHealth: async () => {
    const response = await api.get<ApiResponse<SystemInfo>>('/system/health');
    return response.data;
  },

  getMetrics: async () => {
    const response = await api.get<ApiResponse<Metrics>>('/system/metrics');
    return response.data;
  },

  getAlertRules: async (params: { pageNum: number; pageSize: number }) => {
    const response = await api.get<ApiResponse<PageResult<AlertRule>>>('/system/alert/rules/page', {
      params: { page: params.pageNum, size: params.pageSize },
    });
    return response.data;
  },

  getAlertRule: async (id: number) => {
    const response = await api.get<ApiResponse<AlertRule>>(`/system/alert/rules/${id}`);
    return response.data;
  },

  createAlertRule: async (rule: { name: string; metricName: string; thresholdValue: number; alertLevel: string }) => {
    const response = await api.post<ApiResponse<number>>('/system/alert/rules', rule);
    return response.data;
  },

  updateAlertRule: async (rule: { id: number; name: string; metricName: string; thresholdValue: number; alertLevel: string }) => {
    const response = await api.put<ApiResponse<void>>('/system/alert/rules', rule);
    return response.data;
  },

  deleteAlertRule: async (id: number) => {
    const response = await api.delete<ApiResponse<void>>(`/system/alert/rules/${id}`);
    return response.data;
  },

  enableAlertRule: async (id: number) => {
    const response = await api.post<ApiResponse<void>>(`/system/alert/rules/${id}/enable`);
    return response.data;
  },

  disableAlertRule: async (id: number) => {
    const response = await api.post<ApiResponse<void>>(`/system/alert/rules/${id}/disable`);
    return response.data;
  },

  getAlertEvents: async (params: { pageNum: number; pageSize: number }) => {
    const response = await api.get<ApiResponse<PageResult<AlertEvent>>>('/system/alert/events/page', {
      params: { page: params.pageNum, size: params.pageSize },
    });
    return response.data;
  },
};

// 日志管理 API
export const logApi = {
  getLogs: async (params: {
    pageNum: number;
    pageSize: number;
    level?: string;
    service?: string;
    keyword?: string;
    startTime?: string;
    endTime?: string;
  }) => {
    const response = await api.get<ApiResponse<PageResult<SystemLog>>>('/system/logs/page', {
      params: { page: params.pageNum, size: params.pageSize, keyword: params.keyword },
    });
    return response.data;
  },

  createLog: async (data: { logLevel: string; serviceName: string; content: string }) => {
    const response = await api.post<ApiResponse<number>>('/system/logs', data);
    return response.data;
  },
};

// 控制台 API（由 bone-system 提供）
export const consoleApi = {
  getOverview: async () => {
    const response = await api.get<ApiResponse<unknown>>('/console/overview');
    return response.data;
  },
  getServices: async () => {
    const response = await api.get<ApiResponse<unknown>>('/console/services');
    return response.data;
  },
  getResources: async () => {
    const response = await api.get<ApiResponse<unknown>>('/console/resources');
    return response.data;
  },
  getMetrics: async () => {
    const response = await api.get<ApiResponse<Metrics>>('/console/metrics');
    return response.data;
  },
  getQuickActions: async () => {
    const response = await api.get<ApiResponse<unknown>>('/console/quick-actions');
    return response.data;
  },
};

// 系统部署 API
export const systemApi = {
  getInfo: async () => {
    const response = await api.get<ApiResponse<SystemInfo>>('/system/info');
    return response.data;
  },
  deploy: async (data: Record<string, unknown>) => {
    const response = await api.post<ApiResponse<unknown>>('/system/deploy', data);
    return response.data;
  },
  upgrade: async (version: string) => {
    const response = await api.post<ApiResponse<unknown>>('/system/upgrade', { version });
    return response.data;
  },
  restart: async () => {
    const response = await api.post<ApiResponse<unknown>>('/system/restart');
    return response.data;
  },
  shutdown: async () => {
    const response = await api.post<ApiResponse<unknown>>('/system/shutdown');
    return response.data;
  },
};

export default api;
