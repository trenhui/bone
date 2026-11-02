/**
 * synapClaim · 申诉复议（A8 AppealRecord 聚合）。
 */

import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';
import type { AppealResult, AppealReviewResult } from './types';

const client = createApiClient('/api/v1/claims');

export const appealApi = {
  /** 发起申诉（受理）：须已终态且未超 48h；幂等。 */
  submit(claimId: string, req: { reason: string }): Promise<ApiResponse<AppealResult>> {
    return client.post<{ reason: string }, ApiResponse<AppealResult>>(`/${claimId}/appeal`, req);
  },
  /** 复议结论：UPHELD 维持 / REOPEN 推翻重启审核。 */
  review(
    claimId: string,
    req: { result: AppealReviewResult; reason: string },
  ): Promise<ApiResponse<AppealResult>> {
    return client.post<{ result: AppealReviewResult; reason: string }, ApiResponse<AppealResult>>(
      `/${claimId}/appeal/review`,
      req,
    );
  },
  /** 查询最新申诉（无则 data 为 null）。 */
  latest(claimId: string): Promise<ApiResponse<AppealResult | null>> {
    return client.get<never, ApiResponse<AppealResult | null>>(`/${claimId}/appeal`);
  },
};
