/**
 * synapClaim 前端共享类型定义（从 claimApi.ts 拆分而来）。
 *
 * 把 9 个 domain service 文件共同依赖的类型集中到一处，避免循环依赖；
 * 每个 service 文件只放 API 调用函数 + 自身特有的局部类型。
 */

import type { ApiResponse } from '@bone/shared-types';

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

/** 人工审核决策（后端 Decision 字段）。 */
export type Decision = 'APPROVED' | 'REJECTED' | 'REQUEST_MORE_EVIDENCE';

/** 理赔申请请求（员工端提交）。 */
export interface ClaimItemReq {
  itemName: string;
  itemCode?: string;
  category?: ItemCategory;
  /** 费用类别编码（t_ref_fee_category.fee_code）；空 = 未分类，理算按责任主比例并落诊断提示。 */
  feeCode?: string;
  quantity?: number;
  unitPrice?: number;
  amount: number;
}

export interface SubmitClaimReq {
  /**
   * 平台保单号（t_claim_policy.policy_no）。
   *
   * 与 insurerPolicyNo 二者**至少填一个**（都为空后端返回 400 SC_CLAIM_POLICY_KEY_REQUIRED）。
   * 两者都填时以本字段为准，保司号作为交叉校验——两号指向不同保单会被拒收（422 SC_CLAIM_POLICY_NUMBER_CONFLICT）。
   */
  policyNo?: string;
  /**
   * 保司侧保单号（t_claim_policy.insurer_policy_no，§16-2）。
   *
   * 用于只拿得到保司自己保单号的场景（保司来单/团险成员卡面）。该号不唯一：命中多张可用保单且
   * 无法按被保人收敛时，后端返回 422 SC_CLAIM_POLICY_AMBIGUOUS，需改传平台保单号。
   */
  insurerPolicyNo?: string;
  planId: string;
  insuredIdHash: string;
  claimType?: string;
  invoiceCode?: string;
  invoiceNo?: string;
  /**
   * PIPL 单独同意记录号（后端 consent_record_id，选填）。
   *
   * 医疗数据属敏感个人信息，PIPL 第 29 条要求单独同意。选填而非必填：存量渠道与旁路同步尚无此字段，
   * 一刀切必填会让存量报案全线被拒；留痕一旦登记则不可改写（后端强约束）。
   */
  consentRecordId?: string;
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

export interface ClaimDecisionReq {
  decision: Decision;
  /** 审核人不在请求体里：服务端取自已验签 token（身份若可声明，双人复核就只剩自我声明）。 */
  comment?: string;
  rejectType?: string;
  approvedAmount?: number;
}

/** 赔案头（工作台列表行）。 */
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
  /**
   * PIPL 单独同意记录号（后端 t_claim_case.consent_record_id）。
   * null = 未登记引用（存量案件/该渠道未接同意流水），不等于未取得同意。
   */
  consentRecordId: string | null;
  /** 同意撤回标记（0 未撤回 / 1 已撤回）。撤回后后端处理类动作一律 422。 */
  consentWithdrawnFlag: number;
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
  // C1（P0）后端全局把 Long 序列化为字符串，分页 total 可能为 string；
  // 与 claimConfigApi 同口径用 string | number 容错，避免 JS 安全整数截断与类型漂移。
  total: string | number;
  page: number;
  size: number;
}

/** 把可能为 string 的分页 total 归一为 number（C1 容错）。 */
export function normalizeTotal(total: string | number | undefined): number {
  if (total == null) return 0;
  const n = typeof total === 'string' ? Number(total) : total;
  return Number.isFinite(n) ? n : 0;
}

/* ===================== 理算 ===================== */

export type AdjustmentLineType = 'INPUT' | 'RULE' | 'OUTPUT';

export interface AdjustmentLine {
  lineType: AdjustmentLineType;
  /** 责任 UUID（OUTPUT 行为空）。 */
  liabilityUuid?: string;
  /** 输入快照 JSON（仅 INPUT 行有）。 */
  inputSnapshot?: string;
  /** 六归因成分金额（DDL DECIMAL(12,2)；12,2 在 JS 双精度安全整数范围内无损）。 */
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
  formulaHash: string;
  snapshot: string;
  lines: AdjustmentLine[];
  /** 本次实际消抵的年度免赔额；后端 Settlement须把它累加进责任账 deductible_used。 */
  deductibleApplied?: number | null;
  /** 跨案累计账是否解析到（false = 没读到责任账，Settlement 会 fail-closed 拒绝占额）。 */
  accumulationAvailable?: boolean;
  liabilityUuid?: string;
}

/* ===================== 证据 ===================== */

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

/* ===================== 资金 ===================== */

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
  bizDate?: string;
  idempotencyKey: string;
  occurredAt?: string;
}

/**
 * A5 额度账户快照（三值：initial/claimed/frozen）+ 流水。
 *
 * 渲染前必须先看 opened：账户按 (被保人, 保单, 计划, 年度, 维度) 开户，
 * "这个案子还没有额度账户"是合法状态（年度口径切换、开户未跑、报案数据缺行键）。
 */
export interface QuotaSummary {
  quotaYear: number | null;
  quotaYearBasis: string;
  opened: boolean;
  initial: number;
  claimed: number;
  frozen: number;
  deductibleUsed: number;
  capUsed: number;
  available: number;
  capReached: boolean;
  transactions: QuotaTxn[];
}

/* ===================== 决策执行（轨迹回放） ===================== */

/** 自动通过 + 建结算单的结果（12→50 且资金链有单）。 */
export interface AutoApprovalOutcome {
  applied: boolean;
  /** APPLIED / AUTO_APPROVE_DISABLED / CAP_EXCEEDED / ALREADY_SETTLED / NOT_AUTO_APPROVE:<结论> */
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

/* ===================== 申诉 ===================== */

export type AppealReviewResult = 'UPHELD' | 'REOPEN';

export interface AppealResult {
  appealNo: string;
  claimId: string;
  originalStatus: string;
  status: string;
  reviewResult?: string;
}

export type HangUpType = 'EVIDENCE' | 'INVESTIGATION' | 'TIMEOUT' | 'FRAUD';

/* ===================== 后督抽检 ===================== */

export type InspectionDimension = 'AMOUNT' | 'DOCUMENT' | 'MEDICAL' | 'PROCESS';
export type InspectionResult = 'PASS' | 'DIFF';
export type InspectionDiffType =
  | 'ADJUST_MISMATCH'
  | 'RULE_MISS'
  | 'AI_MISJUDGE'
  | 'QUOTA_ABNORMAL'
  | 'NONE';
export type InspectionAction = 'RECALCULATE' | 'REVIEW' | 'REFUND' | 'CLOSE' | 'NONE';

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
  diffDetail?: Record<string, string>;
  action?: InspectionAction;
  targetStatus?: string;
}

/* ===================== 文件服务 ===================== */

export interface FileObjectResp {
  objectName: string;
  originalName: string;
  bucket: string;
  contentType: string;
  size: number;
  tenantId: string;
}

/* ===================== 工作台任务中心 ===================== */

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

/**
 * 把 ApiResponse 的泛型传递到底：服务层直接返回 ApiResponse<T>，
 * 业务组件解包后拿到 T，类型链路不断。
 */
export type ApiResult<T> = ApiResponse<T>;
