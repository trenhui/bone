package com.bone.claim.domain.model.task;

/**
 * 分配求解结果。
 *
 * <p><b>「入池」不是失败，是显式的业务结论</b>：候选全不可用时不得退回「随便挑一个」 也不得抛异常中断收单——正确行为是创建无归属人的任务（{@code PENDING_CLAIM}）+
 * 监控计数。 因此本记录把 {@code assigned=false} 与 {@code reason} 一起返回，让调用方无从「忘记处理无规则场景」 （返回类型里没有 rule
 * 可用，编译期就挡住了臆造归属）。
 *
 * @param rule 命中的规则；{@code assigned=false} 时为 null
 * @param reason 归属理由码（命中为 {@code RULE_MATCH}，否则为 {@code NO_RULE} 或 {@code ALL_INELIGIBLE}）
 */
public record AssignmentDecision(ClaimAssignRule rule, AssignReason reason) {

  public AssignmentDecision {
    if (rule != null && reason != AssignReason.RULE_MATCH) {
      throw new IllegalArgumentException("命中规则时理由码必须是 RULE_MATCH");
    }
    if (rule == null && reason == AssignReason.RULE_MATCH) {
      throw new IllegalArgumentException("RULE_MATCH 必须携带命中的规则");
    }
  }

  public boolean assigned() {
    return rule != null;
  }

  public static AssignmentDecision matched(ClaimAssignRule rule) {
    return new AssignmentDecision(rule, AssignReason.RULE_MATCH);
  }

  public static AssignmentDecision pool(AssignReason reason) {
    if (reason == AssignReason.RULE_MATCH) {
      throw new IllegalArgumentException("入池结论不能用 RULE_MATCH");
    }
    return new AssignmentDecision(null, reason);
  }
}
