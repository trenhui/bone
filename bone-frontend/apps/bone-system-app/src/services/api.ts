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

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
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
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// 系统配置 API
export const systemConfigApi = {
  getConfig: async () => {
    const response = await api.get<ApiResponse<SystemConfig[]>>('/system/config');
    return response.data;
  },

  updateConfig: async (config: SystemConfig) => {
    const response = await api.put<ApiResponse<void>>('/system/config', config);
    return response.data;
  },

  getConfigHistory: async (configId: number) => {
    const response = await api.get<ApiResponse<ConfigHistory[]>>(`/system/config/history?configId=${configId}`);
    return response.data;
  },

  importConfig: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post<ApiResponse<void>>('/system/config/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },

  exportConfig: async () => {
    const response = await api.get('/system/config/export', { responseType: 'blob' });
    return response;
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
    const response = await api.get<ApiResponse<PageResult<AlertRule>>>('/system/alerts', { params });
    return response.data;
  },

  createAlertRule: async (rule: AlertRule) => {
    const response = await api.post<ApiResponse<void>>('/system/alerts', rule);
    return response.data;
  },

  updateAlertRule: async (id: number, rule: AlertRule) => {
    const response = await api.put<ApiResponse<void>>(`/system/alerts/${id}`, rule);
    return response.data;
  },

  deleteAlertRule: async (id: number) => {
    const response = await api.delete<ApiResponse<void>>(`/system/alerts/${id}`);
    return response.data;
  },

  enableAlertRule: async (id: number) => {
    const response = await api.post<ApiResponse<void>>(`/system/alerts/${id}/enable`);
    return response.data;
  },

  disableAlertRule: async (id: number) => {
    const response = await api.post<ApiResponse<void>>(`/system/alerts/${id}/disable`);
    return response.data;
  },

  getAlertEvents: async (params: { pageNum: number; pageSize: number }) => {
    const response = await api.get<ApiResponse<PageResult<AlertEvent>>>('/system/alert-events', { params });
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
    const response = await api.get<ApiResponse<PageResult<SystemLog>>>('/system/logs', { params });
    return response.data;
  },

  searchLogs: async (params: Record<string, unknown>) => {
    const response = await api.post<ApiResponse<PageResult<SystemLog>>>('/system/logs/search', params);
    return response.data;
  },

  exportLogs: async (params: Record<string, unknown>) => {
    const response = await api.get('/system/logs/export', { params, responseType: 'blob' });
    return response;
  },

  analyzeLogs: async (params: Record<string, unknown>) => {
    const response = await api.post<ApiResponse<unknown>>('/system/logs/analyze', params);
    return response.data;
  },
};

// 系统管理 API
export const systemApi = {
  getInfo: async () => {
    const response = await api.get<ApiResponse<SystemInfo>>('/system/info');
    return response.data;
  },

  restart: async () => {
    const response = await api.post<ApiResponse<void>>('/system/restart');
    return response.data;
  },

  shutdown: async () => {
    const response = await api.post<ApiResponse<void>>('/system/shutdown');
    return response.data;
  },

  deploy: async (config: Record<string, unknown>) => {
    const response = await api.post<ApiResponse<void>>('/system/deploy', config);
    return response.data;
  },

  upgrade: async (version: string) => {
    const response = await api.post<ApiResponse<void>>('/system/upgrade', { version });
    return response.data;
  },
};

export default api;
