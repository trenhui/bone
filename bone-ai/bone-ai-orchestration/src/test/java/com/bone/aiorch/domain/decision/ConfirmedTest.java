package com.bone.aiorch.domain.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** {@link Confirmed} 不变量：裁决人 / 证据引用 / 时刻三者必填——缺一项即「确认」不可审计。 */
class ConfirmedTest {

  private static final Instant AT = Instant.parse("2026-10-24T10:00:00Z");

  @Test
  void requires_decided_by() {
    assertThatThrownBy(() -> new Confirmed<>("1000", " ", "trace-1", AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("decidedBy");
  }

  @Test
  void requires_evidence_ref() {
    assertThatThrownBy(() -> new Confirmed<>("1000", "reviewer:tom", null, AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("evidenceRef");
  }

  @Test
  void requires_timestamp() {
    assertThatThrownBy(() -> new Confirmed<>("1000", "reviewer:tom", "trace-1", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void now_factory_fills_timestamp() {
    Confirmed<String> c = Confirmed.now("1000", "rule:R01", "trace-1");
    assertThat(c.at()).isNotNull();
    assertThat(c.decidedBy()).isEqualTo("rule:R01");
  }
}
