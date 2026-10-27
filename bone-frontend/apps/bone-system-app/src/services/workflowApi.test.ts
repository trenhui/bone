// @vitest-environment node
// 为什么显式选 node 而不是仓库缺省的 jsdom：本机 node_modules 里 jsdom → @asamuzakjp/css-color →
// lru-cache 版本不兼容，任何 jsdom 用例在「加载环境」阶段就炸（既有 extensionApi.test.ts 同样跑不起来），
// 属环境项。本用例只需要 localStorage / atob（token 解析用），node 自带 atob，localStorage 由 tests/setup.ts 补齐。
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AxiosRequestConfig } from 'axios';

/**
 * 前后端契约测试：**真实 axios + 假传输层**。
 *
 * <p>只替换 adapter（网络出口），其余全部保留真实实现——`createApiClient` 的 baseURL 拼接、params 序列化、
 * JSON 体序列化、响应拦截器解包、错误拦截器 reject，都按运行时那一套跑。把 axios 整个 mock 掉会遮住
 * 「拦截器返回的是信封还是原始响应」这类踩过的坑（见 extensionApi.test.ts 顶部的注释）。
 *
 * <p>adapter 与录制数组必须建在 `vi.hoisted` 里：`vi.mock` 的工厂被提升到 import 之前执行，而
 * `createApiClient` 在模块加载期就会调用它，普通顶层 `const` 此时尚未初始化（会报
 * "Cannot access 'fakeAdapter' before initialization"）。
 */
const h = vi.hoisted(() => {
  // shared-services 的 getToken() 直接引用裸 window（取 qiankun 全局上下文与 URL 上的 token），
  // node 环境没有 window。这里把它指到 globalThis 并补上 location.search —— 这就是浏览器里
  // `window === globalThis` 的真实形态，不是宽松替代。
  const g = globalThis as unknown as Record<string, unknown>;
  g.window = g;
  g.location = { search: '' };

  const recorded: Array<Record<string, unknown>> = [];
  const state: { status: number; body: unknown } = { status: 200, body: { code: 200 } };

  const adapter = async (config: Record<string, unknown>) => {
    recorded.push(config);
    const response = {
      data: state.body,
      status: state.status,
      statusText: String(state.status),
      headers: {},
      config,
    };
    if (state.status >= 400) {
      // 与真实 axios 一致：非 2xx 走 reject 并携带 response，业务代码只能从 error.response 里读信封
      throw Object.assign(new Error(`Request failed with status code ${state.status}`), {
        response,
        config,
      });
    }
    return response;
  };

  return { recorded, state, adapter };
});

vi.mock('axios', async () => {
  const actual = await vi.importActual<typeof import('axios')>('axios');
  return {
    default: {
      ...actual.default,
      create: (config?: AxiosRequestConfig) =>
        actual.default.create({ ...config, adapter: h.adapter as never }),
    },
  };
});

import { toWorkflowFailure, workflowDesignerApi } from './workflowApi';

const lastConfig = (): AxiosRequestConfig => h.recorded[h.recorded.length - 1] as AxiosRequestConfig;

/** 造一个能解出 sub 的 JWT（只取载荷，不校验签名）。 */
function tokenWithSubject(sub: string): string {
  return `header.${btoa(JSON.stringify({ sub }))}.sig`;
}

