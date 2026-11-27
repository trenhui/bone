// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 决策图配置 API 契约测试：复用既有 `/flows/decision-graph` GET/PUT 端点，不新增 Controller 端点。
 *
 * <p>锁定三件事：路径正确、请求体只含 skipNodes、服务端错误原样上抛（供页面展示错误码与消息，
 * 且页面据此保留现网配置不清空）。
 */
const { mockGet, mockPut } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPut: vi.fn(),
}));

vi.mock('@bone/shared-services', () => ({
  createApiClient: () => ({ get: mockGet, put: mockPut, post: vi.fn(), delete: vi.fn() }),
}));

import { claimConfigApi } from './claimConfigApi';

const view = {
  flowKey: 'decision-graph',
  configVersion: 3,
  skipNodes: ['AiAssess'],
  version: '7',
};

describe('claimConfigApi.getFlowConfig', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPut.mockReset();
  });

  it('读取现网配置走 GET /flows/decision-graph', async () => {
    mockGet.mockResolvedValue({ code: 200, success: true, data: view });
    const res = await claimConfigApi.getFlowConfig();
    expect(mockGet).toHaveBeenCalledTimes(1);
    expect(mockGet).toHaveBeenCalledWith('/flows/decision-graph');
    expect(res.data).toEqual(view);
  });

  it('读取失败时错误原样上抛，不把「没读到」伪装成空配置', async () => {
    const failure = Object.assign(new Error('forbidden'), { code: 'SC_CLAIM_FORBIDDEN' });
    mockGet.mockRejectedValue(failure);
    await expect(claimConfigApi.getFlowConfig()).rejects.toBe(failure);
  });
});

describe('claimConfigApi.updateFlowConfig', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPut.mockReset();
  });

  it('保存走 PUT /flows/decision-graph，请求体只有 skipNodes', async () => {
    mockPut.mockResolvedValue({ code: 200, success: true, data: view });
    const res = await claimConfigApi.updateFlowConfig(['AiAssess']);
    expect(mockPut).toHaveBeenCalledTimes(1);
    expect(mockPut).toHaveBeenCalledWith('/flows/decision-graph', { skipNodes: ['AiAssess'] });
    expect(res.data).toEqual(view);
  });

  it('恢复默认 = 显式提交空集，不是省略字段', async () => {
    mockPut.mockResolvedValue({
      code: 200,
      success: true,
      data: { ...view, skipNodes: [], configVersion: 4 },
    });
    await claimConfigApi.updateFlowConfig([]);
    expect(mockPut).toHaveBeenCalledWith('/flows/decision-graph', { skipNodes: [] });
  });

  it('服务端拒绝时错误原样上抛（错误码/消息交给页面展示，不清空现网配置）', async () => {
    const rejection = Object.assign(new Error('流程配置只允许跳过 AI 评估节点'), {
      code: 'SC_CLAIM_FLOW_CONFIG_INVALID',
    });
    mockPut.mockRejectedValue(rejection);
    await expect(claimConfigApi.updateFlowConfig(['Adjust'])).rejects.toMatchObject({
      code: 'SC_CLAIM_FLOW_CONFIG_INVALID',
      message: '流程配置只允许跳过 AI 评估节点',
    });
  });
});
