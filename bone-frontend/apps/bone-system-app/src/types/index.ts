// 系统配置类型
export interface SystemConfig {
  id?: number;
  key: string;
  value: string;
  description?: string;
  type: 'SYSTEM' | 'SERVICE' | 'FEATURE';
  createdAt?: string;
  updatedAt?: string;
}

export interface ConfigHistory {
  id?: number;
  configId: number;
  oldValue?: string;
  newValue: string;
  operator?: string;
  createdAt?: string;
}

// 告警规则类型
export interface AlertRule {
  id?: number;
  name: string;
  metric: string;
  threshold: number;
  level: 'CRITICAL' | 'WARNING' | 'INFO';
  notificationChannels: string[];
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface AlertEvent {
  id?: number;
  alertRuleId: number;
  value: number;
  message: string;
  status: 'TRIGGERED' | 'RESOLVED';
  createdAt?: string;
  resolveTime?: string;
}

// 系统日志类型
export interface SystemLog {
  id?: number;
  level: 'ERROR' | 'WARN' | 'INFO' | 'DEBUG' | 'TRACE';
  service: string;
  content: string;
  traceId?: string;
  createdAt?: string;
}

// 监控指标类型
export interface Metrics {
  cpu: number;
  memory: number;
  disk: number;
  apiResponseTime: number;
  errorRate: number;
  qps: number;
  dbConnections: number;
}

// 系统信息类型
export interface SystemInfo {
  version: string;
  uptime: string;
  healthStatus: 'HEALTHY' | 'UNHEALTHY' | 'DEGRADED';
  services: string[];
}

// API 响应类型
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  list: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}
