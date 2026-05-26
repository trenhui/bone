package com.bone.blueprint.domain.order.valueobject;

import com.bone.core.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void ofRejectsNegative() {
        assertThrows(DomainException.class, () -> Money.of(new BigDecimal("-1")));
    }

    @Test
    void addAndCompare() {
        Money a = Money.of(new BigDecimal("100"));
        Money b = Money.of(new BigDecimal("50"));
        assertEquals(new BigDecimal("150"), a.add(b).toBigDecimal());
        assertTrue(a.greaterThan(b));
    }
}
