package com.bone.masterdata.domain.model.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link ReferenceValue} 纯单测：创建默认启用、停用/启用切换、更新回填。 */
class ReferenceValueTest {

  @Test
  void testCreateDefaultsToEnabled() {
    ReferenceValue v = ReferenceValue.create(1L, 2L, "CNY", "人民币", "156", 1);
    assertTrue(v.getEnabled());
    assertEquals("CNY", v.getValueCode());
    assertEquals("人民币", v.getValueName());
    assertEquals("156", v.getExternalCode());
    assertEquals(1, v.getSortOrder());
  }

  @Test
  void testDisableThenEnable() {
    ReferenceValue v = ReferenceValue.create(1L, 2L, "CNY", "人民币", null, null);
    v.disable();
    assertFalse(v.getEnabled());
    v.enable();
    assertTrue(v.getEnabled());
  }

  @Test
  void testUpdateKeepsSortOrderWhenNull() {
    ReferenceValue v = ReferenceValue.create(1L, 2L, "CNY", "人民币", null, 5);
    v.update("RMB", null, null);
    assertEquals("RMB", v.getValueName());
    assertEquals(5, v.getSortOrder());
  }
}
