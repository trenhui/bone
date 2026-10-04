import { createApiClient, setQiankunToken } from '@bone/shared-services';
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

export { setQiankunToken };

// 创建 axios 实例
const apiClient = createApiClient('/api/v1/integration', { timeout: 30000 });

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
  getConnectorById: (id: string): Promise<ApiResponse<Connector>> => {
    return apiClient.get(`/connectors/${id}`);
  },
  
  // 更新连接器
  updateConnector: (id: string, data: UpdateConnectorReq): Promise<ApiResponse<void>> => {
    return apiClient.put(`/connectors/${id}`, data);
  },
  
  // 删除连接器
  deleteConnector: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.delete(`/connectors/${id}`);
  },
  
  // 测试连接器
  testConnector: (id: string): Promise<ApiResponse<{ success: boolean; message: string }>> => {
    return apiClient.post(`/connectors/${id}/test`);
  },
  
  // 启用连接器
  enableConnector: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.post(`/connectors/${id}/enable`);
  },
  
  // 禁用连接器
  disableConnector: (id: string): Promise<ApiResponse<void>> => {
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
  getFlowById: (id: string): Promise<ApiResponse<IntegrationFlow>> => {
    return apiClient.get(`/flows/${id}`);
  },
  
  // 更新流程
  updateFlow: (id: string, data: UpdateFlowReq): Promise<ApiResponse<void>> => {
    return apiClient.put(`/flows/${id}`, data);
  },
  
  // 删除流程
  deleteFlow: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.delete(`/flows/${id}`);
  },
  
  // 测试流程
  testFlow: (id: string, inputData: unknown): Promise<ApiResponse<{ success: boolean; output: unknown; error?: string }>> => {
    return apiClient.post(`/flows/${id}/test`, { inputData });
  },
  
  // 激活流程
  activateFlow: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.post(`/flows/${id}/activate`);
  },
  
  // 停用流程
  deactivateFlow: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.post(`/flows/${id}/deactivate`);
  },
  
  // 获取流程版本历史
  getFlowVersions: (id: string): Promise<ApiResponse<Array<Record<string, unknown>>>> => {
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
  getExecutionById: (id: string): Promise<ApiResponse<IntegrationLog>> => {
    return apiClient.get(`/executions/${id}`);
  },
  
  // 获取执行日志
  getExecutionLogs: (id: string): Promise<ApiResponse<Array<Record<string, unknown>>>> => {
    return apiClient.get(`/executions/${id}/logs`);
  },
  
  // 重试执行
  retryExecution: (id: string): Promise<ApiResponse<number>> => {
    return apiClient.post(`/executions/${id}/retry`);
  },
  
  // 获取流程执行统计
  getStatistics: (): Promise<ApiResponse<FlowStatistics[]>> => {
    return apiClient.get('/statistics');
  }
};