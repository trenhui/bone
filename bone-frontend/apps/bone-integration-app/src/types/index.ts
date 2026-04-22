// 连接器相关类型
export interface Connector {
  id: number;
  name: string;
  type: string;
  config: Record<string, any>;
  status: string;
  createTime: string;
  updateTime: string;
}

export interface CreateConnectorReq {
  name: string;
  type: string;
  config: Record<string, any>;
}

export interface UpdateConnectorReq {
  name: string;
  type: string;
  config: Record<string, any>;
}

// 流程相关类型
export interface FlowNode {
  id: number;
  flowId: number;
  name: string;
  type: string;
  config: Record<string, any>;
  positionX: number;
  positionY: number;
  createTime: string;
  updateTime: string;
}

export interface FlowConnection {
  id: number;
  flowId: number;
  sourceNodeId: number;
  targetNodeId: number;
  condition: string;
  createTime: string;
  updateTime: string;
}

export interface IntegrationFlow {
  id: number;
  name: string;
  description: string;
  status: string;
  createTime: string;
  updateTime: string;
  nodes: FlowNode[];
  connections: FlowConnection[];
}

export interface CreateFlowReq {
  name: string;
  description: string;
  nodes: {
    name: string;
    type: string;
    config: Record<string, any>;
    positionX: number;
    positionY: number;
  }[];
  connections: {
    sourceNodeId: number;
    targetNodeId: number;
    condition: string;
  }[];
}

export interface UpdateFlowReq {
  name: string;
  description: string;
  nodes: {
    id?: number;
    name: string;
    type: string;
    config: Record<string, any>;
    positionX: number;
    positionY: number;
  }[];
  connections: {
    id?: number;
    sourceNodeId: number;
    targetNodeId: number;
    condition: string;
  }[];
}

// 执行日志相关类型
export interface IntegrationLog {
  id: number;
  flowId: number;
  flowName: string;
  status: string;
  startTime: string;
  endTime: string;
  inputData: string;
  outputData: string;
  errorMessage: string;
  createTime: string;
}

// 统计相关类型
export interface FlowStatistics {
  flowId: number;
  flowName: string;
  executionCount: number;
  successCount: number;
  failureCount: number;
  avgExecutionTime: number;
  updateTime: string;
}

// 通用响应类型
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  total: number;
  list: T[];
  pageNum: number;
  pageSize: number;
}

// 分页查询参数
export interface PageQuery {
  pageNum: number;
  pageSize: number;
  keyword?: string;
  status?: string;
}