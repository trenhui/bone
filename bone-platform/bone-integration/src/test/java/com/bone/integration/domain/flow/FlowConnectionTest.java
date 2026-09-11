package com.bone.integration.domain.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import org.junit.jupiter.api.Test;

/** {@link FlowConnection} 纯单测：连线端点约束与条件更新语义（无容器）。 */
class FlowConnectionTest {

  @Test
  void testCreateRecordsEndpoints() {
    FlowConnection connection = FlowConnection.create(1L, 100L, 11L, 12L, null);

    assertEquals(100L, connection.getFlowId());
    assertEquals(11L, connection.getSourceNodeId());
    assertEquals(12L, connection.getTargetNodeId());
    assertNull(connection.getCondition());
  }

  @Test
  void testCreateRejectsMissingEndpoints() {
    assertThrows(DomainException.class, () -> FlowConnection.create(1L, null, 11L, 12L, "c"));
    assertThrows(DomainException.class, () -> FlowConnection.create(1L, 100L, null, 12L, "c"));
    assertThrows(DomainException.class, () -> FlowConnection.create(1L, 100L, 11L, null, "c"));
  }

  @Test
  void testUpdateOverridesCondition() {
    FlowConnection connection = FlowConnection.create(1L, 100L, 11L, 12L, "amount > 100");

    connection.update("amount > 1000");
    assertEquals("amount > 1000", connection.getCondition());

    connection.update(null);
    assertNull(connection.getCondition());
  }
}
