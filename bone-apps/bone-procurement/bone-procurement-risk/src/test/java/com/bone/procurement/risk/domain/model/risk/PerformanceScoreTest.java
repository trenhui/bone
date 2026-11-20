package com.bone.procurement.risk.domain.model.risk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 绩效红线：分数是只读投影，不得直接产出供应商授信/淘汰决定。 */
class PerformanceScoreTest {

  @Test
  @DisplayName("绩效分数不得自动决定供应商去留（owner 红线）")
  void scoreCannotAutoDecide() {
    PerformanceScore s = new PerformanceScore();
    s.setTotalScore(java.math.BigDecimal.valueOf(12.5));
    assertThat(s.canDecideSupplierAutomatically()).isFalse();
  }
}
