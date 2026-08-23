/**
 * 集成引擎领域类型（对齐 integration-app 真实模型）
 */

export interface Connector {
  id: number;
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
  id: number;
  flowId: number;
  name: string;
  type: string;
  config: Record<string, unknown>;
  positionX: number;
  positionY: number;
  createdAt: string;
  updatedAt: string;
}

export interface FlowConnection {
  id: number;
  flowId: number;
  sourceNodeId: number;
  targetNodeId: number;
  condition: string;
  createdAt: string;
  updatedAt: string;
}

export interface IntegrationFlow {
  id: number;
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
    config: Record<string, unknown>;
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
  createdAt: string;
}

export interface FlowStatistics {
  flowId: number;
  flowName: string;
  executionCount: number;
  successCount: number;
  failureCount: number;
  avgExecutionTime: number;
  updatedAt: string;
}
