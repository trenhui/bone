package com.bone.procurement.advisory.domain.model.advisory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 响应红线：advisoryOnly 恒真、双标识缺失不可出域、UNKNOWN 不压成「AI 未参与」。 */
class AdvisoryResponseTest {

  private AdvisoryResponse response(String explicit, String implicit, AiAvailability ai) {
    return new AdvisoryResponse(
        "M1",
        "招标体检",
        ai,
        true,
        ai.aiActuallyParticipated() ? null : "rules_only",
        explicit,
        implicit,
        "ref",
        "R",
        AdvisoryResponse.DISCLOSURE,
        Instant.now());
  }

  @Test
  @DisplayName("双标识缺一不可出域")
  void dualLabelsRequired() {
    assertThat(response("explicit", "implicit", AiAvailability.AVAILABLE).canRelease()).isTrue();
    assertThat(response("explicit", null, AiAvailability.AVAILABLE).canRelease()).isFalse();
    assertThat(response(null, "implicit", AiAvailability.AVAILABLE).hasDualLabels()).isFalse();
  }

  @Test
  @DisplayName("UNKNOWN/DEGRADED 不得压成 AI 未参与或 AVAILABLE")
  void unknownNotFlattened() {
    assertThat(AiAvailability.UNKNOWN.aiActuallyParticipated()).isFalse();
    assertThat(AiAvailability.DEGRADED.aiActuallyParticipated()).isFalse();
    assertThat(AiAvailability.AVAILABLE.aiActuallyParticipated()).isTrue();
    AdvisoryResponse denied = response("e", "i", AiAvailability.UNKNOWN);
    assertThat(denied.degradedTo()).isEqualTo("rules_only");
    assertThat(denied.aiAvailable()).isEqualTo(AiAvailability.UNKNOWN);
  }
}
