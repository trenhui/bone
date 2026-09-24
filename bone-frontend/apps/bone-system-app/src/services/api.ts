/* eslint-disable @typescript-eslint/explicit-module-boundary-types */
import { createApiClient, setQiankunToken } from '@bone/shared-services';
import type {
  SystemConfig,
  ConfigHistory,
  AlertRule,
  AlertRecord,
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
    const p = { pageNum: params?.pageNum ?? 1, pageSize: params?.pageSize ?? 100, keyword: params?.keyword ?? '' };
    return api.get<never, ApiResponse<PageResult<SystemConfig>>>('/system/config/page', { params: p });
  },

  getConfigDetail: async (id: number) => {
    return api.get<never, ApiResponse<SystemConfig>>(`/system/config/${id}`);
  },

  createConfig: async (data: { configKey: string; configValue: string; configType: string; description?: string }) => {
    return api.post<never, ApiResponse<number>>('/system/config', data);
  },

  updateConfig: async (data: { id: number; configKey: string; configValue: string; configType: string }) => {
    return api.put<never, ApiResponse<void>>('/system/config', data);
  },

  deleteConfig: async (id: number) => {
    return api.delete<never, ApiResponse<void>>(`/system/config/${id}`);
  },

  getConfigHistory: async (id: number) => {
    return api.get<never, ApiResponse<ConfigHistory[]>>(`/system/config/${id}/history`);
  },

  exportConfig: async () => {
    return api.post<never, ApiResponse<Blob>>('/system/config/export', null, {
      responseType: 'blob',
    });
  },

  importConfig: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post<never, ApiResponse<void>>('/system/config/import', formData);
  },
};

// 监控告警 API
export const monitorApi = {
  getHealth: async () => {
    return api.get<never, ApiResponse<SystemInfo>>('/system/health');
  },

  getMetrics: async () => {
    return api.get<never, ApiResponse<Metrics>>('/system/metrics');
  },

  getAlertRules: async (params: { pageNum: number; pageSize: number }) => {
    return api.get<never, ApiResponse<PageResult<AlertRule>>>('/system/alert/rules/page', {
      params: { pageNum: params.pageNum, pageSize: params.pageSize },
    });
  },

  getAlertRule: async (id: number) => {
    return api.get<never, ApiResponse<AlertRule>>(`/system/alert/rules/${id}`);
  },

  createAlertRule: async (rule: { name: string; metricName: string; thresholdValue: number; alertLevel: string }) => {
    return api.post<never, ApiResponse<number>>('/system/alert/rules', rule);
  },

  updateAlertRule: async (rule: { id: number; name: string; metricName: string; thresholdValue: number; alertLevel: string }) => {
    return api.put<never, ApiResponse<void>>('/system/alert/rules', rule);
  },

  deleteAlertRule: async (id: number) => {
    return api.delete<never, ApiResponse<void>>(`/system/alert/rules/${id}`);
  },

  enableAlertRule: async (id: number) => {
    return api.post<never, ApiResponse<void>>(`/system/alert/rules/${id}/enable`);
  },

  disableAlertRule: async (id: number) => {
    return api.post<never, ApiResponse<void>>(`/system/alert/rules/${id}/disable`);
  },

  getAlertEvents: async (params: { pageNum: number; pageSize: number }) => {
    return api.get<never, ApiResponse<PageResult<AlertRecord>>>('/system/alert/events/page', {
      params: { pageNum: params.pageNum, pageSize: params.pageSize },
    });
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
    return api.get<never, ApiResponse<PageResult<SystemLog>>>('/system/logs/page', {
      params: { pageNum: params.pageNum, pageSize: params.pageSize, keyword: params.keyword },
    });
  },

  createLog: async (data: { logLevel: string; serviceName: string; content: string }) => {
    return api.post<never, ApiResponse<number>>('/system/logs', data);
  },

  exportLogs: async (params: { service?: string; level?: string; startTime?: string; endTime?: string }) => {
    return api.post<never, ApiResponse<Blob>>('/system/logs/export', params, {
      responseType: 'blob',
    });
  },

  analyzeLogs: async (params: { service?: string; startTime?: string; endTime?: string }) => {
    return api.get<never, ApiResponse<unknown>>('/system/logs/analyze', { params });
  },
};

