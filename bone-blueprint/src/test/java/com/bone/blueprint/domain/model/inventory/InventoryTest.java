package com.bone.blueprint.domain.model.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 库存聚合纯单测（R8）。
 *
 * <p><b>为什么这些断言值得写</b>：库存是超卖的<strong>唯一</strong>拦截点，且多渠道共享同一份。
 * 「预留不足抛异常」「可用量永不为负」「释放不会凭空造库存」三条不变量一旦被改坏， 表现是线上超卖——不是报错，而是卖出了不存在的货，事后只能靠对账发现。
 */
class InventoryTest {

  private static Inventory stock(int qty) {
    return Inventory.create(1L, 0L, 900001L, "测试商品", "DEFAULT", qty, 0);
  }

  @Test
  void receiveIncreasesAvailable() {
    Inventory inventory = stock(100);

    inventory.receive(50);

    assertEquals(150, inventory.getAvailableQty());
    assertEquals(0, inventory.getReservedQty());
  }

  @Test
  void reserveMovesAvailableToReserved() {
    Inventory inventory = stock(100);

    inventory.reserve(30);

    assertEquals(70, inventory.getAvailableQty(), "预留必须同步减少可用量，否则多渠道会重复卖");
    assertEquals(30, inventory.getReservedQty());
  }

  @Test
  void reserveBeyondAvailableIsRejected() {
    Inventory inventory = stock(10);

    assertThrows(IllegalStateException.class, () -> inventory.reserve(11));
    assertEquals(10, inventory.getAvailableQty(), "失败的预留不得改变任何数量");
  }

  @Test
  void confirmReleaseRoundsTripCorrectly() {
    Inventory inventory = stock(100);

    inventory.reserve(40);
    inventory.confirm(40);

    assertEquals(60, inventory.getAvailableQty());
    assertEquals(0, inventory.getReservedQty(), "确认出库后预留应清零");

    inventory.reserve(20);
    inventory.release(20);

    assertEquals(60, inventory.getAvailableQty(), "释放后可用量回到预留前水平，不得凭空造库存");
    assertEquals(0, inventory.getReservedQty());
  }

  @Test
  void releaseNeverExceedsReserved() {
    Inventory inventory = stock(50);

    inventory.reserve(10);
    inventory.release(999);

    assertEquals(50, inventory.getAvailableQty(), "超量释放只能还回真实预留量");
    assertEquals(0, inventory.getReservedQty());
  }

  @Test
  void deductNeverGoesNegative() {
    Inventory inventory = stock(5);

    assertThrows(IllegalStateException.class, () -> inventory.deduct(6));
    assertEquals(5, inventory.getAvailableQty());
  }

  @Test
  void safetyStockDrivesLowStockFlag() {
    Inventory inventory = Inventory.create(2L, 0L, 900001L, "测试商品", "DEFAULT", 30, 20);

    assertFalse(inventory.isBelowSafetyStock(), "可用 30 > 安全 20，不应预警");

    inventory.reserve(15);

    assertTrue(inventory.isBelowSafetyStock(), "可用 15 <= 安全 20，应触发补货预警");
  }

  @Test
  void negativeInputIsRejected() {
    Inventory inventory = stock(10);
    assertThrows(IllegalArgumentException.class, () -> inventory.receive(0));
    assertThrows(IllegalArgumentException.class, () -> inventory.reserve(-1));
  }
}
