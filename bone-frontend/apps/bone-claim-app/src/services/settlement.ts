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

/**
 * 资金写动作的幂等键：由「案件 + 动作 + 结算单快照」派生。
 *
 * <p>pay/refund 后端 `required=true`；cancel 后端接入可选幂等。重试同一动作（响应丢失/双击）必须命中同一快照，
 * 因此不能像 `newIdempotencyKey()` 那样每次现取 UUID——那等于关掉幂等。结算单快照变了（阶段推进/建新单）
 * 说明是另一个业务动作，自然换键。
 */
export function stableSettlementKey(
  claimId: string,
  action: 'pay' | 'refund' | 'cancel',
  settlement?: Pick<SettlementResult, 'settlementNo' | 'fundingStage' | 'attempt'> | null,
): string {
  const parts = [
    claimId,
    action,
    settlement?.settlementNo,
    settlement?.fundingStage,
    settlement?.attempt == null ? undefined : String(settlement.attempt),
  ].filter((v): v is string => Boolean(v && v.trim()));
  // 结算单尚未加载时没有可锚定的业务事实，此时宁可拒绝调用方传空键（由后端 400 暴露），不用伪稳定键掩盖。
  return parts.length >= 3 ? `settlement:${parts.join('|')}` : newIdempotencyKey();
}

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
  /** 支付（后端要求 Idempotency-Key 必传；缺省键仅供无结算单快照时兜底，页面应传稳定键）。 */
  pay(claimId: string, idempotencyKey = newIdempotencyKey()): Promise<ApiResponse<SettlementResult>> {
    return client.post<never, ApiResponse<SettlementResult>>(`/${claimId}/settlement/pay`, undefined, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  },
  cancel(
    claimId: string,
    req: { payFailed?: boolean },
    idempotencyKey = newIdempotencyKey(),
  ): Promise<ApiResponse<SettlementResult>> {
    return client.post<{ payFailed?: boolean }, ApiResponse<SettlementResult>>(
      `/${claimId}/settlement/cancel`,
      req,
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  },
  /** 退票退款（后端要求 Idempotency-Key 必传）。 */
  refund(claimId: string, idempotencyKey = newIdempotencyKey()): Promise<ApiResponse<SettlementResult>> {
    return client.post<never, ApiResponse<SettlementResult>>(
      `/${claimId}/settlement/refund`,
      undefined,
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  },
};
