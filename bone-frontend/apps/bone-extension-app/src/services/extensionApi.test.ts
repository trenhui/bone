import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockPut = vi.fn();
const mockDelete = vi.fn();

vi.mock('axios', () => ({
  default: {
    create: () => ({
      get: mockGet,
      post: mockPost,
      put: mockPut,
      delete: mockDelete,
      interceptors: { request: { use: vi.fn() } },
    }),
  },
}));

import {
  createExtPoint,
  createPlugin,
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

  it('listExtPoints calls GET /extension/points', async () => {
    mockGet.mockResolvedValue({
      data: { success: true, data: [{ id: 1, name: 'p1', enabled: true }] },
    });
    const rows = await listExtPoints();
    expect(mockGet).toHaveBeenCalledWith('/extension/points');
    expect(rows).toHaveLength(1);
  });

  it('createExtPoint calls POST /extension/points', async () => {
    mockPost.mockResolvedValue({
      data: { success: true, data: { id: 2, name: 'n', interfaceName: 'com.X', enabled: true } },
    });
    await createExtPoint({ name: 'n', interfaceName: 'com.X' });
    expect(mockPost).toHaveBeenCalledWith('/extension/points', { name: 'n', interfaceName: 'com.X' });
  });

  it('publishPluginRuntime calls POST publish-runtime', async () => {
    mockPost.mockResolvedValue({
      data: { success: true, data: { id: 1, published: true } },
    });
    await publishPluginRuntime(1);
    expect(mockPost).toHaveBeenCalledWith('/extension/plugins/1/publish-runtime');
  });

  it('listPlugins throws when API returns success=false', async () => {
    mockGet.mockResolvedValue({
      data: { success: false, message: '加载插件失败' },
    });
    await expect(listPlugins()).rejects.toThrow('加载插件失败');
  });
});
