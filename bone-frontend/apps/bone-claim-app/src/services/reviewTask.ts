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
  /** 审核决策（路由到案件中心人工核赔；审核人取认证主体，请求体不得带审核人）。 */
  decide(claimId: string, req: ReviewTaskDecisionReq): Promise<ApiResponse<void>> {
    return reviewClient.post<ReviewTaskDecisionReq, ApiResponse<void>>(
      `/tasks/${claimId}/decision`,
      req,
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
