package com.bone.claim.domain.service;

import com.bone.claim.domain.model.task.AssignReason;
import com.bone.claim.domain.model.task.AssigneeType;
import com.bone.claim.domain.model.task.AssignmentDecision;
import com.bone.claim.domain.model.task.ClaimAssignRule;
import com.bone.claim.domain.model.task.TaskAssignmentContext;
import com.bone.claim.domain.port.out.AssigneeEligibilityPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 任务分配求解器（纯函数，无框架依赖）。
 *
 * <p><b>求解顺序固定为 priority 升序 + id 升序</b>：同优先级必须由主键定序而不是集合迭代序。 集合序在数据量变化时会变（数据库返回序、Map
 * 扩容序），同一案件在两个时刻可能命中不同规则， 而「归属是事实」要求同输入同输出。这条判据由单测 grep 断言：注入打乱顺序的规则集，结果必须一致。
 *
 * <p><b>三级回落，每一级都显式</b>：
 *
 * <ol>
 *   <li>无匹配规则 → {@link AssignReason#NO_RULE}（规则表空也算，不报错——新租户上线即无规则）
 *   <li>有匹配但候选目标未配置（空 assignee / 非法类型）或主体不可用 → {@link AssignReason#ALL_INELIGIBLE}
 *   <li>命中 → {@link AssignmentDecision#matched}
 * </ol>
 *
 * <p>两条回落都产出「入池」结论，而不是抛异常：收单流程不能因为「找不到人」而失败， 那会把「分配没配好」升级成「案件收不进来」。
 */
public final class TaskAssigner {

  /** 固定求解顺序：priority 升序，同优先级按 id 升序（null id 排最后，保证确定性）。 */
  static final Comparator<ClaimAssignRule> SOLVE_ORDER =
      Comparator.comparing(
              (ClaimAssignRule r) -> r.getPriority() == null ? Integer.MAX_VALUE : r.getPriority())
          .thenComparing(r -> r.getId() == null ? Long.MAX_VALUE : r.getId());

  private TaskAssigner() {}

  /**
   * 求解归属。
   *
   * @param ctx 求解上下文（任务类型 + 案件维度快照）
   * @param rules 候选规则（调用方已按租户 + 任务类型过滤；本方法不信任入参顺序）
   * @param eligibility 主体可用性判定
   * @return 分配结论（永不返回 null；无可用候选时返回入池结论）
   */
  public static AssignmentDecision decide(
      TaskAssignmentContext ctx, List<ClaimAssignRule> rules, AssigneeEligibilityPort eligibility) {
    if (ctx == null) {
      throw new IllegalArgumentException("求解上下文不能为空");
    }
    if (eligibility == null) {
      throw new IllegalArgumentException("主体可用性端口不能为空（禁止跳过准入闸门）");
    }
    List<ClaimAssignRule> candidates = new ArrayList<>();
    if (rules != null) {
      for (ClaimAssignRule r : rules) {
        if (r != null && r.isEnabled() && r.matches(ctx)) {
          candidates.add(r);
        }
      }
    }
    if (candidates.isEmpty()) {
      return AssignmentDecision.pool(AssignReason.NO_RULE);
    }
    candidates.sort(SOLVE_ORDER);
    for (ClaimAssignRule r : candidates) {
      AssigneeType type = r.targetType();
      if (!r.hasTarget() || type == null) {
        continue;
      }
      if (eligibility.isEligible(type, r.getAssigneeId())) {
        return AssignmentDecision.matched(r);
      }
    }
    return AssignmentDecision.pool(AssignReason.ALL_INELIGIBLE);
  }
}
