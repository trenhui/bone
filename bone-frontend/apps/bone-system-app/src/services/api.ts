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
  DictType,
  DictItem,
  DictOption,
  DictBinding,
  DictEnumDiff,
  DictExport,
  DictItemText,
  ScheduleTask,
  ScheduleTaskLog,
  ApiResponse,
  PageResult,
} from '@/types';

export { setQiankunToken };

const api = createApiClient('/api/v1', { headers: { 'Content-Type': 'application/json' } });

// 系统配置 API
export const systemConfigApi = {
  getConfig: async (params?: { keyword?: string; page?: number; size?: number }) => {
    const p = { page: params?.page ?? 1, size: params?.size ?? 100, keyword: params?.keyword ?? '' };
    return api.get<never, ApiResponse<PageResult<SystemConfig>>>('/system/config/page', { params: p });
  },

  getConfigDetail: async (id: string | string) => {
    return api.get<never, ApiResponse<SystemConfig>>(`/system/config/${id}`);
  },

  createConfig: async (data: { configKey: string; configValue: string; configType: string; description?: string }) => {
    return api.post<never, ApiResponse<number>>('/system/config', data);
  },

  updateConfig: async (data: { id: string | string; configKey: string; configValue: string; configType: string; description?: string }) => {
    return api.put<never, ApiResponse<void>>('/system/config', data);
  },

  deleteConfig: async (id: string | string) => {
    return api.delete<never, ApiResponse<void>>(`/system/config/${id}`);
  },

  getConfigHistory: async (id: string | string) => {
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

  getAlertRules: async (params: { page: number; size: number }) => {
    return api.get<never, ApiResponse<PageResult<AlertRule>>>('/system/alert/rules/page', {
      params: { page: params.page, size: params.size },
    });
  },

  getAlertRule: async (id: string | string) => {
    return api.get<never, ApiResponse<AlertRule>>(`/system/alert/rules/${id}`);
  },

  createAlertRule: async (rule: { name: string; metricName: string; threshold: number; alertLevel: string }) => {
    return api.post<never, ApiResponse<number>>('/system/alert/rules', rule);
  },

  updateAlertRule: async (rule: { id: string | string; name: string; metricName: string; threshold: number; alertLevel: string }) => {
    return api.put<never, ApiResponse<void>>('/system/alert/rules', rule);
  },

  deleteAlertRule: async (id: string | string) => {
    return api.delete<never, ApiResponse<void>>(`/system/alert/rules/${id}`);
  },

  enableAlertRule: async (id: string | string) => {
    return api.post<never, ApiResponse<void>>(`/system/alert/rules/${id}/enable`);
  },

  disableAlertRule: async (id: string | string) => {
    return api.post<never, ApiResponse<void>>(`/system/alert/rules/${id}/disable`);
  },

  getAlertEvents: async (params: { page: number; size: number }) => {
    return api.get<never, ApiResponse<PageResult<AlertRecord>>>('/system/alert/events/page', {
      params: { page: params.page, size: params.size },
    });
  },
};

// 日志管理 API
export const logApi = {
  getLogs: async (params: {
    page: number;
    size: number;
    logLevel?: string;
    serviceName?: string;
    keyword?: string;
    startTime?: string;
    endTime?: string;
  }) => {
    return api.get<never, ApiResponse<PageResult<SystemLog>>>('/system/logs/page', {
      // 必须透传全部过滤条件：否则界面上的级别/服务/时间筛选是「配了不生效」
      params: {
        page: params.page,
        size: params.size,
        keyword: params.keyword,
        logLevel: params.logLevel,
        serviceName: params.serviceName,
        startTime: params.startTime,
        endTime: params.endTime,
      },
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

// 系统字典 API（两级模型：/system/dict/types + /system/dict/items）
export const dictApi = {
  // ---- 字典类型（定义层） ----
  getTypePage: async (params: { keyword?: string; category?: string; moduleCode?: string; status?: number; page?: number; size?: number }) => {
    return api.get<never, ApiResponse<PageResult<DictType>>>('/system/dict/types/page', {
      params: {
        page: params.page ?? 1,
        size: params.size ?? 20,
        keyword: params.keyword,
        category: params.category,
        moduleCode: params.moduleCode,
        status: params.status,
      },
    });
  },
  getTypeByCode: async (code: string) => {
    return api.get<never, ApiResponse<DictType>>(`/system/dict/types/${code}`);
  },
  createType: async (data: Partial<DictType>) => {
    return api.post<never, ApiResponse<number>>('/system/dict/types', data);
  },
  updateType: async (id: string | string, data: Partial<DictType>) => {
    return api.put<never, ApiResponse<void>>(`/system/dict/types/${id}`, data);
  },
  deleteType: async (id: string | string) => {
    return api.delete<never, ApiResponse<void>>(`/system/dict/types/${id}`);
  },
  getEnumDiff: async (code: string) => {
    return api.get<never, ApiResponse<DictEnumDiff>>(`/system/dict/types/${code}/enum-diff`);
  },
  syncEnum: async (code: string) => {
    return api.post<never, ApiResponse<number>>(`/system/dict/types/${code}/enum-sync`);
  },
  getHierarchies: async (code: string) => {
    return api.get<never, ApiResponse<string[]>>(`/system/dict/types/${code}/hierarchies`);
  },
  exportType: async (code: string) => {
    return api.get<never, ApiResponse<DictExport>>(`/system/dict/types/${code}/export`);
  },
  importType: async (code: string, payload: DictExport) => {
    return api.post<never, ApiResponse<number>>(`/system/dict/types/${code}/import`, payload);
  },

  // ---- 字典项（值层） ----
  getItemPage: async (params: { typeCode?: string; parentCode?: string; hierarchyCode?: string; keyword?: string; status?: number; page?: number; size?: number }) => {
    return api.get<never, ApiResponse<PageResult<DictItem>>>('/system/dict/items/page', {
      params: {
        page: params.page ?? 1,
        size: params.size ?? 20,
        typeCode: params.typeCode,
        parentCode: params.parentCode,
        hierarchyCode: params.hierarchyCode,
        keyword: params.keyword,
        status: params.status,
      },
    });
  },
  getItemTree: async (typeCode: string, hierarchyCode?: string) => {
    return api.get<never, ApiResponse<DictItem[]>>('/system/dict/items/tree', {
      params: { typeCode, hierarchyCode },
    });
  },
  getTexts: async (typeCode: string, code: string) => {
    return api.get<never, ApiResponse<DictItemText[]>>(`/system/dict/items/${typeCode}/${code}/texts`);
  },
  saveTexts: async (typeCode: string, code: string, payload: DictItemText[]) => {
    return api.put<never, ApiResponse<number>>(`/system/dict/items/${typeCode}/${code}/texts`, payload);
  },
  getItem: async (typeCode: string, code: string) => {
    return api.get<never, ApiResponse<DictItem>>(`/system/dict/items/${typeCode}/${code}`);
  },
  createItem: async (data: Partial<DictItem>) => {
    return api.post<never, ApiResponse<number>>('/system/dict/items', data);
  },
  updateItem: async (id: string | string, data: Partial<DictItem>) => {
    return api.put<never, ApiResponse<void>>(`/system/dict/items/${id}`, data);
  },
  deleteItem: async (id: string | string) => {
    return api.delete<never, ApiResponse<void>>(`/system/dict/items/${id}`);
  },
  moveItem: async (id: string | string, data: { parentCode?: string | null; hierarchyCode?: string; sort?: number }) => {
    return api.put<never, ApiResponse<void>>(`/system/dict/items/${id}/move`, data);
  },
  markDefault: async (id: string | string) => {
    return api.put<never, ApiResponse<void>>(`/system/dict/items/${id}/default`);
  },
  /**
   * 弃用字典项（ACTIVE → DEPRECATED）：新表单不再出现，存量写仍放行。
   * 三态生命周期（界面设计器字典打通 v1.1 §4.5）的过渡态入口。
   */
  deprecateItem: async (id: string | string) => {
    return api.put<never, ApiResponse<void>>(`/system/dict/items/${id}/deprecate`);
  },
  /** 恢复弃用的字典项（DEPRECATED → ACTIVE）。 */
  restoreItem: async (id: string | string) => {
    return api.put<never, ApiResponse<void>>(`/system/dict/items/${id}/restore`);
  },
  /**
   * 下拉数据源：全平台消费字典的唯一入口。
   * 合并平台与租户覆盖 → 过滤停用与未生效 → 按 lang 本地化标签。
   */
  getOptions: async (type: string, parent?: string, lang?: string) => {
    return api.get<never, ApiResponse<DictOption[]>>('/system/dict/items/options', {
      params: { type, parent, lang },
    });
  },
  /**
   * 字典引用方清单（界面设计器字典打通 v1.1 §5.4）：该值域被哪些实体/字段绑定。
   *
   * <p>跨服务调用 metadata-server（经网关路由）——绑定关系存在元数据域，字典域只发问。
   * 用于字典管理页的「被引用情况 + 漂移告警入口」：改值域前先看谁会受影响。
   */
  getBindings: async (typeCode: string) => {
    return api.get<never, ApiResponse<DictBinding[]>>('/metadata/fields/dict-bindings', {
      params: { typeCode },
    });
  },
};

// 系统定时任务 API
export const scheduleTaskApi = {
  getScheduleTaskPage: async (params: { keyword?: string; status?: string; page?: number; size?: number }) => {
    return api.get<never, ApiResponse<PageResult<ScheduleTask>>>('/system/schedule-tasks/page', {
      params: { page: params.page ?? 1, size: params.size ?? 20, keyword: params.keyword, status: params.status },
    });
  },
  createScheduleTask: async (data: { name: string; cron: string; handler: string; status?: string }) => {
    return api.post<never, ApiResponse<number>>('/system/schedule-tasks', data);
  },
  updateScheduleTask: async (id: string | string, data: { name: string; cron: string; handler: string }) => {
    return api.put<never, ApiResponse<void>>(`/system/schedule-tasks/${id}`, data);
  },
  deleteScheduleTask: async (id: string | string) => {
    return api.delete<never, ApiResponse<void>>(`/system/schedule-tasks/${id}`);
  },
  toggleScheduleTask: async (id: string | string, enabled: boolean) => {
    return api.put<never, ApiResponse<void>>(`/system/schedule-tasks/${id}/toggle`, null, { params: { enabled } });
  },
  runScheduleTaskNow: async (id: string | string) => {
    return api.post<never, ApiResponse<number>>(`/system/schedule-tasks/${id}/run`);
  },
  getScheduleTaskExecutions: async (id: string | string, params: { page?: number; size?: number } = {}) => {
    return api.get<never, ApiResponse<PageResult<ScheduleTaskLog>>>(`/system/schedule-tasks/${id}/executions`, {
      params: { page: params.page ?? 1, size: params.size ?? 10 },
    });
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
