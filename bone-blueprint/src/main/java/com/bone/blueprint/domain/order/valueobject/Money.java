package com.bone.blueprint.domain.order.valueobject;

import com.bone.core.exception.DomainException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 金额值对象（不可变）。
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

    public boolean greaterThan(Money other) {
        Objects.requireNonNull(other, "other");
        return amount.compareTo(other.amount) > 0;
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
}
