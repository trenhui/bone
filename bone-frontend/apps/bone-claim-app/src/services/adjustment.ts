/**
 * synapClaim · 理算 API（A6 Adjustment 聚合）。
 *
 * 金额计算唯一入口，AI 不参与。产出示例、重算、确认三步。
 */

import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';
import type { AdjustmentResult } from './types';
import { newIdempotencyKey } from './claim';

const client = createApiClient('/api/v1/claims');

export const adjustApi = {
  /** 查询最新理算结果（未理算 data 为 null）。 */
  latest(claimId: string): Promise<ApiResponse<AdjustmentResult | null>> {
    return client.get<never, ApiResponse<AdjustmentResult | null>>(`/${claimId}/adjust`);
  },
  /** 执行理算（前置：证据已锁定 + 生效责任；产出 FROZEN）。 */
  calculate(claimId: string, idempotencyKey = newIdempotencyKey()): Promise<ApiResponse<AdjustmentResult>> {
    return client.post<never, ApiResponse<AdjustmentResult>>(`/${claimId}/adjust`, undefined, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  },
  /** 重算（规则/责任版本变更后；已确认拒绝）。 */
  recalculate(claimId: string, idempotencyKey = newIdempotencyKey()): Promise<ApiResponse<AdjustmentResult>> {
    return client.post<never, ApiResponse<AdjustmentResult>>(`/${claimId}/adjust/recalculate`, undefined, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  },
  /** 理算确认（核赔通过：结果锁定）。 */
  confirm(claimId: string): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/adjust/confirm`);
  },
};
