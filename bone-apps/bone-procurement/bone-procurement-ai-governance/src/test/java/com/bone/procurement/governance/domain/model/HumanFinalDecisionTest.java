package com.bone.procurement.governance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 人工终决红线单测：终决权恒在人工侧、override 识别、具名/耗时强校验。 */
class HumanFinalDecisionTest {

  private HumanFinalDecision decision(
      String aiConclusion, String finalConclusion, String reviewer, long duration) {
    return new HumanFinalDecision(
        "hfd-1",
        new CapabilityCode("M2"),
        "CT-2026-001",
        aiConclusion,
        0.95,
        finalConclusion,
        reviewer,
        "复核理由",
        duration,
        true,
        aiConclusion != null && !aiConclusion.equals(finalConclusion),
        Instant.now());
  }

  @Test
  void 终决权恒在人工侧() {
    assertThat(decision("LOW", "HIGH", "reviewer-1", 5000).humanHasFinalAuthority()).isTrue();
  }

  @Test
  void AI结论与终决不一致识别为override并触发评测回流() {
    HumanFinalDecision d = decision("LOW", "HIGH", "reviewer-1", 5000);
    assertThat(d.isOverride()).isTrue();
    assertThat(d.evaluationFeedbackRaised()).isTrue();
  }

  @Test
  void 一致时不算override() {
    HumanFinalDecision d = decision("HIGH", "HIGH", "reviewer-1", 5000);
    assertThat(d.isOverride()).isFalse();
    assertThat(d.evaluationFeedbackRaised()).isFalse();
  }

  @Test
  void 匿名复核被拒绝() {
    assertThatThrownBy(() -> decision("LOW", "HIGH", "  ", 5000))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("具名复核人");
  }

  @Test
  void 空终决结论被拒绝() {
    assertThatThrownBy(() -> decision("LOW", "", "reviewer-1", 5000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 负复核耗时被拒绝() {
    assertThatThrownBy(() -> decision("LOW", "HIGH", "reviewer-1", -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void AI无结论时不算override但仍有终决() {
    HumanFinalDecision d = decision(null, "HIGH", "reviewer-1", 5000);
    assertThat(d.isOverride()).isFalse();
    assertThat(d.humanHasFinalAuthority()).isTrue();
  }
}
