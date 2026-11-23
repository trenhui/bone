package com.bone.procurement.risk.domain.model.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** evalset RISK-01/02 源码落点：信号不足转 UNKNOWN 人工复核；高风险只标记，不自动放行。 */
class ProcuraSupplierRiskTest {

  @Test
  @DisplayName("insufficientSignal_fallsBackToUnknown")
  void insufficientSignal_fallsBackToUnknown() {
    assertThat(Severity.parse(null)).isEqualTo(Severity.UNKNOWN);
    assertThat(Severity.parse("  ")).isEqualTo(Severity.UNKNOWN);
    assertThat(Severity.parse("not-a-severity").isIndeterminate()).isTrue();

    RiskAlert alert =
        RiskAlert.raise(
            1L,
            "SUP-UNKNOWN",
            "EVALSET",
            "RISK-01",
            "?",
            "QUALITY",
            "e2e://risk/RISK-01",
            new Date());

    assertThat(alert.severity()).isEqualTo(Severity.UNKNOWN);
    assertThat(alert.requiresHumanReview()).isTrue();
    assertThat(alert.getStatus()).isEqualTo("OPEN");
  }

  @Test
  @DisplayName("highRisk_isFlaggedNotAutoReleased")
  void highRisk_isFlaggedNotAutoReleased() {
    RiskAlert alert =
        RiskAlert.raise(
            1L,
            "SUP-HIGH",
            "EVALSET",
            "RISK-02",
            "HIGH",
            "QUALITY",
            "e2e://risk/RISK-02",
            new Date());

    assertThat(alert.severity()).isEqualTo(Severity.HIGH);
    assertThat(alert.getStatus()).isEqualTo("OPEN");
    assertThatThrownBy(() -> alert.acknowledgeByHuman("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("具名人工");
    assertThat(alert.getStatus()).isEqualTo("OPEN");

    alert.acknowledgeByHuman("reviewer-1");
    assertThat(alert.getStatus()).isEqualTo("ACKNOWLEDGED");
  }
}
