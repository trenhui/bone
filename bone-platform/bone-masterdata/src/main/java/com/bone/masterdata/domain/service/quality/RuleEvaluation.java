package com.bone.masterdata.domain.service.quality;

import java.util.List;

/**
 * 单条质量规则对一批记录的求值结果。
 *
 * <p>两种互斥形态：
 *
 * <ul>
 *   <li><b>已求值</b>：{@link #violations()} 为命中明细（可以为空，表示全部通过）；
 *   <li><b>未求值</b>：规则类型/表达式不受支持，{@link #unsupportedReason()} 给出原因—— 刻意与「通过」区分，避免把没算过当成没问题的结论。
 * </ul>
 */
public final class RuleEvaluation {

  /** 单条记录违反规则的明细。 */
  public record Violation(Long recordId, String field, String message) {}

  private final Long ruleId;
  private final String ruleName;
  private final String type;
  private final List<Violation> violations;
  private final String unsupportedReason;

  private RuleEvaluation(
      Long ruleId,
      String ruleName,
      String type,
      List<Violation> violations,
      String unsupportedReason) {
    this.ruleId = ruleId;
    this.ruleName = ruleName;
    this.type = type;
    this.violations = violations == null ? List.of() : List.copyOf(violations);
    this.unsupportedReason = unsupportedReason;
  }

  /** 已求值结果（violations 为空即全部通过）。 */
  public static RuleEvaluation of(
      Long ruleId, String ruleName, String type, List<Violation> violations) {
    return new RuleEvaluation(ruleId, ruleName, type, violations, null);
  }

  /** 未求值结果：类型不受支持或表达式不可解析，绝不冒充为「通过」。 */
  public static RuleEvaluation unsupported(
      Long ruleId, String ruleName, String type, String reason) {
    return new RuleEvaluation(ruleId, ruleName, type, List.of(), reason);
  }

  public Long ruleId() {
    return ruleId;
  }

  public String ruleName() {
    return ruleName;
  }

  public String type() {
    return type;
  }

  public List<Violation> violations() {
    return violations;
  }

  /** 未求值原因；已求值为 {@code null}。 */
  public String unsupportedReason() {
    return unsupportedReason;
  }

  public boolean isUnsupported() {
    return unsupportedReason != null;
  }

  public int violationCount() {
    return violations.size();
  }
}
