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
  /**
   * 本次实际消抵的年度免赔额（后端 {@code AdjustmentResult.deductibleApplied}）。
   *
   * 后端注释明确：A7 结算须把它累加进责任账 `deductible_used`，否则次案仍会重扣全额免赔。
   * 前端此前未声明该字段 ⇒ 页面上「免赔扣了多少」不可见，核赔员无法核对「为什么这单只赔这么多」。
   */
  deductibleApplied?: number | null;
  /**
   * 跨案累计账是否解析到（{@code accumulationAvailable}）。
   *
   * `false` 表示没读到责任账（历史账本缺失）。此时理算结果仍可展示，但 A7 会 fail-closed 拒绝占额——
   * 必须上屏告知，否则用户只会看到「资金工作台点不动」而不知道为什么。
   */
  accumulationAvailable?: boolean;
  /** 本次理算命中的责任（单责任场景）。 */
  liabilityUuid?: string;
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

/**
 * 决策执行（`POST /claims/{id}/decision/execute`）**故意不在前端暴露**。
 *
 * <p>它是**写**操作：落 `claim_decision_execution` + `claim_decision_trace`，且遇 `AUTO_APPROVE`
 * 会按开关自动结案并建结算单（`DecisionController#execute` 的 javadoc 明写）。先前工作台的
 * 「决策图」按钮为了拿一个 `traceId` 去调它，等价于**点一下「查看」就重跑一次决策**：
 * 轻则 409 `SC_CLAIM_DECISION_ACTIVE_CONFLICT`（同案仅允许一个活跃执行），重则为已结案的案子
 * 再造一张结算单。查看类动作不得产生写副作用。
 *
 * <p>只读真源因此是工作台契约 `<b>GET /api/v1/review/tasks/{claimId}/trace</b>`
 * （对应 {@link reviewTaskApi.trace}）：取该案**最近一次**执行的全链节点，无执行记录返回
 * `SC_CLAIM_DECISION_NOT_FOUND`。需要补执行入口时应由业务流程（初审通过/自动通过）驱动，
 * 而不是绑在某个「查看」按钮上。
 */

/** 自动通过 + 建结算单的结果（12→50 且资金链有单）。 */
export interface AutoApprovalOutcome {
  applied: boolean;
  /** APPLIED / AUTO_APPROVE_DISABLED / CAP_EXCEEDED / ALREADY_SETTLED / NOT_AUTO_APPROVE:xxx */
  reason: string;
  settlementNo?: string;
  amount?: number;
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

/**
 * 决策执行摘要（`GET /review/tasks/{id}/execution`）。
 *
 * <p>`consumptionReason` 为 `null` 表示**本次执行没有自动通过结论可消费**（结论是
 * SUGGEST_REJECT / MANUAL_REQUIRED / NEED_MORE_EVIDENCE），不是「消费了但原因未知」——
 * 后者在库里不该出现，前端也不该用兜底文案把它掩盖成正常态。
 */
export interface DecisionExecutionSummary {
  traceId: string;
  decisionResult: string;
  decisionTier: string;
  status: string;
  /** APPLIED / AUTO_APPROVE_DISABLED / CAP_EXCEEDED / ALREADY_SETTLED / NOT_AUTO_APPROVE:<结论> / null */
  consumptionReason?: string | null;
  consumedAt?: string | null;
  finishedAt?: string | null;
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

  /** 待办/列表分页（默认待核赔 PENDING_ADJUST；不传 statuses 时后端按待核赔兜底）。 */
  page(params?: { statuses?: ClaimStatusName[]; sourceChannel?: string; page?: number; size?: number }): Promise<ApiResponse<PageResult<ClaimHeadProjection>>> {
    return client.get<never, ApiResponse<PageResult<ClaimHeadProjection>>>('/', {
      params,
      /**
       * 数组参数序列化为 `statuses=A&statuses=B`（{@code indexes: null}），而不是 axios 默认的
       * `statuses[]=A`。后端 `ClaimPageReq.statuses` 是 `List<ClaimStatus>`，依赖 `getParameterValues("statuses")`
       * 绑定；`statuses[]` 这种带方括号的键名不在「同名重复参数」的绑定形态内。
       *
       * <p>后果不对称：绑定失败时后端把空集兜底成「待核赔 40」，于是**默认视图看起来是对的**，
       * 而用户切到「已结案/复核中」筛选时列表纹丝不动——典型的「配了不生效」。显式指定序列化后，
       * 行为不再取决于框架对 `foo[]` 的隐式支持。
       */
      paramsSerializer: { indexes: null },
    });
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

  /**
   * 初审完成并回填理算核定金额（11/13→12）。
   *
   * <p>核定金额走 `X-Approved-Amount` 请求头（后端 {@code ClaimCaseController#completePreExam} 用
   * `@RequestHeader` 接收，不是请求体）——写成 body 会 400「Required request header is not present」。
   *
   * <p>该方法此前在前端**完全缺失**：`allocate/startPreExam/escalate` 三个方法定义了却没有任何页面调用，
   * 缺了「完成」这一步，初审段（7→10→11/13→12）在 UI 上根本无法走通。
   */
  completePreExam(claimId: string, approvedAmount: number): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/pre-exam/complete`, undefined, {
      headers: { 'X-Approved-Amount': String(approvedAmount) },
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

  /**
   * 复核结论（44→50 通过 / 45 拒赔）。
   *
   * <p>必须由**非第一审核人**的账号操作，服务端比对 token 主体；通过与否走 `X-Approved` 请求头。
   *
   * <p>该方法此前缺失：页面只能「发起复核」，没有「出复核结论」的出口 ⇒ 案件进入 REVIEWING(44) 后
   * 无论如何点都不能结案（`auto-approve` 只覆盖 12→50，不处理 44），复核成为死锁态。
   */
  completeSecondReview(claimId: string, approved: boolean): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/second-review/complete`, undefined, {
      headers: { 'X-Approved': String(approved) },
    });
  },

