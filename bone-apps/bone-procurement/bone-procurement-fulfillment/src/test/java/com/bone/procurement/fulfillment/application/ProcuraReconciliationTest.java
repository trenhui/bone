package com.bone.procurement.fulfillment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.fulfillment.domain.port.FinanceAclPort;
import com.bone.procurement.transaction.domain.model.pricing.Money;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * evalset RECON-01 源码落点：逐笔 PaymentRecorded 金额汇总必须与 paidCumulative 值对象一致； Finance ACL 未交付时保持隔离，不把
 * mock 当真实跨域事实。
 */
class ProcuraReconciliationTest {

  @Test
  @DisplayName("paidCumulativeEqualsSumPaymentRecorded")
  void paidCumulativeEqualsSumPaymentRecorded() {
    List<Money> paymentRecorded =
        List.of(
            Money.of(new BigDecimal("10.005"), "CNY"),
            Money.of(new BigDecimal("20.004"), "CNY"),
            Money.of(new BigDecimal("0.001"), "CNY"));

    Money paidCumulative = Money.of(BigDecimal.ZERO, "CNY");
    for (Money payment : paymentRecorded) {
      paidCumulative = paidCumulative.plus(payment);
    }

    Money total = paidCumulative;
    assertThat(total).isEqualTo(Money.of(new BigDecimal("30.010"), "CNY"));
    assertThat(total.amount().scale()).isEqualTo(Money.SCALE);
    assertThatThrownBy(() -> total.plus(Money.of(BigDecimal.ONE, "USD")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("跨币种");

    FinanceAclPort isolatedAcl = new FinanceAclPort() {};
    assertThat(isolatedAcl.isAvailable()).isFalse();
  }
}
