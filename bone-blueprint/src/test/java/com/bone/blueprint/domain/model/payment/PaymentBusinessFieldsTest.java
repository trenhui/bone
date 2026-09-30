package com.bone.blueprint.domain.model.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 支付单真实场景字段单测：币种（跨境与对账前置）+ 支付有效期（超时关单驱动）。 */
class PaymentBusinessFieldsTest {

  private Payment create(String currency, Instant expireAt) {
    return Payment.create(
        1L,
        1L,
        100L,
        200L,
        new BigDecimal("200"),
        currency,
        com.bone.blueprint.domain.model.payment.valueobject.PaymentChannel.SIMULATED,
        "http://pay",
        expireAt);
  }

  @Test
  @DisplayName("币种：缺省与空白回落 CNY，绝不留下「无币种金额」这种 ambiguous 状态")
  void currency_shouldDefaultToCny() {
    assertEquals("CNY", create(null, null).getCurrency());
    assertEquals("CNY", create("  ", null).getCurrency());
    assertEquals("USD", create("usd", null).getCurrency());
  }

  @Test
  @DisplayName("币种非法（非 3 位大写字母）→ 领域异常，脏数据不得入库")
  void invalidCurrency_shouldBeRejected() {
    assertThrows(DomainException.class, () -> create("人民币", null));
    assertThrows(DomainException.class, () -> create("US", null));
    assertThrows(DomainException.class, () -> create("USDD", null));
  }

  @Test
  @DisplayName("支付有效期：未设 → 永不判过期；已过 → isExpired 为真（驱动超时关单）")
  void payExpireAt_shouldDriveExpiration() {
    assertFalse(create("CNY", null).isExpired());
    assertFalse(create("CNY", Instant.now().plus(30, ChronoUnit.MINUTES)).isExpired());
    assertTrue(create("CNY", Instant.now().minus(1, ChronoUnit.MINUTES)).isExpired());
  }
}
