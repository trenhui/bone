package com.bone.claim.domain.model.task;

import java.math.BigDecimal;

/**
 * 分配求解的输入快照（案件侧维度）。
 *
 * <p><b>为什么是快照而不是案件聚合</b>：任务行要留下「当时按什么维度匹配的」这一审计事实。
 * 若求解时直接读案件聚合、事后不落快照，那么案件金额被修正后，回看任务行就无法解释当初为什么命中金额段规则 （同一任务在审计视角下变成「按现在的金额匹配了过去的规则」）。故快照随任务行一起持久化。
 *
 * @param taskType 任务类型（求解入口）
 * @param claimType 险种（空串=未知，仍可命中通配规则）
 * @param sourceChannel 来源渠道（空串=未知）
 * @param claimAmount 申请金额（可为 null，表示未知；未知金额不命中任何设了金额段的规则）
 */
public record TaskAssignmentContext(
    TaskType taskType, String claimType, String sourceChannel, BigDecimal claimAmount) {

  public TaskAssignmentContext {
    if (taskType == null) {
      throw new IllegalArgumentException("任务类型不能为空");
    }
    claimType = claimType == null ? "" : claimType.trim();
    sourceChannel = sourceChannel == null ? "" : sourceChannel.trim();
  }
}
