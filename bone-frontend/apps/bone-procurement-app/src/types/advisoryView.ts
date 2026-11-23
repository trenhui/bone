/** AI 建议态视图：与后端 AdvisoryResponse 对齐，只展示建议，不把 AVAILABLE 当成自动决策许可。 */

export interface AdvisoryAdviceDTO {
  capabilityCode: string;
  displayName: string;
  aiAvailable: string;
  advisoryOnly: boolean;
  degradedTo: string | null;
  explicitLabel: string | null;
  implicitMetadata: string | null;
  evidenceRef: string | null;
  reasonCode: string;
  disclosure: string;
  decidedAt: string;
}

const ADVICE_TEXT: Record<string, string> = {
  AVAILABLE: 'AI 辅助建议（待人工终决）',
  UNAVAILABLE: 'AI 未参与',
};

/** 未知态保留原始码并提示无法确认，绝不渲染成「AI 未参与」或「AI 已参与」。 */
export function adviceText(code: string | null | undefined): string {
  if (!code) return '无法确认';
  return ADVICE_TEXT[code] ?? `无法确认（${code}）`;
}

export function adviceErrorMessage(err: unknown): string {
  const status = (err as { response?: { status?: number } })?.response?.status;
  if (status === 403) return '权限不足：无权查看 AI 建议态（403）';
  if (status === 404) return '能力码不存在或建议端点不可达（404）';
  if (status != null) return `无法确认（${status}）`;
  return '无法确认';
}

export const ADVISORY_MANUAL_NOTICE =
  'AI 仅提供待复核建议；最终决定由人工作出，AI 不参与资金支付、授标与准入定性。';
