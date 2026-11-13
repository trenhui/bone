/**
 * 工作台联调契约（后端已就绪）—— 跨键检索 + AI 建议曝光/裁决回流。
 *
 * <p>路由全部挂在网关已白名单的 `/api/v1/claims/**` 下（见 bone-gateway application.yml 的
 * `synap-claim` 路由），不新开 `/api/ai/v1/**`。createApiClient 已统一注入 Bearer 与
 * X-Tenant-Id，且响应拦截器把 `success:false` 归一为 reject、把 2xx 成功体的 code 归一为 200，
 * 故调用方只需按 `res.code === 200` 判定成功，沿用 claimApi 的同款口径。
 *
 * <p>后端对照（bone-apps/synap-claim .../adapter/web/controller/ClaimWorkbenchController）：
 *  - GET  /search                                  → ClaimSearchApi.search
 *  - POST /{claimId}/ai/feedback                   → aiFeedbackApi.record
 *  - GET  /{claimId}/ai/feedback                   → aiFeedbackApi.list
 *  - GET  /ai/feedback/summary                     → aiFeedbackApi.summary
 */

import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';

const client = createApiClient('/api/v1/claims');

/** 回流事件响应：replayed=true 表示同 eventId 幂等重放，未产生新记录。 */
export interface AiFeedbackEventResp {
  id: string;
  eventId: string;
  claimId: string;
  eventType: string;
  involvement: string;
  disposition: string | null;
  reasonType: string | null;
  scene: string;
  actorId: string;
  createdAt: string;
  replayed: boolean;
}

/** 服务端采纳率口径：曝光为分母、裁决为分子，30 天滚动窗口；serverSide 恒 true。 */
export interface AiFeedbackSummaryResp {
  impressions: number;
  dispositions: number;
  adopts: number;
  rejects: number;
  modifies: number;
  windowDays: number;
  serverSide: boolean;
}

/** 跨键检索命中：只回最小字段，绝不回 name_masked / phone_masked / person_id_hash 原值。 */
export interface ClaimSearchHit {
  claimId: string;
  claimNo?: string | null;
  policyNo?: string | null;
  status?: string | null;
  stage?: string | null;
  /** 受控分类码，不是被匹配到的原始 PII。 */
  matchedKey?: string | null;
  createdAt?: string | null;
}

export const aiFeedbackApi = {
  /** 登记 AI 建议曝光 / 人工裁决；eventId 幂等，写 append-only 表，不改案件状态与资金。 */
  record(claimId: string | number, payload: Record<string, unknown>): Promise<ApiResponse<AiFeedbackEventResp>> {
    return client.post<never, ApiResponse<AiFeedbackEventResp>>(
      `/${claimId}/ai/feedback`,
      payload,
    );
  },
  /** 查询该案的 AI 回流事件（最多 50 条，按服务端接收时间倒序）。 */
  list(claimId: string | number): Promise<ApiResponse<AiFeedbackEventResp[]>> {
    return client.get<never, ApiResponse<AiFeedbackEventResp[]>>(`/${claimId}/ai/feedback`);
  },
  /** 租户级 AI 采纳率概览（30 天滚动窗口；曝光为分母，裁决为分子）。 */
  summary(): Promise<ApiResponse<AiFeedbackSummaryResp>> {
    return client.get<never, ApiResponse<AiFeedbackSummaryResp>>('/ai/feedback/summary');
  },
};

export const claimSearchApi = {
  /** 报案号/保单号/发票号/批次号/被保人假名化ID + 干系人遮蔽姓名/手机；租户隔离由 SDK 注入。 */
  search(keyword: string, limit = 10): Promise<ApiResponse<ClaimSearchHit[]>> {
    return client.get<never, ApiResponse<ClaimSearchHit[]>>('/search', {
      params: { keyword, limit },
    });
  },
};