// 控制台 API（由 bone-system 提供）
export const consoleApi = {
  getOverview: async () => {
    return api.get<never, ApiResponse<unknown>>('/console/overview');
  },
  getServices: async () => {
    return api.get<never, ApiResponse<unknown>>('/console/services');
  },
  getResources: async () => {
    return api.get<never, ApiResponse<unknown>>('/console/resources');
  },
  getMetrics: async () => {
    return api.get<never, ApiResponse<Metrics>>('/console/metrics');
  },
  getQuickActions: async () => {
    return api.get<never, ApiResponse<unknown>>('/console/quick-actions');
  },
};

// 系统字典 API
export const dictApi = {
  getDictsByType: async (type: string) => {
    return api.get<never, ApiResponse<SysDict[]>>(`/system/dicts/type/${type}`);
  },
  getDictPage: async (params: { type?: string; keyword?: string; pageNum?: number; pageSize?: number }) => {
    return api.get<never, ApiResponse<PageResult<SysDict>>>('/system/dicts/page', {
      params: { pageNum: params.pageNum ?? 1, pageSize: params.pageSize ?? 20, type: params.type, keyword: params.keyword },
    });
  },
  createDict: async (data: Omit<SysDict, 'id'>) => {
    return api.post<never, ApiResponse<number>>('/system/dicts', data);
  },
  updateDict: async (id: number, data: Partial<SysDict>) => {
    return api.put<never, ApiResponse<void>>(`/system/dicts/${id}`, data);
  },
  deleteDict: async (id: number) => {
    return api.delete<never, ApiResponse<void>>(`/system/dicts/${id}`);
  },
};

// 系统定时任务 API
export const scheduleTaskApi = {
  getScheduleTaskPage: async (params: { keyword?: string; status?: string; pageNum?: number; pageSize?: number }) => {
    return api.get<never, ApiResponse<PageResult<ScheduleTask>>>('/system/schedule-tasks/page', {
      params: { pageNum: params.pageNum ?? 1, pageSize: params.pageSize ?? 20, keyword: params.keyword, status: params.status },
    });
  },
  createScheduleTask: async (data: { name: string; cron: string; handler: string; status?: string }) => {
    return api.post<never, ApiResponse<number>>('/system/schedule-tasks', data);
  },
  updateScheduleTask: async (id: number, data: { name: string; cron: string; handler: string }) => {
    return api.put<never, ApiResponse<void>>(`/system/schedule-tasks/${id}`, data);
  },
  deleteScheduleTask: async (id: number) => {
    return api.delete<never, ApiResponse<void>>(`/system/schedule-tasks/${id}`);
  },
  toggleScheduleTask: async (id: number, enabled: boolean) => {
    return api.put<never, ApiResponse<void>>(`/system/schedule-tasks/${id}/toggle`, null, { params: { enabled } });
  },
  runScheduleTaskNow: async (id: number) => {
    return api.post<never, ApiResponse<number>>(`/system/schedule-tasks/${id}/run`);
  },
};

// 系统部署 API
export const systemApi = {
  getInfo: async () => {
    return api.get<never, ApiResponse<SystemInfo>>('/system/info');
  },
  deploy: async (data: Record<string, unknown>) => {
    return api.post<never, ApiResponse<unknown>>('/system/deploy', data);
  },
  upgrade: async (version: string) => {
    return api.post<never, ApiResponse<unknown>>('/system/upgrade', { version });
  },
  restart: async () => {
    return api.post<never, ApiResponse<unknown>>('/system/restart');
  },
  shutdown: async () => {
    return api.post<never, ApiResponse<unknown>>('/system/shutdown');
  },
};

export default api;
