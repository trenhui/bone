import axios, { type AxiosResponse } from 'axios';

/**
 * bone-extension-studio REST 客户端。
 *
 * 契约：doc/design/modules/5. 扩展管理模块详细设计方案.md §5
 * PRD：doc/prd/BONE产品需求文档正式版.md §4.6
 */
export type ProblemDetail = {
  errorCode?: string;
  status?: number;
  detail?: string;
  traceId?: string;
  title?: string;
  type?: string;
};

export type StudioApiResponse<T> = {
  code?: number;
  success: boolean;
  message?: string;
  data: T;
  meta?: Record<string, unknown>;
};

/** 将 API 错误转为用户可读文案（含 traceId）。 */
export function formatStudioError(error: unknown, fallback = '操作失败'): string {
  if (error instanceof StudioApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

export class StudioApiError extends Error {
  readonly errorCode?: string;
  readonly traceId?: string;
  readonly httpStatus?: number;

  constructor(
    message: string,
    opts?: { errorCode?: string; traceId?: string; httpStatus?: number },
  ) {
    super(message);
    this.name = 'StudioApiError';
    this.errorCode = opts?.errorCode;
    this.traceId = opts?.traceId;
    this.httpStatus = opts?.httpStatus;
  }
}

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

function parseProblem(body: StudioApiResponse<unknown> | undefined): ProblemDetail | undefined {
  const data = body?.data;
  if (data && typeof data === 'object' && ('errorCode' in data || 'traceId' in data)) {
    return data as ProblemDetail;
  }
  return undefined;
}

function toStudioError(
  message: string,
  body?: StudioApiResponse<unknown>,
  httpStatus?: number,
): StudioApiError {
  const problem = parseProblem(body);
  const traceId =
    problem?.traceId ??
    (typeof body?.meta?.traceId === 'string' ? body.meta.traceId : undefined);
  const detail = problem?.detail ?? body?.message ?? message;
  const errorCode = problem?.errorCode;
  const suffix = traceId ? ` (traceId: ${traceId})` : '';
  return new StudioApiError(`${detail}${suffix}`, {
    errorCode,
    traceId,
    httpStatus: problem?.status ?? httpStatus ?? body?.code,
  });
}

client.interceptors.response.use(
  (response) => response,
  (error) => {
    const body = error.response?.data as StudioApiResponse<unknown> | undefined;
    if (body) {
      const status = error.response?.status;
      const fallback =
        status === 412
          ? '数据已被他人修改，请刷新后重试'
          : status === 409
            ? '幂等冲突或资源状态冲突'
            : body.message || '请求失败';
      return Promise.reject(toStudioError(fallback, body, status));
    }
    const traceId = error.response?.headers?.['x-trace-id'] as string | undefined;
    if (traceId) {
      return Promise.reject(
        new StudioApiError(`${error.message} (traceId: ${traceId})`, { traceId }),
      );
    }
    return Promise.reject(error);
  },
);

function assertSuccess<T>(response: AxiosResponse<StudioApiResponse<T>>): T {
  const body = response.data;
  if (body && body.success === false) {
    throw toStudioError(body.message || '请求失败', body as StudioApiResponse<unknown>);
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
  /** 乐观锁版本，更新时传 If-Match: "v{version}" */
  version?: number;
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
  version?: number;
};

export type StudioPageResult<T> = {
  records: T[];
  total?: number;
  page?: number;
  size?: number;
  pages?: number;
  hasNext?: boolean;
  hasPrevious?: boolean;
};

export type StudioWriteOptions = {
  /** 乐观锁：对应服务端 ETag / If-Match */
  version?: number;
  idempotencyKey?: string;
};

/** 写操作幂等键（RFC 7231 Idempotency-Key）。 */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`;
}

export function ifMatchHeader(version?: number): Record<string, string> {
  if (version == null) {
    return {};
  }
  return { 'If-Match': `"v${version}"` };
}

function writeHeaders(opts?: StudioWriteOptions): Record<string, string> {
  const headers: Record<string, string> = { ...ifMatchHeader(opts?.version) };
  if (opts?.idempotencyKey) {
    headers['Idempotency-Key'] = opts.idempotencyKey;
  }
  return headers;
}

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

export type ListExtPointsParams = {
  keyword?: string;
  domain?: string;
  category?: string;
  page?: number;
  size?: number;
};

function unwrapListPage<T>(data: T[] | StudioPageResult<T> | null | undefined): T[] {
  if (!data) {
    return [];
  }
  if (Array.isArray(data)) {
    return data;
  }
  return data.records ?? [];
}

export async function listExtPoints(
  params?: ListExtPointsParams,
): Promise<ExtPointRow[] | StudioPageResult<ExtPointRow>> {
  const res = await client.get<StudioApiResponse<ExtPointRow[] | StudioPageResult<ExtPointRow>>>(
    `${EXTENSION_BASE}/points`,
    { params },
  );
  const data = assertSuccess(res);
  if (params?.page != null || params?.size != null) {
    const page = (data && typeof data === 'object' && 'records' in data
      ? data
      : { records: unwrapListPage(data), total: unwrapListPage(data).length }) as StudioPageResult<ExtPointRow>;
    return {
      records: page.records ?? [],
      total: page.total,
      page: page.page ?? params.page,
      size: page.size ?? params.size,
      pages: page.pages,
      hasNext: page.hasNext,
      hasPrevious: page.hasPrevious,
    };
  }
  return unwrapListPage(data);
}

export async function createExtPoint(
  payload: ExtPointPayload,
  opts?: StudioWriteOptions,
): Promise<ExtPointRow> {
  const res = await client.post<StudioApiResponse<ExtPointRow>>(`${EXTENSION_BASE}/points`, payload, {
    headers: writeHeaders(opts),
  });
  return assertSuccess(res);
}

export async function updateExtPoint(
  id: number,
  payload: ExtPointPayload,
  opts?: StudioWriteOptions,
): Promise<ExtPointRow> {
  const res = await client.put<StudioApiResponse<ExtPointRow>>(`${EXTENSION_BASE}/points/${id}`, payload, {
    headers: writeHeaders(opts),
  });
  return assertSuccess(res);
}

export async function deleteExtPoint(id: number): Promise<void> {
  const res = await client.delete<StudioApiResponse<void>>(`${EXTENSION_BASE}/points/${id}`);
  if (res.status === 204) {
    return;
  }
  if (res.data?.success === false) {
    throw toStudioError(res.data.message || '删除失败', res.data as StudioApiResponse<unknown>);
  }
}

export async function postExtPointEnable(id: number, enable: boolean): Promise<void> {
  const action = enable ? 'enable' : 'disable';
  const res = await client.post<StudioApiResponse<ExtPointRow>>(
    `${EXTENSION_BASE}/points/${id}:${action}`,
  );
  assertSuccess(res);
}

export type ListPluginsParams = {
  extPointId?: number;
  page?: number;
  size?: number;
};

export async function listPlugins(
  params?: ListPluginsParams,
): Promise<ExtensionRow[] | StudioPageResult<ExtensionRow>> {
  const res = await client.get<StudioApiResponse<ExtensionRow[] | StudioPageResult<ExtensionRow>>>(
    `${EXTENSION_BASE}/plugins`,
    { params },
  );
  const data = assertSuccess(res);
  if (params?.page != null || params?.size != null) {
    const page = (data && typeof data === 'object' && 'records' in data
      ? data
      : { records: unwrapListPage(data), total: unwrapListPage(data).length }) as StudioPageResult<ExtensionRow>;
    return {
      records: page.records ?? [],
      total: page.total,
      page: page.page ?? params.page,
      size: page.size ?? params.size,
      pages: page.pages,
      hasNext: page.hasNext,
      hasPrevious: page.hasPrevious,
    };
  }
  return unwrapListPage(data);
}

export async function createPlugin(
  payload: ExtensionPayload,
  opts?: StudioWriteOptions,
): Promise<ExtensionRow> {
  const res = await client.post<StudioApiResponse<ExtensionRow>>(`${EXTENSION_BASE}/plugins`, payload, {
    headers: writeHeaders(opts),
  });
  return assertSuccess(res);
}

export async function updatePlugin(
  id: number,
  payload: ExtensionPayload,
  opts?: StudioWriteOptions,
): Promise<ExtensionRow> {
  const res = await client.put<StudioApiResponse<ExtensionRow>>(`${EXTENSION_BASE}/plugins/${id}`, payload, {
    headers: writeHeaders(opts),
  });
  return assertSuccess(res);
}

export async function deletePlugin(id: number): Promise<void> {
  const res = await client.delete<StudioApiResponse<void>>(`${EXTENSION_BASE}/plugins/${id}`);
  if (res.status === 204) {
    return;
  }
  if (res.data?.success === false) {
    throw toStudioError(res.data.message || '删除失败', res.data as StudioApiResponse<unknown>);
  }
}

export type StudioOperation = {
  operationId?: string;
  type?: string;
  resourceId?: number;
  done: boolean;
  progress?: number;
  result?: Record<string, unknown>;
  error?: ProblemDetail;
};

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

function operationIdFromDeployResponse(
  response: AxiosResponse<StudioApiResponse<{ operationId?: string } | ExtensionRow>>,
): string | undefined {
  const location = response.headers?.location as string | undefined;
  if (location) {
    const trimmed = location.replace(/\/$/, '');
    return trimmed.substring(trimmed.lastIndexOf('/') + 1);
  }
  const data = response.data?.data;
  if (data && typeof data === 'object' && 'operationId' in data) {
    return (data as { operationId?: string }).operationId;
  }
  return undefined;
}

export async function getOperation(operationId: string): Promise<StudioOperation> {
  const res = await client.get<StudioApiResponse<StudioOperation>>(
    `${EXTENSION_BASE}/operations/${operationId}`,
  );
  return assertSuccess(res);
}

export type PollOperationOptions = {
  intervalMs?: number;
  timeoutMs?: number;
  onProgress?: (progress: number, operation: StudioOperation) => void;
};

export async function pollOperationUntilDone(
  operationId: string,
  opts?: PollOperationOptions,
): Promise<StudioOperation> {
  const intervalMs = opts?.intervalMs ?? 500;
  const deadline = Date.now() + (opts?.timeoutMs ?? 60_000);
  let lastReported = -1;
  while (Date.now() < deadline) {
    const op = await getOperation(operationId);
    const progress = op.progress ?? (op.done ? 100 : 0);
    if (opts?.onProgress && progress !== lastReported) {
      lastReported = progress;
      opts.onProgress(progress, op);
    }
    if (op.done) {
      if (op.error) {
        throw toStudioError(op.error.detail ?? '操作失败', {
          success: false,
          data: op.error,
        } as StudioApiResponse<unknown>);
      }
      return op;
    }
    await sleep(intervalMs);
  }
  throw new StudioApiError('操作超时，请稍后在操作历史中查看状态');
}

export type DeployPluginOptions = {
  /** true：同步 200；false：LRO 202 并轮询；undefined：不传 sync，由服务端配置决定 */
  sync?: boolean;
  pollIntervalMs?: number;
  pollTimeoutMs?: number;
  /** LRO 轮询进度（0–100） */
  onProgress?: (progress: number, operation: StudioOperation) => void;
};

export async function deployPlugin(
  id: number,
  deploy: boolean,
  opts?: DeployPluginOptions,
): Promise<void> {
  const action = deploy ? 'deploy' : 'undeploy';
  const params = opts?.sync != null ? { sync: opts.sync } : undefined;
  const res = await client.post<StudioApiResponse<{ operationId?: string } | ExtensionRow>>(
    `${EXTENSION_BASE}/plugins/${id}:${action}`,
    {},
    { params, validateStatus: (status) => status === 200 || status === 202 },
  );
  if (res.status === 202 && deploy) {
    const operationId = operationIdFromDeployResponse(res);
    if (!operationId) {
      throw new StudioApiError('部署已接受但缺少 operationId');
    }
    opts?.onProgress?.(0, { operationId, done: false, progress: 0 });
    await pollOperationUntilDone(operationId, {
      intervalMs: opts?.pollIntervalMs,
      timeoutMs: opts?.pollTimeoutMs,
      onProgress: opts?.onProgress,
    });
    return;
  }
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
  total?: number;
  page?: number;
  size?: number;
  pages?: number;
  hasNext?: boolean;
  hasPrevious?: boolean;
  nextCursor?: string | null;
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

export type AuditLogRow = {
  id: number;
  traceId?: string;
  userId?: string;
  action: string;
  resourceType?: string;
  resourceId?: string;
  result: string;
  detail?: string;
  createdAt?: string;
};

export type AuditLogPage = {
  records: AuditLogRow[];
  nextCursor?: string | null;
  hasNext?: boolean;
};

export async function listAuditLogs(params?: {
  action?: string;
  resourceType?: string;
  cursor?: string;
  limit?: number;
}): Promise<AuditLogPage & { rows: AuditLogRow[] }> {
  const res = await client.get<StudioApiResponse<AuditLogPage>>(`${EXTENSION_BASE}/audit-logs`, {
    params: {
      action: params?.action,
      resourceType: params?.resourceType,
      cursor: params?.cursor,
      limit: params?.limit ?? 20,
    },
  });
  const raw = assertSuccess(res);
  const records = unwrapPage(raw);
  return {
    records,
    rows: records,
    nextCursor: raw.nextCursor ?? null,
    hasNext: raw.hasNext,
  };
}

export async function listExecutionLogs(params?: {
  pluginId?: number;
  status?: string;
  cursor?: string;
  limit?: number;
  page?: number;
  size?: number;
}): Promise<ExecutionLogPage & { rows: ExecutionLogRow[] }> {
  const query =
    params?.page != null
      ? { pluginId: params.pluginId, status: params.status, page: params.page, size: params.size ?? 20 }
      : {
        pluginId: params?.pluginId,
        status: params?.status,
        cursor: params?.cursor,
        limit: params?.limit ?? 20,
      };
  const res = await client.get<StudioApiResponse<ExecutionLogPage>>(`${EXTENSION_BASE}/execution-logs`, {
    params: query,
  });
  const raw = assertSuccess(res);
  const records = unwrapPage(raw);
  const page: ExecutionLogPage = {
    records,
    total: raw.total,
    page: raw.page,
    size: raw.size ?? params?.limit ?? params?.size ?? 20,
    pages: raw.pages,
    hasNext: raw.hasNext,
    hasPrevious: raw.hasPrevious,
    nextCursor: raw.nextCursor ?? null,
  };
  return { ...page, rows: records };
}

export async function simulatePlugin(pluginId: number): Promise<ExecutionLogRow> {
  const res = await client.post<StudioApiResponse<ExecutionLogRow>>(
    `${EXTENSION_BASE}/plugins/${pluginId}:simulate`,
  );
  return assertSuccess(res);
}
