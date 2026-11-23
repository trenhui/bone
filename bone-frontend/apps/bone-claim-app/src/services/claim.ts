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
  AppendCaseReq,
  AutoApprovalOutcome,
  ClaimCaseResp,
  ClaimDecisionReq,
  ClaimHeadProjection,
  ClaimReportDetail,
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
 * 幂等键长度上限，对齐后端 `t_claim_case.idempotency_key / t_claim_report.idempotency_key`
 * 的 `varchar(64) NOT NULL`。稳定键的业务片段（报案号、invoiceNo、policyNo）来自真实数据、
 * 长度不可控；键一旦超过 64，insert 会撞「数据违反完整性约束」400——前端单测全绿，
 * 只在真库联调时暴露。所有稳定键统一过这道闸。
 */
const IDEMPOTENCY_KEY_MAX = 64;

/** FNV-1a 32 位：非加密哈希，快且确定；仅用于把超长业务片段压成定长十六进制。 */
function fnv1a32(input: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return (h >>> 0).toString(16).padStart(8, '0');
}

/**
 * 有界稳定键：短键原样返回（保留可读性与既有格式），超长键保留前缀、
 * 对主体做哈希压缩到 64 以内。同一输入恒等；不同业务快照不碰撞（FNV 32 位 + 前缀）。
 */
function boundedStableKey(prefix: string, body: string): string {
  if (prefix.length + body.length <= IDEMPOTENCY_KEY_MAX) return `${prefix}${body}`;
  return `${prefix}${fnv1a32(body)}`;
}

/**
 * 提交幂等键：优先由稳定业务键 batchNo + invoiceNo 派生——同一批次同一张发票
 * 重试/双提交天然同键，后端幂等表可直接去重；默认 UUID 只在无业务键可依时兜底。
 */
function stableSubmitKey(req: SubmitClaimReq): string {
  // 报案页从不填 batchNo，旧实现必然落到 UUID 兜底 ⇒ 网络重试等同重新建案。
  // 用「批次（若有）+ 保单号 + 票据代码/号码 + 出险时间」派生稳定业务键；
  // 同一份表单重试/双击得到同一键，后端幂等表才有去重依据。
  const parts = [
    req.batchNo,
    req.policyNo ?? req.insurerPolicyNo,
    req.invoiceCode,
    req.invoiceNo,
    req.outInsureTime,
  ].filter((v): v is string => Boolean(v && v.trim()));
  return parts.length ? boundedStableKey('claim:', parts.join('|')) : newIdempotencyKey();
}

/**
 * 案件写动作幂等键：案件 + 动作 + 当前状态快照。
 *
 * <p>后端决策/自动通过按可选幂等接入（同键重放首次结果）。同一案件状态未变时的重试必须同键；
 * 状态推进后再次决策是另一个业务动作，自然换键。请求体本身还参与后端指纹校验，防止同键换内容。
 */
export function stableClaimActionKey(
  claimId: string,
  action: 'decide' | 'auto-approve' | 'review-decide',
  status?: string,
): string {
  const parts = [claimId, action, status].filter((v): v is string => Boolean(v && v.trim()));
  return parts.length >= 2 ? boundedStableKey('claim:', parts.join('|')) : newIdempotencyKey();
}

/**
 * 追加赔案幂等键：报案号 + 票据代码/号码 + 明细金额合计。
 *
 * <p>为什么不用 UUID 兜底：追加赔案是「同一事故第二笔赔付」，网络超时后的重试若不换键，
 * 后端幂等表（uk_tenant_idem）能直接去重；换键就等于关掉幂等，用户点两次会真的多赔一笔。
 * 明细金额合计进键，是为了让「同一个案子补录了更多明细后再次提交」得到新键（那是另一笔业务）。
 */
export function stableAppendCaseKey(reportNo: string, req: AppendCaseReq): string {
  const total = (req.items ?? []).reduce((sum, it) => sum + (Number(it.amount) || 0), 0);
  const parts = [reportNo, req.invoiceCode, req.invoiceNo, total.toFixed(2)].filter((v): v is string =>
    Boolean(v && v.trim()),
  );
  return parts.length >= 2 ? boundedStableKey('claim:append:', parts.join('|')) : newIdempotencyKey();
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
  decide(
    claimId: string,
    req: ClaimDecisionReq,
    idempotencyKey = stableClaimActionKey(claimId, 'decide'),
  ): Promise<ApiResponse<void>> {
    return client.post<never, ApiResponse<void>>(`/${claimId}/decision`, req, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
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
  autoApprove(
    claimId: string,
    idempotencyKey = stableClaimActionKey(claimId, 'auto-approve'),
  ): Promise<ApiResponse<AutoApprovalOutcome>> {
    return client.post<never, ApiResponse<AutoApprovalOutcome>>(`/${claimId}/auto-approve`, undefined, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  },

  /** 挂起/恢复（正交机制，不改状态码；FRAUD 仅风控岗可恢复）。 */
  hangUp(claimId: string, type?: HangUpType): Promise<ApiResponse<void>> {
    const headers: Record<string, string> = type
      ? { 'X-Hangup-Action': 'HANG_UP', 'X-Hangup-Type': type }
      : { 'X-Hangup-Action': 'RESUME' };
    return client.post<never, ApiResponse<void>>(`/${claimId}/hangup`, undefined, { headers });
  },

  /**
   * 报案详情（一次事故告知 + 同案赔案清单）。
   *
   * <p>入参是**报案号**（RP 前缀），不是赔案号：二者是不同命名空间，拿赔案号来查会在后端按号查不到
   * 并返回 404（`SC_CLAIM_REPORT_NOT_FOUND`）。UI 上的入口必须从赔案详情的 `reportNo` 取。
   */
  reportDetail(reportNo: string): Promise<ApiResponse<ClaimReportDetail>> {
    return client.get<never, ApiResponse<ClaimReportDetail>>(`/reports/${encodeURIComponent(reportNo)}`);
  },

  /**
   * 在既有报案下追加赔案（201；带 Idempotency-Key）。
   *
   * <p>保单/计划/被保人/出险时间不在请求体里——它们从报案继承。传了会被后端按
   * `SC_CLAIM_REPORT_CONTEXT_MISMATCH` 拒收（400），因为「允许请求覆盖」等于「允许把 A 保单的赔付挂到 B 事故上」。
   */
  appendCase(
    reportNo: string,
    req: AppendCaseReq,
    idempotencyKey = stableAppendCaseKey(reportNo, req),
  ): Promise<ApiResponse<ClaimCaseResp>> {
    return client.post<never, ApiResponse<ClaimCaseResp>>(
      `/reports/${encodeURIComponent(reportNo)}/cases`,
      req,
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  },

  /**
   * 撤回个人信息处理同意（PIPL 第 15 条；后端 `POST /{id}/consent/revoke`，权限 claim:claims:write）。
   *
   * <p>幂等：重复撤回返回 200 + data=false（本来就已撤回），不 bump version。撤回后分配/初审/核赔/
   * 复核/自动通过等处理类用例一律 422 拒绝；关单/挂起等收尾动作不受影响。**本系统内不可撤销**——
   * 重新处理须由上游同意服务重新采集并签发新的同意记录号，故调用方必须二次确认并明示后果。
   */
  revokeConsent(claimId: string): Promise<ApiResponse<boolean>> {
    return client.post<never, ApiResponse<boolean>>(`/${claimId}/consent/revoke`);
  },
};
