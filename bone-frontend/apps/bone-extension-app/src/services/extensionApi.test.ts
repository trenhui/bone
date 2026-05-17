import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockGet, mockPost, mockPut, mockDelete } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPost: vi.fn(),
  mockPut: vi.fn(),
  mockDelete: vi.fn(),
}));

vi.mock('axios', () => ({
  default: {
    create: () => ({
      get: mockGet,
      post: mockPost,
      put: mockPut,
      delete: mockDelete,
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() },
      },
    }),
  },
}));

import {
  StudioApiError,
  createExtPoint,
  deployPlugin,
  ifMatchHeader,
  listAuditLogs,
  listExecutionLogs,
  listExtPoints,
  listPlugins,
  publishPluginRuntime,
  updateExtPoint,
} from './extensionApi';

describe('extensionApi', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
    mockPut.mockReset();
    mockDelete.mockReset();
  });

  it('listExtPoints calls GET /v1/extension/points', async () => {
    mockGet.mockResolvedValue({
      data: { success: true, data: [{ id: 1, name: 'p1', enabled: true }] },
    });
    const rows = await listExtPoints();
    expect(mockGet).toHaveBeenCalledWith('/v1/extension/points');
    expect(rows).toHaveLength(1);
  });

  it('createExtPoint calls POST /v1/extension/points', async () => {
    mockPost.mockResolvedValue({
      data: { success: true, data: { id: 2, name: 'n', interfaceName: 'com.X', enabled: true } },
    });
    await createExtPoint({ name: 'n', interfaceName: 'com.X' });
    expect(mockPost).toHaveBeenCalledWith(
      '/v1/extension/points',
      { name: 'n', interfaceName: 'com.X' },
      { headers: {} },
    );
  });

  it('deployPlugin sync calls POST with sync=true', async () => {
    mockPost.mockResolvedValue({
      status: 200,
      data: { success: true, data: { id: 1, enabled: true } },
      headers: {},
    });
    await deployPlugin(1, true, { sync: true });
    expect(mockPost).toHaveBeenCalledWith(
      '/v1/extension/plugins/1:deploy',
      {},
      { params: { sync: true }, validateStatus: expect.any(Function) },
    );
  });

  it('deployPlugin LRO invokes onProgress', async () => {
    const onProgress = vi.fn();
    mockPost.mockResolvedValue({
      status: 202,
      data: { success: true, data: { operationId: 'op-progress' } },
      headers: { location: '/api/v1/extension/operations/op-progress' },
    });
    mockGet.mockResolvedValueOnce({
      data: { success: true, data: { done: false, progress: 40 } },
    });
    mockGet.mockResolvedValueOnce({
      data: { success: true, data: { done: true, progress: 100 } },
    });
    await deployPlugin(1, true, { sync: false, pollIntervalMs: 1, pollTimeoutMs: 5000, onProgress });
    expect(onProgress).toHaveBeenCalledWith(0, expect.objectContaining({ done: false }));
    expect(onProgress).toHaveBeenCalledWith(40, expect.objectContaining({ progress: 40 }));
    expect(onProgress).toHaveBeenCalledWith(100, expect.objectContaining({ done: true }));
  });

  it('deployPlugin LRO polls operations until done', async () => {
    mockPost.mockResolvedValue({
      status: 202,
      data: { success: true, data: { operationId: 'op-test' } },
      headers: { location: '/api/v1/extension/operations/op-test' },
    });
    mockGet.mockResolvedValueOnce({
      data: { success: true, data: { done: false, progress: 50 } },
    });
    mockGet.mockResolvedValueOnce({
      data: { success: true, data: { done: true, progress: 100, result: { id: 1 } } },
    });
    await deployPlugin(1, true, { sync: false, pollIntervalMs: 1, pollTimeoutMs: 5000 });
    expect(mockGet).toHaveBeenCalledWith('/v1/extension/operations/op-test');
  });

  it('publishPluginRuntime calls POST publish-runtime', async () => {
    mockPost.mockResolvedValue({
      data: { success: true, data: { id: 1, published: true } },
    });
    await publishPluginRuntime(1);
    expect(mockPost).toHaveBeenCalledWith('/v1/extension/plugins/1:publish-runtime');
  });

  it('listPlugins throws when API returns success=false', async () => {
    mockGet.mockResolvedValue({
      data: { success: false, message: '加载插件失败' },
    });
    await expect(listPlugins()).rejects.toThrow('加载插件失败');
  });

  it('listAuditLogs calls GET /audit-logs with limit', async () => {
    mockGet.mockResolvedValue({
      data: { success: true, data: { records: [], nextCursor: null } },
    });
    await listAuditLogs({ limit: 15 });
    expect(mockGet).toHaveBeenCalledWith('/v1/extension/audit-logs', {
      params: { action: undefined, resourceType: undefined, cursor: undefined, limit: 15 },
    });
  });

  it('listExecutionLogs uses cursor params by default', async () => {
    mockGet.mockResolvedValue({
      data: {
        success: true,
        data: { records: [], nextCursor: null, hasNext: false },
      },
    });
    await listExecutionLogs({ limit: 20 });
    expect(mockGet).toHaveBeenCalledWith('/v1/extension/execution-logs', {
      params: { pluginId: undefined, status: undefined, cursor: undefined, limit: 20 },
    });
  });

  it('ifMatchHeader formats version for Studio API', () => {
    expect(ifMatchHeader(3)).toEqual({ 'If-Match': '"v3"' });
    expect(ifMatchHeader()).toEqual({});
  });

  it('updateExtPoint sends If-Match when version provided', async () => {
    mockPut.mockResolvedValue({
      data: { success: true, data: { id: 1, name: 'n', interfaceName: 'com.X', enabled: true, version: 4 } },
    });
    await updateExtPoint(1, { name: 'n', interfaceName: 'com.X' }, { version: 2 });
    expect(mockPut).toHaveBeenCalledWith(
      '/v1/extension/points/1',
      { name: 'n', interfaceName: 'com.X' },
      { headers: { 'If-Match': '"v2"' } },
    );
  });

  it('listPlugins surfaces ProblemDetail traceId', async () => {
    mockGet.mockResolvedValue({
      data: {
        success: false,
        message: '校验失败',
        code: 400,
        data: { errorCode: 'COMMON_VALIDATION_FAILED', traceId: 'trace-abc', detail: '校验失败' },
      },
    });
    const err = await listPlugins().catch((e) => e);
    expect(err).toBeInstanceOf(StudioApiError);
    expect(err).toMatchObject({
      message: expect.stringContaining('trace-abc'),
      traceId: 'trace-abc',
      errorCode: 'COMMON_VALIDATION_FAILED',
    });
  });
});
