/** 合同人工终决视图：只提交理由与终决等级，复核人由后端按登录态权威解析。 */

export const FINAL_RISK_LEVELS = ['HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'] as const;
export type FinalRiskLevel = (typeof FINAL_RISK_LEVELS)[number];

export interface ContractReviewReportDTO {
  reviewId: string;
  contractNo: string;
  status: string;
  aiAvailable: string;
  riskLevel: string;
  humanFinalized: boolean;
  humanReason: string | null;
}

export interface ContractOverridePayload {
  reason: string;
  finalRiskLevel: FinalRiskLevel;
}

const RISK_TEXT: Record<string, string> = {
  HIGH: '高风险',
  MEDIUM: '中风险',
  LOW: '低风险',
  UNKNOWN: '无法确认',
};

export function riskLevelText(code: string | null | undefined): string {
  if (!code) return '无法确认';
  return RISK_TEXT[code] ?? code;
}

/** 未知风险码原样透传，不压成 LOW/HIGH，避免伪装成已判定。 */
export function displayRiskLevel(code: string | null | undefined): string {
  if (!code) return '无法确认';
  return RISK_TEXT[code] ?? `未知码：${code}`;
}

/** 构造终决 payload；校验失败返回 null，调用方不得发请求。 */
export function buildOverridePayload(
  reason: string | null | undefined,
  finalRiskLevel: string | null | undefined,
): ContractOverridePayload | null {
  if (!reason || reason.trim().length === 0) return null;
  if (!finalRiskLevel || !FINAL_RISK_LEVELS.includes(finalRiskLevel as FinalRiskLevel)) return null;
  return { reason: reason.trim(), finalRiskLevel: finalRiskLevel as FinalRiskLevel };
}

/** 人工终决后的报告不得被再次覆写；未知状态不乐观判为可终决。 */
export function canFinalize(report: ContractReviewReportDTO | null): boolean {
  return !!report && !report.humanFinalized;
}
