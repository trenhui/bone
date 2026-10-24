package com.bone.airag.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link HeuristicAnswerQualityJudge} 单测（2026-10-24 缺口③）：锁死确定性判官的三项指标口径与 fail-closed 姿态。
 *
 * <p>重点防线：① 无据/无产出必须 0 分（fail-closed，不得把「没有上下文」算成「忠实」）； ② faithfulness 与 answerRelevance
 * 必须分离（据作答但答非所问 = 高忠实低相关，灰度放行判据依赖此分离）； ③ 未支撑断言必须可枚举（低分归因输入）。
 */
class HeuristicAnswerQualityJudgeTest {

  private final HeuristicAnswerQualityJudge judge = new HeuristicAnswerQualityJudge();

  private static final String C_PENALTY = "逾期付款应支付违约金";
  private static final String C_CONFIDENTIALITY = "保密期限为五年";
  private static final String C_TERMINATION = "合同解除后恢复原状";

  @Test
  @DisplayName("完全据作答：faithfulness/relevance/precision 全 1，无未支撑断言")
  void fully_grounded_answer_scores_full_marks() {
    AnswerQuality q = judge.judge("逾期付款违约金", List.of(C_PENALTY, C_CONFIDENTIALITY), C_PENALTY);

    assertThat(q.faithfulness()).isEqualTo(1.0);
    assertThat(q.answerRelevance()).isGreaterThan(0.8);
    assertThat(q.contextPrecision()).isEqualTo(0.5); // 仅第 1 条上下文被使用
    assertThat(q.unsupportedClaims()).isEmpty();
  }

  @Test
  @DisplayName("话题漂移幻觉：faithfulness 0，未支撑断言可枚举")
  void off_topic_answer_scores_zero_faithfulness_and_lists_unsupported() {
    AnswerQuality q =
        judge.judge("逾期付款违约金", List.of(C_PENALTY, C_CONFIDENTIALITY), "本合同适用联合国海洋法公约，争议由海牙法院管辖。");

    assertThat(q.faithfulness()).isZero();
    assertThat(q.unsupportedClaims()).containsExactly("本合同适用联合国海洋法公约", "争议由海牙法院管辖");
  }

  @Test
  @DisplayName("混合答案（半据半编）：faithfulness 0.5——部分忠实是灰度放行的拦截信号")
  void mixed_answer_scores_partial_faithfulness() {
    AnswerQuality q =
        judge.judge(
            "逾期付款违约金", List.of(C_PENALTY, C_CONFIDENTIALITY), C_PENALTY + "。本合同适用联合国海洋法公约。");

    assertThat(q.faithfulness()).isEqualTo(0.5);
    assertThat(q.unsupportedClaims()).containsExactly("本合同适用联合国海洋法公约");
  }

  @Test
  @DisplayName("fail-closed：空上下文 = 无据 = 0 分（拒绝服务不得计为忠实）")
  void empty_contexts_fail_closed() {
    AnswerQuality q = judge.judge("任意查询", List.of(), C_PENALTY);

    assertThat(q.faithfulness()).isZero();
    assertThat(q.answerRelevance()).isZero();
    assertThat(q.contextPrecision()).isZero();
    assertThat(q.unsupportedClaims()).isEmpty();
  }

  @Test
  @DisplayName("fail-closed：null 上下文同样 0 分（防御性，调用方不得依赖 NPE 之外的行为）")
  void null_contexts_fail_closed() {
    AnswerQuality q = judge.judge("任意查询", null, C_PENALTY);

    assertThat(q.faithfulness()).isZero();
  }

  @Test
  @DisplayName("fail-closed：空答案 = 无产出 = 0 分")
  void blank_answer_fail_closed() {
    AnswerQuality q = judge.judge("逾期付款违约金", List.of(C_PENALTY), "   ");

    assertThat(q.faithfulness()).isZero();
  }

  @Test
  @DisplayName("纯标点断言不计入分母（「。。」不得稀释忠实度）")
  void punctuation_only_claims_do_not_dilute_faithfulness() {
    AnswerQuality q = judge.judge("逾期付款违约金", List.of(C_PENALTY), C_PENALTY + "。！");

    assertThat(q.faithfulness()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("忠实与相关分离：据作答但答非所问 = faithfulness 1.0 / relevance 0.0")
  void grounded_but_irrelevant_answer_separates_faithfulness_from_relevance() {
    AnswerQuality q = judge.judge("保密期限", List.of(C_PENALTY, C_CONFIDENTIALITY), C_PENALTY);

    assertThat(q.faithfulness()).isEqualTo(1.0);
    assertThat(q.answerRelevance()).isZero();
  }

  @Test
  @DisplayName("contextPrecision 只计真正支撑了断言的上下文（检索噪音检测）")
  void context_precision_counts_only_used_contexts() {
    AnswerQuality q =
        judge.judge("逾期付款违约金", List.of(C_PENALTY, C_CONFIDENTIALITY, C_TERMINATION), C_PENALTY);

    assertThat(q.contextPrecision()).isEqualTo(1.0 / 3.0);
  }

  @Test
  @DisplayName("支撑阈值必须落在 (0,1]：0 放行幻觉、>1 误杀同义表述")
  void support_threshold_must_be_in_unit_interval() {
    assertThatThrownBy(() -> new HeuristicAnswerQualityJudge(0.0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new HeuristicAnswerQualityJudge(1.5))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("短文本退化为单元组（单字答案不丢信号）")
  void short_single_char_text_degrades_to_unigram() {
    assertThat(HeuristicAnswerQualityJudge.bigrams("合")).containsExactly("合");
  }

  @Test
  @DisplayName("归一化剔除标点空白：二元组跨标点拼接，大小写不敏感")
  void normalization_strips_punctuation_and_is_case_insensitive() {
    assertThat(HeuristicAnswerQualityJudge.bigrams("abc，123"))
        .containsExactlyInAnyOrder("ab", "bc", "c1", "12", "23");
    assertThat(HeuristicAnswerQualityJudge.bigrams("ABC")).containsExactlyInAnyOrder("ab", "bc");
    assertThat(HeuristicAnswerQualityJudge.splitClaims("逾期付款。保密期限！\n不可抗力；争议提交仲裁"))
        .containsExactly("逾期付款", "保密期限", "不可抗力", "争议提交仲裁");
  }
}
