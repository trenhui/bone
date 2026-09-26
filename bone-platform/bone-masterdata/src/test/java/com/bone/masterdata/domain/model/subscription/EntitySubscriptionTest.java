package com.bone.masterdata.domain.model.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import org.junit.jupiter.api.Test;

/** {@link EntitySubscription} 纯单测：PENDING → ACTIVE → REVOKED 状态机与非法迁移防护。 */
class EntitySubscriptionTest {

  @Test
  void testDefaultsToPendingRead() {
    EntitySubscription s = EntitySubscription.request(1L, 10L, 20L, null, 9L);
    assertEquals("PENDING", s.getStatus());
    assertEquals("READ", s.getSubscribeMode());
  }

  @Test
  void testApproveOnlyFromPending() {
    EntitySubscription s = EntitySubscription.request(1L, 10L, 20L, "EVENT", 9L);
    s.approve(8L);
    assertEquals("ACTIVE", s.getStatus());
    assertEquals(8L, s.getApprovedBy());
    assertThrows(DomainException.class, () -> s.approve(7L));
  }

  @Test
  void testRevokeOnlyFromActive() {
    EntitySubscription s = EntitySubscription.request(1L, 10L, 20L, "READ", 9L);
    assertThrows(DomainException.class, s::revoke);
    s.approve(8L);
    s.revoke();
    assertEquals("REVOKED", s.getStatus());
  }
}
