/** 治理态势 DTO（与后端 GovernancePostureView 对齐）。 */
export interface GovernancePostureDTO {
  verdict: string;
  capabilities: CapabilityPostureEntryDTO[];
  attentionCount: number;
  undeterminedCount: number;
  modelHealthClaim: string;
}

/** 能力明细：仅结构化判词/计数，不含 reason 原文 / AI output / PII。 */
export interface CapabilityPostureEntryDTO {
  code: string;
  displayName: string;
  state: string;
  decisionAllowed: boolean;
  decisionReasonCode: string;
  driftVerdict: string;
  oversightVerdict: string;
  requiresOwnerReview: boolean;
}

const KNOWN: Record<string, { text: string; color: string }> = {
  UNDETERMINED: { text: '无法确认', color: 'red' },
  ALL_CLEAR: { text: '全部正常', color: 'green' },
  ATTENTION_REQUIRED: { text: '需关注', color: 'orange' },
};

/** 反假绿：未知判词一律保留原始码，绝不压成「正常」。 */
export function postureVerdictTextOrRaw(code: string | null | undefined): string {
  if (!code) return '无法确认';
  const known = KNOWN[code];
  return known ? `${known.text}（原始码：${code}）` : `无法确认（原始码：${code}）`;
}

/** 告警样式：未知码用红色告警；只有明确 ALL_CLEAR 用绿色。 */
export function postureVerdictColor(code: string | null | undefined): string {
  if (!code) return 'red';
  return KNOWN[code]?.color ?? 'red';
}

/** 取数失败 fail-closed 文案。 */
export const GOVERNANCE_POSTURE_FETCH_ERROR = '无法确认';
