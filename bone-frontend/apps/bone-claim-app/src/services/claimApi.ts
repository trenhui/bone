import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';

/**
 * synapClaim 理赔 API（后端 `synap-claim` 模块，`/api/v1/claims`）。
 *
 * 复用平台 `createApiClient`：统一 Bearer 注入、ApiResponse 信封解包、401 过期广播。
 * 幂等：写请求带 `Idempotency-Key` 请求头（UUID v4），同键同参返回首次结果。
 */

const client = createApiClient('/api/v1/claims');

/** 平台文件服务（bone-file，端口 8107；`PlatformApiPaths.FILE_V1 = /api/v1/file`）。 */
const fileClient = createApiClient('/api/v1/file');

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
  /** 审核人不在请求体里：服务端取自已验签 token（身份若可声明，双人复核就只剩自我声明）。 */
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

/* ===================== A6 理算归因（只读展示；§7.4 六归因成分） ===================== */

export type AdjustmentLineType = 'INPUT' | 'RULE' | 'OUTPUT';

export interface AdjustmentLine {
  lineType: AdjustmentLineType;
  /** 责任 UUID（OUTPUT 行为空）。 */
  liabilityUuid?: string;
  /** 输入快照 JSON（仅 INPUT 行有）。 */
  inputSnapshot?: string;
  /** 六归因成分金额（DECIMAL(20,6) 序列化为 number）。 */
  feeCalculate?: number;
  feeDeduct?: number;
  ratioPayout?: number;
  feeOccupied?: number;
  feePayout?: number;
  feeRemain?: number;
  /** 零赔付归因（受控枚举）。 */
  zeroReason?: string;
  /** 拒赔/零赔付原因码（§8.3 受控字典 01-13 + 20/30/40）。 */
  rejectType?: string;
  /** 逐行公式哈希（复算校验）。 */
  formulaHash?: string;
}

export interface AdjustmentResult {
  calculationId: string;
  totalAmount: number;
  /** 公式哈希（可复算锚点）。 */
  formulaHash: string;
  /** 公式快照（人类可读的归因链描述）。 */
  snapshot: string;
  lines: AdjustmentLine[];
}

/* ===================== A2 证据材料 ===================== */

export type DocType = string;
export type OcrStatus = string;

export interface EvidenceResp {
  id: string;
  claimId: string;
  docType?: string;
  fileRef?: string;
  contentHash?: string;
  ocrStatus?: string;
  ocrConfidence?: number;
  verifyValid?: string;
  locked?: boolean;
}

export interface RegisterEvidenceReq {
  fileRef: string;
  contentHash: string;
  docType?: string;
  verifyValid?: string;
}

/* ===================== A7 资金结算 ===================== */

export type AccountType = 'NO_ACCOUNT' | 'PUBLIC' | 'PERSONAL';

export interface SettlementResult {
  settlementNo: string;
  claimId: string;
  amount: number;
  fundingStage: string;
  attempt: number;
}

export interface QuotaTxn {
  direction: string;
  txnType: string;
  amount: number;
  beforeAmount: number;
  afterAmount: number;
  idempotencyKey: string;
  occurredAt?: string;
}

/** A5 额度账户快照（三值：initial/claimed/frozen；可用 = initial − claimed − frozen）+ 流水。 */
export interface QuotaSummary {
  initial: number;
  claimed: number;
  frozen: number;
  available: number;
  transactions: QuotaTxn[];
}

/* ===================== A3 决策执行（AI 编排 DAG：轨迹回放） ===================== */

export interface DecisionExecutionResult {
  traceId: string;
  claimId: string;
  decisionResult: string;
  decisionTier: string;
  status: string;
  traceCount: number;
}

export interface DecisionTraceNode {
  traceId: string;
  claimId: string;
  executionId: string;
  nodeSeq: number;
  nodeType: string;
  nodeName: string;
  inputSummary: string;
  output?: string;
  actionResult: string;
  durationMs: number;
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

  /** 领取核赔任务：领取人即当前登录用户（第一审核人），无需也不可传身份。 */
  claimAdjustTask(claimId: string): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/adjust/claim`);
  },

  /** 人工核赔决策（APPROVED 必填 approvedAmount；REJECTED 必填 comment+rejectType）。 */
  decide(claimId: string, req: ClaimDecisionReq): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/decision`, req);
  },

  /** 发起双人复核：复核人 = 当前登录用户，故同一账号重复发起会被 409 拒（职责分离）。 */
  submitForSecondReview(claimId: string): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/second-review`);
  },

  autoApprove(claimId: string): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/auto-approve`);
  },

  hangUp(claimId: string, type?: HangUpType): Promise<ApiResponse<void>> {
    const headers: Record<string, string> = type
      ? { 'X-Hangup-Action': 'HANG_UP', 'X-Hangup-Type': type }
      : { 'X-Hangup-Action': 'RESUME' };
    return client.post<never, ApiResponse<void>>(`/${claimId}/hangup`, undefined, { headers });
  },
};

