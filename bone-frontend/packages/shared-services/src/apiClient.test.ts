import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  createApiClient,
  getToken,
  setQiankunToken,
  getGlobalContext,
} from './apiClient';

const store = vi.hoisted(() => ({
  reqHandlers: [] as Array<(c: any) => any>,
  resHandlers: [] as Array<{ onFulfilled: (r: any) => any; onRejected: (e: any) => any }>,
  instance: null as any,
}));

vi.mock('axios', () => {
  store.instance = {
    defaults: {},
    interceptors: {
      request: { use: (h: (c: any) => any) => store.reqHandlers.push(h) },
      response: {
        use: (onFulfilled: (r: any) => any, onRejected: (e: any) => any) =>
          store.resHandlers.push({ onFulfilled, onRejected }),
      },
    },
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  };
  return { default: { create: () => store.instance } };
});

describe('token 来源链路', () => {
  beforeEach(() => {
    localStorage.clear();
    (window as any).__BONE_TOKEN__ = undefined;
    (window as any).__BONE_GLOBAL_CONTEXT__ = undefined;
    setQiankunToken(null);
  });

  it('getToken 优先读取 qiankun 内存 token', () => {
    setQiankunToken('qk-token');
    expect(getToken()).toBe('qk-token');
  });

  it('getToken 回退到 localStorage', () => {
    localStorage.setItem('token', 'ls-token');
    expect(getToken()).toBe('ls-token');
  });

  it('getGlobalContext 读取全局上下文', () => {
    (window as any).__BONE_GLOBAL_CONTEXT__ = { token: 'ctx-token' };
    expect(getGlobalContext()?.token).toBe('ctx-token');
  });
});

describe('createApiClient 拦截器', () => {
  beforeEach(() => {
    localStorage.clear();
    (window as any).__BONE_TOKEN__ = undefined;
    (window as any).__BONE_GLOBAL_CONTEXT__ = undefined;
  });

  it('请求拦截器注入 Authorization', async () => {
    createApiClient('/api/v1/iam');
    const reqHandler = store.reqHandlers[0];
    const cfg: any = { headers: {} };
    const out = await reqHandler(cfg);
    expect(out.headers.Authorization).toBeUndefined();

    setQiankunToken('abc');
    const cfg2: any = { headers: {} };
    const out2 = await reqHandler(cfg2);
    expect(out2.headers.Authorization).toBe('Bearer abc');
  });

  it('响应拦截器解包 response.data', async () => {
    createApiClient('/api/v1/iam');
    const h = store.resHandlers[0];
    const wrapped = { data: { code: 200, message: 'ok', data: { id: 1 } } };
    const result = await h.onFulfilled(wrapped);
    expect(result).toEqual({ code: 200, message: 'ok', data: { id: 1 } });
  });

  it('401 响应派发 bone:auth:expired 事件', async () => {
    createApiClient('/api/v1/iam');
    const h = store.resHandlers[0];
    const events: any[] = [];
    window.addEventListener('bone:auth:expired', (e: any) => events.push(e.detail));
    const err: any = { response: { status: 401, data: { message: 'unauthorized' } } };
    await expect(h.onRejected(err)).rejects.toBe(err);
    expect(events.length).toBe(1);
    expect(events[0].status).toBe(401);
    expect(err.displayMessage).toBe('unauthorized');
  });
});
