package com.bone.blueprint.domain.model.replenishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.blueprint.domain.model.replenishment.valueobject.ReplenishmentStatus;
import com.bone.core.exception.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 补货单聚合纯单测。 */
class ReplenishmentOrderTest {

  private static ReplenishmentOrder draft() {
    return ReplenishmentOrder.create(
        1L, 0L, 900001L, "测试商品", "DEFAULT", 50, 50, 5, 20, "SUP-A", null);
  }

  @Test
  void suggestQuantityFillsToDoubleSafety() {
    assertEquals(35, ReplenishmentOrder.suggestQuantity(5, 20));
    assertEquals(1, ReplenishmentOrder.suggestQuantity(100, 0));
  }

  @Test
  void happyPathDraftToReceived() {
    ReplenishmentOrder order = draft();
    Instant now = Instant.parse("2026-10-09T08:00:00Z");

    order.submit(now);
    assertEquals(ReplenishmentStatus.SUBMITTED, order.getStatus());
    order.approve(now);
    assertEquals(ReplenishmentStatus.APPROVED, order.getStatus());
    order.markReceived(now);
    assertEquals(ReplenishmentStatus.RECEIVED, order.getStatus());
    assertTrue(order.getReplenishNo().startsWith("RP"));
  }

  @Test
  void cannotReceiveWithoutApprove() {
    ReplenishmentOrder order = draft();
    order.submit(Instant.now());
    assertThrows(DomainException.class, () -> order.markReceived(Instant.now()));
  }

  @Test
  void cancelFromDraft() {
    ReplenishmentOrder order = draft();
    order.cancel();
    assertEquals(ReplenishmentStatus.CANCELLED, order.getStatus());
    assertThrows(DomainException.class, () -> order.submit(Instant.now()));
  }
}