  /** 自动通过：结案（12→50）+ 建结算单软占额度（同事务；结案必有结算单）。 */
  autoApprove(claimId: string): Promise<ApiResponse<AutoApprovalOutcome>> {
    return client.post<never, ApiResponse<AutoApprovalOutcome>>(`/${claimId}/auto-approve`);
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
   *
   * @param opts.onProgress 上传百分比（0–100）。理赔影像动辄数十 MB，没有进度条时页面在哈希+上传期间
   *     完全无反馈，用户会重复点「上传」——重复登记由 `contentHash` 去重兜底，但交互上仍是缺陷。
   */
  upload(
    file: File,
    opts?: { bucket?: string; onProgress?: (percent: number) => void },
  ): Promise<ApiResponse<FileObjectResp>> {
    const formData = new FormData();
    formData.append('file', file);
    if (opts?.bucket) formData.append('bucket', opts.bucket);
    return fileClient.post<FormData, ApiResponse<FileObjectResp>>('/files', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      // e.total 在部分浏览器首个事件里为 undefined ⇒ 只在已知总量时给百分比，否则报 0 而非 NaN
      onUploadProgress: opts?.onProgress
        ? (e) => opts.onProgress?.(e.total ? Math.round((e.loaded / e.total) * 100) : 0)
        : undefined,
    });
  },
};

/* ===================== A9 后督抽检（管理后台命名空间 /api/v1/review） ===================== */

const reviewClient = createApiClient('/api/v1/review');

export type InspectionDimension = 'AMOUNT' | 'DOCUMENT' | 'MEDICAL' | 'PROCESS';
export type InspectionResult = 'PASS' | 'DIFF';
export type InspectionDiffType =
  | 'ADJUST_MISMATCH'
  | 'RULE_MISS'
  | 'AI_MISJUDGE'
  | 'QUOTA_ABNORMAL'
  | 'NONE';
export type InspectionAction = 'RECALCULATE' | 'REVIEW' | 'REFUND' | 'CLOSE' | 'NONE';

/** 抽检单（任务 + 结论同表；结论即终态，不可改写）。 */
export interface InspectionView {
  inspectionNo: string;
  claimId: string;
  claimNo: string;
  dimension: InspectionDimension;
  sampleStratum: string;
  inspectType: 'AUTO' | 'MANUAL';
  status: 'PENDING' | 'IN_PROGRESS' | 'CONCLUDED';
  inspectorId: string;
  result: '' | InspectionResult;
  diffType: InspectionDiffType;
  diffDetail?: string | null;
  actionTaken: InspectionAction;
  targetStatus: string;
}

export interface ConcludeInspectionReq {
  result: InspectionResult;
  diffType?: InspectionDiffType;
  /** 差异明细（受控字段 → 本方值/复核值）；DIFF 必填，PASS 必须为空。 */
  diffDetail?: Record<string, string>;
  action?: InspectionAction;
  /** 处置目标状态（§6.2 枚举名或质检段 30/31/32；一期只登记不回流）。 */
  targetStatus?: string;
}

export const inspectionApi = {
  /** #13 发起抽检（MANUAL）：案件须已终态；同案重复发起幂等返回原单。 */
  open(
    claimId: string,
    dimension: InspectionDimension = 'AMOUNT',
  ): Promise<ApiResponse<InspectionView>> {
    return reviewClient.post<{ claimId: string; dimension: InspectionDimension }, ApiResponse<InspectionView>>(
      '/inspections',
      { claimId, dimension },
    );
  },
  /** 领取抽检任务（PENDING → IN_PROGRESS）。 */
  claim(inspectionNo: string): Promise<ApiResponse<InspectionView>> {
    return reviewClient.post<never, ApiResponse<InspectionView>>(
      `/inspections/${encodeURIComponent(inspectionNo)}/claim`,
    );
  },
  /** #14 抽检结论（终态）。 */
  conclude(
    inspectionNo: string,
    req: ConcludeInspectionReq,
  ): Promise<ApiResponse<InspectionView>> {
    return reviewClient.post<ConcludeInspectionReq, ApiResponse<InspectionView>>(
      `/inspections/${encodeURIComponent(inspectionNo)}/conclusion`,
      req,
    );
  },
  /** 按单号查询。 */
  detail(inspectionNo: string): Promise<ApiResponse<InspectionView>> {
    return reviewClient.get<never, ApiResponse<InspectionView>>(
      `/inspections/${encodeURIComponent(inspectionNo)}`,
    );
  },
  /** 按案件查最新抽检单（无则 data 为 null）。 */
  latest(claimId: string): Promise<ApiResponse<InspectionView | null>> {
    return reviewClient.get<never, ApiResponse<InspectionView | null>>('/inspections', {
      params: { claimId },
    });
  },
};

