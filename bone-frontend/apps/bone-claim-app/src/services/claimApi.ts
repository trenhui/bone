import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';

/**
 * synapClaim 理赔 API（后端 `synap-claim` 模块，`/api/v1/claims`）。
 *
 * 复用平台 `createApiClient`：统一 Bearer 注入、ApiResponse 信封解包、401 过期广播。
 * 幂等：写请求带 `Idempotency-Key` 请求头（UUID v4），同键同参返回首次结果。
 */

const client = createApiClient('/api/v1/claims');

/** 费用类别（与后端 ItemCategory 枚举对齐）。 */
export type ItemCategory = 'DRUG' | 'SERVICE' | 'EXAM' | 'MATERIAL';

/** 案件状态（后端 ClaimStatus 枚举 name；DB 持久化口径）。 */
export type ClaimStatusName =
  | 'DRAFT'
  | 'ACCEPTED'
  | 'PENDING_PRE_EXAM'
  | 'PRE_EXAMINING'
  | 'AUTO_PRE_EXAMINING'
  | 'PRE_EXAMINED'
  | 'EVIDENCE_SUBMITTING'
  | 'PENDING_ADJUST'
  | 'ADJUSTING'
  | 'ADJUSTED'
  | 'REVIEWING'
  | 'CLOSED_REJECTED'
  | 'FINISHED'
  | 'APPEALING';

export interface ClaimItemReq {
  itemName: string;
  itemCode?: string;
  category?: ItemCategory;
  quantity?: number;
  unitPrice?: number;
  amount: number;
}

export interface SubmitClaimReq {
  policyNo: string;
  planId: string;
  insuredIdHash: string;
  claimType?: string;
  invoiceCode?: string;
  invoiceNo?: string;
  outInsureTime: string;
  batchNo?: string;
  items: ClaimItemReq[];
}

export interface ClaimCaseResp {
  claimId: string;
  claimNo: string;
  /** 对外两值投影：PENDING_EVIDENCE / REVIEWING（终态为 null） */
  status: string;
}

/** 人工审核决策（对外 decision 字段）。 */
export type Decision = 'APPROVED' | 'REJECTED' | 'REQUEST_MORE_EVIDENCE';

export interface ClaimDecisionReq {
  decision: Decision;
  reviewerId: string;
  comment?: string;
  rejectType?: string;
  approvedAmount?: number;
}

export interface ClaimHeadProjection {
  claimId: string;
  claimNo: string;
  policyNo: string;
  planId: string;
  insuredIdHash: string;
  claimType: string;
  status: ClaimStatusName;
  externalStatus: string;
  hangUpStatus: number;
  sourceChannel: string;
  claimAmount: number;
  approvedAmount: number | null;
  firstReviewerId: string;
  createdAt: string;
}

/** 赔案 + 明细扁平投影（后端每明细一行，无明细时明细列为 null）。 */
export interface ClaimWithItemsProjection {
  claimId: string;
  claimNo: string;
  policyNo: string;
  planId: string;
  insuredIdHash: string;
  claimType: string;
  status: ClaimStatusName;
  externalStatus: string;
  stage: string;
  hangUpStatus: number;
  invoiceCode: string;
  invoiceNo: string;
  outInsureTime: string;
  sourceChannel: string;
  claimAmount: number;
  approvedAmount: number | null;
  firstReviewerId: string;
  secondReviewerId: string;
  rejectComment: string;
  rejectType: string;
  planVersion: string;
  createdAt: string;
  itemId: string | null;
  itemName: string | null;
  itemCode: string | null;
  itemCategory: ItemCategory | null;
  itemQuantity: number | null;
  itemUnitPrice: number | null;
  itemAmount: number | null;
  itemValidAmount: number | null;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

function uuid(): string {
  return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/** 生成幂等键（每次提交动作一个新键；重试同一动作复用同一键由调用方决定）。 */
export function newIdempotencyKey(): string {
  return uuid();
}

export const claimApi = {
  /** 提交理赔申请（201；带 Idempotency-Key）。 */
  submit(req: SubmitClaimReq, idempotencyKey = newIdempotencyKey()): Promise<ApiResponse<ClaimCaseResp>> {
    return client.post<never, ApiResponse<ClaimCaseResp>>('/', req, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  },

  /** 待办/列表分页（默认待核赔 PENDING_ADJUST）。 */
  page(params?: { statuses?: ClaimStatusName[]; sourceChannel?: string; page?: number; size?: number }): Promise<ApiResponse<PageResult<ClaimHeadProjection>>> {
    return client.get<never, ApiResponse<PageResult<ClaimHeadProjection>>>('/', { params });
  },

  /** 案件详情（进度追踪）：赔案头 + 费用明细，每明细一行；查不到返回 404（防枚举）。 */
  detail(claimId: string): Promise<ApiResponse<ClaimWithItemsProjection[]>> {
    return client.get<never, ApiResponse<ClaimWithItemsProjection[]>>(`/${claimId}`);
  },

  allocate(claimId: string): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/allocate`);
  },

  startPreExam(claimId: string, automated: boolean): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/pre-exam/start`, undefined, {
      headers: { 'X-Automated': String(automated) },
    });
  },

  escalate(claimId: string): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/escalate`);
  },

  claimAdjustTask(claimId: string, reviewerId: string): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/adjust/claim`, undefined, {
      headers: { 'X-Operator-Id': reviewerId },
    });
  },

  /** 人工核赔决策（APPROVED 必填 approvedAmount；REJECTED 必填 comment+rejectType）。 */
  decide(claimId: string, req: ClaimDecisionReq): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/decision`, req);
  },

  submitForSecondReview(claimId: string, reviewerId: string): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/second-review`, undefined, {
      headers: { 'X-Operator-Id': reviewerId },
    });
  },

  autoApprove(claimId: string): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/auto-approve`);
  },

  hangUp(claimId: string, type?: 'EVIDENCE' | 'INVESTIGATION' | 'TIMEOUT' | 'FRAUD'): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/hangup`, undefined, {
      headers: type ? { 'X-Hangup-Action': 'HANG_UP', 'X-Hangup-Type': type } : { 'X-Hangup-Action': 'RESUME' },
    });
  },
};