describe('workflowApi 与后端 REST 契约', () => {
  beforeEach(() => {
    h.recorded.length = 0;
    h.state.status = 200;
    h.state.body = { code: 200, message: 'ok', data: [] };
    localStorage.setItem('token', tokenWithSubject('u-designer'));
  });

  it('总览：GET /workflow/definitions，设计器固定带 includeOffline=true', async () => {
    await workflowDesignerApi.listLatest();
    const config = lastConfig();
    expect(config.method).toBe('get');
    expect(config.url).toBe('/workflow/definitions');
    expect(config.baseURL).toBe('/api/v1');
    // 缺省口径只含已上线定义：下线后该 key 会从列表消失，设计器就再也没有入口把它重新上线
    expect(config.params).toEqual({ includeOffline: true });
  });

  it('读草稿：无草稿时响应里没有 data 字段，客户端得到 undefined（页面按「新建」处理）', async () => {
    h.state.body = { code: 200, message: 'ok' };
    const res = await workflowDesignerApi.findDraft('desig_demo');
    expect(lastConfig().url).toBe('/workflow/definitions/desig_demo/draft');
    // @JsonInclude(NON_NULL) 让 data 整体缺省，而不是 data:null
    expect(res.data).toBeUndefined();
  });

  it('保存草稿：POST 草稿路径，body 为 {definitionJson,expectedRevision}，并带审计头', async () => {
    await workflowDesignerApi.saveDraft('desig_demo', '{"key":"desig_demo"}', 3);
    const config = lastConfig();
    expect(config.method).toBe('post');
    expect(config.url).toBe('/workflow/definitions/desig_demo/draft');
    expect(JSON.parse(config.data as string)).toEqual({
      definitionJson: '{"key":"desig_demo"}',
      expectedRevision: 3,
    });
    // 写路径审计列必须回答「谁改的」：操作人从登录态 token 解出
    expect((config.headers as Record<string, string>)['X-Operator-Id']).toBe('u-designer');
  });

  it('发布 / 上下线：POST 各自路径，expectedRevision 允许为 null', async () => {
    await workflowDesignerApi.publish('desig_demo', null);
    expect(lastConfig().url).toBe('/workflow/definitions/desig_demo/publish');
    expect(JSON.parse(lastConfig().data as string)).toEqual({ expectedRevision: null });

    await workflowDesignerApi.setEnabled('desig_demo', 2, false);
    expect(lastConfig().url).toBe('/workflow/definitions/desig_demo/enabled');
    expect(JSON.parse(lastConfig().data as string)).toEqual({ version: 2, enabled: false });
  });

  it('丢弃草稿：DELETE，且带审计头', async () => {
    await workflowDesignerApi.discardDraft('desig_demo');
    expect(lastConfig().method).toBe('delete');
    expect(lastConfig().url).toBe('/workflow/definitions/desig_demo/draft');
    expect((lastConfig().headers as Record<string, string>)['X-Operator-Id']).toBe('u-designer');
  });

  it('校验：body 是裸流程定义原文（不是 {definitionJson} 包装）', async () => {
    const raw = '{"key":"desig_demo"}';
    await workflowDesignerApi.validate(raw);
    expect(lastConfig().url).toBe('/workflow/definitions/validate');
    // 后端签名是 @RequestBody String：多包一层会被当成另一个字段而解析成 null
    expect(lastConfig().data).toBe(raw);
  });

  it('试运行：POST /simulate，body 含 definitionJson/sampleData/initiatorId', async () => {
    await workflowDesignerApi.simulate({
      definitionJson: '{"key":"desig_demo"}',
      sampleData: { amount: 50000 },
      initiatorId: 'u-designer',
    });
    expect(lastConfig().url).toBe('/workflow/definitions/simulate');
    expect(JSON.parse(lastConfig().data as string)).toEqual({
      definitionJson: '{"key":"desig_demo"}',
      sampleData: { amount: 50000 },
      initiatorId: 'u-designer',
    });
  });

  it('缺 token 时回退 anonymous（后端会因缺操作人 400，而不是静默匿名写入）', async () => {
    localStorage.removeItem('token');
    await workflowDesignerApi.discardDraft('desig_demo');
    expect((lastConfig().headers as Record<string, string>)['X-Operator-Id']).toBe('anonymous');
  });
});

describe('失败路径：非 2xx 会被拦截器 reject，必须能解析出原因', () => {
  it('422 发布违规：解析出结构化违规清单供画布高亮', async () => {
    h.state.status = 422;
    h.state.body = {
      code: 422,
      message: '流程定义校验未通过',
      data: {
        violations: [
          { code: 'WF-003', message: '缺结束节点（END）' },
          { code: 'WF-020', message: '缺必需参数 roleCode', locator: 'cfo' },
        ],
      },
    };

    const failure = await workflowDesignerApi.publish('desig_demo', 1).then(
      () => null,
      (error: unknown) => toWorkflowFailure(error),
    );
    expect(failure?.status).toBe(422);
    // 页面按语义档位分流，不在组件里比 HTTP 数字码（门禁 R1 规则）
    expect(failure?.kind).toBe('invalid');
    expect(failure?.message).toBe('流程定义校验未通过');
    expect(failure?.violations).toHaveLength(2);
    expect(failure?.violations[1].locator).toBe('cfo');
  });

  it('409 并发冲突：解析出状态码与提示，页面据此重新拉草稿', async () => {
    h.state.status = 409;
    h.state.body = { code: 409, message: '草稿已被他人修改（revision 过期）' };

    const failure = await workflowDesignerApi.saveDraft('desig_demo', '{}', 1).then(
      () => null,
      (error: unknown) => toWorkflowFailure(error),
    );
    expect(failure?.status).toBe(409);
    expect(failure?.kind).toBe('conflict');
    expect(failure?.message).toContain('revision');
    expect(failure?.violations).toEqual([]);
  });

  it('403 授权拒绝：解析出无权限而非笼统的「请求失败」', async () => {
    h.state.status = 403;
    h.state.body = { code: 403, message: '无权执行 READ：宿主未接线 WorkflowDesignerAuthorizer' };

    const failure = await workflowDesignerApi.listVersions('desig_demo').then(
      () => null,
      (error: unknown) => toWorkflowFailure(error),
    );
    expect(failure?.status).toBe(403);
    expect(failure?.kind).toBe('forbidden');
    expect(failure?.message).toContain('Authorizer');
  });

  it('网络错误（无 response）不应崩在解包上', () => {
    const failure = toWorkflowFailure(new Error('Network Error'));
    expect(failure.status).toBe(0);
    expect(failure.kind).toBe('network');
    expect(failure.message).toBe('Network Error');
    expect(failure.violations).toEqual([]);
  });
});