/* ===================== 工作台任务中心（delivery/05 契约路径 /review/tasks） ===================== */

/**
 * 工作台任务中心 —— 核赔工作台的**取数与决策**唯一入口。
 *
 * <p>与案件中心 `claimApi.page` / `claimApi.decide` 的关系：后端 `ReviewTaskController` 是
 * 「门面而非第二套业务」，三个端点全部转到案件中心能力，本类不含状态判断。工作台必须走门面，
 * 理由都在后端注释里、也都是前端踩过的坑：
 *
 * <ul>
 *   <li><b>任务维 → 案件状态集的映射唯一</b>（`ReviewTaskStatusMapper`）。前端若自己拿
 *       `statuses: ['PENDING_ADJUST']` 拼，就等于复制一份映射：案件一被领取进入 `ADJUSTING(41)`
 *       就从待办里消失，而「通过/拒赔/补件」恰恰只挂在 41 行上 ⇒ 领了单子反而没法审核。
 *   <li><b>轨迹只读</b>。`GET /tasks/{id}/trace` 取最近一次执行的节点，**不触发执行**；
 *       案件侧的 `POST /claims/{id}/decision/execute` 是写操作（建执行记录、AUTO_APPROVE 还会
 *       自动结案建结算单），不能拿来当「查看」用。
 *   <li><b>受控拒赔原因码由服务端强制</b>（`ReviewDecisionAssembler#requireRejectType`）：
 *       缺 `rejectType` 直接 400 `SC_CLAIM_REJECT_REASON_REQUIRED`，不接受用自由文本兜底。
 * </ul>
 */

/** 任务维 4 值（非案件状态；映射见后端 ReviewTaskStatusMapper）。 */
export type ReviewTaskStatus = 'WAITING' | 'ASSIGNED' | 'PROCESSING' | 'DONE';

export interface ReviewTaskDecisionReq {
  decision: 'APPROVED' | 'REJECTED' | 'REQUEST_MORE_EVIDENCE';
  comment?: string;
  /** 拒赔结构化五要素（R-MAN-03）。 */
  rejection?: {
    factFindings?: string;
    clauseBasis?: string;
    evidenceRefs?: string[];
    appealChannel?: string;
    legalDeadline?: string;
  };
  /** 内部扩展：受控拒赔原因码（REJECTED 必填）。 */
  rejectType?: string;
  approvedAmount?: number;
}

export const reviewTaskApi = {
  /** 待办列表（任务维 → 案件状态集；**不传 status 取默认待办 40+41**，即前端「全部待办」）。 */
  tasks(params: {
    status?: ReviewTaskStatus;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResult<ClaimHeadProjection>>> {
    return reviewClient.get<never, ApiResponse<PageResult<ClaimHeadProjection>>>('/tasks', {
      params,
    });
  },
  /** 审核决策（路由到案件中心人工核赔；审核人取认证主体，**请求体不得带审核人**）。 */
  decide(claimId: string, req: ReviewTaskDecisionReq): Promise<ApiResponse<void>> {
    return reviewClient.post<ReviewTaskDecisionReq, ApiResponse<void>>(
      `/tasks/${claimId}/decision`,
      req,
    );
  },
  /** 决策追溯（该案**最近一次**执行的全链轨迹；无执行记录 404 `SC_CLAIM_DECISION_NOT_FOUND`）。 */
  trace(claimId: string): Promise<ApiResponse<DecisionTraceNode[]>> {
    return reviewClient.get<never, ApiResponse<DecisionTraceNode[]>>(`/tasks/${claimId}/trace`);
  },
  /**
   * 决策执行摘要（只读；含自动通过结论的消费结果）。
   *
   * <p>与 {@link trace} 分工：轨迹回答「怎么判出来的」，摘要回答「结论有没有被消费、为什么没有」。
   * 这个信息此前只在 `POST /decision/execute` 的响应里出现，读侧拿不到，于是「已自动通过但资金没动」
   * 只能靠重跑决策来确认——而重跑是写操作。
   */
  execution(claimId: string): Promise<ApiResponse<DecisionExecutionSummary>> {
    return reviewClient.get<never, ApiResponse<DecisionExecutionSummary>>(
      `/tasks/${claimId}/execution`,
    );
  },
};
