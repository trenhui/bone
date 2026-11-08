package com.bone.claim.adapter.web.dto.response;

import com.bone.claim.domain.model.decision.DecisionExecution;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 决策执行摘要（只读）。
 *
 * <p><b>为什么需要它</b>：{@code POST /decision/execute} 的响应里早就有 {@code consumptionReason}，但那是
 * 「执行时才知道」的一次性事实；工作台二次进入、复核、客诉复盘时只能读库。此前读侧没有承载位置， 工作台只好靠「点开决策图 = 重新执行一次决策」把它取回来——查看动作带资金副作用。本 DTO
 * 让只读 轨迹与消费结果可以并列展示：<b>「结论是自动通过，为什么资金没动」不再需要重跑一遍才知道</b>。
 *
 * <p><b>为什么不复用 {@code DecisionRunResp}</b>：那个是执行入口的响应，含 {@code traceCount} 等执行期
 * 计数；读侧要的是持久化事实。硬合并会让「执行」与「查看」两个场景共享一个契约，任一场景加字段都会 波及另一个。
 */
@Schema(description = "决策执行摘要（只读，含自动通过结论的消费结果）")
public record DecisionExecutionResp(
    @Schema(description = "追溯ID（租户内唯一，回放键）") String traceId,
    @Schema(description = "决策结论：AUTO_APPROVE/SUGGEST_REJECT/MANUAL_REQUIRED/NEED_MORE_EVIDENCE")
        String decisionResult,
    @Schema(description = "决策分级：AUTO/REVIEW/MANUAL（内部档，不对外解释为业务话术）") String decisionTier,
    @Schema(description = "执行状态：RUNNING/SUCCESS/FALLBACK/FAILED") String status,
    @Schema(
            description =
                "自动通过结论的消费结果：APPLIED=已结案建单；AUTO_APPROVE_DISABLED=开关关闭；"
                    + "CAP_EXCEEDED=超单笔上限；ALREADY_SETTLED=已结案；"
                    + "NOT_AUTO_APPROVE:<结论>=非自动通过；null=本次执行无自动通过结论可消费")
        String consumptionReason,
    @Schema(description = "消费（或受控跳过）判定时间；与 finishedAt 分离，二者可能相差数秒") Instant consumedAt,
    @Schema(description = "决策执行结束时间") Instant finishedAt) {

  public static DecisionExecutionResp of(DecisionExecution execution) {
    return new DecisionExecutionResp(
        execution.getTraceId(),
        execution.getDecisionResult(),
        execution.getDecisionTier(),
        execution.getStatus(),
        execution.getConsumptionReason(),
        execution.getConsumedAt(),
        execution.getFinishedAt());
  }
}
