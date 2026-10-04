import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 关键：createApiClient 的响应拦截器是 `(response) => response.data`。
 * 之前这里把拦截器 mock 成 no-op，测试传的是「原始 axios 响应」，
 * 与运行时实际传给业务代码的形状不一致 —— 掩盖了 assertSuccess 双重解包的缺陷。
 * 因此这里让 mock 真正执行注册进来的响应拦截器，使单测覆盖运行时契约。
 */
const {
  mockGet,
  mockPost,
  mockPut,
  mockDelete,
  pushResponseHandler,
  throughInterceptors,
} = vi.hoisted(() => {
    const handlers: Array<(r: unknown) => unknown> = [];
    const apply = (r: unknown) => handlers.reduce((acc, h) => h(acc), r);
    return {
      mockGet: vi.fn(),
      mockPost: vi.fn(),
      mockPut: vi.fn(),
      mockDelete: vi.fn(),
      applyResponseInterceptors: apply,
      pushResponseHandler: (h: (r: unknown) => unknown) => {
        handlers.push(h);
      },
      throughInterceptors:
        (fn: (...args: unknown[]) => unknown) =>
        (...args: unknown[]) =>
          Promise.resolve(fn(...args)).then(apply),
    };
  });

vi.mock('axios', () => ({
  default: {
    create: () => ({
      get: throughInterceptors(mockGet),
      post: throughInterceptors(mockPost),
      put: throughInterceptors(mockPut),
      delete: throughInterceptors(mockDelete),
      interceptors: {
        request: { use: vi.fn() },
        response: {
          use: (onFulfilled: (r: unknown) => unknown) => {
            pushResponseHandler(onFulfilled);
          },
        },
      },
    }),
  },
}));

