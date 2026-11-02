package com.bone.claim.domain.model.adjustment.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A1 契约：{@link Money#proRata} 必须保证 Σ各份 ≡ total（分位守恒 I1）。
 *
 * <p>亚分尾差（DOWN 取整产生）必须全额回补到权重最大的一份，不得以 {@code round(weight)} 钳制吞掉——
 * 否则明细加不回合计，违反监管口径「明细可加回合计」。本测试含反证用例（{@link #conservesTinyRemainderRoundingEdge}）， 旧代码（残差被 {@code
 * min(round(weight))} 钳制）会在此转红，确保不是假绿。
 */
class MoneyTest {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

  private void assertConserves(BigDecimal total, List<BigDecimal> weights) {
    List<BigDecimal> shares = Money.proRata(total, weights);
    BigDecimal sum = ZERO;
    for (BigDecimal s : shares) {
      assertTrue(s.compareTo(ZERO) >= 0, "share must be non-negative: " + s);
      sum = sum.add(s);
    }
    assertEquals(
        0,
        sum.compareTo(Money.round(total)),
        "Σ shares must equal total; got sum=" + sum + " total=" + Money.round(total));
  }

  @Test
  void conservesEqualWeights() {
    assertConserves(
        new BigDecimal("100.00"), List.of(new BigDecimal("50.00"), new BigDecimal("50.00")));
  }

  @Test
  void conservesUnequalWeights() {
    assertConserves(
        new BigDecimal("100.00"),
        List.of(new BigDecimal("60.00"), new BigDecimal("30.00"), new BigDecimal("30.00")));
  }

  @Test
  void conservesTinyRemainderRoundingEdge() {
    // 触发 DOWN 取整尾差：target 略低于 weightSum，残差回补到最大份，不得被 weight 钳制破坏守恒
    assertConserves(
        new BigDecimal("100.00"),
        List.of(new BigDecimal("50.00"), new BigDecimal("50.00"), new BigDecimal("0.01")));
  }

  @Test
  void conservesSingleLargestDominates() {
    assertConserves(
        new BigDecimal("100.00"),
        List.of(new BigDecimal("99.00"), new BigDecimal("1.00"), new BigDecimal("1.00")));
  }

  @Test
  void zeroWeightsReturnsAllZero() {
    // 权重全零：无基数可分，方法约定返回全零（Money.proRata 文档契约），不抛异常
    List<BigDecimal> shares =
        Money.proRata(
            new BigDecimal("10.00"), List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    assertEquals(3, shares.size());
    for (BigDecimal s : shares) {
      assertEquals(0, s.compareTo(ZERO), "权重全零时各份必须为 0: " + s);
    }
  }

  @Test
  void emptyWeightsReturnsEmpty() {
    assertTrue(Money.proRata(new BigDecimal("10.00"), List.of()).isEmpty());
  }
}
