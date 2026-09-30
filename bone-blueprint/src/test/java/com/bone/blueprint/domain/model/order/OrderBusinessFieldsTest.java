package com.bone.blueprint.domain.model.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 真实交易场景字段（机制 A）的聚合行为单测。
 *
 * <p>覆盖：业务单号生成、订单来源渠道、金额三口径（明细 + 运费 − 优惠）、0 元单下限保护、 支付完成时刻随状态迁移写入。
 */
class OrderBusinessFieldsTest {

  private static OrderItem item(long id, int qty, String price) {
    return OrderItem.create(id, 1L, 10L, "商品", qty, new BigDecimal(price));
  }

  private static Order order(String channel, String freight, String discount) {
    return Order.create(
        9001L,
        1L,
        100L,
        Collections.singletonList(item(1L, 2, "100")),
        channel,
        freight == null ? null : new BigDecimal(freight),
        discount == null ? null : new BigDecimal(discount));
  }

  @Test
  @DisplayName("业务单号：创建即生成，SO + 日期 + id，且可由 id 重复推导（对账友好）")
  void orderNo_shouldBeGeneratedAndDerivable() {
    Order o1 = order("APP", null, null);
    Order o2 = Order.create(9001L, 1L, 100L, Collections.singletonList(item(1L, 1, "50")));

    assertNotNull(o1.getOrderNo());
    // 同 id 推导出的单号一致（对账可按 id 反推单号，无需额外持久化映射）
    assertEquals(o1.getOrderNo(), o2.getOrderNo());
    assertTrue(o1.getOrderNo().startsWith("SO"));
    assertTrue(o1.getOrderNo().endsWith("9001"));
    assertEquals("APP", o1.getChannelSource());
    assertNull(o2.getChannelSource());
  }

  @Test
  @DisplayName("金额三口径：总额 = 明细小计之和 + 运费 − 优惠")
  void totalAmount_shouldFollowThreePartFormula() {
    // 明细 2×100=200，运费 12，优惠 30 → 182
    assertEquals(0, new BigDecimal("182").compareTo(order("APP", "12", "30").getTotalAmount()));
    // 缺省运费/优惠时退化为「明细小计之和」，与改造前口径一致（无回归）
    assertEquals(0, new BigDecimal("200").compareTo(order(null, null, null).getTotalAmount()));
  }

  @Test
  @DisplayName("优惠大于应付 → 0 元单（不抛「金额不能为负」，保留真实业务语义）")
  void totalAmount_shouldFloorAtZero_whenDiscountExceedsPayable() {
    Order zero = order("APP", "0", "9999");
    assertEquals(0, BigDecimal.ZERO.compareTo(zero.getTotalAmount()));
  }

  @Test
  @DisplayName("负数运费/优惠由 Money 拦截（领域不变量，非业务分支）")
  void negativeAmountComponents_shouldBeRejected() {
    assertThrows(DomainException.class, () -> order("APP", "-1", null));
    assertThrows(DomainException.class, () -> order("APP", null, "-1"));
  }

  @Test
  @DisplayName("支付完成时刻：与 CREATED→PAID 状态迁移同点写入")
  void paidTime_shouldBeSetOnConfirmPaid() {
    Order o = order("APP", null, null);
    assertNull(o.getPaidTime());

    o.confirmPaid();

    assertNotNull(o.getPaidTime());
    assertTrue(!o.getPaidTime().isAfter(Instant.now()));
  }

  @Test
  @DisplayName("明细变更重算总额时仍保留运费与优惠分量")
  void recalculate_shouldKeepFreightAndDiscount() {
    Order o =
        Order.create(
            9002L,
            1L,
            100L,
            List.of(item(1L, 1, "100")),
            "H5",
            new BigDecimal("10"),
            new BigDecimal("5"));
    assertEquals(0, new BigDecimal("105").compareTo(o.getTotalAmount()));

    o.addItem(item(2L, 1, "50"));
    assertEquals(0, new BigDecimal("155").compareTo(o.getTotalAmount()));
    assertEquals("H5", o.getChannelSource());
  }
}
