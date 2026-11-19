package com.bone.procurement.governance.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.governance.domain.model.fairness.BiasSignal;
import com.bone.procurement.governance.domain.model.fairness.FairnessDecision;
import org.junit.jupiter.api.Test;

/**
 * G15 公平确定性规则单测。
 *
 * <p>方法名与 evalset FAIR-01/02 的 testSymbol 精确对齐：ownershipBias_blocksModelPromotion /
 * regionalPreference_noSuggestionEmitted。
 */
class FairnessRuleTest {

  private final FairnessRule rule = new FairnessRule();

  private static BiasSignal signal(String suggestion) {
    return new BiasSignal(
        "S-1",
        BiasSignal.OwnershipType.PRIVATE,
        BiasSignal.RegionTier.EAST,
        BiasSignal.ScaleTier.MEDIUM,
        suggestion,
        0.9);
  }

  @Test
  void ownershipBias_blocksModelPromotion() {
    // FAIR-01：所有制偏见 → 阻断模型晋升 + 伦理审查
    FairnessDecision d = rule.evaluate(signal("建议优先选择国企投标人（国企优先）"));
    assertThat(d.biasDetected()).isTrue();
    assertThat(d.decision()).isEqualTo("REJECT_RELEASE");
    assertThat(d.modelVersionPromoted()).isFalse();
    assertThat(d.ethicsReviewRequired()).isTrue();
    assertThat(d.suggestionEmitted()).isFalse();
    assertThat(d.neverPromotesModel()).isTrue();
  }

  @Test
  void regionalPreference_noSuggestionEmitted() {
    // FAIR-02：地域偏好 → 转人工、不发建议
    FairnessDecision d = rule.evaluate(signal("本地优先，异地排除"));
    assertThat(d.biasDetected()).isTrue();
    assertThat(d.tier()).isEqualTo("MANUAL");
    assertThat(d.suggestionEmitted()).isFalse();
    assertThat(d.modelVersionPromoted()).isFalse();
  }

  @Test
  void scaleBias转人工不发建议() {
    FairnessDecision d = rule.evaluate(signal("大企业优先，小企业排除"));
    assertThat(d.biasDetected()).isTrue();
    assertThat(d.dimension()).isEqualTo("SCALE");
    assertThat(d.tier()).isEqualTo("MANUAL");
    assertThat(d.suggestionEmitted()).isFalse();
  }

  @Test
  void 无偏见航行放行但仅建议() {
    FairnessDecision d = rule.evaluate(signal("按价格与履约评分综合排序，供人工复核"));
    assertThat(d.biasDetected()).isFalse();
    assertThat(d.tier()).isEqualTo("ADVISORY_ONLY");
    assertThat(d.suggestionEmitted()).isTrue();
    assertThat(d.modelVersionPromoted()).isFalse();
  }

  @Test
  void 特征UNKNOWN时failClosed转人工不压成无偏见() {
    BiasSignal unknown =
        new BiasSignal(
            "S-2",
            BiasSignal.OwnershipType.UNKNOWN,
            BiasSignal.RegionTier.EAST,
            BiasSignal.ScaleTier.MEDIUM,
            "正常建议",
            0.8);
    FairnessDecision d = rule.evaluate(unknown);
    assertThat(d.biasDetected()).isTrue();
    assertThat(d.tier()).isEqualTo("MANUAL");
    assertThat(d.suggestionEmitted()).isFalse();
    assertThat(d.dimension()).isEqualTo("INDETERMINATE");
  }

  @Test
  void 英文偏见词同样命中() {
    FairnessDecision d = rule.evaluate(signal("SOE preferred for shortlist"));
    assertThat(d.biasDetected()).isTrue();
    assertThat(d.dimension()).isEqualTo("OWNERSHIP");
  }
}
