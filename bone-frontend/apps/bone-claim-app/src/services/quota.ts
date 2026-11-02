/**
 * synapClaim · 额度查询（A5 QuotaAccount 聚合）。
 *
 * 只读；资金工作台展示额度三值与流水。
 */

import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';
import type { QuotaSummary } from './types';

const client = createApiClient('/api/v1/claims');

export const quotaApi = {
  /** 查询案件额度账户快照 + 流水（无则 data 为 null 语义由后端 404 CLAIM_QUOTA_NOT_FOUND 表达）。 */
  get(claimId: string): Promise<ApiResponse<QuotaSummary>> {
    return client.get<never, ApiResponse<QuotaSummary>>(`/${claimId}/quota`);
  },
};
