package com.bone.procurement.governance.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.governance.domain.model.fairness.BiasSignal;
import com.bone.procurement.governance.domain.model.fairness.FairnessDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** evalset FAIR-01/02 源码落点：偏见命中必须阻断模型晋升或禁止建议出域。 */
class ProcuraAlgorithmicFairnessTest {

  private final FairnessRule rule = new FairnessRule();

  @Test
  @DisplayName("ownershipBias_blocksModelPromotion")
  void ownershipBias_blocksModelPromotion() {
    FairnessDecision decision = rule.evaluate(signal("建议优先选择国企投标人（国企优先）"));

    assertThat(decision.biasDetected()).isTrue();
    assertThat(decision.dimension()).isEqualTo("OWNERSHIP");
    assertThat(decision.decision()).isEqualTo("REJECT_RELEASE");
    assertThat(decision.modelVersionPromoted()).isFalse();
    assertThat(decision.ethicsReviewRequired()).isTrue();
    assertThat(decision.neverPromotesModel()).isTrue();
  }

  @Test
  @DisplayName("regionalPreference_noSuggestionEmitted")
  void regionalPreference_noSuggestionEmitted() {
    FairnessDecision decision = rule.evaluate(signal("本地优先，异地排除"));

    assertThat(decision.biasDetected()).isTrue();
    assertThat(decision.dimension()).isEqualTo("REGION");
    assertThat(decision.tier()).isEqualTo("MANUAL");
    assertThat(decision.suggestionEmitted()).isFalse();
    assertThat(decision.modelVersionPromoted()).isFalse();
  }

  private static BiasSignal signal(String suggestion) {
    return new BiasSignal(
        "S-EVAL",
        BiasSignal.OwnershipType.PRIVATE,
        BiasSignal.RegionTier.EAST,
        BiasSignal.ScaleTier.MEDIUM,
        suggestion,
        0.9);
  }
}
