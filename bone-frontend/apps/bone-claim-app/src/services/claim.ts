/**
 * synapClaim · 案件主链路 API（A1 ClaimCase 聚合）。
 *
 * 覆盖：报案提交 / 待办列表 / 案件详情 / 分配初审 / 开始初审 / 初审完成 / 转人工 /
 * 领取核赔任务 / 人工决策 / 发起双人复核 / 复核结论 / 自动通过 / 挂起恢复。
 *
 * 踩坑记录（保留作为团队知识）：
 * - POSTMapping 必须显式登记 {"", "/"} 两个路径：前端 axios combineURLs 会把 base + '/'
 *   拼成带尾斜杠的 /api/v1/claims/，Spring Boot 3 默认 PathPatternParser 不做尾斜杠匹配
 * - 幂等键优先由稳定业务键（batchNo + invoiceNo）派生，默认 UUID 只在无业务键时兜底；
 *   每次重试都是新键等于关掉幂等，网络超时重试会真的再建一个案件
 */

import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';
import type {
  AutoApprovalOutcome,
  ClaimCaseResp,
  ClaimDecisionReq,
  ClaimHeadProjection,
  ClaimStatusName,
  ClaimWithItemsProjection,
  HangUpType,
  PageResult,
  SubmitClaimReq,
} from './types';

const client = createApiClient('/api/v1/claims');

function uuid(): string {
  return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/** 生成幂等键（每次提交动作一个新键；重试同一动作必须复用同一键）。 */
export function newIdempotencyKey(): string {
  return uuid();
}

/**
 * 大额双人复核门（后端 {@code ClaimThresholds.DUAL_REVIEW_AMOUNT} 单一真源，1 万元）。
 *
 * 前端仅用于列表徽标与计数展示，不参与任何资金判定；阈值调整必须与后端同批同步。
 * 注意：这是前端镜像常量，真实判定以后端为准——接入配置中心 /dict 后应替换为运行时取值，消除此处第二份真源。
 */
export const REVIEW_THRESHOLD = 10000;

/**
 * 提交幂等键：优先由稳定业务键 batchNo + invoiceNo 派生——同一批次同一张发票
 * 重试/双提交天然同键，后端幂等表可直接去重；默认 UUID 只在无业务键可依时兜底。
 */
function stableSubmitKey(req: SubmitClaimReq): string {
  return req.batchNo && req.invoiceNo
    ? `claim:${req.batchNo}:${req.invoiceNo}`
    : newIdempotencyKey();
}

export const claimApi = {
  /**
   * 提交理赔申请（201；带 Idempotency-Key）。
   * 幂等键优先由稳定业务键派生，缺省时用 UUID。
   */
  submit(req: SubmitClaimReq, idempotencyKey?: string): Promise<ApiResponse<ClaimCaseResp>> {
    return client.post<never, ApiResponse<ClaimCaseResp>>('/', req, {
      headers: { 'Idempotency-Key': idempotencyKey ?? stableSubmitKey(req) },
    });
  },

  /** 待办/列表分页（默认待核赔 PENDING_ADJUST；不传 statuses 时后端按待核赔兜底）。 */
  page(params?: {
    statuses?: ClaimStatusName[];
    sourceChannel?: string;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResult<ClaimHeadProjection>>> {
    return client.get<never, ApiResponse<PageResult<ClaimHeadProjection>>>('/', {
      params,
      /**
       * 数组参数序列化为 statuses=A&statuses=B（indexes: null），
       * axios 默认的 statuses[]=A 带方括号键名不在 Spring 同名重复参数绑定形态内。
       * 后果：绑定失败时后端把空集兜底成"待核赔 40"，默认视图看着是对的，
       * 而切到"已结案/复核中"筛选时列表纹丝不动——典型的"配了不生效"。
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
   * 核定金额走 X-Approved-Amount 请求头（后端用 @RequestHeader 接收，不是请求体）；
   * 该方法此前在前端完全缺失，缺了"完成"这一步初审段在 UI 上根本无法走通。
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
   * 必须由非第一审核人的账号操作，服务端比对 token 主体；通过与否走 X-Approved 请求头。
   * 该方法此前缺失：页面只能"发起复核"，没有"出复核结论"的出口 ⇒ 案件进入 REVIEWING(44) 后
   * 无论如何点都不能结案（auto-approve 只覆盖 12→50，不处理 44），复核成为死锁态。
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

  /** 挂起/恢复（正交机制，不改状态码；FRAUD 仅风控岗可恢复）。 */
  hangUp(claimId: string, type?: HangUpType): Promise<ApiResponse<void>> {
    const headers: Record<string, string> = type
      ? { 'X-Hangup-Action': 'HANG_UP', 'X-Hangup-Type': type }
      : { 'X-Hangup-Action': 'RESUME' };
    return client.post<never, ApiResponse<void>>(`/${claimId}/hangup`, undefined, { headers });
  },
};
