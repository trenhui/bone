package com.bone.blueprint.domain.model.shared.valueobject;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * 金额值对象（不可变，**跨聚合共享**）。
 *
 * <p>为何在 {@code domain/shared/valueobject} 而非某个聚合包下：金额同时被订单（{@code Order}、{@code
 * OrderItem}）与支付（{@code Payment}）使用。若放在 {@code domain/order/valueobject}，支付聚合需反向依赖订单包，
 * 造成限界上下文的概念耦合；共享值对象独立成包，两个上下文各自引用，互不依赖。
 *
 * <p><b>统一口径</b>：金额运算一律经本类，禁止在领域内裸用 {@code BigDecimal} 比较或运算。
 */
public final class Money {

  private final BigDecimal amount;

  private Money(BigDecimal amount) {
    if (amount == null) {
      throw new DomainException("金额不能为空");
    }
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new DomainException("金额不能为负");
    }
    this.amount = amount;
  }

  public static Money of(BigDecimal amount) {
    return new Money(amount);
  }

  public static Money zero() {
    return new Money(BigDecimal.ZERO);
  }

  public Money add(Money other) {
    Objects.requireNonNull(other, "other");
    return new Money(amount.add(other.amount));
  }

  public Money multiply(int quantity) {
    if (quantity <= 0) {
      throw new DomainException("数量必须大于0");
    }
    return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
  }

  /**
   * 相减。结果为负时由构造器抛 {@link DomainException}（金额不可为负）。
   *
   * <p>调用方若需给出业务化错误信息（如「退款金额不能超过已付金额」），应先自行比较再调用。
   */
  public Money subtract(Money other) {
    Objects.requireNonNull(other, "other");
    return new Money(amount.subtract(other.amount));
  }

  public boolean greaterThan(Money other) {
    Objects.requireNonNull(other, "other");
    return amount.compareTo(other.amount) > 0;
  }

  public boolean greaterThanOrEqual(Money other) {
    Objects.requireNonNull(other, "other");
    return amount.compareTo(other.amount) >= 0;
  }

  public boolean lessThan(Money other) {
    Objects.requireNonNull(other, "other");
    return amount.compareTo(other.amount) < 0;
  }

  public boolean isZero() {
    return amount.compareTo(BigDecimal.ZERO) == 0;
  }

  public BigDecimal toBigDecimal() {
    return amount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Money money)) {
      return false;
    }
    return amount.compareTo(money.amount) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount.stripTrailingZeros());
  }

  @Override
  public String toString() {
    return amount.toPlainString();
  }
}
