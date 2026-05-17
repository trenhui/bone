import axios, { type AxiosResponse } from 'axios';

/**
 * bone-extension-studio REST 客户端。
 *
 * 契约：doc/design/modules/5. 扩展管理模块详细设计方案.md §5
 * PRD：doc/prd/BONE产品需求文档正式版.md §4.6
 */
export type StudioApiResponse<T> = {
  code?: number;
  success: boolean;
  message?: string;
  data: T;
  meta?: Record<string, unknown>;
};

/** 规范前缀，见 doc/architecture/Bone-API-规范.md §13.1 */
const EXTENSION_BASE = '/v1/extension';

const client = axios.create({
  baseURL: '/api',
  timeout: 20000,
});

const token = () => localStorage.getItem('token');
client.interceptors.request.use((config) => {
  const t = token();
  if (t) {
    config.headers.Authorization = `Bearer ${t}`;
  }
  return config;
});

function assertSuccess<T>(response: AxiosResponse<StudioApiResponse<T>>): T {
  const body = response.data;
  if (body && body.success === false) {
    throw new Error(body.message || '请求失败');
  }
  return body?.data as T;
}

export type ExtPointRow = {
  id: number;
  name: string;
  description?: string;
  interfaceName?: string;
  domain?: string;
  category?: string;
  enabled: boolean;
};

export type ExtensionRow = {
  id: number;
  extPointId: number;
  name: string;
  description?: string;
  className?: string;
  tenantCode?: string;
  bizCode?: string;
  useCase?: string;
  scenario?: string;
  userGroup?: string;
  priority?: number;
  config?: string;
  enabled: boolean;
};

export type ExtPointPayload = {
  name: string;
  description?: string;
  interfaceName: string;
  domain?: string;
  category?: string;
  enabled?: boolean;
};

export type ExtensionPayload = {
  extPointId: number;
  name: string;
  description?: string;
  className: string;
  tenantCode?: string;
  bizCode?: string;
  useCase?: string;
  scenario?: string;
  userGroup?: string;
  priority?: number;
  config?: string;
  enabled?: boolean;
};

export async function listExtPoints(): Promise<ExtPointRow[]> {
  const res = await client.get<StudioApiResponse<ExtPointRow[]>>(`${EXTENSION_BASE}/points`);
  return assertSuccess(res) ?? [];
}

export async function createExtPoint(payload: ExtPointPayload): Promise<ExtPointRow> {
  const res = await client.post<StudioApiResponse<ExtPointRow>>(`${EXTENSION_BASE}/points`, payload);
  return assertSuccess(res);
}

export async function updateExtPoint(id: number, payload: ExtPointPayload): Promise<ExtPointRow> {
  const res = await client.put<StudioApiResponse<ExtPointRow>>(`${EXTENSION_BASE}/points/${id}`, payload);
  return assertSuccess(res);
}

export async function deleteExtPoint(id: number): Promise<void> {
  const res = await client.delete<StudioApiResponse<void>>(`${EXTENSION_BASE}/points/${id}`);
  if (res.status === 204) {
    return;
  }
  if (res.data?.success === false) {
    throw new Error(res.data.message || '删除失败');
  }
}

export async function postExtPointEnable(id: number, enable: boolean): Promise<void> {
  const action = enable ? 'enable' : 'disable';
  const res = await client.post<StudioApiResponse<ExtPointRow>>(
    `${EXTENSION_BASE}/points/${id}:${action}`,
  );
  assertSuccess(res);
}

export async function listPlugins(): Promise<ExtensionRow[]> {
  const res = await client.get<StudioApiResponse<ExtensionRow[]>>(`${EXTENSION_BASE}/plugins`);
  return assertSuccess(res) ?? [];
}

export async function createPlugin(payload: ExtensionPayload): Promise<ExtensionRow> {
  const res = await client.post<StudioApiResponse<ExtensionRow>>(`${EXTENSION_BASE}/plugins`, payload);
  return assertSuccess(res);
}

export async function updatePlugin(id: number, payload: ExtensionPayload): Promise<ExtensionRow> {
  const res = await client.put<StudioApiResponse<ExtensionRow>>(`${EXTENSION_BASE}/plugins/${id}`, payload);
  return assertSuccess(res);
}

export async function deletePlugin(id: number): Promise<void> {
  const res = await client.delete<StudioApiResponse<void>>(`${EXTENSION_BASE}/plugins/${id}`);
  if (res.status === 204) {
    return;
  }
  if (res.data?.success === false) {
    throw new Error(res.data.message || '删除失败');
  }
}

export async function deployPlugin(id: number, deploy: boolean): Promise<void> {
  const action = deploy ? 'deploy' : 'undeploy';
  const res = await client.post<StudioApiResponse<ExtensionRow>>(
    `${EXTENSION_BASE}/plugins/${id}:${action}`,
  );
  assertSuccess(res);
}

