package com.bone.procurement.risk.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 风险预警不变量：UNKNOWN fail-closed 转人工、处置必须具名人工。 */
class RiskAlertTest {

  @Test
  @DisplayName("未知严重度与空值都归 UNKNOWN，必须转人工")
  void unknownSeverityRequiresHuman() {
    RiskAlert a =
        RiskAlert.raise(1L, "SUP-1", "EXTERNAL", "evt-1", "UNKNOWN", "CREDIT", "ev-1", new Date());
    assertThat(a.severity()).isEqualTo(Severity.UNKNOWN);
    assertThat(a.requiresHumanReview()).isTrue();
    RiskAlert b =
        RiskAlert.raise(1L, "SUP-2", "EXTERNAL", "evt-2", "garbage", "CREDIT", "ev-2", new Date());
    assertThat(b.requiresHumanReview()).isTrue();
    RiskAlert c =
        RiskAlert.raise(1L, "SUP-3", "EXTERNAL", "evt-3", null, "CREDIT", "ev-3", new Date());
    assertThat(c.severity()).isEqualTo(Severity.UNKNOWN);
  }

  @Test
  @DisplayName("预警处置必须具名人工，终态不可重复确认")
  void acknowledgeRequiresHuman() {
    RiskAlert a =
        RiskAlert.raise(1L, "SUP-1", "EXTERNAL", "evt-1", "HIGH", "CREDIT", "ev-1", new Date());
    assertThatThrownBy(() -> a.acknowledgeByHuman(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("人工");
    a.acknowledgeByHuman("human-1");
    assertThat(a.getStatus()).isEqualTo("ACKNOWLEDGED");
    assertThatThrownBy(() -> a.acknowledgeByHuman("human-2"))
        .isInstanceOf(IllegalStateException.class);
  }
}
