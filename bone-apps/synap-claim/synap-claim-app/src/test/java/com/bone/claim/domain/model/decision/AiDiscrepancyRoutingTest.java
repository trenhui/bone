package com.bone.claim.domain.model.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AI 认知路由：只做安全方向的单向收紧，只认白名单码，且同一组差异恒得同一处置。 */
class AiDiscrepancyRoutingTest {

  private static final DecisionTriage.Outcome AUTO =
      new DecisionTriage.Outcome(
          DecisionResult.AUTO_APPROVE, DecisionTier.AUTO, "ALL_GATES_PASSED");

  @Test
  @DisplayName("金额差异：自动通过降为复核档，原因码带差异码可回放")
  void amountMismatchMovesAutoApproveToReview() {
    DecisionTriage.Outcome outcome = AiDiscrepancyRouting.apply(List.of("AMOUNT_MISMATCH"), AUTO);

    assertThat(outcome.result()).isEqualTo(DecisionResult.MANUAL_REQUIRED);
    assertThat(outcome.tier()).isEqualTo(DecisionTier.REVIEW);
    assertThat(outcome.reason()).isEqualTo("AI_DISC:AMOUNT_MISMATCH");
  }

  @Test
  @DisplayName("缺件差异：自动通过改为补件流转")
  void evidenceMissingRequestsMoreEvidence() {
    DecisionTriage.Outcome outcome = AiDiscrepancyRouting.apply(List.of("EVIDENCE_MISSING"), AUTO);

    assertThat(outcome.result()).isEqualTo(DecisionResult.NEED_MORE_EVIDENCE);
    assertThat(outcome.tier()).isEqualTo(DecisionTier.MANUAL);
    assertThat(outcome.reason()).isEqualTo("AI_DISC:EVIDENCE_MISSING");
  }

  @Test
  @DisplayName("白名单外自由文本与 null 一律忽略，模型不得用自由文本改流程")
  void unknownCodesAreIgnored() {
    assertThat(AiDiscrepancyRouting.apply(List.of("模型自己编的理由"), AUTO)).isEqualTo(AUTO);
    assertThat(AiDiscrepancyRouting.apply(java.util.Arrays.asList((String) null), AUTO))
        .isEqualTo(AUTO);
    assertThat(AiDiscrepancyRouting.apply(null, AUTO)).isEqualTo(AUTO);
    assertThat(AiDiscrepancyRouting.apply("AMOUNT_MISMATCH", AUTO)).isEqualTo(AUTO);
  }

  @Test
  @DisplayName("非自动通过结论保持原样：不覆盖风险命中/大额复核等更精确原因")
  void nonAutoOutcomeIsPreserved() {
    DecisionTriage.Outcome riskHit =
        new DecisionTriage.Outcome(DecisionResult.MANUAL_REQUIRED, DecisionTier.MANUAL, "RISK_HIT");

    DecisionTriage.Outcome outcome =
        AiDiscrepancyRouting.apply(List.of("AMOUNT_MISMATCH"), riskHit);

    assertThat(outcome).isEqualTo(riskHit);
  }

  @Test
  @DisplayName("同一组差异无论模型怎么排列，结论与原因码完全一致（审计可回放）")
  void outcomeIsIndependentOfModelOrdering() {
    List<String> codes =
        List.of("DRUG_INDICATION_MISMATCH", "AMOUNT_MISMATCH", "EVIDENCE_MISSING", "OTHER");

    Set<String> outcomes = new LinkedHashSet<>();
    for (List<String> permutation : permutations(codes)) {
      DecisionTriage.Outcome outcome = AiDiscrepancyRouting.apply(permutation, AUTO);
      outcomes.add(outcome.result() + "/" + outcome.tier() + "/" + outcome.reason());
    }

    // 24 种排列只能产出一种结论——模型给数组排序没有语义，流程状态不能跟着它跳。
    assertThat(outcomes)
        .containsExactly(
            DecisionResult.NEED_MORE_EVIDENCE
                + "/"
                + DecisionTier.MANUAL
                + "/AI_DISC:EVIDENCE_MISSING");
  }

  @Test
  @DisplayName("缺件与其它差异并存时恒先补件：没有材料无法复核")
  void evidenceMissingOutranksOtherFindings() {
    assertThat(AiDiscrepancyRouting.apply(List.of("AMOUNT_MISMATCH", "EVIDENCE_MISSING"), AUTO))
        .isEqualTo(
            AiDiscrepancyRouting.apply(List.of("EVIDENCE_MISSING", "AMOUNT_MISMATCH"), AUTO));
    assertThat(
            AiDiscrepancyRouting.apply(List.of("AMOUNT_MISMATCH", "EVIDENCE_MISSING"), AUTO)
                .result())
        .isEqualTo(DecisionResult.NEED_MORE_EVIDENCE);
  }

  @Test
  @DisplayName("多条复核类差异按声明顺序取主码，原因码稳定可聚合")
  void highestPrecedenceCodeWins() {
    DecisionTriage.Outcome outcome =
        AiDiscrepancyRouting.apply(
            List.of("OTHER", "DRUG_INDICATION_MISMATCH", "AMOUNT_MISMATCH"), AUTO);

    assertThat(outcome.reason()).isEqualTo("AI_DISC:AMOUNT_MISMATCH");
    assertThat(outcome.result()).isEqualTo(DecisionResult.MANUAL_REQUIRED);
    assertThat(outcome.tier()).isEqualTo(DecisionTier.REVIEW);
  }

  @Test
  @DisplayName("原因码长度必须装得进 triage_reason VARCHAR(32)：超宽会让决策执行整条写不进去")
  void reasonCodesFitTriageReasonColumn() {
    for (String code : AiDiscrepancyCodes.ALL) {
      String reason = AiDiscrepancyRouting.reason(code);
      // MySQL 严格模式下超长是 ERROR 1406 + 回滚，不是静默截断；这条断言把「写不进去」提前到构建期。
      assertThat(reason.length())
          .as(
              "原因码 %s 必须 <= %d 字符（当前 %d）",
              reason, AiDiscrepancyCodes.TRIAGE_REASON_MAX_LENGTH, reason.length())
          .isLessThanOrEqualTo(AiDiscrepancyCodes.TRIAGE_REASON_MAX_LENGTH);
    }
  }

  @Test
  @DisplayName("全部差异码都在单一真源里，网关白名单与路由不会漂移；顺序即处置优先级")
  void codesCoverGatewayAndRouting() {
    assertThat(AiDiscrepancyCodes.ALL)
        .containsExactly(
            "EVIDENCE_MISSING",
            "AMOUNT_MISMATCH",
            "DIAGNOSIS_FEE_MISMATCH",
            "DRUG_INDICATION_MISMATCH",
            "DUPLICATE_ITEM",
            "OTHER");
  }

  /** 全排列（测试用，规模固定为 4 个码 = 24 项）。 */
  private static List<List<String>> permutations(List<String> source) {
    List<List<String>> result = new ArrayList<>();
    permute(new ArrayList<>(source), 0, result);
    return result;
  }

  private static void permute(List<String> current, int index, List<List<String>> acc) {
    if (index == current.size()) {
      acc.add(List.copyOf(current));
      return;
    }
    for (int i = index; i < current.size(); i++) {
      java.util.Collections.swap(current, index, i);
      permute(current, index + 1, acc);
      java.util.Collections.swap(current, index, i);
    }
  }
}