import {
  StudioApiError,
  bindPlugin,
  createExtPoint,
  deleteExtPoint,
  deletePlugin,
  deployPlugin,
  downloadPluginVersion,
  getDeploymentState,
  getDependencyGraph,
  ifMatchHeader,
  installMarketplaceItem,
  listAuditLogs,
  listExecutionLogs,
  listExtPoints,
  listMarketplaceItems,
  listPlugins,
  newIdempotencyKey,
  publishPluginRuntime,
  unbindPlugin,
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
    expect(mockGet).toHaveBeenCalledWith('/v1/extension/points', { params: undefined });
    expect(rows).toHaveLength(1);
  });

  it('listExtPoints with page returns PageResult shape', async () => {
    mockGet.mockResolvedValue({
      data: {
        success: true,
        data: { records: [{ id: 1, name: 'p1', enabled: true }], total: 1, page: 1, size: 10 },
      },
    });
    const page = await listExtPoints({ page: 1, size: 10 });
    expect(mockGet).toHaveBeenCalledWith('/v1/extension/points', { params: { page: 1, size: 10 } });
    expect(Array.isArray(page)).toBe(false);
    if (!Array.isArray(page)) {
      expect(page.records).toHaveLength(1);
      expect(page.total).toBe(1);
    }
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

  it('createExtPoint sends Idempotency-Key when provided', async () => {
    mockPost.mockResolvedValue({
      data: { success: true, data: { id: 3, name: 'n', interfaceName: 'com.Y', enabled: true } },
    });
    await createExtPoint(
      { name: 'n', interfaceName: 'com.Y' },
      { idempotencyKey: 'test-idem-key' },
    );
    expect(mockPost).toHaveBeenCalledWith(
      '/v1/extension/points',
      { name: 'n', interfaceName: 'com.Y' },
      { headers: { 'Idempotency-Key': 'test-idem-key' } },
    );
  });

  it('newIdempotencyKey returns non-empty string', () => {
    expect(newIdempotencyKey().length).toBeGreaterThan(8);
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

  it('bindPlugin POSTs extensionPointId payload', async () => {
    mockPost.mockResolvedValue({
      data: { success: true, data: { id: 7, extPointId: 12 } },
    });
    const row = await bindPlugin(7, 12);
    expect(mockPost).toHaveBeenCalledWith(
      '/v1/extension/plugins/7:bind',
      { extensionPointId: 12 },
    );
    expect(row).toMatchObject({ id: 7, extPointId: 12 });
  });

  it('unbindPlugin POSTs without body', async () => {
    mockPost.mockResolvedValue({
      data: { success: true, data: { id: 7, extPointId: 0 } },
    });
    const row = await unbindPlugin(7);
    expect(mockPost).toHaveBeenCalledWith('/v1/extension/plugins/7:unbind');
    expect(row).toMatchObject({ id: 7, extPointId: 0 });
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

  it('getDeploymentState GETs /plugins/{id}/deployment-state', async () => {
    mockGet.mockResolvedValue({
      data: {
        success: true,
        data: {
          pluginId: 5,
          currentStatus: 'ACTIVE',
          allStates: ['UPLOADED', 'ACTIVE'],
          transitions: [{ from: 'UPLOADED', to: 'ACTIVE' }],
          versionStates: [],
        },
      },
    });
    const state = await getDeploymentState(5);
    expect(mockGet).toHaveBeenCalledWith('/v1/extension/plugins/5/deployment-state');
    expect(state.currentStatus).toBe('ACTIVE');
    expect(state.transitions).toHaveLength(1);
  });

  it('getDependencyGraph GETs /dependency-graph with optional extPointId', async () => {
    mockGet.mockResolvedValue({
      data: {
        success: true,
        data: { nodes: [], edges: [] },
      },
    });
    await getDependencyGraph(42);
    expect(mockGet).toHaveBeenCalledWith('/v1/extension/dependency-graph', {
      params: { extPointId: 42 },
    });
    await getDependencyGraph();
    expect(mockGet).toHaveBeenLastCalledWith('/v1/extension/dependency-graph', { params: undefined });
  });

  it('listMarketplaceItems passes search params', async () => {
    mockGet.mockResolvedValue({
      data: { success: true, data: [] },
    });
    await listMarketplaceItems({ keyword: 'promo', category: 'discount' });
    expect(mockGet).toHaveBeenCalledWith('/v1/extension/marketplace', {
      params: { keyword: 'promo', category: 'discount' },
    });
  });

  it('installMarketplaceItem POSTs install endpoint', async () => {
    mockPost.mockResolvedValue({
      data: { success: true, data: { pluginId: 10, itemId: 'sample.foo', extPointId: 3 } },
    });
    const result = await installMarketplaceItem('sample.foo', 3);
    expect(mockPost).toHaveBeenCalledWith('/v1/extension/marketplace/sample.foo:install', { extPointId: 3 });
    expect(result.pluginId).toBe(10);
  });

  it('installMarketplaceItem passes appId for app-scoped install (5a G5)', async () => {
    mockPost.mockResolvedValue({
      data: {
        success: true,
        data: { pluginId: 11, itemId: 'sample.foo', extPointId: 3, appId: 77001 },
      },
    });
    const result = await installMarketplaceItem('sample.foo', 3, 77001);
    expect(mockPost).toHaveBeenCalledWith('/v1/extension/marketplace/sample.foo:install', {
      extPointId: 3,
      appId: 77001,
    });
    expect(result.appId).toBe(77001);
  });

  it('listPlugins forwards appId filter (5a G4)', async () => {
    mockGet.mockResolvedValue({
      data: { success: true, data: [{ id: 1, name: 'p1', enabled: true, appId: 9001 }] },
    });
    await listPlugins({ appId: 9001 });
    expect(mockGet).toHaveBeenCalledWith('/v1/extension/plugins', {
      params: { extPointId: undefined, appId: 9001, page: undefined, size: undefined },
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

  // ---- 以下 4 例锁定「响应拦截器已解包」这一运行时契约（2026-10-03 新增）----
  //
  // createApiClient 的响应拦截器是 `(response) => response.data`，故业务代码拿到的
  // 直接是 ApiResponse 本体而非 AxiosResponse。deleteXxx / downloadPluginVersion
  // 曾按原始 AxiosResponse 形状读 `res.status` / `res.data`：
  //   - `res.status` 恒为 undefined（status 在拦截器那层就被丢掉了）
  //   - `res.data` 恒为 undefined（body 本身就是 ApiResponse，再取 .data 是双重解包）
  // 于是「删除失败」的错误分支永远不触发（失败被静默吞成成功），
  // 且下载时 URL.createObjectURL(undefined) 直接抛 TypeError。

  it('deleteExtPoint throws StudioApiError when API returns success=false', async () => {
    mockDelete.mockResolvedValue({
      data: {
        success: false,
        message: '扩展点已被引用，无法删除',
        code: 409,
        data: { errorCode: 'EXT_POINT_IN_USE', detail: '扩展点已被引用，无法删除' },
      },
    });
    const err = await deleteExtPoint(7).catch((e) => e);
    expect(err).toBeInstanceOf(StudioApiError);
    expect(err).toMatchObject({ errorCode: 'EXT_POINT_IN_USE', httpStatus: 409 });
  });

  it('deleteExtPoint resolves silently when API returns success=true', async () => {
    mockDelete.mockResolvedValue({ data: { success: true, data: null } });
    await expect(deleteExtPoint(7)).resolves.toBeUndefined();
  });

  it('deletePlugin throws StudioApiError when API returns success=false', async () => {
    mockDelete.mockResolvedValue({
      data: {
        success: false,
        message: '插件运行中，禁止删除',
        code: 409,
        data: { errorCode: 'PLUGIN_RUNNING', detail: '插件运行中，禁止删除' },
      },
    });
    const err = await deletePlugin(9).catch((e) => e);
    expect(err).toBeInstanceOf(StudioApiError);
    expect(err).toMatchObject({ errorCode: 'PLUGIN_RUNNING', httpStatus: 409 });
  });

  it('downloadPluginVersion passes the Blob straight through (no double unwrap)', async () => {
    const blob = new Blob(['jar-bytes'], { type: 'application/java-archive' });
    // 与其余用例一致：mock 的是**原始 axios 响应**，`{ data: blob }` 经拦截器 `(r) => r.data`
    // 解包后业务代码拿到的就是 blob 本身，而不是 { data: blob }。
    mockGet.mockResolvedValue({ data: blob });

    const created: Blob[] = [];
    const revoked: string[] = [];
    const realCreate = URL.createObjectURL;
    const realRevoke = URL.revokeObjectURL;
    // jsdom 未实现 createObjectURL/revokeObjectURL
    (URL as unknown as Record<string, unknown>).createObjectURL = (b: Blob) => {
      created.push(b);
      return 'blob:mock-url';
    };
    (URL as unknown as Record<string, unknown>).revokeObjectURL = (u: string) => {
      revoked.push(u);
    };
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    try {
      await downloadPluginVersion(3, '1.0.0');
      expect(mockGet).toHaveBeenCalledWith(
        '/v1/extension/plugins/3/versions/1.0.0:download',
        { responseType: 'blob' },
      );
      expect(created).toHaveLength(1);
      expect(created[0]).toBe(blob);
      expect(revoked).toEqual(['blob:mock-url']);
      expect(clickSpy).toHaveBeenCalledTimes(1);
    } finally {
      clickSpy.mockRestore();
      (URL as unknown as Record<string, unknown>).createObjectURL = realCreate;
      (URL as unknown as Record<string, unknown>).revokeObjectURL = realRevoke;
    }
  });
});
