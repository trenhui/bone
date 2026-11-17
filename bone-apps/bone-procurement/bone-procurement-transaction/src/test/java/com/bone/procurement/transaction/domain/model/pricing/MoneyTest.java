package com.bone.procurement.transaction.domain.model.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** TDD RED：金额值对象的不变量——scale=4、HALF_UP、逐行舍入后再汇总、跨币种拒绝。 */
class MoneyTest {

  @Test
  void 保留四位小数并按HALF_UP舍入() {
    Money m = Money.of(new BigDecimal("12.34565"), "CNY");
    assertThat(m.amount()).isEqualByComparingTo(new BigDecimal("12.3457"));
    assertThat(m.amount().scale()).isEqualTo(4);
    assertThat(m.currency()).isEqualTo("CNY");
  }

  @Test
  void 逐行舍入后求和与先求和再舍入可能不同() {
    // 0.00005 → HALF_UP 到 4 位 = 0.0001，两行合计 0.0002；先求和 0.0001 再舍入 = 0.0001
    Money a = Money.of(new BigDecimal("0.00005"), "CNY");
    Money b = Money.of(new BigDecimal("0.00005"), "CNY");
    Money sum = a.plus(b);
    assertThat(sum.amount()).isEqualByComparingTo(new BigDecimal("0.0002"));
  }

  @Test
  void 跨币种相加直接拒绝() {
    Money cny = Money.of(new BigDecimal("1"), "CNY");
    Money usd = Money.of(new BigDecimal("1"), "USD");
    assertThatThrownBy(() -> cny.plus(usd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("币种");
  }

  @Test
  void 乘法按行舍入到四位() {
    Money price = Money.of(new BigDecimal("2.3456"), "CNY");
    Money line = price.times(new BigDecimal("1.0001"));
    // 2.3456 * 1.0001 = 2.34583456 → 2.3458
    assertThat(line.amount()).isEqualByComparingTo(new BigDecimal("2.3458"));
    assertThat(line.amount().scale()).isEqualTo(4);
  }
}
