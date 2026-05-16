import axios, { type AxiosResponse } from 'axios';

/**
 * bone-extension-studio REST 客户端。
 *
 * 契约：doc/design/modules/5. 扩展管理模块详细设计方案.md §5
 * PRD：doc/prd/BONE产品需求文档正式版.md §4.6
 */
export type StudioApiResponse<T> = {
  success: boolean;
  message?: string;
  data: T;
  meta?: Record<string, unknown>;
};

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
  const res = await client.get<StudioApiResponse<ExtPointRow[]>>('/extension/points');
  return assertSuccess(res) ?? [];
}

export async function createExtPoint(payload: ExtPointPayload): Promise<ExtPointRow> {
  const res = await client.post<StudioApiResponse<ExtPointRow>>('/extension/points', payload);
  return assertSuccess(res);
}

export async function updateExtPoint(id: number, payload: ExtPointPayload): Promise<ExtPointRow> {
  const res = await client.put<StudioApiResponse<ExtPointRow>>(`/extension/points/${id}`, payload);
  return assertSuccess(res);
}

export async function deleteExtPoint(id: number): Promise<void> {
  const res = await client.delete<StudioApiResponse<void>>(`/extension/points/${id}`);
  if (res.status === 204) {
    return;
  }
  if (res.data?.success === false) {
    throw new Error(res.data.message || '删除失败');
  }
}

export async function postExtPointEnable(id: number, enable: boolean): Promise<void> {
  const path = enable ? 'enable' : 'disable';
  const res = await client.post<StudioApiResponse<ExtPointRow>>(`/extension/points/${id}/${path}`);
  assertSuccess(res);
}

export async function listPlugins(): Promise<ExtensionRow[]> {
  const res = await client.get<StudioApiResponse<ExtensionRow[]>>('/extension/plugins');
  return assertSuccess(res) ?? [];
}

export async function createPlugin(payload: ExtensionPayload): Promise<ExtensionRow> {
  const res = await client.post<StudioApiResponse<ExtensionRow>>('/extension/plugins', payload);
  return assertSuccess(res);
}

export async function updatePlugin(id: number, payload: ExtensionPayload): Promise<ExtensionRow> {
  const res = await client.put<StudioApiResponse<ExtensionRow>>(`/extension/plugins/${id}`, payload);
  return assertSuccess(res);
}

export async function deletePlugin(id: number): Promise<void> {
  const res = await client.delete<StudioApiResponse<void>>(`/extension/plugins/${id}`);
  if (res.status === 204) {
    return;
  }
  if (res.data?.success === false) {
    throw new Error(res.data.message || '删除失败');
  }
}

export async function deployPlugin(id: number, deploy: boolean): Promise<void> {
  const path = deploy ? 'deploy' : 'undeploy';
  const res = await client.post<StudioApiResponse<ExtensionRow>>(`/extension/plugins/${id}/${path}`);
  assertSuccess(res);
}

export async function publishPluginRuntime(id: number): Promise<void> {
  const res = await client.post<StudioApiResponse<{ id: number; published: boolean }>>(
    `/extension/plugins/${id}/publish-runtime`,
  );
  assertSuccess(res);
}
