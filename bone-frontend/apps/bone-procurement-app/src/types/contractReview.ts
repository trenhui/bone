/** AI 参与披露 DTO（与后端 AiDisclosureView 对齐）。 */
export interface AiDisclosureDTO {
  reviewId: string;
  aiAvailableCode: string;
  disclosureText: string;
  canConfirm: boolean;
  modelVersionSnapshot: string | null;
}

const KNOWN_TEXT: Record<string, string> = {
  AVAILABLE: 'AI 已参与',
  UNAVAILABLE: 'AI 未参与',
};

/**
 * 未知态（UNKNOWN / UNDETERMINED / 缺失）一律显示「无法确认」，绝不渲染成「AI 未参与」。
 * 仅按参与码判定，modelVersionSnapshot 不作为参与证据。
 */
export function disclosureText(code: string | null | undefined): string {
  if (!code) return '无法确认';
  return KNOWN_TEXT[code] ?? '无法确认';
}

/**
 * 披露文案单一真源：后端 `disclosureText` 为准，但须与参与码一致才透传。
 *
 * <p>双真源风险（SUGGESTION-5）：后端文案与前端 KNOWN_TEXT 各自维护会漂移。这里以
 * 后端文本为真源优先展示，同时用参与码做一致性守卫——若后端文本被篡改或与码冲突，
 * 立即回退到按码生成的本地文案，绝不放宽「未知态 = 无法确认」的合规红线。
 */
export function resolveDisclosureText(
  code: string | null | undefined,
  backendText: string | null | undefined,
): string {
  const fallback = disclosureText(code);
  if (backendText && backendText.trim() === fallback) return backendText;
  return fallback;
}

/**
 * 从接口异常提取可展示的错误码，映射为降级文案（SUGGESTION-2）。
 * 403/404 给出人话提示，其余保留原始码便于排障；未知异常归为 UNKNOWN。
 */
export function disclosureErrorMessage(err: unknown): string {
  const status = (err as { response?: { status?: number } })?.response?.status;
  if (status === 403) return '权限不足：无权查看该审查披露（403）';
  if (status === 404) return '审查记录不存在或已失效（404）';
  if (status != null) return String(status);
  return 'UNKNOWN';
}
