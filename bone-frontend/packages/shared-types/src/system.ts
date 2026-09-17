/**
 * 系统管理领域类型（对齐 system-app 真实模型）
 */

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

export interface AlertRecord {
  id?: number;
  alertRuleId: number;
  value: number;
  message: string;
  status: 'TRIGGERED' | 'RESOLVED';
  createdAt?: string;
  resolveTime?: string;
}

export interface SystemLog {
  id?: number;
  level: 'ERROR' | 'WARN' | 'INFO' | 'DEBUG' | 'TRACE';
  service: string;
  content: string;
  traceId?: string;
  createdAt?: string;
}

export interface Metrics {
  cpu: number;
  memory: number;
  disk: number;
  apiResponseTime: number;
  errorRate: number;
  qps: number;
  dbConnections: number;
}

export interface SystemInfo {
  version: string;
  uptime: string;
  healthStatus: 'HEALTHY' | 'UNHEALTHY' | 'DEGRADED';
  services: string[];
}

/** 系统字典项（对齐 DictResp） */
export interface SysDict {
  id?: number;
  type: string;
  typeName?: string;
  code: string;
  label: string;
  value?: string;
  sort?: number;
  status?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** 系统定时任务（对齐 ScheduleTaskResp） */
export interface ScheduleTask {
  id?: number;
  name: string;
  cron: string;
  handler: string;
  status: 'ENABLED' | 'DISABLED';
  lastRunAt?: string;
  nextRunAt?: string;
  createdAt?: string;
  updatedAt?: string;
}
