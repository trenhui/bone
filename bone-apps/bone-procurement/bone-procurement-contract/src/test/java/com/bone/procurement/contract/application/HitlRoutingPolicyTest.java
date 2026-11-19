package com.bone.procurement.contract.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.contract.domain.model.AiParticipation;
import com.bone.procurement.contract.domain.model.ContractReview;
import com.bone.procurement.contract.domain.model.HumanOverride;
import com.bone.procurement.contract.domain.model.ReviewSuggestion;
import com.bone.procurement.contract.domain.model.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

/** HITL 分层路由是确定性规则，不由 LLM 决定监督强度；人工终决必须留痕。 */
class HitlRoutingPolicyTest {

  private final HitlRoutingPolicy policy = new HitlRoutingPolicy();

  @Test
  void 低置信转人工() {
    HitlRoutingDecision decision = policy.route(0.5, RiskLevel.LOW, false);
    assertThat(decision.humanReviewRequired()).isTrue();
    assertThat(decision.triggers()).containsExactly(HitlTrigger.LOW_CONFIDENCE);
  }

  @Test
  void 高后果转人工() {
    HitlRoutingDecision high = policy.route(0.99, RiskLevel.HIGH, false);
    assertThat(high.humanReviewRequired()).isTrue();
    assertThat(high.triggers()).containsExactly(HitlTrigger.HIGH_CONSEQUENCE);

    HitlRoutingDecision unknown = policy.route(0.99, RiskLevel.UNKNOWN, false);
    assertThat(unknown.humanReviewRequired()).isTrue();
    assertThat(unknown.triggers()).containsExactly(HitlTrigger.HIGH_CONSEQUENCE);
  }

  @Test
  void 命中负向转人工() {
    HitlRoutingDecision decision = policy.route(0.99, RiskLevel.LOW, true);
    assertThat(decision.humanReviewRequired()).isTrue();
    assertThat(decision.triggers()).containsExactly(HitlTrigger.NEGATIVE_HIT);
  }

  @Test
  void 正常结果无需强制人工() {
    HitlRoutingDecision decision = policy.route(0.95, RiskLevel.LOW, false);
    assertThat(decision.humanReviewRequired()).isFalse();
    assertThat(decision.triggers()).isEmpty();
  }

  @Test
  void 高置信错误被人工推翻留痕() {
    ContractReview review = ContractReview.pending("rv-override", "CT-OVERRIDE");
    review.markParsed();
    review.startReview();
    review.applyAiSuggestion(
        new ReviewSuggestion(
            AiParticipation.AVAILABLE, RiskLevel.LOW, List.of(), 0.99, false, "model-v1"));

    assertThat(policy.route(review.aiConfidence(), review.riskLevel(), false).humanReviewRequired())
        .as("高置信低风险不触发强制人工，但结论仍是建议")
        .isFalse();

    review.routeToHuman("人工法审发现条款实为高风险");
    review.recordHumanOverride(
        new HumanOverride("reviewer-1", "人工推翻 AI 无风险判定", 0.99, RiskLevel.LOW, RiskLevel.HIGH));

    assertThat(review.overrides()).hasSize(1);
    assertThat(review.riskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(review.isHumanFinalized()).isTrue();
    assertThat(review.overrides().get(0).aiRiskLevel()).isEqualTo(RiskLevel.LOW);
    assertThat(review.overrides().get(0).finalRiskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(review.overrides().get(0).aiConfidence()).isEqualTo(0.99);
  }
}
