/**
 * 决策轨迹「人话化」解释器（synap-claim A3 核赔工作台）。
 *
 * <p>后端 `decision_trace.output` 是各节点输出的 JSON 字符串（含 AI 建议 / 结构化拒赔原因码 / 反欺诈闸
 * 可用性与原因码 / 证据齐备度等）。核赔员与审计需要的不是原始 JSON，而是「为什么给出这个结论」——
 * 本模块把受控原因码映射为中文、把布尔翻成可判读的事实；前端只渲染 {@link explainNode} 返回的 facts，
 * 单一真源在此。
 *
 * <p>原因码须与后端白名单保持一致：拒赔原因码对齐 `RejectReasonCodes`，反欺诈闸原因码对齐
 * `RuleCheckNodeHandler#fraudGateReason`。不一致时降级显示原始码（绝不臆造理由）。
 */

/** AI 结构化拒赔原因码 → 中文（对齐后端 RejectReasonCodes 白名单）。 */
export const REJECT_REASON_LABELS: Record<string, string> = {
  EVIDENCE_INSUFFICIENT: '证据不足',
  CLINICAL_UNREASONABLE: '临床不合理',
  POLICY_EXCLUSION: '保单责任免除',
  AMOUNT_MISMATCH: '金额不符',
  FRAUD_SUSPECTED: '疑似欺诈',
  OTHER: '其他（需人工补充）',
};

/** 反欺诈硬闸未过原因码 → 中文（对齐后端 RuleCheckNodeHandler#fraudGateReason）。 */
export const FRAUD_GATE_LABELS: Record<string, string> = {
  ANTI_FRAUD_CAPABILITY_MISSING: '增强反欺诈能力未集成（转人工）',
  RISK_HIT: '命中风险',
  VERIFY_NOT_PASSED: '验真未通过',
  PASSED: '通过',
};

/** fact 语气：good 通过 / bad 风险或否决 / muted 不可用或未参与 / info 中性。 */
export type FactTone = 'good' | 'bad' | 'muted' | 'info';

export interface ExplainFact {
  label: string;
  value: string;
  tone: FactTone;
}

interface TraceNodeLike {
  nodeName: string;
  output?: string;
}

