/* eslint-disable @typescript-eslint/explicit-module-boundary-types */
import { createApiClient, setQiankunToken } from '@bone/shared-services';
import type {
  SystemConfig,
  ConfigHistory,
  AlertRule,
  AlertEvent,
  SystemLog,
  Metrics,
  SystemInfo,
  SysDict,
  ScheduleTask,
  ApiResponse,
  PageResult,
} from '@/types';

export { setQiankunToken };

const api = createApiClient('/api/v1', { headers: { 'Content-Type': 'application/json' } });

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

// 系统字典 API
export const dictApi = {
  getDictsByType: async (type: string) => {
    const response = await api.get<ApiResponse<SysDict[]>>(`/system/dicts/type/${type}`);
    return response.data;
  },
  getDictPage: async (params: { type?: string; keyword?: string; pageNum?: number; pageSize?: number }) => {
    const response = await api.get<ApiResponse<PageResult<SysDict>>>('/system/dicts/page', {
      params: { page: params.pageNum ?? 1, size: params.pageSize ?? 20, type: params.type, keyword: params.keyword },
    });
    return response.data;
  },
  createDict: async (data: Omit<SysDict, 'id'>) => {
    const response = await api.post<ApiResponse<number>>('/system/dicts', data);
    return response.data;
  },
  updateDict: async (id: number, data: Partial<SysDict>) => {
    const response = await api.put<ApiResponse<void>>(`/system/dicts/${id}`, data);
    return response.data;
  },
  deleteDict: async (id: number) => {
    const response = await api.delete<ApiResponse<void>>(`/system/dicts/${id}`);
    return response.data;
  },
};

// 系统定时任务 API
export const scheduleTaskApi = {
  getScheduleTaskPage: async (params: { keyword?: string; status?: string; pageNum?: number; pageSize?: number }) => {
    const response = await api.get<ApiResponse<PageResult<ScheduleTask>>>('/system/schedule-tasks/page', {
      params: { page: params.pageNum ?? 1, size: params.pageSize ?? 20, keyword: params.keyword, status: params.status },
    });
    return response.data;
  },
  createScheduleTask: async (data: { name: string; cron: string; handler: string; status?: string }) => {
    const response = await api.post<ApiResponse<number>>('/system/schedule-tasks', data);
    return response.data;
  },
  updateScheduleTask: async (id: number, data: { name: string; cron: string; handler: string }) => {
    const response = await api.put<ApiResponse<void>>(`/system/schedule-tasks/${id}`, data);
    return response.data;
  },
  deleteScheduleTask: async (id: number) => {
    const response = await api.delete<ApiResponse<void>>(`/system/schedule-tasks/${id}`);
    return response.data;
  },
  toggleScheduleTask: async (id: number, enabled: boolean) => {
    const response = await api.put<ApiResponse<void>>(`/system/schedule-tasks/${id}/toggle`, null, { params: { enabled } });
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
