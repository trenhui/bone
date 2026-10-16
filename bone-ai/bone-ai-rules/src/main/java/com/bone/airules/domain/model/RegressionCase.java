package com.bone.airules.domain.model;

import java.util.Map;

/**
 * 规则回归样本（发布前正例/负例）：一段求值上下文 + 期望命中结果。
 *
 * <p>业界对位 OPA / Drools / 规则平台"发布即回归"：新表达式发布前用样本集验证，防止破坏性规则进版本链。 正例（{@code
 * expectHit=true}）要求命中；负例（{@code expectHit=false}）要求不命中。
 *
 * @param input 求值上下文（键为 DSL 变量名）
 * @param expectHit 期望命中结果（true=正例，false=负例）
 * @param label 样本语义标签（如"金额≥阈值应拦截"），失败时回显定位
 */
public record RegressionCase(Map<String, Object> input, boolean expectHit, String label) {

  public RegressionCase {
    input = (input == null) ? Map.of() : Map.copyOf(input);
  }

  public static RegressionCase positive(Map<String, Object> input, String label) {
    return new RegressionCase(input, true, label);
  }

  public static RegressionCase negative(Map<String, Object> input, String label) {
    return new RegressionCase(input, false, label);
  }
}
