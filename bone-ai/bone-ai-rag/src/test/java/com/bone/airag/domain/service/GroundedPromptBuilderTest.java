package com.bone.airag.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.airag.domain.model.GroundedPrompt;
import com.bone.airag.domain.model.RagResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 接地提示词装配回归：数据围栏、编号引用、token 预算截断。 */
class GroundedPromptBuilderTest {

  private static final String CLAUSE_A =
      "甲方逾期付款的，每逾期一日按未付金额千分之一支付违约金";
  private static final String CLAUSE_B = "保密期限自合同生效之日起五年";

  private static RagResult result(String text, String clauseRef, String path) {
    return new RagResult("chunk-" + clauseRef, clauseRef, 0, 10, 0.9, path, text);
  }

  @Test
  void wraps_context_in_data_fence_and_numbers_citations() {
    GroundedPrompt prompt =
        new GroundedPromptBuilder(0)
            .build("逾期怎么算违约金", List.of(result(CLAUSE_A, "clause-1", "S1")));

    // 围栏把「数据」与「指令」结构分离（间接注入第一道防线）
    assertThat(prompt.user()).contains(GroundedPromptBuilder.DATA_FENCE_OPEN);
    assertThat(prompt.user()).contains(GroundedPromptBuilder.DATA_FENCE_CLOSE);
    // 编号 + 出处锚点：守卫要有号可校
    assertThat(prompt.user()).contains("[1] 出处: clause-1 #0-10");
    assertThat(prompt.user()).contains("章节: S1");
    assertThat(prompt.user()).contains(CLAUSE_A);
    // 系统提示须含强制引用与数字一致两条硬约束
    assertThat(prompt.system()).contains("出处编号").contains("金额、比例、期限");
    assertThat(prompt.included()).hasSize(1);
    assertThat(prompt.truncated()).isFalse();
  }

  @Test
  void renumbers_by_include_order_not_by_original_index() {
    GroundedPrompt prompt =
        new GroundedPromptBuilder(0)
            .build(
                "问题",
                List.of(result(CLAUSE_A, "clause-1", "S1"), result(CLAUSE_B, "clause-2", "S2")));

    int first = prompt.user().indexOf("[1] 出处: clause-1");
    int second = prompt.user().indexOf("[2] 出处: clause-2");
    assertThat(first).isGreaterThan(-1);
    assertThat(second).isGreaterThan(first);
    assertThat(prompt.included()).hasSize(2);
  }

  @Test
  void truncates_by_budget_and_keeps_at_least_first() {
    int costA = GroundedPromptBuilder.estimateTokens(CLAUSE_A);
    // 预算只够第一条（第一条永远保留，否则等于白检索）
    GroundedPrompt prompt =
        new GroundedPromptBuilder(costA)
            .build(
                "问题",
                List.of(result(CLAUSE_A, "clause-1", "S1"), result(CLAUSE_B, "clause-2", "S2")));

    assertThat(prompt.included()).hasSize(1);
    assertThat(prompt.included().get(0).clauseRef()).isEqualTo("clause-1");
    assertThat(prompt.truncated()).isTrue();
    assertThat(prompt.user()).doesNotContain("clause-2");
  }

  @Test
  void zero_budget_means_unlimited() {
    String longText = CLAUSE_A.repeat(50);
    GroundedPrompt prompt =
        new GroundedPromptBuilder(0)
            .build(
                "问题",
                List.of(result(longText, "c-1", "S1"), result(CLAUSE_B, "c-2", "S2")));

    assertThat(prompt.included()).hasSize(2);
    assertThat(prompt.truncated()).isFalse();
  }

  @Test
  void estimate_tokens_counts_cjk_per_char_and_latin_runs_per_word() {
    assertThat(GroundedPromptBuilder.estimateTokens("")).isZero();
    // 5 个汉字 = 5；"bge" "m3" 两个连续字母数字串 = 2；空格与短横线不计
    assertThat(GroundedPromptBuilder.estimateTokens("逾期违约金 bge-m3")).isEqualTo(7);
  }
}
