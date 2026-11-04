package com.bone.claim.domain.model.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AI 认知路由：只做安全方向的单向收紧，且只认白名单码。 */
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
    assertThat(outcome.reason()).isEqualTo("AI_DISCREPANCY:AMOUNT_MISMATCH");
  }

  @Test
  @DisplayName("缺件差异：自动通过改为补件流转")
  void evidenceMissingRequestsMoreEvidence() {
    DecisionTriage.Outcome outcome = AiDiscrepancyRouting.apply(List.of("EVIDENCE_MISSING"), AUTO);

    assertThat(outcome.result()).isEqualTo(DecisionResult.NEED_MORE_EVIDENCE);
    assertThat(outcome.tier()).isEqualTo(DecisionTier.MANUAL);
    assertThat(outcome.reason()).isEqualTo("AI_DISCREPANCY:EVIDENCE_MISSING");
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
  @DisplayName("多条差异按白名单顺序取首个有效码，原因码稳定可聚合")
  void firstWhitelistedCodeWins() {
    DecisionTriage.Outcome outcome =
        AiDiscrepancyRouting.apply(
            List.of("自由文本", "DRUG_INDICATION_MISMATCH", "EVIDENCE_MISSING"), AUTO);

    assertThat(outcome.reason()).isEqualTo("AI_DISCREPANCY:DRUG_INDICATION_MISMATCH");
    assertThat(outcome.result()).isEqualTo(DecisionResult.MANUAL_REQUIRED);
  }

  @Test
  @DisplayName("全部差异码都在单一真源里，网关白名单与路由不会漂移")
  void codesCoverGatewayAndRouting() {
    assertThat(AiDiscrepancyCodes.ALL)
        .containsExactly(
            "AMOUNT_MISMATCH",
            "DIAGNOSIS_FEE_MISMATCH",
            "DRUG_INDICATION_MISMATCH",
            "EVIDENCE_MISSING",
            "DUPLICATE_ITEM",
            "OTHER");
  }
}
