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
  listAuditLogs,
  listExecutionLogs,
  listExtPoints,
  listPlugins,
  publishPluginRuntime,
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
    expect(mockPost).toHaveBeenCalledWith('/v1/extension/points', { name: 'n', interfaceName: 'com.X' });
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
