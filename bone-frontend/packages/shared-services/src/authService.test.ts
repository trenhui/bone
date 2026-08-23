import { describe, it, expect, vi, beforeEach } from 'vitest';
import { authService } from './authService';

const store = vi.hoisted(() => ({ instance: null as any }));

vi.mock('axios', () => {
  store.instance = {
    defaults: {},
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  };
  return { default: { create: () => store.instance } };
});

beforeEach(() => {
  localStorage.clear();
  (window as any).__BONE_TOKEN__ = undefined;
  store.instance.post.mockReset();
});

describe('authService', () => {
  it('login 成功后写入 token', async () => {
    store.instance.post.mockResolvedValue({ code: 200, message: 'ok', data: { token: 'tk-123', account: { username: 'admin' } } });
    const res = await authService.login({ username: 'admin', password: 'secret' });
    expect(res.token).toBe('tk-123');
    expect(localStorage.getItem('token')).toBe('tk-123');
    expect(store.instance.post).toHaveBeenCalledWith('/login', { username: 'admin', password: 'secret' });
  });

  it('logout 调用接口并清理 token', async () => {
    store.instance.post.mockResolvedValue({ code: 200, message: 'ok', data: null });
    localStorage.setItem('token', 'tk-123');
    await authService.logout();
    expect(store.instance.post).toHaveBeenCalledWith('/logout');
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('changePassword 调用正确接口', async () => {
    store.instance.post.mockResolvedValue({ code: 200, message: 'ok', data: null });
    await authService.changePassword({ oldPassword: 'a', newPassword: 'b' });
    expect(store.instance.post).toHaveBeenCalledWith('/me/change-password', {
      oldPassword: 'a',
      newPassword: 'b',
    });
  });
});
