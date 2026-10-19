import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  createApiClient,
  getToken,
  setQiankunToken,
  getGlobalContext,
} from './apiClient';

/** 请求拦截器可见的最小形状（测试替身，非 axios 真实类型） */
type ReqLike = { headers: Record<string, string> };
/** 响应拦截器解包前的最小形状 */
type ResLike = { data: unknown };

/** window 扩展属性：与 apiClient.ts 生产代码同一套 cast 口径 */
const w = window as unknown as {
  __BONE_TOKEN__?: string;
  __BONE_GLOBAL_CONTEXT__?: Record<string, unknown>;
};

const store = vi.hoisted(() => ({
  reqHandlers: [] as Array<(c: ReqLike) => ReqLike>,
  resHandlers: [] as Array<{ onFulfilled: (r: ResLike) => unknown; onRejected: (e: unknown) => unknown }>,
  instance: null as unknown,
}));

vi.mock('axios', () => {
  store.instance = {
    defaults: {},
    interceptors: {
      request: { use: (h: (c: ReqLike) => ReqLike) => store.reqHandlers.push(h) },
      response: {
        use: (onFulfilled: (r: ResLike) => unknown, onRejected: (e: unknown) => unknown) =>
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
    w.__BONE_TOKEN__ = undefined;
    w.__BONE_GLOBAL_CONTEXT__ = undefined;
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
    w.__BONE_GLOBAL_CONTEXT__ = { token: 'ctx-token' };
    expect(getGlobalContext()?.token).toBe('ctx-token');
  });
});

describe('createApiClient 拦截器', () => {
  beforeEach(() => {
    localStorage.clear();
    w.__BONE_TOKEN__ = undefined;
    w.__BONE_GLOBAL_CONTEXT__ = undefined;
  });

  it('请求拦截器注入 Authorization', async () => {
    createApiClient('/api/v1/iam');
    const reqHandler = store.reqHandlers[0];
    const cfg: ReqLike = { headers: {} };
    const out = await reqHandler(cfg);
    expect(out.headers.Authorization).toBeUndefined();

    setQiankunToken('abc');
    const cfg2: ReqLike = { headers: {} };
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
    const events: Array<{ status: number }> = [];
    window.addEventListener('bone:auth:expired', (e) =>
      events.push((e as CustomEvent<{ status: number }>).detail));
    const err: { response: { status: number; data: { message: string } }; displayMessage?: string } = {
      response: { status: 401, data: { message: 'unauthorized' } },
    };
    await expect(h.onRejected(err)).rejects.toBe(err);
    expect(events.length).toBe(1);
    expect(events[0].status).toBe(401);
    expect(err.displayMessage).toBe('unauthorized');
  });
});
