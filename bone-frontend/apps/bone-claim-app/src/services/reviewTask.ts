/**
 * synapClaim · 工作台任务中心（delivery/05 契约路径 /review/tasks）。
 *
 * 核赔工作台的取数与决策唯一入口。后端 ReviewTaskController 是「门面而非第二套业务」，
 * 三个端点全部转到案件中心能力，本类不含状态判断。
 *
 * 工作台必须走门面，理由：
 * 1. 任务维 → 案件状态集的映射唯一（ReviewTaskStatusMapper）
 * 2. 轨迹只读：GET /tasks/{id}/trace 不触发执行
 * 3. 受控拒赔原因码由服务端强制（REJECTED 必填 rejectType）
 */

import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';
import { newIdempotencyKey } from './claim';
import { stableClaimActionKey } from './claim';
import type {
  ClaimHeadProjection,
  Decision,
  DecisionExecutionSummary,
  DecisionTraceNode,
  PageResult,
  ReviewTaskDecisionReq,
  ReviewTaskStatus,
} from './types';

/** 批量决策单项：ID 用 string（与列表行 claimId 同型），不复用单案请求的五要素结构。 */
export interface BatchDecisionItem {
  claimId: string;
  decision: Decision;
  rejectType?: string;
  approvedAmount?: number;
}

/** 部分成功：succeeded 为案件 ID 列表，failed 逐条带稳定业务码（可据此重试）。 */
export interface BatchDecisionResp {
  succeeded: string[];
  failed: Array<{ claimId: string; code: string; message: string }>;
}

/** 后端上限 20 单；前端同步收口，避免发过去换 400。 */
export const MAX_BATCH_DECISION = 20;

/**
 * 批量决策允许的决策类型：仅「通过」。
 *
 * <p>拒赔必须逐案填写结构化五要素（事实认定 + 条款依据 + 证据位置 + 申诉渠道 + 法定时限），
 * 一批案件的事实与条款各不相同，共用一套五要素本身即不合规，故不能批量。
 *
 * <p>此外后端批量端点 `ReviewTaskController#batchDecision` 的 `BatchDecisionItem` 根本没有
 * `rejection` / `comment` 字段，构造单案请求时把两者写死为 null，域层 `ClaimCase.decide`
 * 的 REJECTED 分支会因 comment 为空必抛异常 ⇒ **批量拒赔 100% 落入 failed 列表**
 * （code=SC_CLAIM_STATUS_CONFLICT），从来就没有成功过。与其让用户点了必失败，不如在前端
 * 显式禁止并引导逐案拒赔。解禁的前提是后端 DTO + 校验链路改造完成，届时改本函数一处即可。
 */
export function isBatchDecisionAllowed(decision: Decision): boolean {
  return decision === 'APPROVED';
}

const reviewClient = createApiClient('/api/v1/review');

export const reviewTaskApi = {
  /** 待办列表（任务维 → 案件状态集；不传 status 取默认待办 40+41，即前端"全部待办"）。 */
  tasks(params: {
    status?: ReviewTaskStatus;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResult<ClaimHeadProjection>>> {
    return reviewClient.get<never, ApiResponse<PageResult<ClaimHeadProjection>>>('/tasks', {
      params,
    });
  },
  /**
   * 审核决策（路由到案件中心人工核赔；审核人取认证主体，请求体不得带审核人）。
   * 后端按可选幂等接入：重试同一决策必须复用同一键，状态推进后自然换键。
   */
  decide(
    claimId: string,
    req: ReviewTaskDecisionReq,
    idempotencyKey = stableClaimActionKey(claimId, 'review-decide'),
  ): Promise<ApiResponse<void>> {
    return reviewClient.post<ReviewTaskDecisionReq, ApiResponse<void>>(
      `/tasks/${claimId}/decision`,
      req,
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  },
  /**
   * 批量决策（上限 20 单，整批幂等键 + 逐条派生子键；部分成功不中断整批）。
   *
   * <p>与 decide 同源同 client；幂等键既放请求体也放 Idempotency-Key 头，与后端
   * `ReviewTaskController#batchDecision` 契约一致。拒赔仍须受控原因码与五要素，
   * 不满足的案件由后端进 failed 列表，前端逐条展示供重试。
   */
  batchDecide(
    items: BatchDecisionItem[],
    idempotencyKey: string = newIdempotencyKey(),
  ): Promise<ApiResponse<BatchDecisionResp>> {
    return reviewClient.post<{ items: BatchDecisionItem[]; idempotencyKey: string }, ApiResponse<BatchDecisionResp>>(
      '/tasks/batch-decision',
      { items, idempotencyKey },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  },
  /** 决策追溯（该案最近一次执行的全链轨迹；无执行记录 404）。 */
  trace(claimId: string): Promise<ApiResponse<DecisionTraceNode[]>> {
    return reviewClient.get<never, ApiResponse<DecisionTraceNode[]>>(`/tasks/${claimId}/trace`);
  },
  /**
   * 决策执行摘要（只读；含自动通过结论的消费结果）。
   *
   * 与 trace 分工：轨迹回答"怎么判出来的"，摘要回答"结论有没有被消费、为什么没有"。
   */
  execution(claimId: string): Promise<ApiResponse<DecisionExecutionSummary>> {
    return reviewClient.get<never, ApiResponse<DecisionExecutionSummary>>(
      `/tasks/${claimId}/execution`,
    );
  },
};
