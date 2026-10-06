package com.bone.blueprint.domain.model.shipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.blueprint.domain.model.shipment.event.ShipmentShippedEvent;
import com.bone.blueprint.domain.model.shipment.event.ShipmentSignedEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 发货单 → 订单联动事件（R8 纯单测）。
 *
 * <p><b>为什么这些事件必须存在</b>：修复前发货链路只推进 Shipment 状态，从不推进 Order 状态， 生产库实测出现「发货单 SIGNED、订单仍
 * CREATED」，甚至「已取消订单被发货并签收」。 本测试把联动契约锁住：<b>发货必产生事件、签收必产生事件、事件带orderId</b>—— 少任何一条，两套状态机就会重新割裂，且E2E
 * 全绿也发现不了（E2E 曾长期漏检此项）。
 */
class ShipmentOrderSyncEventTest {

  private static Shipment shipmentWithOrder(Long orderId) {
    return Shipment.create(1L, 0L, orderId, "TAOBAO", "张三", "13800000000", "测试地址");
  }

  @Test
  void shipRecordsEventCarryingOrderId() {
    Shipment shipment = shipmentWithOrder(777L);

    shipment.ship("顺丰速运", "SF1234567890", Instant.now());

    List<ShipmentShippedEvent> events =
        shipment.getDomainEvents().stream()
            .filter(ShipmentShippedEvent.class::isInstance)
            .map(ShipmentShippedEvent.class::cast)
            .toList();
    assertEquals(1, events.size(), "发货必须且只能产生一条联动事件");
    ShipmentShippedEvent event = events.get(0);
    assertEquals(777L, event.orderId());
    assertEquals("TAOBAO", event.channelCode());
    assertEquals("SF1234567890", event.trackingNo());
  }

  @Test
  void signRecordsEventCarryingOrderId() {
    Shipment shipment = shipmentWithOrder(777L);
    shipment.ship("顺丰速运", "SF1234567890", Instant.now());
    // ship 事件先清空，避免与 sign 事件混淆
    shipment.clearDomainEvents();

    shipment.sign(Instant.now());

    List<ShipmentSignedEvent> events =
        shipment.getDomainEvents().stream()
            .filter(ShipmentSignedEvent.class::isInstance)
            .map(ShipmentSignedEvent.class::cast)
            .toList();
    assertEquals(1, events.size(), "签收必须且只能产生一条联动事件");
    assertEquals(777L, events.get(0).orderId());
  }

  @Test
  void shipmentWithoutOrderRecordsNoEvent() {
    // orderId 为 null（手工发货/历史数据）时不应产生联动事件，
    // 否则处理器会拿着null orderId 去查订单并刷无谓WARN。
    Shipment shipment = Shipment.create(1L, 0L, null, "TAOBAO", "张三", "13800000000", "地址");

    shipment.ship("顺丰速运", "SF999", Instant.now());

    assertTrue(
        shipment.getDomainEvents().stream().noneMatch(ShipmentShippedEvent.class::isInstance),
        "无关联订单的发货单不应产生联动事件");
  }
}
