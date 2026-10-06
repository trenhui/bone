package com.bone.blueprint.domain.model.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.blueprint.domain.model.order.event.OrderPaidEvent;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 渠道订单支付状态语义单测（R8纯单测）。
 *
 * <p><b>锁住的核心不变量</b>：渠道订单（淘宝/京东/抖音/拼多多）拉单后必须是 {@code PAID} 且<b>不发 {@link OrderPaidEvent}</b>。
 *
 * <p>为什么这两条都要锁：
 *
 * <ul>
 *   <li>状态必须是 PAID——渠道平台只在买家付款后推送订单，系统里不存在「待支付」态。 落成 CREATED 会让发货守卫把渠道单全部挡下、退款判定永远为false。
 *   <li>不能发 OrderPaidEvent——该事件的订阅者会 {@code confirmStock}「消费预留」， 而拉单事务内已经 {@code reserve}
 *       过；再发一次就是<b>同一批预留被扣两次</b>， 可售量凭空少一份。这是「少卖」型超卖，比多卖更难发现。
 * </ul>
 */
class OrderChannelPaidTest {

  private static Order newChannelOrder() {
    return Order.createFromChannel(
        1L,
        0L,
        555L,
        List.of(OrderItem.create(2L, 1L, 900001L, "样例商品", 2, new BigDecimal("77.70"))),
        "TB",
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "TAOBAO",
        "TBE2E001");
  }

  @Test
  void channelOrderBecomesPaidAfterMarkChannelPaid() {
    Order order = newChannelOrder();
    assertEquals(
        com.bone.blueprint.domain.model.order.valueobject.OrderStatus.CREATED, order.getStatus());

    order.markChannelPaid();

    assertEquals(
        com.bone.blueprint.domain.model.order.valueobject.OrderStatus.PAID, order.getStatus());
    assertTrue(order.getPaidTime() != null, "已支付时刻必须写入，否则对账时间轴缺刻度");
  }

  @Test
  void markChannelPaidEmitsNoPaidEvent() {
    Order order = newChannelOrder();

    order.markChannelPaid();

    assertTrue(
        order.getDomainEvents().stream().noneMatch(OrderPaidEvent.class::isInstance),
        "渠道拉单已在事务内预留库存，若再发 OrderPaidEvent 会导致预留被扣两次");
  }

  @Test
  void markChannelPaidIsIdempotent() {
    Order order = newChannelOrder();
    order.markChannelPaid();

    // 重复调用不应抛异常（幂等），也不应重复迁移
    order.markChannelPaid();

    assertEquals(
        com.bone.blueprint.domain.model.order.valueobject.OrderStatus.PAID, order.getStatus());
  }

  @Test
  void markChannelPaidRejectsNonCreatedOrder() {
    Order order = newChannelOrder();
    order.cancel();

    assertThrows(RuntimeException.class, order::markChannelPaid);
  }

  @Test
  void channelOrderCanShipAfterMarkChannelPaid() {
    // 打通「渠道单 → 可发货」这条此前完全断裂的链路
    Order order = newChannelOrder();
    order.markChannelPaid();

    order.ship();

    assertEquals(
        com.bone.blueprint.domain.model.order.valueobject.OrderStatus.SHIPPED, order.getStatus());
  }
}
