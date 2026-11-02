/**
 * synapClaim · 资金结算（A7 Settlement 聚合）。
 *
 * 两段式：软占 → 硬预占/复核 → 支付。
 */

import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';
import type { AccountType, SettlementResult } from './types';
import { newIdempotencyKey } from './claim';

const client = createApiClient('/api/v1/claims');

export const settlementApi = {
  /** 查询最新结算单（无则 data 为 null）。 */
  latest(claimId: string): Promise<ApiResponse<SettlementResult | null>> {
    return client.get<never, ApiResponse<SettlementResult | null>>(`/${claimId}/settlement`);
  },
  /** 建结算单（软占额度；幂等同 claim+attempt）。 */
  create(
    claimId: string,
    req: { attempt: number; accountType: AccountType },
    idempotencyKey = newIdempotencyKey(),
  ): Promise<ApiResponse<SettlementResult>> {
    return client.post<{ attempt: number; accountType: AccountType }, ApiResponse<SettlementResult>>(
      `/${claimId}/settlement`,
      req,
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  },
  /**
   * 审核通过（第一道认证）：小额直接硬预占，≥1 万进入复核冻结。
   * 无请求体——审核人就是当前登录账号的 token，复核人名单由服务端在下一步比对，不接受客户端提名。
   */
  auditPass(claimId: string): Promise<ApiResponse<SettlementResult>> {
    return client.post<never, ApiResponse<SettlementResult>>(`/${claimId}/settlement/audit-pass`);
  },
  /**
   * 大额复核放行（第二道认证）：REVIEW_FREEZE → HARD_RESERVED。
   * 必须换一个账号操作：与审核发起人相同、或是该赔案的第一审核人，服务端一律拒绝。
   */
  confirmReview(claimId: string): Promise<ApiResponse<SettlementResult>> {
    return client.post<never, ApiResponse<SettlementResult>>(`/${claimId}/settlement/review-confirm`);
  },
  pay(claimId: string): Promise<ApiResponse<SettlementResult>> {
    return client.post<never, ApiResponse<SettlementResult>>(`/${claimId}/settlement/pay`);
  },
  cancel(claimId: string, req: { payFailed?: boolean }): Promise<ApiResponse<SettlementResult>> {
    return client.post<{ payFailed?: boolean }, ApiResponse<SettlementResult>>(
      `/${claimId}/settlement/cancel`,
      req,
    );
  },
  refund(claimId: string): Promise<ApiResponse<SettlementResult>> {
    return client.post<never, ApiResponse<SettlementResult>>(`/${claimId}/settlement/refund`);
  },
};
