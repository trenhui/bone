package com.bone.airules.domain.service;

import com.bone.airules.domain.gateway.RuleEngine;
import com.bone.airules.domain.model.RegressionCase;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则发布前回归门禁（AI 中台 §4.3 治理面 · 业界规则平台"发布即回归"）。
 *
 * <p>新表达式发布（首版 / 新版本）前，用正例 + 负例样本验证行为不变或符合预期：正例必须命中、负例必须不命中，
 * 任一不符即拒绝发布。这是"规则兼容性检查"的最小可落地形态——不依赖样本持久化，由发布调用方携带样本集。
 *
 * <p>刻意实现：
 *
 * <ul>
 *   <li><b>逐条回显</b>：失败时返回 {@link RegressionCase#label()}，快速定位是哪条语义断言被破坏。
 *   <li><b>编译失败即失败</b>：样本命中但求值报错（errorMessage 非空）计为失败，防坏表达式借样本误过。
 *   <li><b>可回放</b>：返回的 {@link RegressionResult} 含逐条命中明细，可写 trace 复核。
 * </ul>
 */
public class RuleRegressionGate {

  private final RuleEngine ruleEngine;

  public RuleRegressionGate(RuleEngine ruleEngine) {
    this.ruleEngine = ruleEngine;
  }

  /** 对表达式跑全样本，返回通过与否 + 逐条明细。空样本集直接通过（仅编译预检由调用方负责）。 */
  public RegressionResult evaluate(String expression, List<RegressionCase> cases) {
    if (cases == null || cases.isEmpty()) {
      return RegressionResult.pass(List.of());
    }
    List<RegressionResult.CaseResult> results = new ArrayList<>();
    for (RegressionCase c : cases) {
      RuleEngine.RuleEvaluation ev = ruleEngine.evaluate(expression, c.input(), false);
      boolean hit;
      String detail;
      if (ev.errorMessage() != null) {
        hit = false;
        detail = "EVAL_ERROR: " + ev.errorMessage();
      } else {
        hit = ev.hit();
        detail = ev.tracePath();
      }
      boolean ok = hit == c.expectHit();
      results.add(new RegressionResult.CaseResult(c.label(), c.expectHit(), hit, ok, detail));
    }
    return new RegressionResult(
        results.stream().allMatch(RegressionResult.CaseResult::passed), results);
  }

  /** 回归结果：是否整体通过 + 逐条样本明细。 */
  public record RegressionResult(boolean passed, List<CaseResult> cases) {

    public static RegressionResult pass(List<CaseResult> cases) {
      return new RegressionResult(true, cases);
    }

    public record CaseResult(
        String label, boolean expectHit, boolean actualHit, boolean passed, String detail) {}
  }
}
