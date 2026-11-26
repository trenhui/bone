/**
 * 案件签收 API（线上自动签收 + 线下人工批次签收）。
 *
 * 复用 claimApi 的 createApiClient 与幂等键约定：写动作带 Idempotency-Key；
 * 稳定语义操作（回执、确认）用业务标识派生稳定键，重试复用同键；
 * 键超长由 boundedStableKey 压到 varchar(64) 内（真库联调实测坑）。
 */
import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';
import { stableClaimActionKey } from './claim';

const client = createApiClient('/api/v1/claims');

export interface SignCaseItem {
  itemId: string;
  claimNo: string;
  caseId?: string;
  caseStatus: string;
  result: string;
}

export interface SignBatch {
  batchId: string;
  batchNo: string;
  source: 'ONLINE' | 'OFFLINE';
  signType: 'AUTO' | 'MANUAL';
  signChannel: string;
  signStatus: 'WAIT_FOR_PARTICIPANT' | 'WAIT_FOR_IMAGE' | 'WAIT_FOR_CONFIRMATION' | 'FINISHED' | 'DELETED';
  claimCount: number;
  imageReadyCount: number;
  expressCompany?: string;
  expressNo?: string;
  sender?: string;
  signTime?: string;
  signOperator?: string;
  signRemark?: string;
  createdCaseCount?: number;
  skippedCaseCount?: number;
  manualCaseCount?: number;
  caseItems?: SignCaseItem[];
}

export interface SignTrack {
  id: string;
  batchId: string;
  claimId?: string;
  signStatus?: string;
  actionType: string;
  message?: string;
  remark?: string;
  operator?: string;
  occurredAt: string;
}

export interface SignCaseSummary {
  createdCaseCount: number;
  skippedCaseCount: number;
  manualCaseCount: number;
  items: SignCaseItem[];
}

export interface SignBatchListParams {
  source?: 'ONLINE' | 'OFFLINE';
  signStatus?: string;
  page?: number;
  size?: number;
}

export interface SignBatchPage {
  records: SignBatch[];
  total: string | number;
  page: number;
  size: number;
}

export interface CreateOfflineBatchReq {
  signChannel: string;
  businessKey?: string;
  expressCompany?: string;
  expressNo?: string;
  sender?: string;
  arriveTime?: string;
}

export const claimSignApi = {
  /** 线上自动签收回执（外部推送）。 */
  acknowledge(businessKey: string, signChannel = 'API_PUSH') {
    return client.post<never, ApiResponse<{ batchNo: string; batchId: string; created: boolean }>>(
      '/sign',
      { businessKey, signChannel, claimNo: null, claimAmount: null },
      { headers: { 'Idempotency-Key': stableClaimActionKey(businessKey, 'sign-ack') } },
    );
  },

  /** 线下创建批次。 */
  createOfflineBatch(req: CreateOfflineBatchReq) {
    return client.post<never, ApiResponse<string>>('/sign/batches', req);
  },

  /** 导入名单（赔案 ID 列表）。 */
  importParticipants(batchId: string, claimIds: string[]) {
    return client.post<never, ApiResponse<void>>(`/sign/batches/${batchId}/participants`, { claimIds });
  },

  /** 登记影像完备。 */
  registerImage(batchId: string) {
    return client.post<never, ApiResponse<void>>(`/sign/batches/${batchId}/image`);
  },

  /** 二次确认签收。 */
  confirm(batchId: string, remark: string) {
    return client.post<never, ApiResponse<SignCaseSummary>>(
      `/sign/batches/${batchId}/confirm`,
      { remark },
      { headers: { 'Idempotency-Key': stableClaimActionKey(batchId, 'sign-confirm') } },
    );
  },

  /** 批次详情。 */
  detail(batchId: string) {
    return client.get<never, ApiResponse<SignBatch>>(`/sign/batches/${batchId}`);
  },

  /** 轨迹。 */
  tracks(batchId: string) {
    return client.get<never, ApiResponse<SignTrack[]>>(`/sign/batches/${batchId}/tracks`);
  },

  /** 批次列表（服务端真分页）。 */
  list(params: SignBatchListParams) {
    return client.get<never, ApiResponse<SignBatchPage>>('/sign/batches', { params });
  },
};
