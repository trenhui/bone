package com.bone.airag.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.airag.domain.model.GroundingVerdict;
import com.bone.airag.domain.model.RagResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 生成侧接地守卫回归（「无出处不得生成」红线）。
 *
 * <p>覆盖四类真实失败形态：幻觉引用、完全无引用、编造陈述、编造数字；以及 fail-closed 边界
 * （空上下文 / 空答案 / 仅有引用标记的答案）。
 */
class GroundingGuardTest {

  private static final String CLAUSE =
      "甲方逾期付款的，每逾期一日按未付金额千分之一支付违约金";

  private final GroundingGuard guard = new GroundingGuard(0.6, 0.0);

  private static RagResult ctx(String text) {
    return new RagResult("chunk-1", "clause-1", 0, text.length(), 0.9, "S1", text);
  }

  @Test
  void pass_when_answer_traced_to_context() {
    GroundingVerdict v = guard.verify(CLAUSE + "[1]。", List.of(ctx(CLAUSE + "。")));

    assertThat(v.grounded()).isTrue();
    assertThat(v.reason()).isEqualTo(GroundingGuard.REASON_PASS);
    assertThat(v.sentenceCount()).isEqualTo(1);
    assertThat(v.unsupportedCount()).isZero();
    assertThat(v.meanSupport()).isGreaterThan(0.9);
  }

  @Test
  void reject_phantom_citation() {
    GroundingVerdict v = guard.verify(CLAUSE + "[9]。", List.of(ctx(CLAUSE + "。")));

    assertThat(v.grounded()).isFalse();
    assertThat(v.reason()).isEqualTo(GroundingGuard.REASON_INVALID_CITATION);
    assertThat(v.invalidCitations()).containsExactly("[9]");
  }

  @Test
  void reject_answer_without_any_citation() {
    GroundingVerdict v = guard.verify(CLAUSE + "。", List.of(ctx(CLAUSE + "。")));

    assertThat(v.grounded()).isFalse();
    assertThat(v.reason()).isEqualTo(GroundingGuard.REASON_NO_CITATION);
  }

  @Test
  void reject_fabricated_claim() {
    String answer = "乙方应在十日内完成交付并承担全部运输费用[1]。";

    GroundingVerdict v = guard.verify(answer, List.of(ctx(CLAUSE + "。")));

    assertThat(v.grounded()).isFalse();
    assertThat(v.reason()).isEqualTo(GroundingGuard.REASON_UNSUPPORTED_CLAIM);
    assertThat(v.unsupportedCount()).isEqualTo(1);
    assertThat(v.unsupportedSentences()).hasSize(1);
  }

  @Test
  void reject_fabricated_number_even_when_wording_matches() {
    String answer = "甲方逾期付款的，每逾期一日按未付金额3%支付违约金[1]。";

    GroundingVerdict v = guard.verify(answer, List.of(ctx(CLAUSE + "。")));

    // 词面高度相似（支撑比高于阈值），但上下文里没有 "3" —— 编造金额/比例必须拦下
    assertThat(v.meanSupport()).isGreaterThan(0.6);
    assertThat(v.grounded()).isFalse();
    assertThat(v.reason()).isEqualTo(GroundingGuard.REASON_UNSUPPORTED_CLAIM);
  }

  @Test
  void fail_closed_when_no_context() {
    GroundingVerdict v = guard.verify(CLAUSE + "[1]。", List.of());

    assertThat(v.grounded()).isFalse();
    assertThat(v.reason()).isEqualTo(GroundingGuard.REASON_NO_CONTEXT);
  }

  @Test
  void fail_closed_when_answer_blank() {
    GroundingVerdict v = guard.verify("   ", List.of(ctx(CLAUSE)));

    assertThat(v.grounded()).isFalse();
    assertThat(v.reason()).isEqualTo(GroundingGuard.REASON_NO_CONTEXT);
  }

  @Test
  void fail_closed_when_answer_has_only_citation_marker() {
    GroundingVerdict v = guard.verify("[1]", List.of(ctx(CLAUSE)));

    assertThat(v.grounded()).isFalse();
    assertThat(v.reason()).isEqualTo(GroundingGuard.REASON_NO_CITATION);
  }

  @Test
  void pass_english_answer_traced_to_context() {
    String context = "The supplier must deliver the goods within thirty days.";
    String answer = "The supplier must deliver the goods within thirty days [1].";

    GroundingVerdict v = guard.verify(answer, List.of(ctx(context)));

    assertThat(v.grounded()).isTrue();
    assertThat(v.reason()).isEqualTo(GroundingGuard.REASON_PASS);
  }

  @Test
  void tolerance_allows_configured_share_of_unsupported_sentences() {
    GroundingGuard lenient = new GroundingGuard(0.6, 0.5);
    String fabricated = "乙方应在十日内完成交付并承担全部运输费用";
    String answer = CLAUSE + "[1]。" + fabricated + "[1]。";

    GroundingVerdict v = lenient.verify(answer, List.of(ctx(CLAUSE + "。")));

    // 2 句中 1 句无支撑，占比 0.5 ≤ 0.5 容差 —— 放行但仍如实报告无支撑句数
    assertThat(v.grounded()).isTrue();
    assertThat(v.sentenceCount()).isEqualTo(2);
    assertThat(v.unsupportedCount()).isEqualTo(1);
  }
}
