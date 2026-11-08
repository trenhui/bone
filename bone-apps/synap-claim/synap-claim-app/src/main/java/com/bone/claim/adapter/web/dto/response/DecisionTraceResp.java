package com.bone.claim.adapter.web.dto.response;

import com.bone.claim.domain.model.decision.DecisionTraceNode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 决策轨迹节点（只读响应契约）。
 *
 * <p>此前 {@code GET /decision/trace} 与 {@code GET /review/tasks/{id}/trace} 直接返回 {@code
 * DecisionTraceNode} 领域实体，把 {@code tenantId}、审计列与 SDK 映射注解的序列化形态一起暴露给调用方，
 * 且领域类加一列就会无意识地改掉对外契约（HC-003）。本 DTO 固定对外字段，装配统一走 {@code DecisionTraceAssembler}。
 *
 * <p>刻意不暴露 {@code tenantId} 与基类主键：租户是服务端隔离维度，基类主键是持久化实现细节，两者都不是回放轨迹的业务字段。
 */
@Schema(description = "决策轨迹节点（只读）")
public record DecisionTraceResp(
    @Schema(description = "追溯ID") String traceId,
    @Schema(description = "案件ID") Long claimId,
    @Schema(description = "执行ID") Long executionId,
    @Schema(description = "节点序号") Integer nodeSeq,
    @Schema(description = "对外节点类型：OCR/RULE/LLM/DECISION") String nodeType,
    @Schema(description = "内部节点名") String nodeName,
    @Schema(description = "输入摘要（≤200 字，不含原始 PII）") String inputSummary,
    @Schema(description = "节点完整输出（JSON 文本）") String output,
    @Schema(description = "节点状态或受控原因码") String actionResult,
    @Schema(description = "节点耗时（毫秒）") Integer durationMs) {

  public static DecisionTraceResp of(DecisionTraceNode node) {
    return new DecisionTraceResp(
        node.getTraceId(),
        node.getClaimId(),
        node.getExecutionId(),
        node.getNodeSeq(),
        node.getNodeType(),
        node.getNodeName(),
        node.getInputSummary(),
        node.getOutput(),
        node.getActionResult(),
        node.getDurationMs());
  }
}
