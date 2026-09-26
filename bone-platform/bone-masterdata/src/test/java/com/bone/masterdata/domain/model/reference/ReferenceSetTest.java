package com.bone.masterdata.domain.model.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link ReferenceSet} / {@link ReferenceValue} 纯单测：默认发布态、停用与归档。 */
class ReferenceSetTest {

  @Test
  void testSetDefaultsToPublished() {
    ReferenceSet s = ReferenceSet.create(1L, "CURRENCY", "货币", "ISO4217", null);
    assertEquals("PUBLISHED", s.getStatus());
    s.archive();
    assertEquals("ARCHIVED", s.getStatus());
  }

  @Test
  void testValueEnableDisable() {
    ReferenceValue v = ReferenceValue.create(2L, 1L, "CNY", "人民币", "156", 1);
    assertTrue(v.getEnabled());
    v.disable();
    assertFalse(v.getEnabled());
    v.enable();
    assertTrue(v.getEnabled());
  }
}