export async function publishPluginRuntime(id: number): Promise<void> {
  const res = await client.post<StudioApiResponse<{ id: number; published: boolean }>>(
    `${EXTENSION_BASE}/plugins/${id}:publish-runtime`,
  );
  assertSuccess(res);
}

export type PluginVersionRow = {
  id: number;
  pluginId: number;
  version: string;
  filePath: string;
  fileSize: number;
  checksum: string;
  active: boolean;
  changeLog?: string;
  createdAt?: string;
};

export async function listPluginVersions(pluginId: number): Promise<PluginVersionRow[]> {
  const res = await client.get<StudioApiResponse<PluginVersionRow[]>>(
    `${EXTENSION_BASE}/plugins/${pluginId}/versions`,
  );
  return assertSuccess(res) ?? [];
}

export type UploadPluginParams = {
  file: File;
  name: string;
  className: string;
  extPointId?: number;
  pluginId?: number;
  version?: string;
  description?: string;
};

export async function uploadPlugin(params: UploadPluginParams): Promise<ExtensionRow> {
  const form = new FormData();
  form.append('file', params.file);
  form.append('name', params.name);
  form.append('className', params.className);
  if (params.extPointId != null) {
    form.append('extPointId', String(params.extPointId));
  }
  if (params.pluginId != null) {
    form.append('pluginId', String(params.pluginId));
  }
  if (params.version) {
    form.append('version', params.version);
  }
  if (params.description) {
    form.append('description', params.description);
  }
  const res = await client.post<StudioApiResponse<ExtensionRow>>(`${EXTENSION_BASE}/plugins:upload`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return assertSuccess(res);
}

export async function rollbackPlugin(pluginId: number, version?: string): Promise<ExtensionRow> {
  const res = await client.post<StudioApiResponse<ExtensionRow>>(
    `${EXTENSION_BASE}/plugins/${pluginId}:rollback`,
    version ? { version } : {},
  );
  return assertSuccess(res);
}

export type SandboxConfig = {
  runtime: string;
  maxMemoryMb: number;
  maxCpuCores: number;
  timeoutSeconds: number;
  syncEnabled: boolean;
  extPointCount?: number;
  pluginCount?: number;
  executionTotal?: number;
  executionSuccess?: number;
  executionFailed?: number;
  executionRunning?: number;
  successRate?: number;
  runtimeSyncEnabled?: boolean;
};

export type ExecutionLogRow = {
  id: number;
  pluginId: number;
  extensionPointId: number;
  executionId: string;
  status: string;
  inputData?: string;
  outputData?: string;
  errorMessage?: string;
  durationMs?: number;
  createdAt?: string;
};

export type ExecutionLogPage = {
  records: ExecutionLogRow[];
  total: number;
  page: number;
  size: number;
  pages?: number;
  hasNext?: boolean;
  hasPrevious?: boolean;
};

function unwrapPage<T>(data: T | { records?: T[]; list?: T[] }): T[] {
  if (Array.isArray(data)) {
    return data;
  }
  if (data && typeof data === 'object') {
    const p = data as { records?: T[]; list?: T[] };
    return p.records ?? p.list ?? [];
  }
  return [];
}

export async function getSandboxConfig(): Promise<SandboxConfig> {
  const res = await client.get<StudioApiResponse<SandboxConfig>>(`${EXTENSION_BASE}/sandbox/config`);
  return assertSuccess(res);
}

export async function getExtensionOverview(): Promise<SandboxConfig> {
  const res = await client.get<StudioApiResponse<SandboxConfig>>(`${EXTENSION_BASE}/overview`);
  return assertSuccess(res);
}

export async function listExecutionLogs(params?: {
  pluginId?: number;
  status?: string;
  page?: number;
  size?: number;
}): Promise<ExecutionLogPage & { rows: ExecutionLogRow[] }> {
  const res = await client.get<StudioApiResponse<ExecutionLogPage>>(`${EXTENSION_BASE}/execution-logs`, {
    params,
  });
  const raw = assertSuccess(res);
  const records = unwrapPage(raw);
  const page: ExecutionLogPage = {
    records,
    total: raw.total ?? records.length,
    page: raw.page ?? params?.page ?? 1,
    size: raw.size ?? params?.size ?? 20,
    pages: raw.pages,
    hasNext: raw.hasNext,
    hasPrevious: raw.hasPrevious,
  };
  return { ...page, rows: records };
}

export async function simulatePlugin(pluginId: number): Promise<ExecutionLogRow> {
  const res = await client.post<StudioApiResponse<ExecutionLogRow>>(
    `${EXTENSION_BASE}/plugins/${pluginId}:simulate`,
  );
  return assertSuccess(res);
}
