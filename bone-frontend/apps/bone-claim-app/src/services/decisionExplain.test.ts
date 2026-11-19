// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { explainNode } from './decisionExplain';

/**
 * 决策轨迹「人话化」与后端 RuleCheckNodeHandler 输出的跨层契约测试（前后端联调）。
 *
 * 后端 `RuleCheckNodeHandler` 把图筛查结果写进 `decision_trace.output` JSON：
 * `graphAvailable`（本次是否真实执行）、`graphHit`（是否命中团伙）、`graphClusterId`（团伙标识，仅 available&&hit 非空）。
 * 本测试用后端输出的真实字段形状喂给前端解释器，锁定三件事：
 *  1. 未集成/未执行 →  muted，「未执行」，不谎称「无风险」；
 *  2. 已执行无命中 →  good，「已执行」，且**不**渲染团伙标识（null 不得被解读为已筛查结果）；
 *  3. 已执行且命中 → 带出团伙标识并标注「本地筛查」，语气 bad（风险方向）。
 */
const ruleCheckNode = (output: Record<string, unknown>) => ({
  nodeName: 'RuleCheck',
  output: JSON.stringify(output),
});

describe('explainNode · RuleCheck 图筛查跨层契约', () => {
  it('图筛查未执行：显示「未执行」muted，不渲染团伙标识', () => {
    const facts = explainNode(
      ruleCheckNode({
        ruleConflict: false,
        riskHit: false,
        fraudGatePassed: false,
        fraudGateReason: 'ANTI_FRAUD_CAPABILITY_MISSING',
        graphAvailable: false,
        graphHit: false,
        graphClusterId: null,
      }),
    );
    const graph = facts.find((f) => f.label === '图筛查');
    expect(graph).toMatchObject({ value: '未执行', tone: 'muted' });
    expect(facts.some((f) => f.label === '图筛查团伙')).toBe(false);
  });

  it('图筛查已执行无命中：显示「已执行」good，不渲染团伙标识', () => {
    const facts = explainNode(
      ruleCheckNode({
        ruleConflict: false,
        riskHit: false,
        fraudGatePassed: false,
        graphAvailable: true,
        graphHit: false,
        graphClusterId: null,
      }),
    );
    expect(facts.find((f) => f.label === '图筛查')).toMatchObject({
      value: '已执行',
      tone: 'good',
    });
    expect(facts.some((f) => f.label === '图筛查团伙')).toBe(false);
  });

  it('图筛查命中团伙：带出 clusterId 并标注本地筛查，语气 bad', () => {
    const facts = explainNode(
      ruleCheckNode({
        ruleConflict: false,
        riskHit: true,
        fraudGatePassed: false,
        fraudGateReason: 'RISK_HIT',
        graphAvailable: true,
        graphHit: true,
        graphClusterId: 'INV-a1b2c3d4e5f60718',
      }),
    );
    const cluster = facts.find((f) => f.label === '图筛查团伙');
    expect(cluster).toBeDefined();
    expect(cluster!.value).toContain('INV-a1b2c3d4e5f60718');
    expect(cluster!.value).toContain('本地筛查');
    expect(cluster!.tone).toBe('bad');
  });

  it('旧轨迹（无 graphClusterId 字段）：向后兼容，不渲染团伙事实', () => {
    const facts = explainNode(
      ruleCheckNode({ ruleConflict: false, riskHit: false, fraudGatePassed: false }),
    );
    expect(facts.some((f) => f.label === '图筛查团伙')).toBe(false);
    expect(facts.some((f) => f.label === '图筛查')).toBe(false);
  });
});
