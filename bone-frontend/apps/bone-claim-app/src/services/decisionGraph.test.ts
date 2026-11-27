// @vitest-environment node
import { describe, expect, it } from 'vitest';
import {
  DECISION_GRAPH_NODES,
  SKIPPABLE_NODES,
  validateSkipNodes,
} from './decisionGraph';

/**
 * 决策图编排的「即时反馈」校验钉子。
 *
 * <p>后端真源：`DecisionGraph.canonical()` 固定 5 节点顺序、`SKIPPABLE_NODES = Set.of(AiAssess)`；
 * 本测试锁定前端投影不漂移，且校验只覆盖「节点存在性 / 白名单 / 必需节点不可跳过」三件事。
 * 注意：这里是即时反馈，不是第二套门禁——服务端 `save()` 才是最终裁决。
 */
describe('decisionGraph · canonical 投影', () => {
  it('节点顺序与后端 canonical() 一一对应，且只读投影不可被改写', () => {
    expect(DECISION_GRAPH_NODES.map((n) => n.name)).toEqual([
      'EvidenceLoad',
      'RuleCheck',
      'AiAssess',
      'Adjust',
      'Arbitrate',
    ]);
  });

  it('白名单只有 AiAssess（金额 Adjust / 结论 Arbitrate 绝不外放）', () => {
    expect(SKIPPABLE_NODES).toEqual(['AiAssess']);
    expect(DECISION_GRAPH_NODES.filter((n) => n.skippable).map((n) => n.name)).toEqual([
      'AiAssess',
    ]);
  });
});

describe('validateSkipNodes · 合法配置', () => {
  it('空集 = 恢复 canonical 图，本地校验通过', () => {
    expect(validateSkipNodes([])).toEqual([]);
  });

  it('AiAssess 在白名单内，校验通过', () => {
    expect(validateSkipNodes(['AiAssess'])).toEqual([]);
  });
});

describe('validateSkipNodes · 非法配置即时反馈', () => {
  it('节点存在性：执行图里不存在的节点被拒，提示「不在决策图中」', () => {
    const errors = validateSkipNodes(['FraudScreen']);
    expect(errors).toHaveLength(1);
    expect(errors[0]).toContain('不在决策图中');
  });

  it('必需节点不可跳过：Adjust / Arbitrate / EvidenceLoad / RuleCheck 逐个被拒', () => {
    for (const node of ['Adjust', 'Arbitrate', 'EvidenceLoad', 'RuleCheck']) {
      const errors = validateSkipNodes([node]);
      expect(errors).toHaveLength(1);
      expect(errors[0]).toContain('必需节点');
      expect(errors[0]).toContain(node);
    }
  });

  it('混合提交含非法节点时非法项被拒，白名单项不背锅', () => {
    const errors = validateSkipNodes(['AiAssess', 'Adjust']);
    expect(errors).toHaveLength(1);
    // 被拒的是 Adjust；AiAssess 只作为「当前仅 X 可跳过」的提示出现，不是被拒项。
    expect(errors[0]).toContain('必需节点「Adjust」不可跳过');
  });

  it('skipNodes 为 null / undefined 被拒（与后端 @NotNull 同口径）', () => {
    expect(validateSkipNodes(null)[0]).toContain('skipNodes 不能为空');
    expect(validateSkipNodes(undefined)[0]).toContain('skipNodes 不能为空');
  });

  it('空白字符串节点被拒，不把空值悄悄当成「没跳过」', () => {
    const errors = validateSkipNodes(['']);
    expect(errors).toHaveLength(1);
    expect(errors[0]).toContain('不在决策图中');
  });

  it('重复节点给出即时提示（服务端按 Set 存储，前端先提醒用户）', () => {
    const errors = validateSkipNodes(['AiAssess', 'AiAssess']);
    expect(errors).toHaveLength(1);
    expect(errors[0]).toContain('重复');
  });
});
