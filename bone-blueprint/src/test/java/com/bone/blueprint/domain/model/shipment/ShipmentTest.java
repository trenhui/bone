package com.bone.blueprint.domain.model.shipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.blueprint.domain.model.shipment.valueobject.ShipmentStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 发货单聚合纯单测（R8）。
 *
 * <p><b>核心不变量</b>：渠道回传失败<strong>不回退发货状态</strong>。货已经发出去了， 把状态退回「未发货」会制造与物理事实不符的假象，客服与用户看到的都是错的。
 * 正确形态是保留 SHIPPED + channelAck=false + 记录原因，由补偿任务重试。
 */
class ShipmentTest {

  private static Shipment newShipment() {
    return Shipment.create(1L, 0L, 777L, "TAOBAO", "张三", "13800000000", "测试地址");
  }

  @Test
  void createStartsCreatedWithShipmentNo() {
    Shipment shipment = newShipment();

    assertEquals(ShipmentStatus.CREATED, shipment.getStatus());
    assertNotNull(shipment.getShipmentNo());
    assertTrue(shipment.getShipmentNo().startsWith("SF"));
    assertFalse(shipment.getChannelAck());
  }

  @Test
  void shipRequiresTrackingNo() {
    Shipment shipment = newShipment();

    assertThrows(IllegalArgumentException.class, () -> shipment.ship("顺丰速运", "  ", Instant.now()));
    assertEquals(ShipmentStatus.CREATED, shipment.getStatus(), "无运单号的「已发货」是虚假发货，必须拦住");
  }

  @Test
  void shipMovesToShipped() {
    Shipment shipment = newShipment();

    shipment.ship("顺丰速运", "SF123", Instant.now());

    assertEquals(ShipmentStatus.SHIPPED, shipment.getStatus());
    assertEquals("SF123", shipment.getTrackingNo());
    assertNotNull(shipment.getShippedAt());
  }

  @Test
  void channelAckFailureDoesNotRollbackStatus() {
    Shipment shipment = newShipment();
    shipment.ship("顺丰速运", "SF123", Instant.now());
    shipment.markChannelAcked(Instant.now());

    shipment.markChannelAckFailed("TIMEOUT");

    assertEquals(ShipmentStatus.SHIPPED, shipment.getStatus(), "回传失败不得回退发货状态");
    assertFalse(shipment.getChannelAck());
    assertEquals("TIMEOUT", shipment.getFailReason());
  }

  @Test
  void signOnlyFromShippedOrInTransit() {
    Shipment shipment = newShipment();

    assertThrows(IllegalStateException.class, () -> shipment.sign(Instant.now()), "待发货不能直接签收");

    shipment.ship("顺丰速运", "SF123", Instant.now());
    shipment.markInTransit();
    shipment.sign(Instant.now());

    assertEquals(ShipmentStatus.SIGNED, shipment.getStatus());
    assertNotNull(shipment.getSignedAt());
  }

  @Test
  void doubleShipIsRejected() {
    Shipment shipment = newShipment();
    shipment.ship("顺丰速运", "SF123", Instant.now());

    assertThrows(IllegalStateException.class, () -> shipment.ship("中通快递", "ZTO456", Instant.now()));
    assertEquals("SF123", shipment.getTrackingNo(), "重复发货不得覆盖原运单号");
  }
}