function parseOutput(output?: string): Record<string, unknown> | null {
  if (!output || !output.trim()) {
    return null;
  }
  try {
    const parsed = JSON.parse(output);
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

const isTrue = (v: unknown): boolean => v === true;

const toNumber = (v: unknown): number | null => {
  const n = typeof v === 'number' ? v : typeof v === 'string' ? Number(v) : NaN;
  return Number.isNaN(n) ? null : n;
};

const fmtPercent = (v: unknown): string | null => {
  const n = toNumber(v);
  return n === null ? null : `${(n * 100).toFixed(0)}%`;
};

const fmtMoney = (v: unknown): string | null => {
  const n = toNumber(v);
  return n === null ? null : `¥${n.toFixed(2)}`;
};

/** 布尔事实：明确指定"真/假"两种语气，避免"真=好"的武断假设（证据齐备为真好，规则冲突为真坏）。 */
const boolFact = (
  label: string,
  v: unknown,
  yes: string,
  no: string,
  onTone: FactTone,
  offTone: FactTone,
): ExplainFact => ({
  label,
  value: isTrue(v) ? yes : no,
  tone: isTrue(v) ? onTone : offTone,
});

function explainAiAssess(o: Record<string, unknown>): ExplainFact[] {
  const facts: ExplainFact[] = [];
  const reject = isTrue(o.rejectSuggested);
  facts.push({
    label: 'AI 建议',
    value: reject ? '建议拒赔' : '不建议拒赔',
    tone: reject ? 'bad' : 'good',
  });
  const reasons = Array.isArray(o.rejectReasons) ? (o.rejectReasons as unknown[]) : [];
  if (reasons.length > 0) {
    facts.push({
      label: '拒赔原因',
      value: reasons.map((r) => REJECT_REASON_LABELS[String(r)] ?? String(r)).join('、'),
      tone: 'bad',
    });
  }
  if (o.aiAvailable !== undefined) {
    facts.push(
      isTrue(o.aiAvailable)
        ? { label: 'AI 能力', value: '已调用模型', tone: 'good' }
        : { label: 'AI 能力', value: '未可用（降级纯规则）', tone: 'muted' },
    );
  }
  if (o.knowledgeAvailable !== undefined) {
    facts.push(
      isTrue(o.knowledgeAvailable)
        ? { label: '临床知识库', value: '已参与核验', tone: 'good' }
        : { label: '临床知识库', value: '未集成（无法判定合理性）', tone: 'muted' },
    );
  }
  return facts;
}

function explainRuleCheck(o: Record<string, unknown>): ExplainFact[] {
  const facts: ExplainFact[] = [
    boolFact('规则冲突', o.ruleConflict, '存在', '无', 'bad', 'good'),
    boolFact('风险命中', o.riskHit, '是', '否', 'bad', 'good'),
  ];
  const gateReason = typeof o.fraudGateReason === 'string' ? o.fraudGateReason : '';
  const gatePassed = isTrue(o.fraudGatePassed);
  facts.push({
    label: '反欺诈硬闸',
    value: gateReason ? FRAUD_GATE_LABELS[gateReason] ?? gateReason : gatePassed ? '通过' : '未通过',
    tone: gatePassed ? 'good' : gateReason === 'RISK_HIT' ? 'bad' : 'muted',
  });
  if (o.visualAvailable !== undefined) {
    facts.push(
      isTrue(o.visualAvailable)
        ? { label: '视觉 E1', value: '已执行', tone: 'good' }
        : { label: '视觉 E1', value: '未集成', tone: 'muted' },
    );
  }
  if (o.graphAvailable !== undefined) {
    facts.push(
      isTrue(o.graphAvailable)
        ? { label: '图计算 E2', value: '已执行', tone: 'good' }
        : { label: '图计算 E2', value: '未集成', tone: 'muted' },
    );
  }
  return facts;
}

function explainAdjust(o: Record<string, unknown>): ExplainFact[] {
  const amount = fmtMoney(o.amount);
  return amount ? [{ label: '理算金额（节点信号）', value: amount, tone: 'info' }] : [];
}

function explainEvidence(o: Record<string, unknown>): ExplainFact[] {
  const facts: ExplainFact[] = [];
  if (o.hasEvidence !== undefined) {
    facts.push(boolFact('证据齐备', o.hasEvidence, '是', '否', 'good', 'bad'));
  }
  if (o.evidenceParsed !== undefined) {
    facts.push(boolFact('解析完成', o.evidenceParsed, '是', '否', 'good', 'bad'));
  }
  const ocr = fmtPercent(o.ocrConfidence);
  if (ocr) {
    facts.push({ label: 'OCR 最低置信度', value: ocr, tone: 'info' });
  }
  if (o.evidenceAllVerifyPassed !== undefined) {
    facts.push(boolFact('验真通过', o.evidenceAllVerifyPassed, '是', '否', 'good', 'bad'));
  }
  return facts;
}

/**
 * 把一条轨迹节点解释成人类可读的事实列表。无输出 / 非 JSON / 未知节点 → 空数组（调用方回退原始 JSON）。
 */
export function explainNode(node: TraceNodeLike): ExplainFact[] {
  const o = parseOutput(node.output);
  if (!o) {
    return [];
  }
  switch (node.nodeName) {
    case 'AiAssess':
      return explainAiAssess(o);
    case 'RuleCheck':
      return explainRuleCheck(o);
    case 'Adjust':
      return explainAdjust(o);
    case 'EvidenceLoad':
      return explainEvidence(o);
    default:
      return [];
  }
}

/** fact 语气 → antd Tag 颜色。 */
export function toneColor(tone: FactTone): string {
  switch (tone) {
    case 'good':
      return 'green';
    case 'bad':
      return 'red';
    case 'muted':
      return 'default';
    default:
      return 'blue';
  }
}
