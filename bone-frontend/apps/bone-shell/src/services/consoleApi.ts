import axios from 'axios';

/**
 * 后端契约：`bone-system` `ConsoleController#overview`（详设 §3.3.2 / §5.1）。
 * 字段命名与 `com.bone.system.domain.model.console.*` 值对象一一对应。
 *
 * ⚠️ 平台全局 Long → JSON string 序列化：后端 Long 字段（计数/字节数/延迟）实际以字符串返回，
 * Double（cpuPercent 等）仍为数字 —— 因此数值字段类型为 `number | string`，消费方须用 toNum 归一。
 */
export type ServiceStatus = {
  name?: string;
  serviceCode?: string;
  port?: string;
  status?: string;
  latencyMs?: number | string;
};

export type ResourceUsage = {
  memoryUsedBytes?: number | string;
  memoryMaxBytes?: number | string;
  cpuPercent?: number | string;
  diskUsedPercent?: number | string;
  updatedAt?: string;
};

export type KeyMetrics = {
  userCount?: number | string;
  entityCount?: number | string;
  integrationFlowCount?: number | string;
  extensionPluginCount?: number | string;
  orderCount?: number | string;
  transactionAmount?: number | string;
  jvmThreadsLive?: number | string;
  jvmThreadsDaemon?: number | string;
  updatedAt?: string;
};

export type ConsoleAlert = {
  message?: string;
  level?: string;
};

export type ConsoleOverview = {
  services?: ServiceStatus[];
  resourceUsage?: ResourceUsage;
  keyMetrics?: KeyMetrics;
  alerts?: ConsoleAlert[];
  updatedAt?: string;
};

export type QuickAction = {
  id: string;
  title: string;
  path: string;
  icon?: string;
};

type BoneApiResponse<T> = { code?: number; success?: boolean; data: T };

function isOk<T>(body: BoneApiResponse<T>): boolean {
  return body.code === 200 || body.success === true;
}

export async function fetchConsoleOverview(): Promise<ConsoleOverview | null> {
  const { data } = await axios.get<BoneApiResponse<ConsoleOverview>>('/api/v1/console/overview');
  return isOk(data) ? data.data : null;
}

export async function fetchQuickActions(): Promise<QuickAction[]> {
  const { data } = await axios.get<BoneApiResponse<QuickAction[]>>('/api/v1/console/quick-actions');
  return isOk(data) ? data.data ?? [] : [];
}
