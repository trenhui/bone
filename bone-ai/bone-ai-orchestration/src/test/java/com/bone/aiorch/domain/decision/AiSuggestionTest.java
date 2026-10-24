package com.bone.aiorch.domain.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 类型护栏（详设 §3.3 规则 6）：{@link AiSuggestion} 不变量 + 闸门便捷重载 + ArchUnit 边界的契约锁。
 *
 * <p>「AI 建议不得直接成为业务决定」从工程约定升级为编译期 + 架构门禁双重机械强制——这些用例锁死不变量， 防回归（如有人给 {@code evidenceRef}
 * 放开空值、或让闸门绕过建议类型直接吃裸置信度）。
 */
class AiSuggestionTest {

  @Test
  void rejects_blank_evidence_ref() {
    // 无出处的建议不是建议——与检索侧引用强制同源的 fail-closed
    assertThatThrownBy(() -> new AiSuggestion<>("赔付 1000", 0.9, "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("evidenceRef");
    assertThatThrownBy(() -> new AiSuggestion<>("赔付 1000", 0.9, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_out_of_range_confidence() {
    assertThatThrownBy(() -> new AiSuggestion<>("v", 1.2, "trace-1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AiSuggestion<>("v", -0.01, "trace-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void confidence_line_matches_gate_default() {
    assertThat(new AiSuggestion<>("v", 0.85, "t").meetsDefaultConfidenceLine()).isTrue();
    assertThat(new AiSuggestion<>("v", 0.8499, "t").meetsDefaultConfidenceLine()).isFalse();
  }

  @Test
  void gate_adjudicates_suggestion_without_unwrapping() {
    DecisionGate gate = new DecisionGate();
    AiSuggestion<String> high = new AiSuggestion<>("建议赔付 1000", 0.92, "trace-1");
    AiSuggestion<String> low = new AiSuggestion<>("建议赔付 9000", 0.60, "trace-2");

    assertThat(gate.adjudicate(high, new BigDecimal("1000"), true).autoApproved()).isTrue();
    assertThat(gate.adjudicate(low, new BigDecimal("9000"), true).requiresHuman()).isTrue();
    // AI 不可用：建议再好也转人工（绝不 fail 到直连模型）
    assertThat(gate.adjudicate(high, new BigDecimal("1000"), false).requiresHuman()).isTrue();
  }
}