/* ===================== A6 理算（金额计算唯一入口，AI 不参与） ===================== */

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

/* ===================== A2 证据材料（影像本体走附件服务 STS 直传，本接口只登记 fileRef） ===================== */

export const evidenceApi = {
  list(claimId: string): Promise<ApiResponse<EvidenceResp[]>> {
    return client.get<never, ApiResponse<EvidenceResp[]>>(`/${claimId}/evidences`);
  },
  register(claimId: string, req: RegisterEvidenceReq, idempotencyKey = newIdempotencyKey()): Promise<ApiResponse<number>> {
    return client.post<RegisterEvidenceReq, ApiResponse<number>>(`/${claimId}/evidences`, req, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  },
  /** 锁定证据集（理算前置）。 */
  lock(claimId: string): Promise<ApiResponse<number>> {
    return client.post<never, ApiResponse<number>>(`/${claimId}/evidences/lock`);
  },
  /** 回填解析结果（OCR/LLM 回调或人工补录）。 */
  markParsed(
    claimId: string,
    evidenceId: string,
    req: { ocrStatus?: string; ocrConfidence?: number; extractedJson?: string; verifyValid?: string },
  ): Promise<ApiResponse<void>> {
    return client.post<unknown, ApiResponse<void>>(`/${claimId}/evidences/${evidenceId}/parse-result`, req);
  },
};

/* ===================== A7 资金结算（两段式：软占 → 硬预占/复核 → 支付） ===================== */

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

/* ===================== A5 额度查询（只读；资金工作台展示额度三值与流水） ===================== */

export const quotaApi = {
  /** 查询案件额度账户快照 + 流水（无则 data 为 null 语义由后端 404 CLAIM_QUOTA_NOT_FOUND 表达）。 */
  get(claimId: string): Promise<ApiResponse<QuotaSummary>> {
    return client.get<never, ApiResponse<QuotaSummary>>(`/${claimId}/quota`);
  },
};

/* ===================== A3 决策执行（执行一次决策图并回放轨迹） ===================== */

export const decisionApi = {
  /** 执行决策图（同案仅一个活跃执行；结论由确定性分诊计算，AI 只产建议与置信度）。 */
  execute(
    claimId: string,
    req?: {
      graphVersion?: string;
      ruleVersion?: string;
      modelVersion?: string;
      promptVersion?: string;
      catalogVersion?: string;
    },
  ): Promise<ApiResponse<DecisionExecutionResult>> {
    return client.post<unknown, ApiResponse<DecisionExecutionResult>>(
      `/${claimId}/decision/execute`,
      req ?? {},
    );
  },
  /** 轨迹回放（按 traceId 取全链节点序列：证据 → 解析 → 核责 → 理算 → 决策）。 */
  trace(claimId: string, traceId: string): Promise<ApiResponse<DecisionTraceNode[]>> {
    return client.get<never, ApiResponse<DecisionTraceNode[]>>(`/${claimId}/decision/trace`, {
      params: { traceId },
    });
  },
};

/* ===================== A8 申诉复议 ===================== */

export type AppealReviewResult = 'UPHELD' | 'REOPEN';

export interface AppealResult {
  appealNo: string;
  claimId: string;
  originalStatus: string;
  status: string;
  reviewResult?: string;
}

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

/** 挂起类型（正交机制，不改状态码；FRAUD 仅风控岗可恢复）。 */
export type HangUpType = 'EVIDENCE' | 'INVESTIGATION' | 'TIMEOUT' | 'FRAUD';

/* ===================== 平台文件服务（bone-file：影像本体直传，返回 objectName 即 fileRef） ===================== */

export interface FileObjectResp {
  objectName: string;
  originalName: string;
  bucket: string;
  contentType: string;
  size: number;
  tenantId: string;
}

export const attachmentApi = {
  /**
   * 上传影像本体到平台附件服务（multipart）。返回 {@link FileObjectResp.objectName} 即证据材料登记的 `fileRef`。
   * 证据登记只需 fileRef + 内容哈希，影像本体不经 synap-claim 应用线程池（§7.2）。
   */
  upload(file: File, bucket?: string): Promise<ApiResponse<FileObjectResp>> {
    const formData = new FormData();
    formData.append('file', file);
    if (bucket) formData.append('bucket', bucket);
    return fileClient.post<FormData, ApiResponse<FileObjectResp>>('/files', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};
