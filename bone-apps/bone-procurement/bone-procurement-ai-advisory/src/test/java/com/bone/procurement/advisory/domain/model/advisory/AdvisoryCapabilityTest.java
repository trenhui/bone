package com.bone.procurement.advisory.domain.model.advisory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 能力码登记：8 码齐全，未知码一律 fail-closed（parse 返回 null）。 */
class AdvisoryCapabilityTest {

  @Test
  @DisplayName("D3-v2 §1.1 八码登记齐全")
  void eightCapabilitiesRegistered() {
    assertThat(AdvisoryCapability.values()).hasSize(8);
    assertThat(AdvisoryCapability.parse("M1")).isEqualTo(AdvisoryCapability.M1);
    assertThat(AdvisoryCapability.parse("contract-drafting")).isEqualTo(AdvisoryCapability.M7);
    assertThat(AdvisoryCapability.parse("ORCHESTRATION"))
        .isEqualTo(AdvisoryCapability.ORCHESTRATION);
  }

  @Test
  @DisplayName("未知码与空值 fail-closed")
  void unknownCodeFailClosed() {
    assertThat(AdvisoryCapability.parse("UNKNOWN_CODE")).isNull();
    assertThat(AdvisoryCapability.parse(null)).isNull();
    assertThat(AdvisoryCapability.parse("  ")).isNull();
  }
}
