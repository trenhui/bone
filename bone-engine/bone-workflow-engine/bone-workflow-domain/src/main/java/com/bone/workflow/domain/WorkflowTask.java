package com.bone.workflow.domain;

import java.time.Instant;

/**
 * 审批任务：某审批节点下「一位办理人 × 一项待办」。
 *
 * <p><b>指派快照纪律</b>：办理人在节点进入时一次性解析并<b>冻结</b>在本任务上。组织架构日后调整（换主管、调岗）
 * 不影响已生成的任务——否则同一张单据的审批人会在中途变脸，出现「昨天主管批、今天总监批」的不可审计状态。
 */
public record WorkflowTask(
    String id,
    String instanceId,
    String nodeCode,
    String assigneeId,
    Status status,
    Decision decision,
    String comment,
    Instant dueAt,
    Instant createdAt,
    Instant decidedAt) {

  /** 任务状态。 */
  public enum Status {
    /** 待办。 */
    PENDING,
    /** 已通过。 */
    APPROVED,
    /** 已驳回。 */
    REJECTED,
    /** 已转交（办理权转移给 {@code assigneeId} 指向的新人）。 */
    TRANSFERRED,
    /** 已取消（流程提前终止时作废余下任务）。 */
    CANCELLED
  }

  /** 审批结论。 */
  public enum Decision {
    /** 同意。 */
    APPROVE,
    /** 驳回。 */
    REJECT
  }

  /** 是否仍待办。 */
  public boolean isPending() {
    return status == Status.PENDING;
  }
}
