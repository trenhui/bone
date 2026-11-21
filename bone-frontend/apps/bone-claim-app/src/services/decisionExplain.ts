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
 * 中文标签统一由后端 `GET /api/v1/claim-config/dicts` 经 codegen 生成（dicts.generated.ts），本文件不再手抄。
 */
import { AI_REJECT_REASON_DICT, FRAUD_GATE_REASON_DICT } from '../constants/dicts.generated';

/** AI 结构化拒赔原因码 → 中文（对齐后端 RejectReasonCodes 白名单，单一真源 dicts.generated.ts）。 */
export const REJECT_REASON_LABELS = AI_REJECT_REASON_DICT;

/** 反欺诈硬闸未过原因码 → 中文（对齐后端 RuleCheckNodeHandler#fraudGateReason，单一真源 dicts.generated.ts）。 */
export const FRAUD_GATE_LABELS = FRAUD_GATE_REASON_DICT;

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

/** 后端 trace output 中的证据锚点（AiGatewayPort.EvidenceLocator / Fact 的页码级定位）。 */
export interface EvidenceLocatorView {
  /** 证据引用（后端 `text@12:5` 等），用于 `/evidence?selectedId=` 深链。 */
  evidenceRef: string;
  /** 展示名：优先取 aiFacts.value（发票号等业务值），否则回退 evidenceRef。 */
  label: string;
  pageNo: number;
  lineNo: number | null;
}

const asRecord = (v: unknown): Record<string, unknown> | null =>
  v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : null;

/** 页码/行号必须是正整数：0、负数、小数、非数字一律视为「材料没带出坐标」。 */
const positiveInt = (v: unknown): number | null => {
  const n = toNumber(v);
  return n !== null && Number.isInteger(n) && n > 0 ? n : null;
};

/**
 * 解析 output 中的 `aiFacts` + `evidenceLocators`，只返回材料真实带出页码的锚点。
 *
 * <p>判别力核心：无坐标时返回空数组——空数组是事实陈述，不是缺陷；补默认页就是幻觉。
 * aiFacts 先入、evidenceLocators 后入并按「证据引用+页+行」去重，避免同一坐标出现两遍。
 */
export function evidenceLocatorsOf(
  output: Record<string, unknown> | string | null | undefined,
): EvidenceLocatorView[] {
  const o = typeof output === 'string' ? parseOutput(output) : output;
  if (!o) return [];
  const anchors: EvidenceLocatorView[] = [];
  const seen = new Set<string>();
  const push = (evidenceRef: string, label: string, pageNoRaw: unknown, lineNoRaw: unknown) => {
    const pageNo = positiveInt(pageNoRaw);
    if (!evidenceRef || pageNo === null) return;
    const lineNo = positiveInt(lineNoRaw);
    const key = `${evidenceRef}|${pageNo}|${lineNo ?? ''}`;
    if (seen.has(key)) return;
    seen.add(key);
    anchors.push({ evidenceRef, label: label || evidenceRef, pageNo, lineNo });
  };
  const facts = Array.isArray(o.aiFacts) ? o.aiFacts : [];
  for (const item of facts) {
    const fact = asRecord(item);
    if (!fact) continue;
    push(
      typeof fact.evidenceRef === 'string' ? fact.evidenceRef : '',
      typeof fact.value === 'string' ? fact.value : '',
      fact.pageNo,
      fact.lineNo,
    );
  }
  const locators = Array.isArray(o.evidenceLocators) ? o.evidenceLocators : [];
  for (const item of locators) {
    const loc = asRecord(item);
    if (!loc) continue;
    const ref = typeof loc.evidenceRef === 'string' ? loc.evidenceRef : '';
    push(ref, ref, loc.pageNo, loc.lineNo);
  }
  return anchors;
}

/** 锚点 → 受控 ExplainFact（形状不变，Timeline 现有渲染零改动即可带出）。 */
export function explainEvidenceLocators(
  output: Record<string, unknown> | string | null | undefined,
): ExplainFact[] {
  return evidenceLocatorsOf(output).map((a) => ({
    label: '证据锚点',
    value: `${a.label} → 影像第${a.pageNo}页${a.lineNo === null ? '' : `（第${a.lineNo}行）`}`,
    tone: 'info' as const,
  }));
}

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
        ? { label: '图筛查', value: '已执行', tone: 'good' }
        : { label: '图筛查', value: '未执行', tone: 'muted' },
    );
  }
  // 本地图算法命中时带出团伙标识（后端 RuleCheckNodeHandler#graphClusterId，仅 available&&hit 非空）。
  // 让核赔员/审计区分「没查」与「查到同一团伙的关联案」，并诚实标注这是本地筛查而非 E2 平台图库结论。
  const clusterId = typeof o.graphClusterId === 'string' ? o.graphClusterId : '';
  if (clusterId) {
    facts.push({ label: '图筛查团伙', value: `${clusterId}（本地筛查）`, tone: 'bad' });
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
    return [...explainAiAssess(o), ...explainEvidenceLocators(o)];
  case 'RuleCheck':
    return explainRuleCheck(o);
  case 'Adjust':
    return explainAdjust(o);
  case 'EvidenceLoad':
    return [...explainEvidence(o), ...explainEvidenceLocators(o)];
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
