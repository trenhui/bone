// @vitest-environment node
import { describe, it, expect } from 'vitest';
import {
  FORBIDDEN_PAYLOAD_KEYS,
  MAX_PAYLOAD_LENGTH,
  agreementMeta,
  blindLabelApi,
  scanLabelResult,
  scanPayload,
  statusMeta,
} from './blindLabel';

describe('statusMeta', () => {
  it('登记态给出诚实语义', () => {
    expect(statusMeta('PENDING').label).toBe('待标注');
    expect(statusMeta('LABELED').tone).toBe('neutral');
    expect(statusMeta('UNBLINDED').tone).toBe('success');
  });

  it('未登记码降级展示原文，不得当作可提交', () => {
    const meta = statusMeta('SOMETHING_ELSE');
    expect(meta.label).toBe('未登记状态：SOMETHING_ELSE');
    expect(meta.tone).toBe('warning');
    expect(statusMeta(null).label).toBe('未登记状态：(空)');
  });
});

describe('agreementMeta', () => {
  it('null = 未解盲，与 AI_ABSENT 语义不同', () => {
    expect(agreementMeta(null).label).toBe('未解盲');
    expect(agreementMeta('AI_ABSENT').label).not.toBe('未解盲');
    expect(agreementMeta('AI_ABSENT').tone).toBe('warning');
    expect(agreementMeta('PAIRED').tone).toBe('success');
  });

  it('未登记配对结论按最保守口径处理', () => {
    const meta = agreementMeta('WEIRD');
    expect(meta.label).toBe('未登记结论：WEIRD');
    expect(meta.tone).toBe('warning');
    expect(meta.detail).toContain('不得按配对成立处理');
  });
});

describe('scanPayload', () => {
  it('合法材料引用载荷放行', () => {
    const r = scanPayload('{"evidenceRefs":["E1"],"caseNote":"门诊"}');
    expect(r.ok).toBe(true);
    expect(r.forbiddenHits).toEqual([]);
  });

  it('命中 AI 痕迹键即拒绝并列出命中', () => {
    const r = scanPayload('{"ruleHits":["R-AREA"],"evidenceRefs":["E1"]}');
    expect(r.ok).toBe(false);
    expect(r.forbiddenHits).toContain('ruleHits');
    expect(r.structureError).toContain('AI 痕迹键');
  });

  it('非对象/空对象/空串/非法 JSON 按结构错误拒绝', () => {
    expect(scanPayload('[]').structureError).toContain('JSON 对象');
    expect(scanPayload('42').structureError).toContain('JSON 对象');
    expect(scanPayload('{}').structureError).toContain('空对象');
    expect(scanPayload('  ').structureError).toContain('不能为空');
    expect(scanPayload('{bad').structureError).toContain('合法 JSON');
  });

  it('超长载荷先于解析被拒', () => {
    const big = JSON.stringify({ note: 'x'.repeat(MAX_PAYLOAD_LENGTH + 1) });
    expect(scanPayload(big).structureError).toContain('上限');
  });

  it('禁区键清单与后端逐字一致（防两份漂移）', () => {
    // 后端真源：BlindLabelTask.FORBIDDEN_PAYLOAD_KEYS（14 键）。此处断言数量与锚点键，
    // 若后端扩键而前端未跟，人工比对会在这里拿到明确的失败信号。
    expect(FORBIDDEN_PAYLOAD_KEYS).toHaveLength(14);
    expect(FORBIDDEN_PAYLOAD_KEYS).toContain('aiSuggestion');
    expect(FORBIDDEN_PAYLOAD_KEYS).toContain('confidenceSource');
    expect(FORBIDDEN_PAYLOAD_KEYS).toContain('knowledgeBasis');
  });
});

describe('scanLabelResult', () => {
  it('合法 JSON 对象通过', () => {
    expect(scanLabelResult('{"liability":"全责","amount":100}')).toBeNull();
  });

  it('空/标量/非法 JSON 拒绝', () => {
    expect(scanLabelResult('')).toContain('不能为空');
    expect(scanLabelResult('null')).toContain('JSON 对象');
    expect(scanLabelResult('[]')).toContain('JSON 对象');
    expect(scanLabelResult('{bad')).toContain('合法 JSON');
  });
});

describe('blindLabelApi', () => {
  it('5 个端点路径与后端契约逐字一致', () => {
    // 契约真源：BlindLabelController（/api/v1/claim-ops/blind-labels）。
    expect(blindLabelApi.list).toBeTypeOf('function');
    expect(blindLabelApi.get).toBeTypeOf('function');
    expect(blindLabelApi.dispatch).toBeTypeOf('function');
    expect(blindLabelApi.submit).toBeTypeOf('function');
    expect(blindLabelApi.unblind).toBeTypeOf('function');
  });
});
