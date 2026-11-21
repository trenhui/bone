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

/**
 * P0-B：证据锚点页码级定位的判别力。
 *
 * 后端仅在材料真实带出 pageNo 时写入 `evidenceLocators` / `aiFacts.pageNo`，无坐标为空数组。
 * 前端若对旧轨迹补默认页，就是把「不知道在第几页」伪装成「在第 1 页」——幻觉比缺值更危险。
 */
const aiNode = (output: Record<string, unknown>) => ({
  nodeName: 'AiAssess',
  output: JSON.stringify(output),
});

describe('explainNode · 证据锚点页码定位', () => {
  it('AI 节点：aiFacts 带 pageNo/lineNo → 产出锚点 fact，发票号与页码行号上屏', () => {
    const facts = explainNode(
      aiNode({
        rejectSuggested: false,
        evidenceLocators: [{ evidenceRef: 'text@12:5', pageNo: 3, lineNo: 12 }],
        aiFacts: [
          { code: 'invoice_no', value: 'INV-1', evidenceRef: 'text@12:5', pageNo: 3, lineNo: 12 },
        ],
      }),
    );
    const anchors = facts.filter((f) => f.label === '证据锚点');
    // 同一坐标在 aiFacts / evidenceLocators 双写时只出一条（去重，不刷屏）。
    expect(anchors).toHaveLength(1);
    expect(anchors[0].value).toContain('INV-1');
    expect(anchors[0].value).toContain('第3页');
    expect(anchors[0].value).toContain('第12行');
    expect(anchors[0].tone).toBe('info');
  });

  it('证据节点：evidenceLocators 带坐标 → Timeline 也带出锚点', () => {
    const facts = explainNode({
      nodeName: 'EvidenceLoad',
      output: JSON.stringify({
        hasEvidence: true,
        evidenceLocators: [{ evidenceRef: 'text@2:8', pageNo: 2, lineNo: 8 }],
      }),
    });
    const anchor = facts.find((f) => f.label === '证据锚点');
    expect(anchor).toBeDefined();
    expect(anchor!.value).toContain('text@2:8');
    expect(anchor!.value).toContain('第2页');
  });

  it('旧轨迹（无 pageNo、无 evidenceLocators）→ 不产锚点（防幻觉）', () => {
    const facts = explainNode(
      aiNode({
        rejectSuggested: false,
        aiFacts: [{ code: 'invoice_no', value: 'INV-1', evidenceRef: 'text@12:5' }],
        evidenceLocators: [],
      }),
    );
    expect(facts.some((f) => f.label === '证据锚点')).toBe(false);
  });

  it('非法坐标（0 / 负数 / 非数字字符串）→ 不产锚点，绝不补默认页', () => {
    const facts = explainNode(
      aiNode({
        rejectSuggested: false,
        evidenceLocators: [
          { evidenceRef: 'EV-0', pageNo: 0, lineNo: 1 },
          { evidenceRef: 'EV-negative', pageNo: -1, lineNo: 1 },
          { evidenceRef: 'EV-bad', pageNo: 'x', lineNo: 'x' },
        ],
      }),
    );
    expect(facts.some((f) => f.label === '证据锚点')).toBe(false);
  });
});
