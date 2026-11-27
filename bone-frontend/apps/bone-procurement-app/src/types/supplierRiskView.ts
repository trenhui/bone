import type { PerformanceScoreDTO } from '../services/supplierRiskApi';

/** 风险展示模型：字段白名单投影，PII 不进入展示层。 */

export const SEVERITY: Record<string, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '严重',
};

/** 反假绿：未知严重度保留原始码，绝不显示「无风险」。 */
export function severityTextOrRaw(code: string | null | undefined): string {
  if (!code) return '无法确认';
  return SEVERITY[code] ? `${SEVERITY[code]}（原始码：${code}）` : `无法确认（原始码：${code}）`;
}

/** 取数失败统一文案，绝不显示「无风险」。 */
export const FETCH_FAILED_TEXT = '无法确认（取数失败，不代表无风险）';

export interface PerformanceRow {
  supplierId: string | null;
  scoreText: string;
  gradeText: string;
}

/** 绩效缺失显示无法确认，不伪造 0 分。 */
export function toPerformanceRows(scores: PerformanceScoreDTO[] | null | undefined): PerformanceRow[] {
  return (scores ?? []).map((s) => ({
    supplierId: s.supplierId ?? null,
    scoreText: s.score != null && s.score !== '' ? s.score : '无法确认',
    gradeText: s.grade != null && s.grade !== '' ? s.grade : '无法确认',
  }));
}

/** 展示字段白名单：只保留业务标识与绩效结论，剔除证件号/电话等 PII。 */
const ALLOWED = new Set(['supplierId', 'displayName', 'score', 'grade', 'period']);

export function stripPii(raw: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const key of Object.keys(raw ?? {})) {
    if (ALLOWED.has(key)) result[key] = raw[key];
  }
  return result;
}
