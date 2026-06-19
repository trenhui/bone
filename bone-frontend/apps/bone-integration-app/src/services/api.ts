import axios from 'axios';
import type {
  Connector,
  CreateConnectorReq,
  UpdateConnectorReq,
  IntegrationFlow,
  CreateFlowReq,
  UpdateFlowReq,
  IntegrationLog,
  FlowStatistics,
  ApiResponse,
  PageResult,
  PageQuery
} from '../types';

// 模块级内存 token，由 qiankun mount 生命周期写入，优先于 localStorage
let _qiankunToken: string | null = null;

/** 供 main.tsx 在 qiankun mount 时调用，将 props.token 写入内存 */
export function setQiankunToken(token: string | null) {
  _qiankunToken = token;
  if (token) {
    localStorage.setItem('token', token);
  }
}

// 创建 axios 实例
const apiClient = axios.create({
  baseURL: (import.meta.env.VITE_API_BASE_URL || '') + '/api/v1/integration',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
});

// 请求拦截器 - 添加Token
apiClient.interceptors.request.use(
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

apiClient.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      // qiankun 微应用中不直接跳转 /login，而是通知主应用处理
      const event = new CustomEvent('bone:auth:expired', { detail: { status: 401 } });
      window.dispatchEvent(event);
    }
    return Promise.reject(error);
  }
);

// 连接器相关 API
export const connectorApi = {
  // 获取连接器列表
  getConnectors: (params: PageQuery): Promise<ApiResponse<PageResult<Connector>>> => {
    return apiClient.get('/connectors', { params });
  },
  
  // 创建连接器
  createConnector: (data: CreateConnectorReq): Promise<ApiResponse<number>> => {
    return apiClient.post('/connectors', data);
  },
  
  // 获取连接器详情
  getConnectorById: (id: number): Promise<ApiResponse<Connector>> => {
    return apiClient.get(`/connectors/${id}`);
  },
  
  // 更新连接器
  updateConnector: (id: number, data: UpdateConnectorReq): Promise<ApiResponse<void>> => {
    return apiClient.put(`/connectors/${id}`, data);
  },
  
  // 删除连接器
  deleteConnector: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.delete(`/connectors/${id}`);
  },
  
  // 测试连接器
  testConnector: (id: number): Promise<ApiResponse<{ success: boolean; message: string }>> => {
    return apiClient.post(`/connectors/${id}/test`);
  },
  
  // 启用连接器
  enableConnector: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.post(`/connectors/${id}/enable`);
  },
  
  // 禁用连接器
  disableConnector: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.post(`/connectors/${id}/disable`);
  }
};

// 流程相关 API
export const flowApi = {
  // 获取流程列表
  getFlows: (params: PageQuery): Promise<ApiResponse<PageResult<IntegrationFlow>>> => {
    return apiClient.get('/flows', { params });
  },
  
  // 创建流程
  createFlow: (data: CreateFlowReq): Promise<ApiResponse<number>> => {
    return apiClient.post('/flows', data);
  },
  
  // 获取流程详情
  getFlowById: (id: number): Promise<ApiResponse<IntegrationFlow>> => {
    return apiClient.get(`/flows/${id}`);
  },
  
  // 更新流程
  updateFlow: (id: number, data: UpdateFlowReq): Promise<ApiResponse<void>> => {
    return apiClient.put(`/flows/${id}`, data);
  },
  
  // 删除流程
  deleteFlow: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.delete(`/flows/${id}`);
  },
  
  // 测试流程
  testFlow: (id: number, inputData: unknown): Promise<ApiResponse<{ success: boolean; output: unknown; error?: string }>> => {
    return apiClient.post(`/flows/${id}/test`, { inputData });
  },
  
  // 激活流程
  activateFlow: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.post(`/flows/${id}/activate`);
  },
  
  // 停用流程
  deactivateFlow: (id: number): Promise<ApiResponse<void>> => {
    return apiClient.post(`/flows/${id}/deactivate`);
  },
  
  // 获取流程版本历史
  getFlowVersions: (id: number): Promise<ApiResponse<Array<Record<string, unknown>>>> => {
    return apiClient.get(`/flows/${id}/versions`);
  }
};

// 监控相关 API
export const monitorApi = {
  // 获取执行记录列表
  getExecutions: (params: PageQuery): Promise<ApiResponse<PageResult<IntegrationLog>>> => {
    return apiClient.get('/executions', { params });
  },
  
  // 获取执行记录详情
  getExecutionById: (id: number): Promise<ApiResponse<IntegrationLog>> => {
    return apiClient.get(`/executions/${id}`);
  },
  
  // 获取执行日志
  getExecutionLogs: (id: number): Promise<ApiResponse<Array<Record<string, unknown>>>> => {
    return apiClient.get(`/executions/${id}/logs`);
  },
  
  // 重试执行
  retryExecution: (id: number): Promise<ApiResponse<number>> => {
    return apiClient.post(`/executions/${id}/retry`);
  },
  
  // 获取流程执行统计
  getStatistics: (): Promise<ApiResponse<FlowStatistics[]>> => {
    return apiClient.get('/statistics');
  }
};