/**
 * synapClaim · 只读运营分析（G0「度量先行」的前端出口）。
 *
 * 后端契约：`GET /api/v1/claims/analytics/summary?from=&to=`，权限 `claim:claims:read`。
 * 该端点此前已上线并被 Java 场景测覆盖，但前端**从未消费**——运营指标只能靠文档承诺，
 * 是典型的「后端有能力、前端没接」。本服务把它接进工作台治理空间。
 *
 * 口径纪律（与后端 ClaimAnalyticsApplicationService 一致，勿在前端另造真源）：
 * - `shadowAutomationRate` 是「决策图产出 AUTO_APPROVE / 已收敛执行数」的**影子**口径，
 *   自动放款开关默认关闭、E1/E2 未交付，**不等于实际出金率**。页面必须原样展示后端
 *   `scopeNote`，禁止把它改写成「自动结案率」「已自动赔付」。
 * - `byDecisionResult` 是受控枚举键；未知码降级显示原文，不臆造中文标签。
 * - 取不到值一律显示 `—`，绝不拿 0 冒充真实指标。
 */

import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';

/**
 * 后端 AnalyticsSummary record 的 JSON 形态。
 *
 * <p><b>真机联调实证</b>：经网关 GET /analytics/summary 返回体里 `converged`/`fallbackCount`
 * 与 `byDecisionResult` 的 Long 值是**字符串**（`"31"`，Jackson 全局 long→string，与
 * types.ts 的 normalizeTotal 同因），而比率/耗时是数字。类型声明为 `string | number`，
 * 所有消费 helper 必须同时接受两种形态——否则「mock 用数字绿、真机字符串全显示 —」。
 */
export type NumericLike = string | number;

export interface AnalyticsSummary {
  windowFrom: string;
  windowTo: string;
  converged: NumericLike;
  byDecisionResult: Record<string, NumericLike>;
  fallbackCount: NumericLike;
  shadowAutomationRate: number;
  avgDurationMs: number;
  scopeNote: string;
  /** 复核率分母：窗口内已结案案件数（Long → 字符串）。 */
  reviewedCount: NumericLike;
  /** 复核率分子：第二复核人已介入案件数（Long → 字符串）。 */
  secondReviewedCount: NumericLike;
  /** 单案时长分位数：Double，空样本为 null（不得用 0 冒充）。 */
  caseDurationP50Ms: number | null;
  caseDurationP90Ms: number | null;
  /** 推送成功率分子/分母（Long → 字符串）。 */
  pushSucceeded: NumericLike;
  pushFailed: NumericLike;
}

/** 归一化 Jackson long-as-string：非法值返回 null，交给上层显示 `—`（不用 0 冒充）。 */
function toFiniteNumber(value: NumericLike | null | undefined): number | null {
  if (value == null || value === '') return null;
  const n = typeof value === 'string' ? Number(value) : value;
  return Number.isFinite(n) ? n : null;
}

/** 后端 MAX_WINDOW_DAYS = 31，前端同步收口，避免把超限窗口发过去换 400。 */
export const MAX_ANALYTICS_WINDOW_DAYS = 31;
export const DEFAULT_ANALYTICS_WINDOW_DAYS = 7;

/**
 * 以 UTC 日界生成时间窗（与 `governanceWindow` 同口径，避免本地时区导致跨日漂移）。
 * 天数夹在 [1, 31]；缺省 7 天。
 */
export function analyticsWindow(
  days = DEFAULT_ANALYTICS_WINDOW_DAYS,
  now = Date.now(),
): { from: string; to: string } {
  const dayMs = 24 * 60 * 60 * 1000;
  const safeDays = Math.min(MAX_ANALYTICS_WINDOW_DAYS, Math.max(1, Math.floor(days)));
  const toMs = Math.ceil(now / dayMs) * dayMs;
  const isoSecond = (ms: number) => new Date(ms).toISOString().replace(/\.\d{3}Z$/, 'Z');
  return {
    from: isoSecond(toMs - safeDays * dayMs),
    to: isoSecond(toMs),
  };
}

/** 受控决策结论键 → 中文标签；未知键降级原文（不臆造）。 */
const DECISION_RESULT_LABELS: Readonly<Record<string, string>> = {
  AUTO_APPROVE: '自动通过（影子口径）',
  SUGGEST_REJECT: '建议拒赔',
  MANUAL_REQUIRED: '强制转人工',
  NEED_MORE_EVIDENCE: '需补件',
  UNKNOWN: '未知结论',
};

export function decisionResultLabel(code: string): string {
  return DECISION_RESULT_LABELS[code] ?? code;
}

/** 缺失/非法值一律 `—`，不用 0 冒充；同时接受 long-as-string。 */
export function analyticsCountText(value: NumericLike | null | undefined): string {
  const n = toFiniteNumber(value);
  return n === null ? '—' : String(n);
}

export function shadowRateText(rate: NumericLike | null | undefined): string {
  const n = toFiniteNumber(rate);
  return n === null ? '—' : `${(n * 100).toFixed(1)}%`;
}

/** 比率归一化：分子/分母任一缺值或分母 ≤ 0 一律 `—`，不把「无可计算样本」写成 0%。 */
function rateText(numerator: NumericLike | null | undefined, denominator: number): string {
  const n = toFiniteNumber(numerator);
  return n === null || denominator <= 0 ? '—' : `${((n / denominator) * 100).toFixed(1)}%`;
}

/** 复核率：后端分子是 secondReviewedCount，分母是 reviewedCount（两者都可能 long-as-string）。 */
export function reviewRateText(
  reviewedCount: NumericLike | null | undefined,
  secondReviewedCount: NumericLike | null | undefined,
): string {
  const denominator = toFiniteNumber(reviewedCount);
  return denominator === null ? '—' : rateText(secondReviewedCount, denominator);
}

/** 推送成功率：成功数 /（成功数 + 失败数）；任一项缺值 → `—`。 */
export function pushRateText(
  succeeded: NumericLike | null | undefined,
  failed: NumericLike | null | undefined,
): string {
  const s = toFiniteNumber(succeeded);
  const f = toFiniteNumber(failed);
  if (s === null || f === null) return '—';
  return rateText(s, s + f);
}

/** AI 采纳率：来自 aiFeedback summary，adopts /（adopts + rejects + modifies）。 */
export function adoptionRateText(
  adopts: NumericLike | null | undefined,
  rejects: NumericLike | null | undefined,
  modifies: NumericLike | null | undefined,
): string {
  const a = toFiniteNumber(adopts);
  const r = toFiniteNumber(rejects);
  const m = toFiniteNumber(modifies);
  if (a === null || r === null || m === null) return '—';
  return rateText(a, a + r + m);
}

export function durationText(ms: NumericLike | null | undefined): string {
  const n = toFiniteNumber(ms);
  if (n === null) return '—';
  return n < 1000 ? `${Math.round(n)} ms` : `${(n / 1000).toFixed(2)} s`;
}

const client = createApiClient('/api/v1/claims');

export const analyticsApi = {
  /** 只读聚合；时间窗由调用方用 analyticsWindow 生成，服务端再做 31 天上限校验。 */
  summary(from: string, to: string): Promise<ApiResponse<AnalyticsSummary>> {
    return client.get<never, ApiResponse<AnalyticsSummary>>('/analytics/summary', { params: { from, to } });
  },
};
