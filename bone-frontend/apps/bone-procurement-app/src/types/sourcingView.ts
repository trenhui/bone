import type { QuotationComparisonDTO } from '../services/sourcingApi';

/** 寻源展示模型：字段白名单投影，不回传后端 output 原文或 PII。 */

export const BID_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  WITHDRAWN: '已撤回',
};

/** 反假绿：未知投标状态保留原始码，绝不压成「已提交」。 */
export function bidStatusTextOrRaw(code: string | null | undefined): string {
  if (!code) return '无法确认';
  return BID_STATUS[code] ? `${BID_STATUS[code]}（原始码：${code}）` : `无法确认（原始码：${code}）`;
}

export interface QuotationRow {
  quoteId: string | null;
  supplierId: string | null;
  amountText: string;
  aiSuggestion: string | null;
  awarded: boolean;
  reasonCode: string | null;
  reasonText: string | null;
}

const REASON_TEXT: Record<string, string> = {
  CURRENCY_MISMATCH: '币种不一致，不可比',
  EXPIRED: '报价已过期，不可比',
};

/**
 * 报价分行：可比报价才进入有效排序；不可比报价单列原因。
 * AI 推荐只作建议字段（aiSuggestion），awarded 恒 false——页面不自行裁决授标。
 */
export function toQuotationRows(quotes: QuotationComparisonDTO[] | null | undefined): {
  valid: QuotationRow[];
  invalid: QuotationRow[];
} {
  const valid: QuotationRow[] = [];
  const invalid: QuotationRow[] = [];
  for (const q of quotes ?? []) {
    const row: QuotationRow = {
      quoteId: q.quoteId ?? null,
      supplierId: q.supplierId ?? null,
      amountText: q.amount != null ? `${q.amount}${q.currency ?? ''}` : '无法确认',
      aiSuggestion: q.aiRecommendation ?? null,
      awarded: false,
      reasonCode: q.reasonCode ?? null,
      reasonText: q.reasonCode ? (REASON_TEXT[q.reasonCode] ?? `不可比（原始码：${q.reasonCode}）`) : null,
    };
    if (q.comparable === true) valid.push(row);
    else invalid.push(row);
  }
  return { valid, invalid };
}

/** 授标确认人缺失显示无法确认，不伪造人工终决身份。 */
export function awardConfirmedByText(operator: string | null | undefined): string {
  if (!operator || operator.trim() === '') return '无法确认';
  return operator;
}
