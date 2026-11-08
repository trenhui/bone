package com.bone.claim.domain.model.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuotaCapTest {

  @Test
  @DisplayName("两层都配：实际可用 = 较小层剩余（险种 vs 责任 取 min）")
  void minOfTwoLayers() {
    BigDecimal coverageRemaining = new BigDecimal("5000.00");
    BigDecimal liabilityRemaining = new BigDecimal("3000.00");
    Optional<BigDecimal> cap = QuotaCap.minRemaining(coverageRemaining, liabilityRemaining);
    assertThat(cap).contains(new BigDecimal("3000.00"));
  }

  @Test
  @DisplayName("多层取最小：责任 < 险种 < 计划")
  void minOfThreeLayers() {
    Optional<BigDecimal> cap =
        QuotaCap.minRemaining(
            new BigDecimal("9000.00"), new BigDecimal("5000.00"), new BigDecimal("2000.00"));
    assertThat(cap).contains(new BigDecimal("2000.00"));
  }

  @Test
  @DisplayName("某层未配置(null)视为不封顶，在 min 中忽略")
  void nullLayerIgnored() {
    Optional<BigDecimal> cap = QuotaCap.minRemaining(null, new BigDecimal("3000.00"));
    assertThat(cap).contains(new BigDecimal("3000.00"));
  }

  @Test
  @DisplayName("所有层都未配置：返回不限(empty)")
  void allUnlimited() {
    Optional<BigDecimal> cap = QuotaCap.minRemaining(null, null);
    assertThat(cap).isEmpty();
  }

  @Test
  @DisplayName("相等的两层取该值")
  void equalLayers() {
    Optional<BigDecimal> cap =
        QuotaCap.minRemaining(new BigDecimal("1000.00"), new BigDecimal("1000.00"));
    assertThat(cap).contains(new BigDecimal("1000.00"));
  }
}
