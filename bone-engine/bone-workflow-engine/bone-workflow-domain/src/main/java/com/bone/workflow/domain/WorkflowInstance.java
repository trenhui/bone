package com.bone.workflow.domain;

import java.time.Instant;

/**
 * 流程实例：一张业务单据的一次审批过程。
 *
 * <p><b>与业务单据 status 的关系（单一真源纪律）</b>：实例的 {@link Phase} 是<b>流程进度的权威</b>；业务单据上的 {@code status}
 * 字段是<b>对外投影</b>，由引擎经 {@code WorkflowStatusPort} 回调平台状态机写入。二者若出现不一致 （如状态动作失败），由对账作业按「以 phase
 * 为准」重投影修复——不允许出现「phase 说没批、status 说批了」。
 */
public record WorkflowInstance(
    String id,
    String entityCode,
    String recordId,
    String definitionKey,
    int definitionVersion,
    String currentNodeCode,
    Phase phase,
    String startedBy,
    Instant startedAt,
    Instant updatedAt) {

  /** 流程阶段。 */
  public enum Phase {
    /** 进行中。 */
    RUNNING,
    /** 已通过（终态）。 */
    APPROVED,
    /** 已驳回（终态）。 */
    REJECTED,
    /** 已撤回（终态）。 */
    WITHDRAWN,
    /**
     * 已挂起：存在无人可办的节点，等待人工介入。
     *
     * <p><b>刻意不算终态</b>：挂起是「暂停待人处理」，不是「流程结束」。若把它当终态，挂起单据将<b>连撤回都不行</b>
     * （发起人被锁死），且没有任何恢复入口——等于制造死单。故挂起态下仍允许发起人撤回，也允许后续恢复（v2）。
     */
    SUSPENDED
  }

  /** 是否已到终态（挂起不算终态）。 */
  public boolean isTerminated() {
    return phase == Phase.APPROVED || phase == Phase.REJECTED || phase == Phase.WITHDRAWN;
  }

  /** 是否处于挂起（等待人工介入）。 */
  public boolean isSuspended() {
    return phase == Phase.SUSPENDED;
  }
}
