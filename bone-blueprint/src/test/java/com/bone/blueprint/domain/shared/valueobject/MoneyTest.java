package com.bone.blueprint.domain.shared.valueobject;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void ofRejectsNegative() {
    assertThrows(DomainException.class, () -> Money.of(new BigDecimal("-1")));
  }

  @Test
  void ofRejectsNull() {
    assertThrows(DomainException.class, () -> Money.of(null));
  }

  @Test
  void multiply() {
    Money unit = Money.of(new BigDecimal("10"));
    assertEquals(new BigDecimal("30"), unit.multiply(3).toBigDecimal());
  }

  @Test
  void addAndCompare() {
    Money a = Money.of(new BigDecimal("100"));
    Money b = Money.of(new BigDecimal("50"));
    assertEquals(new BigDecimal("150"), a.add(b).toBigDecimal());
    assertTrue(a.greaterThan(b));
    assertFalse(b.greaterThan(a));
  }

  @Test
  void subtract() {
    Money a = Money.of(new BigDecimal("100"));
    Money b = Money.of(new BigDecimal("30"));
    assertEquals(new BigDecimal("70"), a.subtract(b).toBigDecimal());
  }

  /** 金额不可为负：构造期即拦截，无需调用方自行兜底。 */
  @Test
  void subtractBelowZeroRejects() {
    Money a = Money.of(new BigDecimal("30"));
    Money b = Money.of(new BigDecimal("100"));
    assertThrows(DomainException.class, () -> a.subtract(b));
  }

  @Test
  void comparisonHelpers() {
    Money hundred = Money.of(new BigDecimal("100"));
    Money hundredAgain = Money.of(new BigDecimal("100.00"));
    Money fifty = Money.of(new BigDecimal("50"));

    assertTrue(hundred.greaterThanOrEqual(hundredAgain));
    assertTrue(fifty.lessThan(hundred));
    assertFalse(hundred.isZero());
    assertTrue(Money.zero().isZero());
  }

  /** 等值判定按数值比较（BigDecimal scale 不同仍相等）——金额比较的正确语义。 */
  @Test
  void equalityIgnoresScale() {
    assertEquals(Money.of(new BigDecimal("100")), Money.of(new BigDecimal("100.00")));
    assertEquals(
        Money.of(new BigDecimal("100")).hashCode(), Money.of(new BigDecimal("100.00")).hashCode());
  }
}
