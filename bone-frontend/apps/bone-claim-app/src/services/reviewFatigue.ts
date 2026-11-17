/**
 * synapClaim · 审核疲劳只读评估（soft prompt）。
 *
 * 端点只返回评估结论：不清空待办、不阻断提交、不触发转派。
 * `continuousMinutes` 由调用方（工作台）按本机会话给出，服务端不伪造班次事实；
 * `minutesToShiftEnd` 无服务端班次源时故意不传，故"班末硬排除"仍属 gap。
 */

import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';
import type { ReviewFatigueEvaluation } from './types';

const reviewClient = createApiClient('/api/v1/review');

export const reviewFatigueApi = {
  evaluation(params: { continuousMinutes?: number }): Promise<ApiResponse<ReviewFatigueEvaluation>> {
    return reviewClient.get<never, ApiResponse<ReviewFatigueEvaluation>>(
      '/fatigue-guard/evaluation',
      { params },
    );
  },
};

/** 本机会话连续时长（分钟）：soft prompt 的近似事实，刷新页面不重置。 */
const SESSION_KEY = 'claim-review-fatigue-session-start';

export function sessionContinuousMinutes(now = Date.now()): number {
  const raw = sessionStorage.getItem(SESSION_KEY);
  if (!raw) {
    sessionStorage.setItem(SESSION_KEY, String(now));
    return 0;
  }
  const started = Number(raw);
  if (!Number.isFinite(started) || started > now) {
    sessionStorage.setItem(SESSION_KEY, String(now));
    return 0;
  }
  return Math.max(0, Math.floor((now - started) / 60000));
}
