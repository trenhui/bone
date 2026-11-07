package com.bone.aidoc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.aidoc.domain.gateway.ReviewPolicy;
import com.bone.aidoc.domain.model.RiskLevel;
import org.junit.jupiter.api.Test;

/** 配置口径守护：旧扁平阈值不得静默失效，分级阈值非法值须 fail-closed。 */
class TrustedOcrAutoConfigurationTest {

  private final TrustedOcrAutoConfiguration configuration = new TrustedOcrAutoConfiguration();

  @Test
  void default_config_builds_policy_without_legacy_values() {
    DocumentProperties props = new DocumentProperties();
    assertThat(props.getTrustedOcr().hasLegacyFlatThresholds()).isFalse();
    assertThatCode(() -> configuration.trustedReviewPolicy(props)).doesNotThrowAnyException();
  }

  @Test
  void explicitly_configured_legacy_flat_threshold_fails_fast() {
    DocumentProperties props = new DocumentProperties();
    props.getTrustedOcr().setAutoPassThreshold(0.50);
    assertThat(props.getTrustedOcr().hasLegacyFlatThresholds()).isTrue();
    assertThatThrownBy(() -> configuration.trustedReviewPolicy(props))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("risk-thresholds");
  }

  @Test
  void reordered_or_out_of_range_risk_thresholds_are_rejected() {
    DocumentProperties badOrder = new DocumentProperties();
    DocumentProperties.RiskThresholdConfig cfg = new DocumentProperties.RiskThresholdConfig();
    cfg.setAutoPassConfidence(0.60);
    cfg.setManualConfidence(0.95); // 人工复核门槛高于自动通过门槛 → 策略语义反转
    badOrder.getTrustedOcr().getRiskThresholds().put(RiskLevel.HIGH, cfg);
    assertThatThrownBy(() -> configuration.trustedReviewPolicy(badOrder))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HIGH");

    DocumentProperties outOfRange = new DocumentProperties();
    DocumentProperties.RiskThresholdConfig cfg2 = new DocumentProperties.RiskThresholdConfig();
    cfg2.setAutoPassConfidence(1.5);
    outOfRange.getTrustedOcr().getRiskThresholds().put(RiskLevel.LOW, cfg2);
    assertThatThrownBy(() -> configuration.trustedReviewPolicy(outOfRange))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void valid_risk_threshold_is_accepted() {
    DocumentProperties props = new DocumentProperties();
    DocumentProperties.RiskThresholdConfig cfg = new DocumentProperties.RiskThresholdConfig();
    cfg.setAutoPassConfidence(0.995);
    cfg.setAutoPassQuality(0.97);
    cfg.setManualConfidence(0.90);
    cfg.setManualQuality(0.85);
    props.getTrustedOcr().getRiskThresholds().put(RiskLevel.CRITICAL, cfg);

    ReviewPolicy policy = configuration.trustedReviewPolicy(props);
    assertThat(policy).isNotNull();
  }
}
