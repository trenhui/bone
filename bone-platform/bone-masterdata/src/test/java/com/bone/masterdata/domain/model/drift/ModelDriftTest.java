package com.bone.masterdata.domain.model.drift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link ModelDrift} 纯单测：检出默认 PENDING、处置回填。 */
class ModelDriftTest {

  @Test
  void testDetectedDefaultsToPendingDestructive() {
    ModelDrift d = ModelDrift.detected(1L, 10L, 20L, "FIELD_REMOVED", "code", "STRING", null, true);
    assertEquals("PENDING", d.getStatus());
    assertTrue(d.getDestructive());
    assertEquals("FIELD_REMOVED", d.getDriftType());
  }

  @Test
  void testHandleFillsHandler() {
    ModelDrift d = ModelDrift.detected(1L, 10L, 20L, "FIELD_ADDED", "code", null, "STRING", false);
    d.handle("SYNCED", 9L);
    assertEquals("SYNCED", d.getStatus());
    assertEquals(9L, d.getHandledBy());
  }
}
