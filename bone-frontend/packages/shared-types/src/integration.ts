/**
 * 集成引擎领域类型（对齐 integration-app 真实模型）
 */

export interface Connector {
  id: string;
  name: string;
  type: string;
  config: Record<string, unknown>;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateConnectorReq {
  name: string;
  type: string;
  config: Record<string, unknown>;
}

export interface UpdateConnectorReq {
  name: string;
  type: string;
  config: Record<string, unknown>;
}

export interface FlowNode {
  id: string;
  flowId: string;
  name: string;
  type: string;
  config: Record<string, unknown>;
  positionX: number;
  positionY: number;
  createdAt: string;
  updatedAt: string;
}

export interface FlowConnection {
  id: string;
  flowId: string;
  sourceNodeId: string;
  targetNodeId: string;
  condition: string;
  createdAt: string;
  updatedAt: string;
}

export interface IntegrationFlow {
  id: string;
  name: string;
  description: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  nodes: FlowNode[];
  connections: FlowConnection[];
}

export interface CreateFlowReq {
  name: string;
  description: string;
  nodes: {
    name: string;
    type: string;
    config: Record<string, unknown>;
    positionX: number;
    positionY: number;
  }[];
  connections: {
    sourceNodeId: string;
    targetNodeId: string;
    condition: string;
  }[];
}

export interface UpdateFlowReq {
  name: string;
  description: string;
  nodes: {
    id?: string;
    name: string;
    type: string;
    config: Record<string, unknown>;
    positionX: number;
    positionY: number;
  }[];
  connections: {
    id?: string;
    sourceNodeId: string;
    targetNodeId: string;
    condition: string;
  }[];
}

export interface IntegrationLog {
  id: string;
  flowId: string;
  flowName: string;
  status: string;
  startTime: string;
  endTime: string;
  inputData: string;
  outputData: string;
  errorMessage: string;
  createdAt: string;
}

export interface FlowStatistics {
  flowId: string;
  flowName: string;
  executionCount: number;
  successCount: number;
  failureCount: number;
  /** 成功率（百分比，后端 FlowStatisticsDTO 提供） */
  successRate: number;
}
