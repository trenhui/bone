package com.bone.claim.domain.model.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 决策分诊策略回归黄金集（Golden Set）—— 业界实践：LLM/规则决策系统的"策略回归集"。
 *
 * <p>分诊阈值是理赔风控的硬契约（OCR/AI 双阈值、反欺诈硬闸、≥1 万双人复核、拒赔建议转人工）。 任何阈值或路由回归都会直接放错赔付或漏转人工。本集以表驱动方式锁定文档化结论， 改动
 * {@link DecisionTriage} 阈值时若预期结论变化，必须同步更新本文档化用例（而非悄悄放宽）。
 */
class DecisionTriageGoldenSetTest {

  /** 一条"全绿"基准场景：证据齐备、解析成功、金额小额、高置信、无风险、风控通过、不建议拒赔。 */
  private static DecisionTriage.Input green() {
    return new DecisionTriage.Input(
        true,
        true,
        false,
        new BigDecimal("5000.00"),
        new BigDecimal("0.90"),
        new BigDecimal("0.95"),
        false,
        true,
        false);
  }

  @Test
  void baselineGreenScenario_AutoApprove() {
    DecisionTriage.Outcome o = DecisionTriage.evaluate(green());
    assertThat(o.result()).isEqualTo(DecisionResult.AUTO_APPROVE);
    assertThat(o.tier()).isEqualTo(DecisionTier.AUTO);
    assertThat(o.reason()).isEqualTo("ALL_GATES_PASSED");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scenarios")
  void goldenSet(
      String name, DecisionTriage.Input input, DecisionResult result, DecisionTier tier) {
    DecisionTriage.Outcome o = DecisionTriage.evaluate(input);
    assertThat(o.result()).describedAs(name + " -> result").isEqualTo(result);
    assertThat(o.tier()).describedAs(name + " -> tier").isEqualTo(tier);
  }

  static Stream<Arguments> scenarios() {
    return Stream.of(
        Arguments.of(
            "证据缺失→补件(MANUAL)",
            with(b -> b.hasEvidence = false),
            DecisionResult.NEED_MORE_EVIDENCE,
            DecisionTier.MANUAL),
        Arguments.of(
            "证据解析失败→人工",
            with(b -> b.evidenceParsed = false),
            DecisionResult.MANUAL_REQUIRED,
            DecisionTier.MANUAL),
        Arguments.of(
            "规则冲突无法裁决→人工",
            with(b -> b.ruleConflict = true),
            DecisionResult.MANUAL_REQUIRED,
            DecisionTier.MANUAL),
        Arguments.of(
            "金额未知→人工",
            with(b -> b.amount = null),
            DecisionResult.MANUAL_REQUIRED,
            DecisionTier.MANUAL),
        Arguments.of(
            "金额≥1万→双人复核(REVIEW, 非自动通过)",
            with(b -> b.amount = new BigDecimal("10000.00")),
            DecisionResult.MANUAL_REQUIRED,
            DecisionTier.REVIEW),
        Arguments.of(
            "命中风险→人工",
            with(b -> b.riskHit = true),
            DecisionResult.MANUAL_REQUIRED,
            DecisionTier.MANUAL),
        Arguments.of(
            "反欺诈硬闸未过→人工",
            with(b -> b.fraudGatePassed = false),
            DecisionResult.MANUAL_REQUIRED,
            DecisionTier.MANUAL),
        Arguments.of(
            "OCR置信<0.7→人工(独立熔断)",
            with(b -> b.ocrConfidence = new BigDecimal("0.60")),
            DecisionResult.MANUAL_REQUIRED,
            DecisionTier.MANUAL),
        Arguments.of(
            "AI置信未知→人工",
            with(b -> b.confidence = null),
            DecisionResult.MANUAL_REQUIRED,
            DecisionTier.MANUAL),
        Arguments.of(
            "AI置信<0.7→人工",
            with(b -> b.confidence = new BigDecimal("0.60")),
            DecisionResult.MANUAL_REQUIRED,
            DecisionTier.MANUAL),
        Arguments.of(
            "AI置信0.7–0.85→复核档(REVIEW)",
            with(b -> b.confidence = new BigDecimal("0.80")),
            DecisionResult.MANUAL_REQUIRED,
            DecisionTier.REVIEW),
        Arguments.of(
            "AI建议拒赔→转人工(SUGGEST_REJECT)",
            with(b -> b.rejectSuggested = true),
            DecisionResult.SUGGEST_REJECT,
            DecisionTier.MANUAL));
  }

  /** 基于全绿基准做字段覆写，得到单变量场景。 */
  private static DecisionTriage.Input with(java.util.function.Consumer<Builder> mutate) {
    Builder b = new Builder(green());
    mutate.accept(b);
    return b.build();
  }

  private static final class Builder {
    boolean hasEvidence;
    boolean evidenceParsed;
    boolean ruleConflict;
    BigDecimal amount;
    BigDecimal confidence;
    BigDecimal ocrConfidence;
    boolean riskHit;
    boolean fraudGatePassed;
    boolean rejectSuggested;

    Builder(DecisionTriage.Input base) {
      this.hasEvidence = base.hasEvidence();
      this.evidenceParsed = base.evidenceParsed();
      this.ruleConflict = base.ruleConflict();
      this.amount = base.amount();
      this.confidence = base.confidence();
      this.ocrConfidence = base.ocrConfidence();
      this.riskHit = base.riskHit();
      this.fraudGatePassed = base.fraudGatePassed();
      this.rejectSuggested = base.rejectSuggested();
    }

    DecisionTriage.Input build() {
      return new DecisionTriage.Input(
          hasEvidence,
          evidenceParsed,
          ruleConflict,
          amount,
          confidence,
          ocrConfidence,
          riskHit,
          fraudGatePassed,
          rejectSuggested);
    }
  }
}
