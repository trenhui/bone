package com.bone.claim.adapter.web.dto.response;

import com.bone.claim.application.DecisionGraphExecutor.DecisionExecutionResult;
import com.bone.claim.application.DecisionRunApplicationService.DecisionRunResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 决策执行响应（执行结果 + 结论消费结果）。
 *
 * <p><b>为什么是平铺而不是嵌套</b>：既有前端按 {@code data.traceId / data.decisionResult} 直读；嵌套会打断所有读点。 新增的 {@code
 * consumptionReason} 是纯追加字段（旧客户端忽略即可），同时让工作台能显示「为什么没自动放款」 ——把「已自动通过但资金没动」这个最容易引起客诉的状态显性化。
 */
@Schema(description = "决策执行结果（含自动通过结论消费状态）")
public record DecisionRunResp(
    @Schema(description = "追溯ID") String traceId,
    @Schema(description = "案件ID") Long claimId,
    @Schema(description = "决策结论") String decisionResult,
    @Schema(description = "决策分级") String decisionTier,
    @Schema(description = "执行状态") String status,
    @Schema(description = "轨迹节点数") int traceCount,
    @Schema(
            description =
                "自动通过结论消费结果：APPLIED=已结案建单；其余为受控跳过原因"
                    + "（AUTO_APPROVE_DISABLED/CAP_EXCEEDED/ALREADY_SETTLED/NOT_AUTO_APPROVE）")
        String consumptionReason) {

  public static DecisionRunResp of(DecisionRunResult run) {
    DecisionExecutionResult execution = run.execution();
    return new DecisionRunResp(
        execution.traceId(),
        execution.claimId(),
        execution.decisionResult(),
        execution.decisionTier(),
        execution.status(),
        execution.traceCount(),
        run.consumptionReason());
  }
}
