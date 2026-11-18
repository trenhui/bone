package com.bone.procurement.transaction.domain.model.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 金额值对象：币种 + 四位小数定点数。
 *
 * <p>不变量：amount 恒为 scale=4 / HALF_UP；跨币种运算直接拒绝；每一笔运算各自舍入， 使「先逐行舍入再汇总」成为可验证行为（避免总额与行合计漂移）。
 */
public final class Money {

  public static final int SCALE = 4;
  public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

  private final BigDecimal amount;
  private final String currency;

  private Money(BigDecimal amount, String currency) {
    this.amount = normalize(amount);
    this.currency = currency;
  }

  public static Money of(BigDecimal amount, String currency) {
    if (amount == null || currency == null || currency.isBlank()) {
      throw new IllegalArgumentException("金额与币种均不可为空");
    }
    return new Money(amount, currency.trim().toUpperCase());
  }

  private static BigDecimal normalize(BigDecimal v) {
    return v.setScale(SCALE, ROUNDING);
  }

  public BigDecimal amount() {
    return amount;
  }

  public String currency() {
    return currency;
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  public Money times(BigDecimal factor) {
    if (factor == null) {
      throw new IllegalArgumentException("乘数不可为空");
    }
    return new Money(amount.multiply(factor), currency);
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException("跨币种运算被拒绝：" + currency + " vs " + other.currency);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Money other)) return false;
    return currency.equals(other.currency) && amount.compareTo(other.amount) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount.stripTrailingZeros(), currency);
  }

  @Override
  public String toString() {
    return amount.toPlainString() + " " + currency;
  }
}
