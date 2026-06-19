package com.bone.blueprint.domain.order.valueobject;

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
  }
}
